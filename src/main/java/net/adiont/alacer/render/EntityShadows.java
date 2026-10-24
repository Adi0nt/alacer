package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Entity shadow world queries (optimization 6) and the optional coarse shadow timer.
 *
 * <p>For every block position under a nearby entity's shadow, vanilla's
 * {@code EntityRenderer.renderShadow} asks the world for the block state below it and the raw
 * brightness at it, and {@code renderShadowOnBlock} asks for the brightness again. Each answer
 * starts with {@code World.getChunk}, a hash-map lookup in the client chunk cache: three to
 * five lookups per position, 8 to 18 positions per shadow, for chunks that rarely change.
 *
 * <p>With {@code render.fastEntityShadows}, the same answers are computed with the same range
 * checks and the same chunk methods ({@code getBlockState}, {@code getLight} with the world's
 * ambient darkness, the dimension's brightness table), but the chunk comes from a four-entry
 * reference cache instead. Nothing else is cached: block states and light are read from the
 * chunk on every query, so moving entities, block changes and light changes are seen exactly
 * when vanilla sees them. An entry is used only for the same world and column and only while
 * the column's client load/unload generation is unchanged (the table shared with
 * {@link EntityRendering}); it covers the shared empty chunk of unloaded columns too. Entries
 * are indexed by the low bit of each chunk coordinate, so the at most 2x2 columns of one
 * shadow never evict each other.
 *
 * <p>Only the exact {@link ClientWorld} class is answered this way, so a world subclass that
 * overrides the queries keeps its behavior. Blocks that take their light from their neighbors
 * (every non-air block with light opacity 0, such as tall grass and flowers, translucent
 * blocks, slabs, stairs and farmland) get vanilla's maximum over the five neighbors, each with
 * vanilla's range checks and build-height clamp. Verification ({@code render.fastEntityShadows.verify}) also runs vanilla's query for every
 * answered one, uses vanilla's result and logs differences.
 *
 * <p>Everything runs on the client thread, where client chunks are loaded and unloaded too.
 */
public final class EntityShadows {

	private static final int LIMIT = 30000000;
	private static final long SUMMARY_EVERY = 1_000_000L;

	private static final World[] WORLDS = new World[4];
	private static final WorldChunk[] CHUNKS = new WorldChunk[4];
	private static final int[] CHUNK_X = new int[4];
	private static final int[] CHUNK_Z = new int[4];
	private static final int[] GENERATIONS = new int[4];

	private static final BlockPos.Mutable NEIGHBOR = new BlockPos.Mutable();

	private static FrameProfiler profiler;

	private static long verified;
	private static long mismatches;

	private EntityShadows() {
	}

	public static void init(FrameProfiler frameProfiler) {
		profiler = frameProfiler;
	}

	// --- Optional coarse timer (profiler.shadowTiming) ---

	public static void beginShadow() {
		if (profiler != null) {
			profiler.begin(Section.SHADOWS);
			profiler.count(Counter.SHADOWS_DRAWN);
		}
	}

	public static void endShadow() {
		if (profiler != null) {
			profiler.end(Section.SHADOWS);
		}
	}

	// --- Queries ---

