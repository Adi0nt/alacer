package net.adiont.alacer.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.entity.living.player.PlayerEntity;

/**
 * Vanilla's per-frame OpenGL state reads in the world render, optionally answered from CPU-side
 * copies ({@code render.avoidMatrixReads}).
 *
 * <p>Every {@code glGet*} makes the client thread wait until the driver has processed all
 * commands issued so far. Once the per-frame GL error checks are rate-limited, the first such
 * read in a frame is {@code Camera.setup} (modelview, projection, viewport), followed by two
 * {@code Frustum.getInstance()} computations (projection and modelview each). When the driver
 * runs its own submission thread, that first read absorbs the wait the error checks used to.
 * Render chunks also read back a constant matrix in {@code RenderChunk.glTransform} whenever
 * they are repositioned.
 *
 * <p>With the option on:
 * <ul>
 * <li>From the start of {@code GameRenderer.setupCamera} until the frustum culler exists, a
 * {@link MatrixTracker} follows every matrix operation made through {@code GlStateManager} and
 * the {@code gluPerspective} call in {@code setupCamera}. {@code setupCamera} loads both matrices
 * with the identity first, so their earlier contents do not matter. The viewport is the last
 * one set through {@code GlStateManager.viewport}.</li>
 * <li>The reads inside {@code Camera.setup} and {@code Frustum.compute}, during the world render
 * only, are answered from those copies. If a copy is unknown (for example a matrix was popped
 * past the start of tracking), that read goes to OpenGL as in vanilla.</li>
 * <li>{@code new FrustumCuller()}, which recomputes the frustum from unchanged GL state, uses the
 * frustum computed just before instead.</li>
 * <li>{@code RenderChunk.glTransform} gets the matrix it builds (identity, translate, scale,
 * translate) computed on the CPU.</li>
 * </ul>
 *
 * <p>Code that changes the matrices with direct {@code GL11} calls inside {@code setupCamera}
 * would not be seen. Therefore, about once per second (in the frame where the rate-limited GL
 * error checks run, which waits for the driver anyway) the real values are read and compared;
 * after a mismatch, OpenGL is read every frame again for the rest of the session (logged once).
 * With {@code render.avoidMatrixReads.verify} the real values are read and compared in every
 * frame and every chunk reposition, and used, so the game behaves exactly as vanilla.
 *
 * <p>Everything here runs on the client thread.
 */
public final class GlStateReads {

	private static final int GL_VIEWPORT = 0x0BA2;
	private static final int GL_MODELVIEW_MATRIX = 0x0BA6;
	private static final int GL_PROJECTION_MATRIX = 0x0BA7;
	/** Relative tolerance; driver matrix arithmetic may differ from Java's in the last bits. */
	private static final float TOLERANCE = 1.0e-4F;
	private static final int LOGGED_MISMATCHES = 10;
	private static final long SUMMARY_EVERY = 20_000;

	/** Read by the {@code GlStateManager} hooks; true only while the camera matrices are tracked. */
	public static boolean tracking;

	private static final MatrixTracker TRACKER = new MatrixTracker();
	private static final float[] TRACKED = new float[16];
	private static final float[] CHUNK_MATRIX = chunkMatrix();
	private static final int[] VIEWPORT = new int[4];
	private static boolean viewportKnown;

	/** Inside {@code Camera.setup} or the frustum computation of the world render. */
	private static boolean inWorldRead;
	/** This frame's world-render reads are answered from the tracker. */
	private static boolean serve;
	/** ... and also read from OpenGL and compared (check frame or verification). */
	private static boolean compare;
	/** A periodic check found a difference: read from OpenGL for the rest of the session. */
	private static boolean fallback;
	private static FrustumData frustum;
	private static final float[][] PLANES = new float[6][4];

	private static long comparisons;
	private static long mismatches;
	private static long untrackedFrames;
	private static float maxError;
	private static long chunkComparisons;
	private static long chunkMismatches;
	private static float chunkMaxError;

	private GlStateReads() {
	}

	private static boolean enabled(AlacerConfig config) {
		return config.avoidMatrixReads && !fallback;
	}

	// --- Tracking (GlStateManager hooks and setupCamera) ---

	public static MatrixTracker tracker() {
		return TRACKER;
	}

	public static void onViewport(int x, int y, int width, int height) {
		VIEWPORT[0] = x;
		VIEWPORT[1] = y;
		VIEWPORT[2] = width;
		VIEWPORT[3] = height;
		viewportKnown = true;
	}

	/** Head of {@code GameRenderer.setupCamera}. */
	public static void beginCameraSetup() {
		tracking = enabled(Alacer.get().config());
		if (tracking) {
			TRACKER.reset();
		}
	}

