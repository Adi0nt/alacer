package net.adiont.alacer.render;

import java.util.Queue;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.ChunkRenderDispatcherAccessor;
import net.adiont.alacer.mixin.WorldRendererAccessor;
import net.adiont.alacer.profiler.ConcurrentTimings;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.util.math.BlockPos;

/** Opt-in attribution only. No scheduling changes or retained world/task/buffer references. */
public final class ChunkPipelineTimings {
	public enum Work {
		BUILD(Section.CHUNK_BUILD_WORKER, Counter.CHUNK_BUILD_WORKER_CALLS),
		RESORT(Section.CHUNK_RESORT_WORKER, Counter.CHUNK_RESORT_WORKER_CALLS),
		BUFFER(Section.CHUNK_BUFFER_WAIT, Counter.CHUNK_BUFFER_WAITS),
		QUEUE(Section.CHUNK_QUEUE_WAIT, Counter.CHUNK_TASK_STARTS),
		UPLOAD(Section.CHUNK_UPLOAD_LATENCY, Counter.CHUNK_TASK_COMPLETIONS);

		final Section section;
		final Counter counter;
		Work(Section section, Counter counter) {
			this.section = section;
			this.counter = counter;
		}
	}

	private static final Work[] WORK = Work.values();
	private static final ConcurrentTimings COMPLETIONS = new ConcurrentTimings(WORK.length);
	private static final long[] DRAIN = new long[WORK.length * 2];
	private static long frame;

	private ChunkPipelineTimings() { }

	public static void record(Work work, long elapsed) {
		COMPLETIONS.record(work.ordinal(), elapsed);
	}

	public static void build(boolean client, boolean resort, long elapsed) {
		if (client) {
			FrameProfiler p = Alacer.get().profiler();
			p.add(resort ? Section.CHUNK_RESORT_CLIENT : Section.CHUNK_BUILD_CLIENT, elapsed);
			p.count(resort ? Counter.CHUNK_RESORT_CLIENT_CALLS : Counter.CHUNK_BUILD_CLIENT_CALLS);
		} else {
			record(resort ? Work.RESORT : Work.BUILD, elapsed);
		}
	}

	/** Called under vanilla's task lock. Cancellation writes DONE directly and is excluded. */
	public static long status(ChunkCompileTask.Status status, long created, long uploadStart) {
		long now = System.nanoTime();
		if (status == ChunkCompileTask.Status.COMPILING) {
			record(Work.QUEUE, now - created);
		} else if (status == ChunkCompileTask.Status.UPLOADING) {
			return now;
		} else if (status == ChunkCompileTask.Status.DONE && uploadStart != 0L) {
			record(Work.UPLOAD, now - uploadStart);
		}
		return uploadStart;
	}

	public static void end(Section section, Counter count) {
		FrameProfiler p = Alacer.get().profiler();
		p.end(section);
		p.count(count);
	}

	public static void enqueued(RenderChunk chunk, boolean accepted) {
		end(Section.CHUNK_ENQUEUE, Counter.CHUNK_ENQUEUE_CALLS);
		if (accepted) {
			FrameProfiler p = Alacer.get().profiler();
			p.count(Counter.CHUNK_ENQUEUE_ACCEPTED);
			BlockPos origin = chunk.getOrigin();
			long age = ((ChunkDirtyAge) chunk).alacer$dirtyAge().scheduled(frame, System.nanoTime(), origin.getX(), origin.getY(), origin.getZ());
			if (age >= 0L) {
				p.add(Section.CHUNK_SCHEDULE_WAIT, age);
				p.count(Counter.CHUNK_OBSERVED_SCHEDULES);
			}
		}
	}

	public static void deadline(long deadline) {
		if (System.nanoTime() > deadline) { Alacer.get().profiler().count(Counter.CHUNK_EXPIRED_DEADLINES); }
	}

	/** Once per completed frame on client thread. Background intervals are completion totals. */
	public static void frameBoundary(FrameProfiler p) {
		COMPLETIONS.drain(DRAIN);
		for (Work work : WORK) {
			int i = work.ordinal() * 2;
			p.add(work.section, DRAIN[i]);
			p.count(work.counter, (int) DRAIN[i + 1]);
		}
		if (Minecraft.getInstance().worldRenderer == null) { return; }
		WorldRendererAccessor renderer = (WorldRendererAccessor) Minecraft.getInstance().worldRenderer;
		ChunkRenderDispatcherAccessor dispatcher = (ChunkRenderDispatcherAccessor) renderer.alacer$getChunkRenderDispatcher();
		p.count(Counter.CHUNK_DIRTY, renderer.alacer$getDirtyChunks().size());
		long now = System.nanoTime();
		long totalAge = 0L, maxAge = 0L;
		frame++;
		for (RenderChunk chunk : renderer.alacer$getDirtyChunks()) {
			BlockPos origin = chunk.getOrigin();
			long age = ((ChunkDirtyAge) chunk).alacer$dirtyAge().observe(frame, now, origin.getX(), origin.getY(), origin.getZ());
			totalAge += age;
			maxAge = Math.max(maxAge, age);
		}
		p.add(Section.CHUNK_DIRTY_AGE, totalAge);
		p.add(Section.CHUNK_DIRTY_AGE_MAX, maxAge);
		p.count(Counter.CHUNK_QUEUED, dispatcher.alacer$getPendingTasks().size());
		Queue<?> uploads = dispatcher.alacer$getPendingUploads();
		synchronized (uploads) {
			p.count(Counter.CHUNK_READY_UPLOADS, uploads.size());
		}
		p.count(Counter.CHUNK_AVAILABLE_BUFFERS, dispatcher.alacer$getAvailableBuffers().size());
	}
}
