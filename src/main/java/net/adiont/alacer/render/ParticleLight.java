package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Particle light queries (optimization 7).
 *
 * <p>Every particle asks for its light every frame while it is drawn: {@code Entity.getLightLevel}
 * calls {@code World.getLightColor}, and block-break particles call it a second time for their
 * block. {@code getLightColor} computes the sky and the block brightness, and each starts with
 * two {@code ClientChunkCache} hash lookups (the block state, to decide whether the block takes its
 * light from its neighbors, then the light). Blocks that do (tall grass and flowers, other
 * non-air blocks with light opacity 0, translucent blocks, slabs, stairs, farmland) read five
 * neighbors instead, each allocating a {@code BlockPos} and doing another lookup: up to 24 lookups
 * and 10 allocations per query, for a few thousand particles per frame in dense scenes.
 *
 * <p>With {@code render.fastParticleLight}, the same answer is computed with the same range
 * checks, build-height clamp, chunk-source {@code hasChunk} check and chunk methods
 * ({@code WorldChunk.getBlockState}, {@code WorldChunk.getLight} for the requested light type,
 * the dimension's {@code hasNoSky}), in the same sky-then-block order, but the chunk comes from a
 * 16-entry reference cache and one reused mutable position replaces the neighbor copies (the
 * chunk only reads its coordinates). Nothing else is cached: block states and light are read
 * from the chunk on every query, so block and light changes are seen exactly when vanilla sees
 * them. An entry is used only for the same world and column while the column's client
 * load/unload generation is unchanged (the table shared with {@link EntityRendering}); it covers
 * the shared empty chunk of unloaded columns too. Entries are indexed by the low two bits of
 * each chunk coordinate, so any 4x4 group of columns, such as the area particles usually
 * occupy around the player, never evicts itself.
 *
 * <p>Only particles are answered this way ({@code Entity.getLightLevel} is shared with every
 * other entity), only in the exact {@link ClientWorld} class, and only on the client thread
 * that owns the cache; everything else takes vanilla's path. Entries are dropped when the
 * renderer's world changes. Inside an unlit particle pass, {@link ParticleLightReuse} can answer
 * repeated positions from the answers already computed here in that pass. Verification ({@code render.fastParticleLight.verify}) also runs
 * vanilla's query for every answered one, uses vanilla's result and logs differences.
 */
public final class ParticleLight {

	private static final int LIMIT = 30000000;
	private static final int SLOTS = 16;
	private static final long SUMMARY_EVERY = 10_000_000L;

	private static final World[] WORLDS = new World[SLOTS];
	private static final WorldChunk[] CHUNKS = new WorldChunk[SLOTS];
	private static final int[] CHUNK_X = new int[SLOTS];
	private static final int[] CHUNK_Z = new int[SLOTS];
	private static final int[] GENERATIONS = new int[SLOTS];

	private static final BlockPos.Mutable POS = new BlockPos.Mutable();

	private static Thread owner;
	private static long verified;
	private static long mismatches;

	private ParticleLight() {
	}

	/** Called on the client thread at startup; only that thread uses the cache. */
	public static void init() {
		owner = Thread.currentThread();
	}

	/** Replaces {@code world.getLightColor(pos, blockLight)} in the particle light methods. */
	public static int lightColor(Object entity, World world, BlockPos pos, int blockLight) {
		if (ParticleLightRepeats.ENABLED && entity instanceof Particle) {
			ParticleLightRepeats.record(pos.getX(), pos.getY(), pos.getZ(), blockLight);
		}
		AlacerConfig config = Alacer.get().config();
		if (!config.fastParticleLight || !(entity instanceof Particle) || world.getClass() != ClientWorld.class
			|| Thread.currentThread() != owner) {
			return world.getLightColor(pos, blockLight);
		}
		if (ParticleLightReuse.HOOKED && ParticleLightReuse.answers(world)) {
			// Per-pass reuse (session 22); it verifies and returns vanilla's answer itself when asked to.
			return ParticleLightReuse.lightColor(world, pos, pos.getX(), pos.getY(), pos.getZ(), blockLight);
		}
		int color = fastLightColor(world, pos.getX(), pos.getY(), pos.getZ(), blockLight);
		if (config.verifyFastParticleLight) {
			int expected = world.getLightColor(pos, blockLight);
			if (color != expected) {
				mismatch(pos, color, expected);
			}
			compared();
			return expected;
		}
		return color;
	}

	/** {@code World.getLightColor}: sky brightness, then block brightness raised to the minimum. */
	private static int fastLightColor(World world, int x, int y, int z, int blockLight) {
		int sky = brightness(world, LightType.SKY, x, y, z);
		int block = brightness(world, LightType.BLOCK, x, y, z);
		if (block < blockLight) {
			block = blockLight;
		}
		return sky << 20 | block << 4;
	}

	/** {@code World.getBrightness(LightType, BlockPos)}. */
	static int brightness(World world, LightType type, int x, int y, int z) {
		if (world.dimension.hasNoSky() && type == LightType.SKY) {
			return 0;
		}
		if (y < 0) {
			y = 0;
		}
		if (!contains(x, y, z) || !world.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return type.defaultValue;
		}
		WorldChunk chunk = chunk(world, x >> 4, z >> 4);
		POS.set(x, y, z);
		if (chunk.getBlockState(POS).getBlock().usesNeighborLight()) {
			// Vanilla: the largest of up, east, west, south and north, without neighbor light.
			int light = light(world, type, x, y + 1, z);
			light = Math.max(light, light(world, type, x + 1, y, z));
			light = Math.max(light, light(world, type, x - 1, y, z));
			light = Math.max(light, light(world, type, x, y, z + 1));
			return Math.max(light, light(world, type, x, y, z - 1));
		}
		return chunk.getLight(type, POS);
	}

	/** {@code World.getLight(LightType, BlockPos)} at the given coordinates. */
	private static int light(World world, LightType type, int x, int y, int z) {
		if (y < 0) {
			y = 0;
		}
		if (!contains(x, y, z) || !world.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return type.defaultValue;
		}
		WorldChunk chunk = chunk(world, x >> 4, z >> 4);
		POS.set(x, y, z);
		return chunk.getLight(type, POS);
	}

	/** {@code World.contains(BlockPos)}: inside the world border limits and build height. */
	private static boolean contains(int x, int y, int z) {
		return x >= -LIMIT && z >= -LIMIT && x < LIMIT && z < LIMIT && y >= 0 && y < 256;
	}

	// Also used by particle movement, with the same client-thread/world guards. Only chunk
	// references are shared; block states and collision geometry are never cached.
	static WorldChunk chunk(World world, int chunkX, int chunkZ) {
		int slot = chunkX & 3 | (chunkZ & 3) << 2;
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
		ParticleLightReuse.endPass();
		for (int i = 0; i < SLOTS; i++) {
			WORLDS[i] = null;
			CHUNKS[i] = null;
		}
	}

	// --- Verification ---

	private static void compared() {
		verified++;
		if (verified % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Particle light verification: {}", verificationSummary());
		}
	}

	private static void mismatch(BlockPos pos, int fast, int expected) {
		mismatches++;
		if (mismatches <= 10) {
			Alacer.LOGGER.warn("Particle light mismatch at {}: cached-chunk answer {} vs vanilla {}", pos,
				Integer.toHexString(fast), Integer.toHexString(expected));
		}
	}

	public static String verificationSummary() {
		return verified + " queries compared, " + mismatches + " mismatched";
	}
}
