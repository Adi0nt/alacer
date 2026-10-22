package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Exercises the real Java 25 management APIs used by the live profiler. */
class JvmInstrumentationTest {
	private static volatile Object retained;

	@Test
	void allocationCountersMeasureRetainedClientThreadAllocation() {
		FrameProfiler profiler = new FrameProfiler(256);
		assertTrue(profiler.measuresAllocation(), "Java 25 HotSpot allocation counters required");
		profiler.frameBoundary();
		retained = new byte[1024 * 1024];
		profiler.frameBoundary();
		long[] measured = new long[1];
		profiler.frames().copyRecent(FrameProfiler.COL_ALLOC_BYTES, 1, measured);
		assertTrue(measured[0] >= 1024 * 1024, "counter must record the retained allocation");
		retained = null;
	}

	@Test
	void heapAndGcSamplingRetainsMonotonicManagementCounters() {
		JvmMonitor monitor = new JvmMonitor();
		long now = System.nanoTime();
		monitor.sampleIfDue(now);
		long count = monitor.gcCount();
		long millis = monitor.gcMillis();
		assertTrue(monitor.heapUsed() > 0);
		assertTrue(monitor.heapCommitted() >= monitor.heapUsed());
		assertTrue(monitor.heapMax() >= monitor.heapCommitted());
		assertTrue(!monitor.collectorNames().isEmpty());
		monitor.sampleIfDue(now + 1_000_000_001L);
		assertTrue(monitor.seconds().size() == 1);
		assertTrue(monitor.gcCount() >= count);
		assertTrue(monitor.gcMillis() >= millis);
	}
}
