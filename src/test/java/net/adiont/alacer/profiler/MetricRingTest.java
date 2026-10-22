package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MetricRingTest {

	private static void append(MetricRing ring, long a, long b) {
		int row = ring.append();
		ring.set(row, 0, a);
		if (ring.columnCount() > 1) {
			ring.set(row, 1, b);
		}
	}

	@Test
	void keepsMostRecentRowsInOrder() {
		MetricRing ring = new MetricRing(2, 4);
		for (int i = 1; i <= 6; i++) {
			append(ring, i, i * 10);
		}
		assertEquals(4, ring.size());
		assertEquals(6, ring.totalRecorded());

		long[] dest = new long[4];
		assertEquals(4, ring.copyRecent(0, 4, dest));
		assertArrayEquals(new long[] {3, 4, 5, 6}, dest);

		long[] lastTwo = new long[2];
		assertEquals(2, ring.copyRecent(1, 2, lastTwo));
		assertArrayEquals(new long[] {50, 60}, lastTwo);
	}

	@Test
	void copyIsLimitedBySizeAndDestination() {
		MetricRing ring = new MetricRing(1, 8);
		append(ring, 1, 0);
		append(ring, 2, 0);
		long[] dest = new long[8];
		assertEquals(2, ring.copyRecent(0, 8, dest));
		assertEquals(1, dest[0]);
		assertEquals(2, dest[1]);

		long[] small = new long[1];
		assertEquals(1, ring.copyRecent(0, 8, small));
		assertEquals(2, small[0]);
	}

	@Test
	void appendClearsReusedRow() {
		MetricRing ring = new MetricRing(2, 1);
		append(ring, 7, 8);
		int row = ring.append();
		assertEquals(0, ring.get(row, 0));
		assertEquals(0, ring.get(row, 1));
	}

	@Test
	void clearResetsCounters() {
		MetricRing ring = new MetricRing(1, 3);
		append(ring, 1, 0);
		ring.clear();
		assertEquals(0, ring.size());
		assertEquals(0, ring.totalRecorded());
		append(ring, 9, 0);
		long[] dest = new long[3];
		assertEquals(1, ring.copyRecent(0, 3, dest));
		assertEquals(9, dest[0]);
	}
}
