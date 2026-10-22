package net.adiont.alacer.profiler;

import java.util.Arrays;

/**
 * Summary statistics over a series of samples (durations in nanoseconds, byte counts, ...).
 *
 * <p>Percentiles use the nearest-rank method on the sorted samples. "1% low" is reported as
 * the frame rate implied by the mean of the slowest 1% of frames, which (unlike a single
 * percentile) reflects how bad the worst stutters were rather than just where they start.
 */
public final class SeriesStats {

	public final int count;
	public final long total;
	public final double mean;
	public final double stdDev;
	public final long min;
	public final long p50;
	public final long p90;
	public final long p95;
	public final long p99;
	public final long p999;
	public final long max;
	/** Mean of the slowest 1% of samples (at least one sample). */
	public final double worst1PercentMean;
	/** Mean of the slowest 0.1% of samples (at least one sample). */
	public final double worst01PercentMean;

	private SeriesStats(int count, long total, double mean, double stdDev, long[] sorted, double worst1, double worst01) {
		this.count = count;
		this.total = total;
		this.mean = mean;
		this.stdDev = stdDev;
		this.min = count == 0 ? 0 : sorted[0];
		this.p50 = percentile(sorted, count, 50.0);
		this.p90 = percentile(sorted, count, 90.0);
		this.p95 = percentile(sorted, count, 95.0);
		this.p99 = percentile(sorted, count, 99.0);
		this.p999 = percentile(sorted, count, 99.9);
		this.max = count == 0 ? 0 : sorted[count - 1];
		this.worst1PercentMean = worst1;
		this.worst01PercentMean = worst01;
	}

	/**
	 * Computes statistics for {@code values[0..count)}. The input array is not modified;
	 * {@code scratch} must have room for {@code count} elements and is overwritten.
	 */
	public static SeriesStats compute(long[] values, int count, long[] scratch) {
		if (count < 0 || count > values.length || count > scratch.length) {
			throw new IllegalArgumentException("count " + count + " out of range");
		}
		System.arraycopy(values, 0, scratch, 0, count);
		Arrays.sort(scratch, 0, count);

		long total = 0;
		for (int i = 0; i < count; i++) {
			total += scratch[i];
		}
		double mean = count == 0 ? 0.0 : (double) total / count;

		double sumSq = 0.0;
		for (int i = 0; i < count; i++) {
			double d = scratch[i] - mean;
			sumSq += d * d;
		}
		double stdDev = count < 2 ? 0.0 : Math.sqrt(sumSq / (count - 1));

		return new SeriesStats(count, total, mean, stdDev, scratch, tailMean(scratch, count, 0.01), tailMean(scratch, count, 0.001));
	}

	/** Nearest-rank percentile of an ascending array. */
	static long percentile(long[] sorted, int count, double p) {
		if (count == 0) {
			return 0;
		}
		int rank = (int) Math.ceil(p / 100.0 * count);
		return sorted[Math.max(0, Math.min(count - 1, rank - 1))];
	}

	private static double tailMean(long[] sorted, int count, double fraction) {
		if (count == 0) {
			return 0.0;
		}
		int n = Math.max(1, (int) Math.floor(count * fraction));
		long sum = 0;
		for (int i = count - n; i < count; i++) {
			sum += sorted[i];
		}
		return (double) sum / n;
	}

	/** For frame durations in nanoseconds: frames divided by total frame time. */
	public double averageFps() {
		return this.total == 0 ? 0.0 : this.count * 1.0e9 / this.total;
	}

	public double fpsAt(double nanos) {
		return nanos <= 0 ? 0.0 : 1.0e9 / nanos;
	}
}
