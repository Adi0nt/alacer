package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateLatencyTest {
	@Test
	void theFirstEligibleFrameStartsTheWaitAndPublicationEndsIt() {
		UpdateStamp stamp = new UpdateStamp();
		assertEquals(-1, stamp.publish(50), "nothing waiting");
		assertTrue(stamp.eligible(100));
		assertFalse(stamp.eligible(150), "re-queued after a cancelled task: still the first wait");
		assertTrue(stamp.waiting());
		assertEquals(80, stamp.publish(180));
		assertFalse(stamp.waiting());
		assertEquals(-1, stamp.publish(190), "one update, one latency");
	}

	@Test
	void anUpdateDuringTheRebuildWaitsFromItsNextEligibilityAndAMoveDropsIt() {
		UpdateStamp stamp = new UpdateStamp();
		stamp.eligible(100);
		assertEquals(100, stamp.publish(200));
		assertFalse(stamp.waiting(), "a newer invalidation waits from its next eligible frame");
		assertTrue(stamp.eligible(230));
		assertEquals(20, stamp.publish(250));
		stamp.eligible(300);
		assertTrue(stamp.clear());
		assertFalse(stamp.clear());
		assertEquals(-1, stamp.publish(400));
		assertTrue(stamp.eligible(0), "a zero clock value still counts as waiting");
		assertEquals(4, stamp.publish(5), "stamped as 1 ns");
	}

	@Test
	void frameTotalsDrainIndependentlyOfPerEventValues() {
		LatencyLog log = new LatencyLog(3);
		long[] out = new long[3];
		log.record(10);
		log.record(30);
		log.drainFrame(out);
		assertArrayEquals(new long[] {40, 2, 30}, out);
		log.drainFrame(out);
		assertArrayEquals(new long[] {0, 0, 0}, out);
		log.record(5);
		log.record(7);
		assertEquals(1, log.overflow());
		assertArrayEquals(new long[] {10, 30, 5}, log.takeValues());
		assertEquals(0, log.overflow());
		assertArrayEquals(new long[0], log.takeValues());
		log.drainFrame(out);
		assertArrayEquals(new long[] {12, 2, 7}, out, "overflowed values still count in frame totals");
	}

	@Test
	void concurrentPublishersLoseNoLatency() throws InterruptedException {
		LatencyLog log = new LatencyLog(1 << 16);
		List<Thread> threads = new ArrayList<>();
		for (int t = 0; t < 4; t++) {
			Thread thread = new Thread(() -> { for (int i = 0; i < 10_000; i++) { log.record(1); } });
			threads.add(thread);
			thread.start();
		}
		for (Thread thread : threads) { thread.join(); }
		long[] out = new long[3];
		log.drainFrame(out);
		assertEquals(40_000, out[0]);
		assertEquals(40_000, out[1]);
		assertEquals(40_000, log.takeValues().length);
	}
}
