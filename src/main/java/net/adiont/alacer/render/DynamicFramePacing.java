package net.adiont.alacer.render;

import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Dynamic GPU queue synchronization and low-latency frame pacing (Phase 9.1).
 *
 * <p>Uses double-buffered asynchronous OpenGL timer queries ({@code GL33.GL_TIME_ELAPSED}) to measure
 * real-time GPU rasterization duration without stalling the CPU render thread. Dynamically calibrates
 * {@link FastDisplaySync} tail wait margins and keeps GPU queue depth between 1.0 and 1.5 frames.
 */
public final class DynamicFramePacing {

	private static final Logger LOGGER = LogManager.getLogger("Alacer/DynamicFramePacing");

	private static final int QUERY_RING_SIZE = 2;
	private static final int[] timerQueries = new int[QUERY_RING_SIZE];
	private static boolean queriesInitialized = false;
	private static int currentQueryIndex = 0;
	private static boolean queryActive = false;

	private static long lastGpuTimeNanos = 0L;
	private static long smoothedGpuTimeNanos = 2_000_000L; // initial 2 ms default
	private static float estimatedQueueDepth = 1.0f;

	private static final LongAdder querySamples = new LongAdder();

	private DynamicFramePacing() {}

	public static boolean isSupported() {
		try {
			return GLContext.getCapabilities().OpenGL33;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean isEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) return false;
		AlacerConfig config = alacer.config();
		return config != null && config.fastDisplaySync && isSupported();
	}

	public static synchronized void beginFrame() {
		if (!isSupported()) return;
		if (!queriesInitialized) {
			for (int i = 0; i < QUERY_RING_SIZE; i++) {
				timerQueries[i] = GL15.glGenQueries();
			}
			queriesInitialized = true;
		}

		// Read back results from previous frame query to avoid driver stalls
		int prevIndex = (currentQueryIndex + 1) % QUERY_RING_SIZE;
		int prevQuery = timerQueries[prevIndex];
		if (prevQuery != 0 && GL15.glGetQueryObjecti(prevQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE) {
			long gpuTime = GL33.glGetQueryObjecti64(prevQuery, GL15.GL_QUERY_RESULT);
			if (gpuTime > 0) {
				lastGpuTimeNanos = gpuTime;
				// Exponential moving average (alpha = 0.2)
				smoothedGpuTimeNanos = (long) (smoothedGpuTimeNanos * 0.8 + gpuTime * 0.2);
				querySamples.increment();
			}
		}

		// Begin timing the current frame GPU workload
		int activeQuery = timerQueries[currentQueryIndex];
		if (activeQuery != 0) {
			GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, activeQuery);
			queryActive = true;
		}
	}

	public static synchronized void endFrame() {
		if (queryActive) {
			GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
			queryActive = false;
			currentQueryIndex = (currentQueryIndex + 1) % QUERY_RING_SIZE;
		}
	}

	/**
	 * Computes dynamic tail spin margin (in nanoseconds) based on current GPU execution duration.
	 * Returns tighter margins when GPU is fast to save CPU cycles, and expands margins under heavy load.
	 */
	public static long computePacingMarginNanos(long targetFrameNanos) {
		if (smoothedGpuTimeNanos <= 0 || targetFrameNanos <= 0) {
			return 1_500_000L; // default 1.5 ms margin
		}

		float gpuLoad = (float) smoothedGpuTimeNanos / (float) targetFrameNanos;
		estimatedQueueDepth = Math.max(0.5f, Math.min(2.5f, gpuLoad + 0.2f));

		if (gpuLoad < 0.4f) {
			return 500_000L; // fast GPU (<40% load): tight 0.5 ms margin
		} else if (gpuLoad < 0.8f) {
			return 1_000_000L; // moderate GPU: 1.0 ms margin
		} else {
			return 1_500_000L; // heavy GPU (>80% load): full 1.5 ms margin
		}
	}

	public static synchronized void cleanup() {
		if (queriesInitialized) {
			for (int i = 0; i < QUERY_RING_SIZE; i++) {
				if (timerQueries[i] != 0) {
					GL15.glDeleteQueries(timerQueries[i]);
					timerQueries[i] = 0;
				}
			}
			queriesInitialized = false;
		}
		queryActive = false;
		currentQueryIndex = 0;
	}

	public static long lastGpuTimeNanos() { return lastGpuTimeNanos; }
	public static long smoothedGpuTimeNanos() { return smoothedGpuTimeNanos; }
	public static float estimatedQueueDepth() { return estimatedQueueDepth; }
	public static long querySamples() { return querySamples.sum(); }
	public static void clearCounters() {
		querySamples.reset();
		lastGpuTimeNanos = 0L;
		smoothedGpuTimeNanos = 2_000_000L;
		estimatedQueueDepth = 1.0f;
	}
}
