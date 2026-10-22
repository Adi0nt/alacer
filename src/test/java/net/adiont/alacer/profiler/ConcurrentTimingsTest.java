package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConcurrentTimingsTest {
	@Test
	void drainKeepsTimeAndCountTogetherWhileWorkersPublish() throws Exception {
		ConcurrentTimings timings = new ConcurrentTimings(2);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(4);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		for (int n = 0; n < 4; n++) {
			final int metric = n % 2;
			new Thread(() -> {
				try {
					start.await();
					for (int i = 0; i < 10_000; i++) { timings.record(metric, metric + 7); }
				} catch (Throwable t) { failure.set(t); }
				finally { done.countDown(); }
			}).start();
		}
		long[] row = new long[4];
		long[] sums = new long[4];
		start.countDown();
		do {
			timings.drain(row);
			assertEquals(row[1] * 7, row[0]);
			assertEquals(row[3] * 8, row[2]);
			for (int i = 0; i < 4; i++) { sums[i] += row[i]; }
		} while (done.getCount() != 0);
		timings.drain(row);
		for (int i = 0; i < 4; i++) { sums[i] += row[i]; }
		assertEquals(null, failure.get());
		assertEquals(20_000, sums[1]);
		assertEquals(20_000 * 7, sums[0]);
		assertEquals(20_000, sums[3]);
		assertEquals(20_000 * 8, sums[2]);
		timings.drain(row);
		for (long value : row) { assertEquals(0L, value); }
	}
}