	/** Replaces {@code Project.gluPerspective} in {@code setupCamera}, which calls {@code glMultMatrix} directly. */
	public static void perspective(float fovy, float aspect, float zNear, float zFar, Operation<Void> vanilla) {
		if (tracking) {
			TRACKER.perspective(fovy, aspect, zNear, zFar);
		}
		vanilla.call(fovy, aspect, zNear, zFar);
	}

	/** End of the world render method, in case it returned before the culler was created. */
	public static void endWorldRender() {
		tracking = false;
		inWorldRead = false;
	}

	// --- World-render call sites ---

	/** Replaces {@code Camera.setup(player, thirdPerson)} in the world render. */
	public static void cameraSetup(PlayerEntity player, boolean thirdPerson, Operation<Void> vanilla) {
		Alacer alacer = Alacer.get();
		alacer.begin(Section.CAMERA_SETUP);
		AlacerConfig config = alacer.config();
		serve = tracking && viewportKnown
			&& TRACKER.isKnown(MatrixTracker.MODELVIEW) && TRACKER.isKnown(MatrixTracker.PROJECTION);
		if (tracking && !serve) {
			untrackedFrames++;
		}
		compare = serve && (config.verifyAvoidMatrixReads || GlErrorChecks.checksThisFrame());
		inWorldRead = true;
		vanilla.call(player, thirdPerson);
		inWorldRead = false;
		alacer.end(Section.CAMERA_SETUP);
	}

	/** Replaces the world render's {@code Frustum.getInstance()}. */
	public static FrustumData frustum(Operation<FrustumData> vanilla) {
		Alacer alacer = Alacer.get();
		alacer.begin(Section.FRUSTUM);
		inWorldRead = true;
		FrustumData result = vanilla.call();
		inWorldRead = false;
		frustum = result;
		alacer.end(Section.FRUSTUM);
		return result;
	}

	/** Replaces the world render's {@code new FrustumCuller()}, which would compute the frustum again. */
	public static FrustumCuller frustumCuller(Operation<FrustumCuller> vanilla) {
		Alacer alacer = Alacer.get();
		alacer.begin(Section.FRUSTUM_CULLER);
		tracking = false;
		AlacerConfig config = alacer.config();
		FrustumData previous = frustum;
		frustum = null;
		FrustumCuller culler;
		if (!config.avoidMatrixReads || previous == null) {
			culler = vanilla.call();
		} else if (config.verifyAvoidMatrixReads) {
			for (int i = 0; i < 6; i++) {
				System.arraycopy(previous.frustum[i], 0, PLANES[i], 0, 4);
			}
			// Vanilla recomputes the same instance from OpenGL; its planes must be unchanged.
			culler = vanilla.call();
			for (int i = 0; i < 6; i++) {
				if (!Arrays.equals(PLANES[i], previous.frustum[i])) {
					mismatches++;
					logMismatch("frustum culler plane " + i, PLANES[i], previous.frustum[i], 4);
					break;
				}
			}
		} else {
			culler = new FrustumCuller(previous);
		}
		alacer.end(Section.FRUSTUM_CULLER);
		return culler;
	}

	// --- The reads themselves (inside Camera.setup and Frustum.compute) ---

	/** Replaces {@code GlStateManager.getFloat(pname, buffer)} inside {@code Camera.setup} and {@code Frustum.compute}. */
	public static void readMatrix(int pname, FloatBuffer buffer, Operation<Void> vanilla) {
		int mode = pname == GL_MODELVIEW_MATRIX ? MatrixTracker.MODELVIEW : pname == GL_PROJECTION_MATRIX ? MatrixTracker.PROJECTION : 0;
		if (!inWorldRead || !serve || mode == 0) {
			vanilla.call(pname, buffer);
			return;
		}
		TRACKER.get(mode, TRACKED);
		int p = buffer.position();
		if (compare) {
			vanilla.call(pname, buffer);
			comparisons++;
			float error = 0.0F;
			for (int i = 0; i < 16; i++) {
				error = Math.max(error, relativeError(buffer.get(p + i), TRACKED[i]));
			}
			maxError = Math.max(maxError, error);
			if (error > TOLERANCE) {
				float[] real = new float[16];
				for (int i = 0; i < 16; i++) {
					real[i] = buffer.get(p + i);
				}
				onMismatch(mode == MatrixTracker.MODELVIEW ? "modelview" : "projection", real, TRACKED, 16);
			}
			summarize();
			return;
		}
		for (int i = 0; i < 16; i++) {
			buffer.put(p + i, TRACKED[i]);
		}
	}

