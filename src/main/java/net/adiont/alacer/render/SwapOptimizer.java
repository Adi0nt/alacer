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
	private static GLSync previousFrameFence = null;

	private static long lastDisplayUpdateStartNs = 0L;
	private static long lastDriverSwapNs = 0L;
	private static long lastLimiterSyncNs = 0L;
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
				if (previousFrameFence != null) {
					try {
						GL32.glDeleteSync(previousFrameFence);
					} catch (Throwable ignored) {
					}
					previousFrameFence = null;
				}
				previousFrameFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
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
	 * Called before beginning frame rendering to await previous frame completion if fence active.
	 */
	public static void onPreRenderWorld() {
		if (previousFrameFence != null && isFenceSupported()) {
			try {
				GL32.glClientWaitSync(previousFrameFence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 50_000_000L);
				GL32.glDeleteSync(previousFrameFence);
			} catch (Throwable ignored) {
			}
			previousFrameFence = null;
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

	public static void reset() {
		if (previousFrameFence != null) {
			try {
				if (isFenceSupported()) GL32.glDeleteSync(previousFrameFence);
			} catch (Throwable ignored) {
			}
			previousFrameFence = null;
		}
		lastDisplayUpdateStartNs = 0L;
		lastDriverSwapNs = 0L;
		lastLimiterSyncNs = 0L;
		swapHistoryIndex = 0;
		swapHistoryCount = 0;
	}
}
