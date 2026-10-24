package net.adiont.alacer.sound;

import java.util.concurrent.locks.LockSupport;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Optimizes paulscode sound threads (CommandThread and StreamThread) by replacing
 * {@link Thread#sleep(long)} with allocation-free {@link LockSupport#parkNanos(Object, long)}.
 *
 * <p>In vanilla Minecraft, the sound command thread is continuously woken up by
 * {@link Thread#interrupt()}, generating tens of thousands of {@link InterruptedException}
 * instances during normal gameplay. Park/unpark semantics wake the thread without throwing
 * or catching exceptions.
 */
public final class FastSoundThread {

	private static long snoozes;
	private static long interruptionsAvoided;

	private FastSoundThread() {}

	public static boolean isEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return true;
		}
		AlacerConfig config = alacer.config();
		return config == null || config.fastSoundThread;
	}

	public static void snooze(Thread thread, long milliseconds) {
		snoozes++;
		if (milliseconds <= 0) {
			return;
		}
		if (isEnabled()) {
			LockSupport.parkNanos(thread, milliseconds * 1_000_000L);
			if (Thread.interrupted()) {
				interruptionsAvoided++;
			}
		} else {
			try {
				Thread.sleep(milliseconds);
			} catch (InterruptedException ignored) {
			}
		}
	}

	public static void clear() {
		snoozes = 0;
		interruptionsAvoided = 0;
	}

	public static long snoozes() {
		return snoozes;
	}

	public static long interruptionsAvoided() {
		return interruptionsAvoided;
	}
}
