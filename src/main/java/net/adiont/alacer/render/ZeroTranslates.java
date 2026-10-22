package net.adiont.alacer.render;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Skips {@code GlStateManager.translatef(0, 0, 0)} in {@code ModelPart}'s render methods.
 *
 * <p>Every model part translates by its {@code translateX/Y/Z} offset before drawing and by the
 * negated offset afterwards. The offset is zero for practically every part, so each drawn part
 * issues two OpenGL calls that do nothing; for the benchmark's mobs that is 12-24 of about 75
 * calls per entity (see BENCHMARKS.md). The other translations in those methods (the part's
 * pivot) are skipped too when they happen to be zero. Multiplying by a translation of zero is
 * the identity: each matrix element {@code m[12+i]} becomes {@code m[12+i] + 0}, so the matrix is
 * unchanged for any finite matrix, which is why vanilla's result is reproduced exactly.
 *
 * <p>Only these call sites are wrapped, so other callers of {@code translatef} and other mods'
 * hooks into it are unaffected; a skipped call simply does not happen, as when vanilla
 * takes its own existing shortcut for parts without a pivot.
 *
 * <p>Verification ({@code render.skipZeroTranslates.verify}) issues every translation, and in
 * the frame where the rate-limited GL error checks run (about once per second; that frame
 * waits for the driver anyway) it reads the current matrix before and after up to
 * {@value #SAMPLES_PER_CHECK} zero translations and compares them element by element.
 *
 * <p>Client thread only.
 */
public final class ZeroTranslates {

	private static final int SAMPLES_PER_CHECK = 16;
	private static final FloatBuffer BEFORE = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer AFTER = BufferUtils.createFloatBuffer(16);

	private static long sampledFrame = -1;
	private static int samplesThisFrame;
	private static long comparisons;
	private static long mismatches;

	private ZeroTranslates() {
	}

	/**
	 * Whether {@code GlStateManager.translatef(x, y, z)} inside {@code ModelPart} should run.
	 * In verification mode a sampled translation is issued here, between two matrix reads, and
	 * the original call is skipped, so it still happens exactly once.
	 */
	public static boolean shouldTranslate(float x, float y, float z) {
		if (x != 0.0F || y != 0.0F || z != 0.0F) {
			return true;
		}
		AlacerConfig config = Alacer.get().config();
		if (config.verifySkipZeroTranslates) {
			return !sampled(x, y, z);
		}
		return !config.skipZeroTranslates;
	}

	/** Issues and checks the translation if this frame samples one; false if it was not issued. */
	private static boolean sampled(float x, float y, float z) {
		if (!GlErrorChecks.checksThisFrame()) {
			return false;
		}
		long frame = GlErrorChecks.frameNumber();
		if (frame != sampledFrame) {
			sampledFrame = frame;
			samplesThisFrame = 0;
		}
		if (samplesThisFrame >= SAMPLES_PER_CHECK) {
			return false;
		}
		samplesThisFrame++;
		int matrix = currentMatrix();
		GL11.glGetFloat(matrix, BEFORE);
		GlStateManager.translatef(x, y, z);
		GL11.glGetFloat(matrix, AFTER);
		comparisons++;
		for (int i = 0; i < 16; i++) {
			if (BEFORE.get(i) != AFTER.get(i)) {
				mismatches++;
				if (mismatches <= 10) {
					Alacer.LOGGER.warn("Zero translation changed the matrix (element {}: {} -> {})", i, BEFORE.get(i), AFTER.get(i));
				}
				break;
			}
		}
		if (comparisons % 500 == 0) {
			Alacer.LOGGER.info("Zero translation verification: {}", verificationSummary());
		}
		return true;
	}

	private static int currentMatrix() {
		switch (GL11.glGetInteger(GL11.GL_MATRIX_MODE)) {
			case GL11.GL_PROJECTION:
				return GL11.GL_PROJECTION_MATRIX;
			case GL11.GL_TEXTURE:
				return GL11.GL_TEXTURE_MATRIX;
			default:
				return GL11.GL_MODELVIEW_MATRIX;
		}
	}

	public static String verificationSummary() {
		return comparisons + " zero translations compared, " + mismatches + " changed the matrix";
	}
}
