package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SeriesStatsTest {

	private static long[] oneToHundred() {
		long[] values = new long[100];
		for (int i = 0; i < values.length; i++) {
			// Reverse order to make sure sorting happens.
			values[i] = 100 - i;
		}
		return values;
	}

	@Test
	void nearestRankPercentiles() {
		long[] values = oneToHundred();
		SeriesStats stats = SeriesStats.compute(values, values.length, new long[values.length]);
		assertEquals(100, stats.count);
		assertEquals(1, stats.min);
		assertEquals(50, stats.p50);
		assertEquals(90, stats.p90);
		assertEquals(95, stats.p95);
		assertEquals(99, stats.p99);
		assertEquals(100, stats.p999);
		assertEquals(100, stats.max);
		assertEquals(5050, stats.total);
		assertEquals(50.5, stats.mean, 1e-9);
	}

	@Test
	void sampleStandardDeviation() {
		long[] values = {2, 4, 4, 4, 5, 5, 7, 9};
		SeriesStats stats = SeriesStats.compute(values, values.length, new long[values.length]);
		// Sample (n - 1) standard deviation of the classic example set.
		assertEquals(Math.sqrt(32.0 / 7.0), stats.stdDev, 1e-9);
	}

	@Test
	void worstPercentMeansUseAtLeastOneSample() {
		long[] values = oneToHundred();
		SeriesStats stats = SeriesStats.compute(values, values.length, new long[values.length]);
		assertEquals(100.0, stats.worst1PercentMean, 1e-9);
		assertEquals(100.0, stats.worst01PercentMean, 1e-9);

		long[] many = new long[1000];
		for (int i = 0; i < many.length; i++) {
			many[i] = i + 1;
		}
		SeriesStats big = SeriesStats.compute(many, many.length, new long[many.length]);
		// Slowest 10 frames: 991..1000.
		assertEquals(995.5, big.worst1PercentMean, 1e-9);
		assertEquals(1000.0, big.worst01PercentMean, 1e-9);
	}

	@Test
	void inputIsNotModifiedAndPrefixIsRespected() {
		long[] values = {5, 1, 4, 99, 99};
		long[] copy = values.clone();
		SeriesStats stats = SeriesStats.compute(values, 3, new long[5]);
		assertArrayEquals(copy, values);
		assertEquals(3, stats.count);
		assertEquals(5, stats.max);
	}

	@Test
	void emptySeries() {
		SeriesStats stats = SeriesStats.compute(new long[0], 0, new long[0]);
		assertEquals(0, stats.count);
		assertEquals(0, stats.p99);
		assertEquals(0.0, stats.mean);
		assertEquals(0.0, stats.averageFps());
	}

	@Test
	void fpsConversions() {
		long[] frames = {10_000_000L, 10_000_000L, 20_000_000L, 40_000_000L};
		SeriesStats stats = SeriesStats.compute(frames, frames.length, new long[frames.length]);
		// 4 frames in 80 ms.
		assertEquals(50.0, stats.averageFps(), 1e-9);
		assertEquals(25.0, stats.fpsAt(stats.worst1PercentMean), 1e-9);
	}

	@Test
	void rejectsOutOfRangeCount() {
		assertThrows(IllegalArgumentException.class, () -> SeriesStats.compute(new long[2], 3, new long[3]));
		assertThrows(IllegalArgumentException.class, () -> SeriesStats.compute(new long[3], 3, new long[2]));
	}
}
