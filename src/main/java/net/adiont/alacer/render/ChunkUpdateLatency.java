package net.adiont.alacer.render;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.profiler.LatencyLog;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;

/**
 * {@code profiler.chunkLatency}: visible-update latency, from the end of the first terrain setup that finds a chunk
 * dirty and visible (it is then in the renderer's dirty set) to the publication of a completed rebuild, on whichever
 * thread publishes it. Measurement only: no scheduling change and no retained world, task or buffer references.
 *
 * <p>The start has frame resolution: a block change made during a frame's tick is stamped at that frame's terrain
 * setup. Rebuilds made synchronously for chunks next to the camera in the same setup are never stamped (they are
 * visible in that frame). An update that arrives while its chunk's rebuild runs waits from the next setup that finds
 * it eligible (a lower bound), and a slot moved to another position drops its waiting update. The waiting gauge also
 * counts eligible chunks that left the view before their rebuild started; vanilla does not rebuild those until they
 * are visible again, and their latency is only recorded if that happens.
 */
public final class ChunkUpdateLatency {
	/** One launch's phases rarely publish more than a few thousand rebuilds; later ones only count as overflow. */
	public static final LatencyLog LOG = new LatencyLog(1 << 18);
	private static final AtomicInteger WAITING = new AtomicInteger();
	private static final long[] DRAIN = new long[3];

	private ChunkUpdateLatency() { }

	/** End of terrain setup, client thread: stamps newly eligible chunks and counts holes in the visible set. */
	public static void afterSetup(FrameProfiler profiler, Set<RenderChunk> dirty, List<WorldRenderer.RenderChunkInfo> visible) {
		long now = System.nanoTime();
		for (RenderChunk chunk : dirty) {
			if (((ChunkUpdateStamp) chunk).alacer$updateStamp().eligible(now)) {
				WAITING.incrementAndGet();
			}
		}
		int holes = 0;
		for (int i = 0, n = visible.size(); i < n; i++) {
			if (visible.get(i).chunk.getCompiledChunk() == CompiledChunk.UNCOMPILED) {
				holes++;
			}
		}
		profiler.count(Counter.CHUNK_VISIBLE_UNCOMPILED, holes);
	}

	/** A rebuild's compiled data is about to be published (client or worker thread). */
	public static void published(RenderChunk chunk) {
		long waited = ((ChunkUpdateStamp) chunk).alacer$updateStamp().publish(System.nanoTime());
		if (waited >= 0L) {
			LOG.record(waited);
			WAITING.decrementAndGet();
		}
	}

	/** The slot is moving to another section, or its storage is being deleted. */
	public static void moved(RenderChunk chunk) {
		if (((ChunkUpdateStamp) chunk).alacer$updateStamp().clear()) {
			WAITING.decrementAndGet();
		}
	}

	/** Once per completed frame, client thread. */
	public static void frameBoundary(FrameProfiler profiler) {
		LOG.drainFrame(DRAIN);
		profiler.add(Section.CHUNK_UPDATE_LATENCY, DRAIN[0]);
		profiler.count(Counter.CHUNK_UPDATES_PUBLISHED, (int) DRAIN[1]);
		profiler.add(Section.CHUNK_UPDATE_LATENCY_MAX, DRAIN[2]);
		profiler.count(Counter.CHUNK_UPDATES_WAITING, WAITING.get());
	}
}
