package net.adiont.alacer.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Hooks for {@code WorldRenderer.renderEntities}: phase timing, optional per-call timing, and
 * the per-render-chunk world-chunk reference cache.
 *
 * <p><b>The cache.</b> For every visible render chunk, every frame, the entity loop looks up
 * the world chunk containing it ({@code ClientWorld.getChunk}, a hash-map lookup). The answer
 * only changes when a chunk is loaded or unloaded at that column, or the render chunk is moved
 * to another column. Each render chunk therefore remembers the world chunk it last got,
 * together with the load/unload generation of that column. The cached reference is used only
 * if:
 * <ul>
 * <li>it was looked up for the render chunk's current column (render chunks are reused for
 * other columns as the camera moves);</li>
 * <li>no chunk has been loaded or unloaded at that column since (tracked by hooks on
 * {@code ClientChunkCache.loadChunk}/{@code unloadChunk}; a chunk can be replaced by a new load
 * without an unload in between, which the generation check also covers);</li>
 * <li>it belongs to the current client world.</li>
 * </ul>
 * Generations are kept in a small table indexed by the low bits of the chunk coordinates.
 * Different columns can share an entry, which only causes extra (correct) lookups. The shared
 * empty placeholder returned for unloaded columns is cached too; loading a chunk there changes
 * the column's generation.
 *
 * <p>Everything here runs on the client thread; client chunk loading and unloading happen there
 * too, because network packets are processed on it.
 */
public final class EntityRendering {

	private static final int GENERATION_BITS = 6;
	private static final int GENERATION_MASK = (1 << GENERATION_BITS) - 1;
	private static final int[] COLUMN_GENERATION = new int[1 << (2 * GENERATION_BITS)];

	private static FrameProfiler profiler;
	private static boolean detail;

	private static long verifiedLookups;
	private static long lookupMismatches;

	private EntityRendering() {
	}

	public static void init(FrameProfiler frameProfiler, boolean entityDetail, boolean entityStages) {
		profiler = frameProfiler;
		detail = frameProfiler != null && entityDetail;
		if (detail) {
			GlCallStats.init(frameProfiler);
			if (entityStages) {
				EntityDrawDetails.init(frameProfiler);
			}
		}
	}

	/** Whether per-call detail timing is active. */
	public static boolean detail() {
		return detail;
	}

	// --- Phase timing (always on while profiling) ---

	public static void begin() {
		if (profiler != null) {
			profiler.begin(Section.ENTITIES);
			profiler.switchPhase(Section.ENTITY_PREPARE);
		}
	}

	public static void phase(Section next) {
		if (profiler != null) {
			profiler.switchPhase(next);
		}
	}

	public static void finish() {
		if (profiler != null) {
			profiler.endPhase();
			profiler.end(Section.ENTITIES);
		}
	}

	// --- Detail timing ---

	public static void add(Section section, long nanos) {
		profiler.add(section, nanos);
	}

	public static void count(Counter counter) {
		profiler.count(counter);
	}

	// --- World-chunk lookup ---

	/** Replaces {@code world.getChunk(origin)} for a render chunk in the entity loop. */
	public static WorldChunk lookup(ClientWorld world, BlockPos origin, RenderChunk renderChunk, Operation<WorldChunk> vanilla) {
		long start = detail ? System.nanoTime() : 0L;
		WorldChunk chunk;
		AlacerConfig config = Alacer.get().config();
		if (config.fastEntityChunkLookup) {
			chunk = cachedLookup(world, origin, (EntityChunkSlot) renderChunk, vanilla);
			if (config.verifyEntityChunkLookup) {
				verify(chunk, vanilla.call(world, origin), origin);
			}
		} else {
			chunk = vanilla.call(world, origin);
		}
		if (detail) {
			profiler.add(Section.ENTITY_CHUNK_LOOKUP, System.nanoTime() - start);
			profiler.count(Counter.ENTITY_CHUNK_LOOKUPS);
		}
		return chunk;
	}

	private static WorldChunk cachedLookup(ClientWorld world, BlockPos origin, EntityChunkSlot slot, Operation<WorldChunk> vanilla) {
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		long column = (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
		int generation = COLUMN_GENERATION[index(chunkX, chunkZ)];
		WorldChunk cached = slot.alacer$getEntityChunk();
		if (cached != null && slot.alacer$getEntityChunkColumn() == column
			&& slot.alacer$getEntityChunkGeneration() == generation && cached.getWorld() == world) {
			if (detail) {
				profiler.count(Counter.ENTITY_CHUNK_CACHE_HITS);
			}
			return cached;
		}
		WorldChunk chunk = vanilla.call(world, origin);
		// Includes the shared empty placeholder returned for unloaded columns: the column's
		// generation changes when a real chunk is loaded there, which invalidates the entry.
		slot.alacer$setEntityChunk(chunk, column, generation);
		return chunk;
	}

	private static void verify(WorldChunk cached, WorldChunk expected, BlockPos origin) {
		verifiedLookups++;
		if (cached != expected) {
			lookupMismatches++;
			if (lookupMismatches <= 10) {
				Alacer.LOGGER.warn("Entity chunk lookup mismatch at {}: cached chunk {},{} vs world chunk {},{}",
					origin, cached.chunkX, cached.chunkZ, expected.chunkX, expected.chunkZ);
			}
		}
		if (verifiedLookups % 1_000_000 == 0) {
			Alacer.LOGGER.info("Entity chunk lookup verification: {} lookups compared, {} mismatched", verifiedLookups, lookupMismatches);
		}
	}

	/** Called whenever the client loads or unloads a chunk column. */
	public static void onChunkMapChanged(int chunkX, int chunkZ) {
		COLUMN_GENERATION[index(chunkX, chunkZ)]++;
	}

	/**
	 * Load/unload generation of a chunk column: every client chunk load or unload there changes
	 * it. Columns that share a table entry only cause extra changes (and extra, correct lookups).
	 */
	public static int columnGeneration(int chunkX, int chunkZ) {
		return COLUMN_GENERATION[index(chunkX, chunkZ)];
	}

	private static int index(int chunkX, int chunkZ) {
		return (chunkX & GENERATION_MASK) << GENERATION_BITS | chunkZ & GENERATION_MASK;
	}

	public static String verificationSummary() {
		return verifiedLookups + " lookups compared, " + lookupMismatches + " mismatched";
	}
}
