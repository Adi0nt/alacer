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
		// Explicit wakes use the sound library's interrupt-and-clear contract.
		assertEquals(1, FastSoundThread.interruptionsAvoided());
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

	private static final class FakeWaiter implements FastSoundThread.Waiter {
		long now;
		long firstAdvance;
		int calls;
		boolean interrupt;
		final java.util.List<Long> waits = new java.util.ArrayList<>();
		public long nanoTime() { return now; }
		public void park(Thread blocker, long nanos) {
			waits.add(nanos);
			// First call is a spurious return or an already available raw unpark permit.
			if (calls++ > 0) now += nanos;
			else now += firstAdvance;
		}
		public boolean interrupted() { boolean result = interrupt; interrupt = false; return result; }
	}

	@Test void spuriousReturnRetriesRemainingDeadlineAndActuallyVerifies() {
		FakeWaiter waiter = new FakeWaiter();
		FastSoundThread.snooze(Thread.currentThread(), 100, true, waiter);
		assertEquals(java.util.List.of(100_000_000L, 100_000_000L), waiter.waits);
		assertEquals(100_000_000L, waiter.now);
		assertEquals(1, FastSoundThread.verifiedSnoozes());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}

	@Test void interruptedWaitReturnsEarlyAndClearsStatus() {
		FakeWaiter waiter = new FakeWaiter();
		waiter.interrupt = true;
		FastSoundThread.snooze(Thread.currentThread(), 100, true, waiter);
		assertEquals(0, waiter.now);
		assertFalse(waiter.interrupt);
		assertEquals(1, FastSoundThread.interruptionsAvoided());
		assertEquals(1, FastSoundThread.verifiedSnoozes());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}

	@Test void earlyReturnWaitsOnlyForTheRemainingTimeAcrossClockWrap() {
		FakeWaiter waiter = new FakeWaiter();
		waiter.now = Long.MAX_VALUE - 20_000_000L;
		waiter.firstAdvance = 30_000_000L;
		FastSoundThread.snooze(Thread.currentThread(), 100, true, waiter);
		assertEquals(java.util.List.of(100_000_000L, 70_000_000L), waiter.waits);
		assertEquals(1, FastSoundThread.verifiedSnoozes());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}

	@Test void longTimeoutConversionSaturatesWithoutBecomingNegative() {
		FakeWaiter waiter = new FakeWaiter();
		FastSoundThread.snooze(Thread.currentThread(), Long.MAX_VALUE, true, waiter);
		assertEquals(Long.MAX_VALUE, waiter.now);
		assertTrue(waiter.waits.stream().allMatch(nanos -> nanos > 0));
		assertEquals(1, FastSoundThread.verifiedSnoozes());
	}

	@Test void concurrentParkingExerciseVerifiesEveryCompletedWait() throws Exception {
		int threads = 16, iterations = 200;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
		try {
			for (int c = 0; c < threads; c++) {
				futures.add(pool.submit(() -> {
					for (int i = 0; i < iterations; i++) {
						Thread self = Thread.currentThread();
						if (i % 2 != 0) FastSoundThread.unpark(self);
						FastSoundThread.snooze(self, i % 2 == 0 ? 1 : 10, true, FastSoundThread.SYSTEM_WAITER);
						assertFalse(self.isInterrupted());
					}
				}));
			}
			for (java.util.concurrent.Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
			assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
		}
		assertEquals(threads * iterations, FastSoundThread.snoozes());
		assertEquals(threads * iterations, FastSoundThread.verifiedSnoozes());
		assertEquals(threads * iterations / 2, FastSoundThread.interruptionsAvoided());
		assertEquals(0, FastSoundThread.verifyMismatches());
	}
}