	/** Replaces {@code world.getBlockState(pos)} in {@code renderShadow}. */
	public static BlockState blockState(World world, BlockPos pos) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastEntityShadows || world.getClass() != ClientWorld.class) {
			return world.getBlockState(pos);
		}
		BlockState state = fastBlockState(world, pos);
		if (config.verifyFastEntityShadows) {
			BlockState expected = world.getBlockState(pos);
			if (state != expected) {
				mismatch("block state", pos, String.valueOf(state), String.valueOf(expected));
			}
			compared();
			return expected;
		}
		return state;
	}

	/** Replaces {@code world.getRawBrightness(pos)} in {@code renderShadow}. */
	public static int rawBrightness(World world, BlockPos pos) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastEntityShadows || world.getClass() != ClientWorld.class) {
			return world.getRawBrightness(pos);
		}
		int light = fastRawBrightness(world, pos);
		if (config.verifyFastEntityShadows) {
			int expected = world.getRawBrightness(pos);
			if (light != expected) {
				mismatch("raw brightness", pos, String.valueOf(light), String.valueOf(expected));
			}
			compared();
			return expected;
		}
		return light;
	}

	/** Replaces {@code world.getBrightness(pos)} in {@code renderShadowOnBlock}. */
	public static float brightness(World world, BlockPos pos) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastEntityShadows || world.getClass() != ClientWorld.class) {
			return world.getBrightness(pos);
		}
		float brightness = world.dimension.getBrightnessTable()[fastRawBrightness(world, pos)];
		if (config.verifyFastEntityShadows) {
			float expected = world.getBrightness(pos);
			if (Float.floatToIntBits(brightness) != Float.floatToIntBits(expected)) {
				mismatch("brightness", pos, String.valueOf(brightness), String.valueOf(expected));
			}
			compared();
			return expected;
		}
		return brightness;
	}

	/** {@code World.getBrightness(pos)} at the given coordinates. */
	public static float fastBrightness(World world, int x, int y, int z) {
		return world.dimension.getBrightnessTable()[fastRawBrightness(world, x, y, z)];
	}

	/** {@code World.getBlockState}, with the chunk from the cache. */
	public static BlockState fastBlockState(World world, int x, int y, int z) {
		if (x < -LIMIT || z < -LIMIT || x >= LIMIT || z >= LIMIT || y < 0 || y >= 256) {
			return Blocks.AIR.defaultState();
		}
		NEIGHBOR.set(x, y, z);
		return chunk(world, x >> 4, z >> 4).getBlockState(NEIGHBOR);
	}

	private static BlockState fastBlockState(World world, BlockPos pos) {
		return fastBlockState(world, pos.getX(), pos.getY(), pos.getZ());
	}

	/** {@code World.getRawBrightness(pos, true)}, with chunks from the cache. */
	public static int fastRawBrightness(World world, int x, int y, int z) {
		if (x < -LIMIT || z < -LIMIT || x >= LIMIT || z >= LIMIT) {
			return 15;
		}
		if (fastBlockState(world, x, y, z).getBlock().usesNeighborLight()) {
			// Vanilla: the largest of up, east, west, south and north, without neighbor light.
			int light = light(world, x, y + 1, z);
			light = Math.max(light, light(world, x + 1, y, z));
			light = Math.max(light, light(world, x - 1, y, z));
			light = Math.max(light, light(world, x, y, z + 1));
			return Math.max(light, light(world, x, y, z - 1));
		}
		if (y < 0) {
			return 0;
		}
		if (y >= 256) {
			return light(world, x, y, z);
		}
		NEIGHBOR.set(x, y, z);
		return chunk(world, x >> 4, z >> 4).getLight(NEIGHBOR, world.getAmbientDarkness());
	}

	private static int fastRawBrightness(World world, BlockPos pos) {
		return fastRawBrightness(world, pos.getX(), pos.getY(), pos.getZ());
	}

	/** {@code World.getRawBrightness(pos, false)} at the given coordinates. */
	private static int light(World world, int x, int y, int z) {
		if (x < -LIMIT || z < -LIMIT || x >= LIMIT || z >= LIMIT) {
			return 15;
		}
		if (y < 0) {
			return 0;
		}
		// The chunk only reads the coordinates, so one reused position replaces vanilla's copies.
		NEIGHBOR.set(x, Math.min(y, 255), z);
		return chunk(world, x >> 4, z >> 4).getLight(NEIGHBOR, world.getAmbientDarkness());
	}

	private static WorldChunk chunk(World world, int chunkX, int chunkZ) {
		int slot = chunkX & 1 | (chunkZ & 1) << 1;
		int generation = EntityRendering.columnGeneration(chunkX, chunkZ);
		WorldChunk chunk = CHUNKS[slot];
		if (chunk != null && WORLDS[slot] == world && CHUNK_X[slot] == chunkX && CHUNK_Z[slot] == chunkZ
			&& GENERATIONS[slot] == generation) {
			return chunk;
		}
		chunk = world.getChunkAt(chunkX, chunkZ);
		WORLDS[slot] = world;
		CHUNKS[slot] = chunk;
		CHUNK_X[slot] = chunkX;
		CHUNK_Z[slot] = chunkZ;
		GENERATIONS[slot] = generation;
		return chunk;
	}

	/** Drops all chunk references; called when the renderer's world changes. */
	public static void reset() {
		for (int i = 0; i < CHUNKS.length; i++) {
			WORLDS[i] = null;
			CHUNKS[i] = null;
		}
	}

	// --- Verification ---

	private static void compared() {
		verified++;
		if (verified % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Entity shadow verification: {}", verificationSummary());
		}
	}

	private static void mismatch(String query, BlockPos pos, String fast, String expected) {
		mismatches++;
		if (mismatches <= 10) {
			Alacer.LOGGER.warn("Entity shadow {} mismatch at {}: cached-chunk answer {} vs vanilla {}", query, pos, fast, expected);
		}
	}

	public static String verificationSummary() {
		return verified + " queries compared, " + mismatches + " mismatched";
	}
}
