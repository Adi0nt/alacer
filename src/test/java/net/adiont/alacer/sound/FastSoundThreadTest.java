package net.adiont.alacer.sound;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
	void unparkWakesSnoozingThreadCleanly() throws Exception {
		AtomicBoolean finished = new AtomicBoolean(false);
		Thread worker = new Thread(() -> {
			FastSoundThread.snooze(Thread.currentThread(), 60_000);
			finished.set(true);
		});

		worker.start();
		Thread.sleep(50);
		FastSoundThread.unpark(worker);
		worker.join(2000);

		assertFalse(worker.isAlive());
		assertTrue(finished.get());
		assertEquals(1, FastSoundThread.snoozes());
		// Unpark is not an interruption
		assertEquals(0, FastSoundThread.interruptionsAvoided());
	}

	@Test
	void interruptedStatusMatchesVanilla() throws Exception {
		// 1. In vanilla: Thread.sleep catches InterruptedException and clears interrupted status
		AtomicBoolean vanillaInterruptedAfterCatch = new AtomicBoolean(true);
		Thread vanillaWorker = new Thread(() -> {
			try {
				Thread.sleep(60_000);
			} catch (InterruptedException e) {
				vanillaInterruptedAfterCatch.set(Thread.currentThread().isInterrupted());
			}
		});
		vanillaWorker.start();
		Thread.sleep(50);
		vanillaWorker.interrupt();
		vanillaWorker.join(2000);
		assertFalse(vanillaInterruptedAfterCatch.get(), "Vanilla sleep should clear interrupted flag upon exception");

		// 2. In FastSoundThread: snooze clears interrupted status via Thread.interrupted()
		AtomicBoolean fastInterruptedAfterCatch = new AtomicBoolean(true);
		Thread fastWorker = new Thread(() -> {
			FastSoundThread.snooze(Thread.currentThread(), 60_000);
			fastInterruptedAfterCatch.set(Thread.currentThread().isInterrupted());
		});
		fastWorker.start();
		Thread.sleep(50);
		fastWorker.interrupt();
		fastWorker.join(2000);
		assertFalse(fastInterruptedAfterCatch.get(), "FastSoundThread snooze should clear interrupted flag upon return");
	}

	@Test
	void timedSnoozeWithoutInterruptionReturnsAfterElapsed() {
		long start = System.currentTimeMillis();
		FastSoundThread.snooze(Thread.currentThread(), 20);
		long elapsed = System.currentTimeMillis() - start;

		assertTrue(elapsed >= 15, "Should wait for requested duration (with OS scheduling slack)");
		assertEquals(1, FastSoundThread.snoozes());
		assertEquals(0, FastSoundThread.interruptionsAvoided());
	}

	@Test
	void concurrentMultiThreadedSnoozeSafety() throws Exception {
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch readyLatch = new CountDownLatch(threads);
		CountDownLatch doneLatch = new CountDownLatch(threads);

		Thread[] workers = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			final int index = i;
			pool.submit(() -> {
				workers[index] = Thread.currentThread();
				readyLatch.countDown();
				FastSoundThread.snooze(Thread.currentThread(), 60_000);
				doneLatch.countDown();
			});
		}

		assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
		Thread.sleep(50);

		for (int i = 0; i < threads; i++) {
			workers[i].interrupt();
		}

		assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(2, TimeUnit.SECONDS);

		assertEquals(threads, FastSoundThread.snoozes());
		assertEquals(threads, FastSoundThread.interruptionsAvoided());
	}

	@Test
	void clearResetsCounters() {
		FastSoundThread.snooze(Thread.currentThread(), 0);
		assertEquals(1, FastSoundThread.snoozes());

		FastSoundThread.clear();
		assertEquals(0, FastSoundThread.snoozes());
		assertEquals(0, FastSoundThread.interruptionsAvoided());
		assertEquals(0, FastSoundThread.verifiedSnoozes());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}

	@Test
	void heavyAudioStreamingConcurrentLockFreeParkingCertification() throws Exception {
		int channels = 16;
		int iterationsPerChannel = 200;
		ExecutorService channelPool = Executors.newFixedThreadPool(channels);
		CountDownLatch done = new CountDownLatch(channels);

		for (int c = 0; c < channels; c++) {
			channelPool.submit(() -> {
				try {
					Thread self = Thread.currentThread();
					for (int i = 0; i < iterationsPerChannel; i++) {
						// Audio stream channel sleeps briefly or is immediately unparked
						if (i % 2 == 0) {
							FastSoundThread.snooze(self, 1);
						} else {
							FastSoundThread.unpark(self);
							FastSoundThread.snooze(self, 10);
						}
					}
				} finally {
					done.countDown();
				}
			});
		}

		assertTrue(done.await(10, TimeUnit.SECONDS), "All audio streaming channels must complete without deadlock");
		channelPool.shutdown();
		channelPool.awaitTermination(2, TimeUnit.SECONDS);

		assertEquals(channels * iterationsPerChannel, FastSoundThread.snoozes());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}
}
