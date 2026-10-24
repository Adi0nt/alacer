package net.adiont.alacer.render;

import java.util.Iterator;
import java.util.Set;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.ChunkRenderDispatcherAccessor;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;

/**
 * Chunk scheduling experiments, both off by default: {@code render.chunkTimeFloor} ({@link ChunkTimeFloor}, applied
 * through {@link #deadline}) and {@code render.feedChunkWorkers}.
 *
 * <p>{@code render.feedChunkWorkers}: after vanilla's {@code compileChunksUntil}, keep queueing dirty chunks in the
 * same order and with the same calls vanilla's loop makes ({@code rebuildAsync}, then clear the dirty flag and leave
 * the dirty set) until the queue holds {@link FeedTarget}'s depth. Vanilla stops queueing once the frame's chunk
 * deadline has passed, which often happens before its loop starts; queueing a task is cheap next to building it,
 * and uploads stay under vanilla's deadline. What is built, and how it is uploaded and published, is unchanged:
 * vanilla itself queues this many rebuilds whenever its deadline allows. Client thread only.
 */
public final class ChunkSchedulingExperiments {
	private static FeedTarget feed;
	private static final ChunkTimeFloor FLOOR = new ChunkTimeFloor();

	private ChunkSchedulingExperiments() { }

	/** Terrain setup is about to cancel the rebuilds no worker started. */
	public static void beforeClear(ChunkRenderDispatcher dispatcher) {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return;
		}
		int unstarted = ((ChunkRenderDispatcherAccessor) dispatcher).alacer$getPendingTasks().size();
		FrameProfiler profiler = alacer.profiler();
		if (profiler != null) {
			profiler.count(Counter.CHUNK_UNSTARTED_TASKS, unstarted);
		}
		if (feed != null) {
			feed.beforeClear(unstarted);
		}
	}

	/** End of compileChunksUntil, inside its timer. */
	public static void afterCompile(ChunkRenderDispatcher dispatcher, Set<RenderChunk> dirty) {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return;
		}
		AlacerConfig config = alacer.config();
		if (!config.feedChunkWorkers) {
			if (feed != null) {
				feed.afterFeed(-1);
			}
			return;
		}
		int configuredWorkers = ChunkScheduling.threads(config.chunkThreads);
		if (feed == null || feed.workers() != configuredWorkers) {
			feed = new FeedTarget(configuredWorkers);
		}
		int queued = ((ChunkRenderDispatcherAccessor) dispatcher).alacer$getPendingTasks().size();
		int added = 0;
		int target = feed.target();
		Iterator<RenderChunk> iterator = dirty.iterator();
		while (queued < target && iterator.hasNext()) {
			RenderChunk chunk = iterator.next();
			if (!dispatcher.rebuildAsync(chunk)) {
				break;
			}
			chunk.setDirty(false);
			iterator.remove();
			queued++;
			added++;
		}
		feed.afterFeed(((ChunkRenderDispatcherAccessor) dispatcher).alacer$getPendingTasks().size());
		FrameProfiler profiler = alacer.profiler();
		if (profiler != null) {
			profiler.count(Counter.CHUNK_FED_TASKS, added);
		}
	}

	/** Start of compileChunksUntil: {@code render.chunkTimeFloor}, or vanilla's deadline unchanged. */
	public static long deadline(long vanilla) {
		Alacer alacer = Alacer.get();
		if (alacer == null || !alacer.config().chunkTimeFloor) {
			FLOOR.reset();
			return vanilla;
		}
		return FLOOR.deadline(vanilla, System.nanoTime());
	}

	/** World or renderer replacement: start again from the minimum depth and without a previous frame. */
	public static void reset() {
		feed = null;
		FLOOR.reset();
	}
}
