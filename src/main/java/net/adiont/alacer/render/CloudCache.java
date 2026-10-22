package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.opengl.GL11;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.WorldRendererAccessor;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fancy cloud geometry compiled into a display list instead of rebuilt in immediate mode.
 *
 * <p>{@code WorldRenderer.renderFancyClouds} builds 64 tiles of up to 136 vertices each, draws
 * each tile with its own {@code Tesselator.end}, and does all of that twice per frame (a
 * depth-only pass, then color): 10,752 vertices and 128 draws, about 0.47 ms per frame on the
 * client thread (BENCHMARKS.md session 6). Each vertex position is a constant offset minus the
 * fractional part of the camera's cloud cell, plus the camera's height relative to the cloud
 * layer ({@code j}). Everything else depends only on
 *
 * <ul>
 * <li>the integer cloud cell ({@code floor} of camera position plus drift, in 12-block cells),
 * which sets the texture coordinates;</li>
 * <li>the height band, {@code j > -5} (bottom faces) and {@code j <= 5} (top faces);</li>
 * <li>the cloud color after the anaglyph conversion, which sets the vertex colors.</li>
 * </ul>
 *
 * <p>So the geometry is built once with the fractional offsets and {@code j} set to zero,
 * compiled into a display list, and rebuilt only when one of those changes. Each pass draws it
 * with a translation by {@code (-frac x, j, -frac z)} inside vanilla's {@code scalef(12, 1, 12)}.
 * When stationary the drift crosses a cell every 20 s; flying at 20 blocks/s about twice per
 * second; while the cloud color changes (sunrise, sunset, rain and thunder transitions) every
 * frame. Positions then differ from vanilla's by float rounding (translation applied after
 * the offset is added instead of before); texture coordinates, colors and normals are
 * bit-identical. Vanilla's state setup, color masks and pass loop all still run; only the tile
 * loop of each pass is skipped (see {@code mixin/CloudsMixin}). The fast (flat) clouds are not
 * changed.
 *
 * <p>Verification ({@code render.cacheClouds.verify}) maintains the cache as usual but lets
 * vanilla draw, and compares every tile vanilla builds, vertex by vertex, with the cached data
 * translated as it would be drawn: positions within {@value #POSITION_TOLERANCE} cloud units
 * (or two float ULPs at extreme heights), everything else exactly, except unwritten padding.
 *
 * <p>The inputs are recomputed with vanilla's expressions from the camera and the renderer,
 * so a mod that changes vanilla's local variables in {@code renderFancyClouds} would make the
 * two disagree; verification would show it. Client thread only.
 */
public final class CloudCache {

	private static final int TILES = 64;
	/** Per tile at most 4 bottom, 4 top and 4 x 8 for each of the four side directions. */
	private static final int MAX_VERTICES = TILES * (8 + 4 * 32);
	/** {@code POSITION_TEX_COLOR_NORMAL}: 3 floats, 2 floats, 4 color bytes, 3 normal bytes and padding. */
	private static final int VERTEX_BYTES = 28;
	private static final int INTS_PER_VERTEX = VERTEX_BYTES / 4;
	private static final float POSITION_TOLERANCE = 1.0E-4F;
	private static final int SUMMARY_EVERY_FRAMES = 2000;

	private static int list;
	private static boolean built;
	private static int renderMode = -1;
	private static int anaglyphPass = -1;
	private static int cellX;
	private static int cellZ;
	private static boolean bottomFaces;
	private static boolean topFaces;
	private static float red;
	private static float green;
	private static float blue;

	/** This frame's translation of the cached geometry. */
	private static float offsetX;
	private static float offsetY;
	private static float offsetZ;
	/** Whether the passes of the current {@code renderFancyClouds} call draw the cached list. */
	private static boolean drawCached;
	private static boolean verifying;

	/** The cached vertex data (raw ints, as in the vertex buffer) and where each tile starts. */
	private static final int[] VERTICES = new int[MAX_VERTICES * INTS_PER_VERTEX];
	private static final int[] TILE_START = new int[TILES + 1];
	private static final ByteBuffer SCRATCH = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());

	private static long rebuilds;
	private static int verifyTile;
	private static long verifiedFrames;
	private static long verifiedVertices;
	private static long mismatchedVertices;
	private static long mismatchedTiles;
	private static float maxPositionError;
	private static int verifiedBands;
	private static int verifiedPasses;

	private CloudCache() {
	}

	/** Observe flat-cloud calls too, so returning to fancy rebuilds the cache. */
	public static void onClouds(int pass) {
		int mode = Minecraft.getInstance().options.getCloudRenderMode();
		if (mode != renderMode || pass != anaglyphPass) {
			built = false;
		}
		renderMode = mode;
		anaglyphPass = pass;
	}

	/** WorldRenderer.setWorld runs on the render thread, including disconnect. */
	public static void reset() {
		if (list != 0) {
			MemoryTracker.releaseLists(list, 1);
			list = 0;
		}
		built = drawCached = verifying = false;
		renderMode = anaglyphPass = -1;
	}

	/**
	 * Called after vanilla's {@code scalef(12, 1, 12)} in {@code renderFancyClouds}, before the
	 * two passes. Recomputes vanilla's inputs, rebuilds the list if the shape changed and
	 * decides whether this call's passes draw it.
	 */
	public static void prepare(WorldRenderer renderer, float tickDelta, int anaglyphRenderPass, Vec3d color) {
		AlacerConfig config = Alacer.get().config();
		boolean verify = config.verifyCacheClouds;
		drawCached = false;
		verifying = false;
		if (!config.cacheClouds && !verify) {
			return;
		}

		// The same expressions as WorldRenderer.renderFancyClouds, in the same order and types.
		WorldRendererAccessor accessor = (WorldRendererAccessor) renderer;
		Entity camera = Minecraft.getInstance().getCamera();
		float f = (float) (camera.prevY + (camera.y - camera.prevY) * tickDelta);
		double d = accessor.alacer$getTicks() + tickDelta;
		double e = (camera.lastX + (camera.x - camera.lastX) * tickDelta + d * 0.03F) / 12.0;
		double i = (camera.lastZ + (camera.z - camera.lastZ) * tickDelta) / 12.0 + 0.33F;
		float j = accessor.alacer$getWorld().dimension.getCloudHeight() - f + 0.33F;
		int k = MathHelper.floor(e / 2048.0);
		int l = MathHelper.floor(i / 2048.0);
		e -= k * 2048;
		i -= l * 2048;
		float m = (float) color.x;
		float n = (float) color.y;
		float o = (float) color.z;
		if (anaglyphRenderPass != 2) {
			float p = (m * 30.0F + n * 59.0F + o * 11.0F) / 100.0F;
			float q = (m * 30.0F + n * 70.0F) / 100.0F;
			float r = (m * 30.0F + o * 70.0F) / 100.0F;
			m = p;
			n = q;
			o = r;
		}
		int floorE = MathHelper.floor(e);
		int floorI = MathHelper.floor(i);
		boolean bottom = j > -5.0F;
		boolean top = j <= 5.0F;

		if (!built || floorE != cellX || floorI != cellZ || bottom != bottomFaces || top != topFaces
			|| Float.floatToIntBits(m) != Float.floatToIntBits(red)
			|| Float.floatToIntBits(n) != Float.floatToIntBits(green)
			|| Float.floatToIntBits(o) != Float.floatToIntBits(blue)) {
			rebuild(floorE, floorI, bottom, top, m, n, o);
		}

		offsetX = -(float) (e - floorE);
		offsetY = j;
		offsetZ = -(float) (i - floorI);
		drawCached = !verify;
		verifying = verify;
		if (verify) {
			verifiedBands |= bottom ? (top ? 2 : 1) : 4;
			verifiedPasses |= 1 << anaglyphRenderPass;
			verifyTile = 0;
			if (++verifiedFrames % SUMMARY_EVERY_FRAMES == 0) {
				Alacer.LOGGER.info("Cloud cache verification: {}", verificationSummary());
			}
		}
	}

	/**
	 * Replaces the start value of a pass's tile loop ({@code ah = -3}). When the cached list is
	 * used, draws it and returns a start past the loop's end so vanilla's tiles are skipped.
	 */
	public static int tileLoopStart(int start) {
		if (!drawCached) {
			verifyTile = 0;
			return start;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translatef(offsetX, offsetY, offsetZ);
		GlStateManager.callList(list);
		GlStateManager.popMatrix();
		// As after vanilla's draws: the color array leaves OpenGL's current color undefined.
		GlStateManager.clearColor();
		return Integer.MAX_VALUE;
	}

	private static void rebuild(int floorE, int floorI, boolean bottom, boolean top, float m, float n, float o) {
		if (list == 0) {
			list = MemoryTracker.getLists(1);
		}
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.getBuffer();
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
		emit(buffer, floorE, floorI, bottom, top, m, n, o);
		TILE_START[TILES] = buffer.getVertexCount();
		ByteBuffer data = buffer.getBuffer();
		int ints = buffer.getVertexCount() * INTS_PER_VERTEX;
		for (int index = 0; index < ints; index++) {
			VERTICES[index] = data.getInt(index * 4);
		}
		GL11.glNewList(list, GL11.GL_COMPILE);
		tesselator.end();
		GL11.glEndList();

		built = true;
		cellX = floorE;
		cellZ = floorI;
		bottomFaces = bottom;
		topFaces = top;
		red = m;
		green = n;
		blue = o;
		rebuilds++;
		FrameProfiler profiler = Alacer.get().profiler();
		if (profiler != null) {
			profiler.count(Counter.CLOUD_REBUILDS);
		}
	}

	/**
	 * Vanilla's tile loop for one pass, with the fractional cell offsets and {@code j} left out
	 * (they become the translation) and all tiles in one draw. Variable names follow vanilla's.
	 */
	private static void emit(BufferBuilder buffer, int floorE, int floorI, boolean bottom, boolean top, float m, float n, float o) {
		float p = m * 0.9F;
		float q = n * 0.9F;
		float r = o * 0.9F;
		float s = m * 0.7F;
		float t = n * 0.7F;
		float u = o * 0.7F;
		float v = m * 0.8F;
		float w = n * 0.8F;
		float x = o * 0.8F;
		float z = floorE * 0.00390625F;
		float aa = floorI * 0.00390625F;
		int tile = 0;
		for (int ah = -3; ah <= 4; ah++) {
			for (int ai = -3; ai <= 4; ai++) {
				TILE_START[tile++] = buffer.getVertexCount();
				float aj = ah * 8;
				float ak = ai * 8;
				if (bottom) {
					vertex(buffer, aj + 0.0F, 0.0F, ak + 8.0F, (aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa, s, t, u, 0.0F, -1.0F, 0.0F);
					vertex(buffer, aj + 8.0F, 0.0F, ak + 8.0F, (aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa, s, t, u, 0.0F, -1.0F, 0.0F);
					vertex(buffer, aj + 8.0F, 0.0F, ak + 0.0F, (aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa, s, t, u, 0.0F, -1.0F, 0.0F);
					vertex(buffer, aj + 0.0F, 0.0F, ak + 0.0F, (aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa, s, t, u, 0.0F, -1.0F, 0.0F);
				}

				if (top) {
					float y = 4.0F - 9.765625E-4F;
					vertex(buffer, aj + 0.0F, y, ak + 8.0F, (aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa, m, n, o, 0.0F, 1.0F, 0.0F);
					vertex(buffer, aj + 8.0F, y, ak + 8.0F, (aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa, m, n, o, 0.0F, 1.0F, 0.0F);
					vertex(buffer, aj + 8.0F, y, ak + 0.0F, (aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa, m, n, o, 0.0F, 1.0F, 0.0F);
					vertex(buffer, aj + 0.0F, y, ak + 0.0F, (aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa, m, n, o, 0.0F, 1.0F, 0.0F);
				}

				if (ah > -1) {
					for (int an = 0; an < 8; an++) {
						float tu = (aj + an + 0.5F) * 0.00390625F + z;
						vertex(buffer, aj + an + 0.0F, 0.0F, ak + 8.0F, tu, (ak + 8.0F) * 0.00390625F + aa, p, q, r, -1.0F, 0.0F, 0.0F);
						vertex(buffer, aj + an + 0.0F, 4.0F, ak + 8.0F, tu, (ak + 8.0F) * 0.00390625F + aa, p, q, r, -1.0F, 0.0F, 0.0F);
						vertex(buffer, aj + an + 0.0F, 4.0F, ak + 0.0F, tu, (ak + 0.0F) * 0.00390625F + aa, p, q, r, -1.0F, 0.0F, 0.0F);
						vertex(buffer, aj + an + 0.0F, 0.0F, ak + 0.0F, tu, (ak + 0.0F) * 0.00390625F + aa, p, q, r, -1.0F, 0.0F, 0.0F);
					}
				}

				if (ah <= 1) {
					for (int an = 0; an < 8; an++) {
						float tu = (aj + an + 0.5F) * 0.00390625F + z;
						float sx = aj + an + 1.0F - 9.765625E-4F;
						vertex(buffer, sx, 0.0F, ak + 8.0F, tu, (ak + 8.0F) * 0.00390625F + aa, p, q, r, 1.0F, 0.0F, 0.0F);
						vertex(buffer, sx, 4.0F, ak + 8.0F, tu, (ak + 8.0F) * 0.00390625F + aa, p, q, r, 1.0F, 0.0F, 0.0F);
						vertex(buffer, sx, 4.0F, ak + 0.0F, tu, (ak + 0.0F) * 0.00390625F + aa, p, q, r, 1.0F, 0.0F, 0.0F);
						vertex(buffer, sx, 0.0F, ak + 0.0F, tu, (ak + 0.0F) * 0.00390625F + aa, p, q, r, 1.0F, 0.0F, 0.0F);
					}
				}

				if (ai > -1) {
					for (int an = 0; an < 8; an++) {
						float tv = (ak + an + 0.5F) * 0.00390625F + aa;
						vertex(buffer, aj + 0.0F, 4.0F, ak + an + 0.0F, (aj + 0.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, -1.0F);
						vertex(buffer, aj + 8.0F, 4.0F, ak + an + 0.0F, (aj + 8.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, -1.0F);
						vertex(buffer, aj + 8.0F, 0.0F, ak + an + 0.0F, (aj + 8.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, -1.0F);
						vertex(buffer, aj + 0.0F, 0.0F, ak + an + 0.0F, (aj + 0.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, -1.0F);
					}
				}

				if (ai <= 1) {
					for (int an = 0; an < 8; an++) {
						float tv = (ak + an + 0.5F) * 0.00390625F + aa;
						float sz = ak + an + 1.0F - 9.765625E-4F;
						vertex(buffer, aj + 0.0F, 4.0F, sz, (aj + 0.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, 1.0F);
						vertex(buffer, aj + 8.0F, 4.0F, sz, (aj + 8.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, 1.0F);
						vertex(buffer, aj + 8.0F, 0.0F, sz, (aj + 8.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, 1.0F);
						vertex(buffer, aj + 0.0F, 0.0F, sz, (aj + 0.0F) * 0.00390625F + z, tv, v, w, x, 0.0F, 0.0F, 1.0F);
					}
				}
			}
		}
	}

	private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz) {
		buffer.vertex(x, y, z).texture(u, v).color(r, g, b, 0.8F).normal(nx, ny, nz).nextVertex();
	}

	/**
	 * Called before each {@code Tesselator.end} in {@code renderFancyClouds} (one per tile, only
	 * reached when vanilla draws). Compares vanilla's tile with the cached one.
	 */
	public static void verifyTile(BufferBuilder buffer) {
		if (!verifying) {
			return;
		}
		int tile = verifyTile++;
		int count = buffer.getVertexCount();
		if (tile >= TILES || TILE_START[tile + 1] - TILE_START[tile] != count
			|| buffer.getFormat().getVertexSize() != VERTEX_BYTES) {
			report(tile, -1, "tile has " + count + " vertices, cached "
				+ (tile < TILES ? TILE_START[tile + 1] - TILE_START[tile] : 0));
			mismatchedTiles++;
			return;
		}
		ByteBuffer data = buffer.getBuffer();
		int cached = TILE_START[tile] * INTS_PER_VERTEX;
		for (int vertex = 0; vertex < count; vertex++) {
			int at = vertex * VERTEX_BYTES;
			int c = cached + vertex * INTS_PER_VERTEX;
			float dx = Math.abs(data.getFloat(at) - (Float.intBitsToFloat(VERTICES[c]) + offsetX));
			float dy = Math.abs(data.getFloat(at + 4) - (Float.intBitsToFloat(VERTICES[c + 1]) + offsetY));
			float dz = Math.abs(data.getFloat(at + 8) - (Float.intBitsToFloat(VERTICES[c + 2]) + offsetZ));
			float error = Math.max(dx, Math.max(dy, dz));
			if (error > maxPositionError) {
				maxPositionError = error;
			}
			// The padding byte after the normal is never written, so only the normal's three bytes count.
			boolean same = Float.isFinite(error) && dx <= POSITION_TOLERANCE && dz <= POSITION_TOLERANCE
				&& dy <= Math.max(POSITION_TOLERANCE, 2 * Math.ulp(offsetY))
				&& data.getInt(at + 12) == VERTICES[c + 3]
				&& data.getInt(at + 16) == VERTICES[c + 4]
				&& data.getInt(at + 20) == VERTICES[c + 5]
				&& data.getShort(at + 24) == (short) cachedShort(c)
				&& data.get(at + 26) == cachedByte(c, 2);
			verifiedVertices++;
			if (!same) {
				mismatchedVertices++;
				report(tile, vertex, "position error " + error);
			}
		}
	}

	/** The first two normal bytes of cached vertex {@code c}, in buffer byte order. */
	private static int cachedShort(int c) {
		ByteBuffer scratch = SCRATCH;
		scratch.putInt(0, VERTICES[c + 6]);
		return scratch.getShort(0);
	}

	private static byte cachedByte(int c, int index) {
		ByteBuffer scratch = SCRATCH;
		scratch.putInt(0, VERTICES[c + 6]);
		return scratch.get(index);
	}

	private static void report(int tile, int vertex, String what) {
		if (mismatchedVertices + mismatchedTiles <= 10) {
			Alacer.LOGGER.warn("Cloud cache verification: tile {} vertex {} differs ({}; cell {} {}, offset {} {} {})",
				tile, vertex, what, cellX, cellZ, offsetX, offsetY, offsetZ);
		}
	}

	public static String verificationSummary() {
		return verifiedFrames + " frames, " + verifiedVertices + " vertices compared, " + mismatchedVertices
			+ " differed, " + mismatchedTiles + " tiles differed in size, max position error " + maxPositionError
			+ ", " + rebuilds + " rebuilds, band mask " + verifiedBands + ", anaglyph pass mask " + verifiedPasses;
	}

	public static void finish() {
		if (verifying && verifyTile != TILES) {
			mismatchedTiles++;
			report(verifyTile, -1, "incomplete pass");
		}
		verifying = drawCached = false;
	}
}
