package net.adiont.alacer.profiler;

import java.util.Arrays;

/** Fixed-size completion totals. Worker threads never touch the client-only frame profiler. */
public final class ConcurrentTimings {
	private final long[] totals;

	public ConcurrentTimings(int metrics) {
		this.totals = new long[metrics * 2];
	}

	public synchronized void record(int metric, long nanos) {
		this.totals[metric * 2] += nanos;
		this.totals[metric * 2 + 1]++;
	}

	/** Copy and clear under the same lock as record, preserving time/count pairs. */
	public synchronized void drain(long[] out) {
		System.arraycopy(this.totals, 0, out, 0, this.totals.length);
		Arrays.fill(this.totals, 0L);
	}
}
