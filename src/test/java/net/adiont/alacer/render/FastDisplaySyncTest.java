package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FastDisplaySyncTest {

	private static final class MockClock implements FastDisplaySync.Clock {
		private final AtomicLong currentTimeNs = new AtomicLong(1_000_000_000L);
		private final AtomicLong parkedNanos = new AtomicLong(0L);
		private final AtomicInteger parkCount = new AtomicInteger(0);
		private final AtomicInteger spinCount = new AtomicInteger(0);
		private volatile boolean interrupted = false;

		@Override
		public long nanoTime() {
			return currentTimeNs.get();
		}

		@Override
		public void parkNanos(long nanos) {
			parkCount.incrementAndGet();
			parkedNanos.addAndGet(nanos);
			currentTimeNs.addAndGet(nanos);
		}

		@Override
		public void onSpinWait() {
			spinCount.incrementAndGet();
			currentTimeNs.addAndGet(100_000L); // 0.1ms per spin
		}

		@Override
		public boolean interrupted() {
			boolean was = this.interrupted;
			this.interrupted = false;
			return was;
		}

		void advanceTime(long nanos) {
			currentTimeNs.addAndGet(nanos);
		}
	}

	private MockClock clock;

	@BeforeEach
	void setUp() {
		FastDisplaySync.reset();
		SwapOptimizer.reset();
		this.clock = new MockClock();
		FastDisplaySync.setClockForTesting(this.clock);
	}

	@AfterEach
	void tearDown() {
		FastDisplaySync.resetClock();
		FastDisplaySync.reset();
		SwapOptimizer.reset();
	}

	@Test
	void zeroOrNegativeFpsReturnsImmediately() {
		FastDisplaySync.sync(0);
		FastDisplaySync.sync(-10);
		assertEquals(0, this.clock.parkCount.get());
		assertEquals(0, this.clock.spinCount.get());
		assertEquals(0L, FastDisplaySync.getTotalSyncWaitNs());
	}

	@Test
	void firstFrameInitializesMonotonicBaselineWithoutWaiting() {
		FastDisplaySync.sync(240);
		assertEquals(0, this.clock.parkCount.get());
		assertEquals(0, this.clock.spinCount.get());
		assertEquals(0L, FastDisplaySync.getLastWaitNs());
	}

	@Test
	void pacingAt240FpsParksAndSpinsToTargetDeadline() {
		// Target: 240 FPS = 4,166,666 ns per frame
		FastDisplaySync.sync(240);

		// Simulate 1.5ms of frame execution time (leaves ~2.66ms wait)
		this.clock.advanceTime(1_500_000L);

		FastDisplaySync.sync(240);

		// With 2.66ms remaining, it should park for remaining - 1ms (~1.66ms)
		assertTrue(this.clock.parkCount.get() >= 1, "Should have parked during long wait");
		assertTrue(this.clock.spinCount.get() >= 1, "Should have spin-waited final tail");
		assertTrue(FastDisplaySync.getLastWaitNs() > 0L, "Should record wait duration");
		assertTrue(FastDisplaySync.getTotalParks() >= 1);
		assertTrue(FastDisplaySync.getTotalSpins() >= 1);
	}

	@Test
	void shortWaitBelow1Point5MsSpinsWithoutParking() {
		FastDisplaySync.sync(240);

		// Frame took 3.5ms out of 4.16ms interval, remaining is ~0.66ms (< 1.5ms)
		this.clock.advanceTime(3_500_000L);

		FastDisplaySync.sync(240);

		assertEquals(0, this.clock.parkCount.get(), "Short wait should not park");
		assertTrue(this.clock.spinCount.get() >= 1, "Short wait should spin");
		assertTrue(FastDisplaySync.getLastWaitNs() > 0L);
	}

	@Test
	void frameOverrunBoundsDeficitAndDoesNotBurst() {
		FastDisplaySync.sync(240);

		// Frame severely overran: took 20ms (> 4.16ms)
		this.clock.advanceTime(20_000_000L);

		FastDisplaySync.sync(240);

		assertEquals(1, FastDisplaySync.getTotalOverruns());
		assertEquals(0L, FastDisplaySync.getLastWaitNs());
		assertEquals(0, this.clock.parkCount.get());

		// Next frame should reset baseline to current time + interval
		this.clock.advanceTime(2_000_000L);
		FastDisplaySync.sync(240);
		assertTrue(FastDisplaySync.getLastWaitNs() > 0L, "Subsequent frame should pace normally");
	}

	@Test
	void interruptionDuringParkClearsInterruptedStatus() {
		FastDisplaySync.sync(60); // 16.6ms interval

		this.clock.advanceTime(2_000_000L); // 2ms spent
		this.clock.interrupted = true;

		FastDisplaySync.sync(60);

		assertTrue(FastDisplaySync.getLastWaitNs() > 0L);
		assertEquals(false, this.clock.interrupted);
	}

	@Test
	void changingFpsTargetReinitializesBaseline() {
		FastDisplaySync.sync(60);
		this.clock.advanceTime(5_000_000L);

		// Change to 144 FPS
		FastDisplaySync.sync(144);
		assertEquals(0L, FastDisplaySync.getLastWaitNs(), "Changing target FPS should reinitialize baseline");
	}
}
