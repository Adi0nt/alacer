package net.adiont.alacer.sound;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.TimeUnit;
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

	interface Waiter {
		long nanoTime();
		void park(Thread blocker, long nanos);
		boolean interrupted();
	}

	static final Waiter SYSTEM_WAITER = new Waiter() {
		public long nanoTime() { return System.nanoTime(); }
		public void park(Thread blocker, long nanos) { LockSupport.parkNanos(blocker, nanos); }
		public boolean interrupted() { return Thread.interrupted(); }
	};

	public static void snooze(Thread thread, long milliseconds) {
		if (isEnabled()) {
			snooze(thread, milliseconds, isVerifyEnabled(), SYSTEM_WAITER);
		} else {
			snoozes.increment();
			if (milliseconds <= 0) return;
			try { Thread.sleep(milliseconds); }
			catch (InterruptedException ignored) { }
		}
	}

	/** Same wait loop for production and deterministic timing/interrupt regression tests. */
	static void snooze(Thread thread, long milliseconds, boolean verify, Waiter waiter) {
		snoozes.increment();
		if (milliseconds <= 0) return;
		long requested = TimeUnit.MILLISECONDS.toNanos(milliseconds); // saturates instead of overflowing
		long start = waiter.nanoTime();
		long remaining = requested;
		boolean interrupted;
		do {
			waiter.park(thread, remaining);
			interrupted = waiter.interrupted();
			if (interrupted) {
				interruptionsAvoided.increment();
				break;
			}
			// Raw unpark permits and spurious returns do not shorten a timed sleep.
			remaining = requested - (waiter.nanoTime() - start);
		} while (remaining > 0);

		if (verify) {
			verifiedSnoozes.increment();
			long elapsed = waiter.nanoTime() - start;
			if (!interrupted && elapsed < requested) {
				verifyMismatches.increment();
				LOGGER.warn("FastSoundThread: wait returned early: requested {}ns, elapsed {}ns", requested, elapsed);
			}
		}
	}

	/**
	 * Legacy wake helper. Use interrupt so a requested wake can be distinguished from a
	 * spurious park return, just as in the sound library. snooze clears the interrupt flag.
	 */
	public static void unpark(Thread thread) {
		if (thread != null) thread.interrupt();
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
