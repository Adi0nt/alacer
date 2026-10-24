package net.adiont.alacer.sound;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FastSoundThreadTest {

	@BeforeEach
	void setUp() {
		FastSoundThread.clear();
	}

	@Test
	void zeroOrNegativeSleepReturnsImmediately() {
		FastSoundThread.snooze(Thread.currentThread(), 0);
		FastSoundThread.snooze(Thread.currentThread(), -100);
		assertEquals(2, FastSoundThread.snoozes());
		assertEquals(0, FastSoundThread.interruptionsAvoided());
	}

	@Test
	void snoozeUnparksOnInterruptWithoutException() throws Exception {
		Thread worker = new Thread(() -> {
			FastSoundThread.snooze(Thread.currentThread(), 60_000);
		});

		worker.start();
		Thread.sleep(50);
		worker.interrupt();
		worker.join(2000);

		assertFalse(worker.isAlive());
		assertEquals(1, FastSoundThread.snoozes());
		assertEquals(1, FastSoundThread.interruptionsAvoided());
	}

	@Test
	void clearResetsCounters() {
		FastSoundThread.snooze(Thread.currentThread(), 0);
		assertEquals(1, FastSoundThread.snoozes());

		FastSoundThread.clear();
		assertEquals(0, FastSoundThread.snoozes());
		assertEquals(0, FastSoundThread.interruptionsAvoided());
	}
}
