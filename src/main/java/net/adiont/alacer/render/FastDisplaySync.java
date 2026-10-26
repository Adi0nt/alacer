package net.adiont.alacer.render;

import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.Minecraft;

/**
 * High-precision, zero-allocation frame rate limiter and frame pacer.
 *
 * <p>LWJGL's default {@link Display#sync(int)} algorithm is notorious for frame pacing stutter:
 * <ul>
 *   <li>It calls {@link Thread#sleep(long)} with 1ms resolution, which overshoots unpredictably on Windows.</li>
 *   <li>It calls {@link Thread#yield()} in a loop, yielding the CPU to background compiler/audio threads
 *   and causing 5–15 ms latency spikes.</li>
 *   <li>Its internal running averages oscillate and lose sync across frames.</li>
 * </ul>
 *
 * <p>FastDisplaySync:
 * <ul>
 *   <li>Uses {@link System#nanoTime()} for nanosecond-accurate monotonic timing.</li>
 *   <li>Uses {@link LockSupport#parkNanos(long)} for coarse sleep (leaving a 1 ms margin) without
 *   {@link InterruptedException} allocation.</li>
 *   <li>Uses Java's intrinsic {@link Thread#onSpinWait()} (CPU {@code PAUSE}) for the sub-millisecond
 *   tail, achieving sub-microsecond frame pacing precision without context switching.</li>
 *   <li>Coexists cleanly with hardware VSync: avoids beat-frequency phase beating when VSync is active.</li>
 *   <li>Bounds late frame deficits to prevent multi-frame catch-up stutter.</li>
 * </ul>
 */
public final class FastDisplaySync {

	private static final Logger LOGGER = LogManager.getLogger("FastDisplaySync");

	public interface Clock {
		long nanoTime();
		void parkNanos(long nanos);
		void onSpinWait();
		boolean interrupted();
	}

	static final Clock SYSTEM_CLOCK = new Clock() {
		@Override
		public long nanoTime() {
			return System.nanoTime();
		}

		@Override
		public void parkNanos(long nanos) {
			LockSupport.parkNanos(nanos);
		}

		@Override
		public void onSpinWait() {
			Thread.onSpinWait();
		}

		@Override
		public boolean interrupted() {
			return Thread.interrupted();
		}
	};

	private static Clock clock = SYSTEM_CLOCK;

	private static long nextFrameTimeNs = 0L;
	private static int lastTargetFps = -1;
	private static long totalSyncWaitNs = 0L;
	private static long totalParks = 0L;
	private static long totalSpins = 0L;
	private static long totalOverruns = 0L;
	private static long lastWaitNs = 0L;

	private static long verifyTotalFrames = 0L;
	private static long verifyMaxErrorNs = 0L;

	private FastDisplaySync() {
	}

	public static void setClockForTesting(Clock testClock) {
		clock = testClock != null ? testClock : SYSTEM_CLOCK;
	}

	public static void resetClock() {
		clock = SYSTEM_CLOCK;
	}

	/**
	 * Paces the current frame to the specified target FPS limit.
	 */
	public static void sync(int targetFps) {
		if (targetFps <= 0) return;

		Alacer alacer = Alacer.get();
		AlacerConfig config = (alacer != null) ? alacer.config() : null;
		if (config != null && !config.fastDisplaySync) {
			Display.sync(targetFps);
			return;
		}

		// Coexist with hardware VSync:
		// If VSync is enabled in Minecraft options, and targetFps >= refresh rate,
		// allow the display hardware to pace frames cleanly without software clock collision.
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.options != null && mc.options.vsync) {
				int refreshRate = Display.getDisplayMode().getFrequency();
				if (refreshRate <= 0) refreshRate = 60;
				if (targetFps >= refreshRate) {
					nextFrameTimeNs = 0L;
					SwapOptimizer.recordSyncDuration(0L);
					return;
				}
			}
		} catch (Throwable ignored) {
		}

		final long frameIntervalNs = 1_000_000_000L / targetFps;
		final long now = clock.nanoTime();

		if (nextFrameTimeNs == 0L || targetFps != lastTargetFps) {
			lastTargetFps = targetFps;
			nextFrameTimeNs = now + frameIntervalNs;
			lastWaitNs = 0L;
			SwapOptimizer.recordSyncDuration(0L);
			return;
		}

		long remainingNs = nextFrameTimeNs - now;

		// Overrun: the frame took longer than the target interval
		if (remainingNs <= 0L) {
			totalOverruns++;
			// Advance to next frame from now, avoiding catch-up frame bursts
			nextFrameTimeNs = now + frameIntervalNs;
			lastWaitNs = 0L;
			SwapOptimizer.recordSyncDuration(0L);
			return;
		}

		final long waitStart = now;

		// Phase 1: Coarse park if remaining wait exceeds 1.5 ms.
		// Leave a 1.0 ms margin for fine-grained spin wait to absorb OS park latency jitter.
		if (remainingNs > 1_500_000L) {
			long parkNs = remainingNs - 1_000_000L;
			clock.parkNanos(parkNs);
			if (clock.interrupted()) {
				// Clear interrupt status cleanly without allocating exceptions
			}
			totalParks++;
		}

		// Phase 2: Precision spin wait with CPU PAUSE instruction
		while ((nextFrameTimeNs - clock.nanoTime()) > 0L) {
			clock.onSpinWait();
			totalSpins++;
		}

		final long waited = clock.nanoTime() - waitStart;
		lastWaitNs = waited;
		totalSyncWaitNs += waited;
		SwapOptimizer.recordSyncDuration(waited);

		if (config != null && config.verifyFastDisplaySync) {
			verifyPacing(targetFps, waited, remainingNs);
		}

		// Advance monotonic next frame time
		nextFrameTimeNs += frameIntervalNs;
		// Guard against cumulative drift: if still behind current time, reset baseline
		if (nextFrameTimeNs < clock.nanoTime() - (frameIntervalNs / 2)) {
			nextFrameTimeNs = clock.nanoTime() + frameIntervalNs;
		}
	}

	private static void verifyPacing(int targetFps, long actualWaitedNs, long expectedRemainingNs) {
		verifyTotalFrames++;
		long errorNs = Math.abs(actualWaitedNs - expectedRemainingNs);
		if (errorNs > verifyMaxErrorNs) {
			verifyMaxErrorNs = errorNs;
		}
		// Warn only on severe over-sleep (> 2 ms error)
		if (errorNs > 2_000_000L && actualWaitedNs > expectedRemainingNs) {
			LOGGER.warn("FastDisplaySync verify: over-slept by {} µs at {} target FPS",
				errorNs / 1000L, targetFps);
		}
	}

	public static long getTotalSyncWaitNs() {
		return totalSyncWaitNs;
	}

	public static long getTotalParks() {
		return totalParks;
	}

	public static long getTotalSpins() {
		return totalSpins;
	}

	public static long getTotalOverruns() {
		return totalOverruns;
	}

	public static long getLastWaitNs() {
		return lastWaitNs;
	}

	public static long getVerifyTotalFrames() {
		return verifyTotalFrames;
	}

	public static long getVerifyMaxErrorNs() {
		return verifyMaxErrorNs;
	}

	public static void reset() {
		nextFrameTimeNs = 0L;
		lastTargetFps = -1;
		totalSyncWaitNs = 0L;
		totalParks = 0L;
		totalSpins = 0L;
		totalOverruns = 0L;
		lastWaitNs = 0L;
		verifyTotalFrames = 0L;
		verifyMaxErrorNs = 0L;
	}
}
