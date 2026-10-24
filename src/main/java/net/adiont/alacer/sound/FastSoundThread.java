package net.adiont.alacer.sound;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Optimizes paulscode sound threads (CommandThread and StreamThread) by replacing
 * {@link Thread#sleep(long)} with allocation-free {@link LockSupport#parkNanos(Object, long)}.
 *
 * <p>In vanilla Minecraft, the sound command thread is continuously woken up by
 * {@link Thread#interrupt()}, generating tens of thousands of {@link InterruptedException}
 * instances during normal gameplay. Park/unpark semantics wake the thread without throwing
 * or allocating exceptions.
 *
 * <p>When a parked thread is interrupted via {@link Thread#interrupt()}, {@link LockSupport#parkNanos}
 * returns immediately. Calling {@link Thread#interrupted()} clears the interrupted status and
 * returns true, exactly matching vanilla {@link Thread#sleep}'s behavior when an {@link InterruptedException}
 * is caught.
 */
public final class FastSoundThread {

	private static final Logger LOGGER = LogManager.getLogger("Alacer/FastSoundThread");

	private static final LongAdder snoozes = new LongAdder();
	private static final LongAdder interruptionsAvoided = new LongAdder();
	private static final LongAdder verifiedSnoozes = new LongAdder();
	private static final LongAdder verifyMismatches = new LongAdder();

	private FastSoundThread() {}

	public static boolean isEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return true;
		}
		AlacerConfig config = alacer.config();
		return config == null || config.fastSoundThread;
	}

	public static boolean isVerifyEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return false;
		}
		AlacerConfig config = alacer.config();
		return config != null && config.verifyFastSoundThread;
	}

	public static void snooze(Thread thread, long milliseconds) {
		snoozes.increment();
		if (milliseconds <= 0) {
			return;
		}
		if (isEnabled()) {
			boolean verify = isVerifyEnabled();
			long startNanos = verify ? System.nanoTime() : 0L;

			LockSupport.parkNanos(thread, milliseconds * 1_000_000L);
			boolean interrupted = Thread.interrupted();
			if (interrupted) {
				interruptionsAvoided.increment();
			}

			if (verify) {
				verifiedSnoozes.increment();
				long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
				// If not interrupted, elapsed time should have reached at least near the requested time
				// (accounting for standard timer granularity / OS scheduling slack of ~15ms on Windows).
				if (!interrupted && milliseconds > 50 && elapsedMillis < (milliseconds - 20)) {
					verifyMismatches.increment();
					LOGGER.warn("FastSoundThread: premature unpark without interrupt: requested {}ms, elapsed {}ms",
						milliseconds, elapsedMillis);
				}
			}
		} else {
			try {
				Thread.sleep(milliseconds);
			} catch (InterruptedException ignored) {
				// Vanilla sleep clears the interrupted status when throwing InterruptedException
			}
		}
	}

	/**
	 * Unparks the sound thread directly if parked.
	 */
	public static void unpark(Thread thread) {
		if (thread != null) {
			LockSupport.unpark(thread);
		}
	}

	public static void clear() {
		snoozes.reset();
		interruptionsAvoided.reset();
		verifiedSnoozes.reset();
		verifyMismatches.reset();
	}

	public static long snoozes() {
		return snoozes.sum();
	}

	public static long interruptionsAvoided() {
		return interruptionsAvoided.sum();
	}

	public static long verifiedSnoozes() {
		return verifiedSnoozes.sum();
	}

	public static long verifyMismatches() {
		return verifyMismatches.sum();
	}
}