	/** Replaces {@code GL11.glGetInteger(GL_VIEWPORT, buffer)} inside {@code Camera.setup}. */
	public static void readViewport(int pname, IntBuffer buffer, Operation<Void> vanilla) {
		if (!inWorldRead || !serve || pname != GL_VIEWPORT) {
			vanilla.call(pname, buffer);
			return;
		}
		int p = buffer.position();
		if (compare) {
			vanilla.call(pname, buffer);
			comparisons++;
			for (int i = 0; i < 4; i++) {
				if (buffer.get(p + i) != VIEWPORT[i]) {
					float[] real = new float[4];
					float[] tracked = new float[4];
					for (int j = 0; j < 4; j++) {
						real[j] = buffer.get(p + j);
						tracked[j] = VIEWPORT[j];
					}
					onMismatch("viewport", real, tracked, 4);
					break;
				}
			}
			return;
		}
		for (int i = 0; i < 4; i++) {
			buffer.put(p + i, VIEWPORT[i]);
		}
	}

	/** Replaces the modelview read in {@code RenderChunk.glTransform}. */
	public static void chunkMatrix(int pname, FloatBuffer buffer, Operation<Void> vanilla) {
		Alacer alacer = Alacer.get();
		alacer.begin(Section.CHUNK_MATRIX);
		AlacerConfig config = alacer.config();
		int p = buffer.position();
		if (!config.avoidMatrixReads || pname != GL_MODELVIEW_MATRIX) {
			vanilla.call(pname, buffer);
		} else if (config.verifyAvoidMatrixReads) {
			vanilla.call(pname, buffer);
			chunkComparisons++;
			float error = 0.0F;
			for (int i = 0; i < 16; i++) {
				error = Math.max(error, relativeError(buffer.get(p + i), CHUNK_MATRIX[i]));
			}
			chunkMaxError = Math.max(chunkMaxError, error);
			if (error > TOLERANCE) {
				chunkMismatches++;
				if (chunkMismatches <= LOGGED_MISMATCHES) {
					float[] real = new float[16];
					for (int i = 0; i < 16; i++) {
						real[i] = buffer.get(p + i);
					}
					logMismatch("render chunk matrix", real, CHUNK_MATRIX, 16);
				}
			}
			if (chunkComparisons % 10_000 == 0) {
				Alacer.LOGGER.info("Matrix read verification: {} render chunk matrices compared, {} mismatched, max relative error {}",
					chunkComparisons, chunkMismatches, chunkMaxError);
			}
		} else {
			for (int i = 0; i < 16; i++) {
				buffer.put(p + i, CHUNK_MATRIX[i]);
			}
		}
		alacer.end(Section.CHUNK_MATRIX);
		FrameProfiler profiler = alacer.profiler();
		if (profiler != null) {
			profiler.count(Counter.CHUNK_MATRIX_READS);
		}
	}

	// --- Comparison ---

	private static void onMismatch(String what, float[] real, float[] tracked, int count) {
		mismatches++;
		if (Alacer.get().config().verifyAvoidMatrixReads) {
			if (mismatches <= LOGGED_MISMATCHES) {
				logMismatch(what, real, tracked, count);
			}
			return;
		}
		logMismatch(what, real, tracked, count);
		// The remaining reads of this frame go to OpenGL too.
		serve = false;
		compare = false;
		if (!fallback) {
			fallback = true;
			Alacer.LOGGER.warn("Tracked {} differs from OpenGL's (something changes it without GlStateManager): reading matrices from OpenGL every frame again for the rest of the session", what);
		}
	}

	private static void logMismatch(String what, float[] real, float[] tracked, int count) {
		Alacer.LOGGER.warn("Matrix read mismatch ({}): OpenGL {} vs tracked {}",
			what, Arrays.toString(Arrays.copyOf(real, count)), Arrays.toString(Arrays.copyOf(tracked, count)));
	}

	private static void summarize() {
		if (Alacer.get().config().verifyAvoidMatrixReads && comparisons % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Matrix read verification: {} camera/frustum reads compared, {} mismatched, max relative error {}, {} frames untracked",
				comparisons, mismatches, maxError, untrackedFrames);
		}
	}

	static float relativeError(float real, float tracked) {
		return Math.abs(real - tracked) / Math.max(1.0F, Math.abs(real));
	}

	/** {@code glTransform}: identity, translate(-8), scale(1.000001), translate(8), as GL computes it. */
	private static float[] chunkMatrix() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		tracker.translate(-8.0F, -8.0F, -8.0F);
		tracker.scale(1.000001F, 1.000001F, 1.000001F);
		tracker.translate(8.0F, 8.0F, 8.0F);
		float[] matrix = new float[16];
		tracker.get(MatrixTracker.MODELVIEW, matrix);
		return matrix;
	}
}
