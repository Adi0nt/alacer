package net.adiont.alacer.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Optimizes GPU/CPU synchronization and buffer presentation.
 *
 * <p>In vanilla Minecraft, OpenGL 3D drawing commands are never explicitly flushed.
 * They remain in the driver's client-side command buffer until {@code Display.update()},
 * which issues {@code wglSwapBuffers}. This forces the CPU render thread to block until
 * the GPU finishes all rasterization and presentation, creating massive frame time spikes
 * and 40%+ swap stalls.
 *
 * <p>SwapOptimizer:
 * <ul>
 *   <li>Issues an early non-blocking {@link GL11#glFlush()} immediately upon completing
 *   3D world rendering (terrain, entities, sky, weather), allowing the GPU to execute 3D draws
 *   in parallel while the CPU handles 2D HUD, GUI, and client tick processing.</li>
 *   <li>Optionally maintains a 1-frame GPU sync fence ({@link GL32#glFenceSync}) when OpenGL 3.2+
 *   is supported to eliminate driver command buffer overflow stalls.</li>
 *   <li>Suppresses the unconditional {@link Thread#yield()} right after {@code updateDisplay()},
 *   preventing OS scheduler thread-switch latency spikes right before the next frame starts.</li>
 *   <li>Tracks separated driver swap duration and limiter sync duration for profiler attribution.</li>
 * </ul>
 */
public final class SwapOptimizer {

	private static final Logger LOGGER = LogManager.getLogger("SwapOptimizer");

	private static boolean initialized = false;
	private static boolean fenceSupported = false;
	private static final int FENCE_RING_SIZE = 2; // Double-buffered GPU/CPU overlap (1 frame in flight)
	private static final GLSync[] fenceRing = new GLSync[FENCE_RING_SIZE];
	private static int currentFenceIndex = 0;

	private static long lastDisplayUpdateStartNs = 0L;
	private static long lastDriverSwapNs = 0L;
	private static long lastLimiterSyncNs = 0L;
	private static long lastFenceWaitNs = 0L;
	private static long totalDriverSwapNs = 0L;
	private static long totalFlushes = 0L;
	private static long totalYieldsSuppressed = 0L;

	private static final int ROLLING_WINDOW = 64;
	private static final long[] SWAP_HISTORY = new long[ROLLING_WINDOW];
	private static int swapHistoryIndex = 0;
	private static int swapHistoryCount = 0;

	private SwapOptimizer() {
	}

	public static void initCapabilities() {
		if (initialized) return;
		try {
			if (GLContext.getCapabilities() != null) {
				fenceSupported = GLContext.getCapabilities().OpenGL32 || GLContext.getCapabilities().GL_ARB_sync;
			}
		} catch (Throwable t) {
			fenceSupported = false;
		}
		initialized = true;
	}

	public static boolean isFenceSupported() {
		if (!initialized) initCapabilities();
		return fenceSupported;
	}

	/**
	 * Called immediately upon completing 3D world rendering (terrain, entities, particles, weather).
	 * Flushes commands to the GPU early so that GPU rasterization overlaps with 2D GUI/HUD drawing.
	 */
	public static void onPostRenderWorld() {
		Alacer alacer = Alacer.get();
		if (alacer == null) return;
		AlacerConfig config = alacer.config();
		if (!config.fastSwapBuffers) return;

		try {
			GL11.glFlush();
			totalFlushes++;

			if (isFenceSupported() && (config.terrainShader || config.multiDrawIndirect)) {
				GLSync old = fenceRing[currentFenceIndex];
				if (old != null) {
					try {
						GL32.glDeleteSync(old);
					} catch (Throwable ignored) {
					}
					fenceRing[currentFenceIndex] = null;
				}
				fenceRing[currentFenceIndex] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
				currentFenceIndex = (currentFenceIndex + 1) % FENCE_RING_SIZE;
			}

			if (config.verifyFastSwapBuffers) {
				verifyFlush();
			}
		} catch (Throwable t) {
			if (config.verifyFastSwapBuffers) {
				LOGGER.error("SwapOptimizer onPostRenderWorld failed", t);
			}
		}
	}

	/**
	 * Called before beginning frame rendering to await completion of the frame from
	 * {@link #FENCE_RING_SIZE} frames ago.
	 *
	 * <p>Uses non-blocking polling first to avoid CPU stalls during steady-state rendering,
	 * only waiting if the GPU is more than 1 full frame behind.
	 */
	public static void onPreRenderWorld() {
		lastFenceWaitNs = 0L;
		if (!isFenceSupported()) return;

		// The oldest fence in the ring is at currentFenceIndex (submitted FENCE_RING_SIZE frames ago)
		GLSync oldestFence = fenceRing[currentFenceIndex];
		if (oldestFence != null) {
			try {
				int status = GL32.glClientWaitSync(oldestFence, 0, 0L);
				if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
					long waitStart = System.nanoTime();
					GL32.glClientWaitSync(oldestFence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 20_000_000L);
					lastFenceWaitNs = System.nanoTime() - waitStart;
				}
				GL32.glDeleteSync(oldestFence);
			} catch (Throwable ignored) {
			}
			fenceRing[currentFenceIndex] = null;
		}
	}

	/**
	 * Marks the start of {@code Minecraft.updateDisplay()}.
	 */
	public static void beforeDisplayUpdate() {
		lastDisplayUpdateStartNs = System.nanoTime();
	}

	/**
	 * Marks the completion of {@code Minecraft.updateDisplay()}.
	 */
	public static void afterDisplayUpdate() {
		if (lastDisplayUpdateStartNs > 0L) {
			long duration = System.nanoTime() - lastDisplayUpdateStartNs;
			lastDriverSwapNs = Math.max(0L, duration);
			totalDriverSwapNs += lastDriverSwapNs;

			SWAP_HISTORY[swapHistoryIndex] = lastDriverSwapNs;
			swapHistoryIndex = (swapHistoryIndex + 1) % ROLLING_WINDOW;
			if (swapHistoryCount < ROLLING_WINDOW) swapHistoryCount++;

			lastDisplayUpdateStartNs = 0L;
		}
	}

	/**
	 * Handles post-swap yield in {@code Minecraft.runGame()}.
	 */
	public static void onPostSwapYield() {
		Alacer alacer = Alacer.get();
		if (alacer != null && alacer.config().suppressThreadYield) {
			totalYieldsSuppressed++;
			Thread.onSpinWait();
		} else {
			Thread.yield();
		}
	}

	public static void recordSyncDuration(long syncNs) {
		lastLimiterSyncNs = Math.max(0L, syncNs);
	}

	public static long getLastDriverSwapNs() {
		return lastDriverSwapNs;
	}

	public static long getLastLimiterSyncNs() {
		return lastLimiterSyncNs;
	}

	public static double getAverageSwapMillis() {
		if (swapHistoryCount == 0) return 0.0D;
		long sum = 0L;
		for (int i = 0; i < swapHistoryCount; i++) {
			sum += SWAP_HISTORY[i];
		}
		return (sum / (double) swapHistoryCount) / 1_000_000.0D;
	}

	public static long getTotalFlushes() {
		return totalFlushes;
	}

	public static long getTotalYieldsSuppressed() {
		return totalYieldsSuppressed;
	}

	private static void verifyFlush() {
		int error = GL11.glGetError();
		if (error != GL11.GL_NO_ERROR) {
			LOGGER.warn("SwapOptimizer verification: GL error {} after glFlush", error);
		}
	}

	public static long getLastFenceWaitNs() {
		return lastFenceWaitNs;
	}

	public static void reset() {
		for (int i = 0; i < FENCE_RING_SIZE; i++) {
			if (fenceRing[i] != null) {
				try {
					if (isFenceSupported()) GL32.glDeleteSync(fenceRing[i]);
				} catch (Throwable ignored) {
				}
				fenceRing[i] = null;
			}
		}
		currentFenceIndex = 0;
		lastFenceWaitNs = 0L;
		lastDisplayUpdateStartNs = 0L;
		lastDriverSwapNs = 0L;
		lastLimiterSyncNs = 0L;
		swapHistoryIndex = 0;
		swapHistoryCount = 0;
	}
}
