package net.adiont.alacer.profiler;

import java.util.Arrays;

/**
 * Completed latencies from any thread: per-frame totals for the client's frame profiler, plus every value since the
 * last {@link #takeValues} (bounded; later values only count as overflow) for per-event percentiles.
 */
public final class LatencyLog {
	private final long[] values;
	private int size;
	private long overflow;
	private long frameTotal;
	private long frameMax;
	private long frameCount;

	public LatencyLog(int capacity) {
		this.values = new long[capacity];
	}

	public synchronized void record(long nanos) {
		this.frameTotal += nanos;
		this.frameCount++;
		this.frameMax = Math.max(this.frameMax, nanos);
		if (this.size < this.values.length) {
			this.values[this.size++] = nanos;
		} else {
			this.overflow++;
		}
	}

	/** Writes total, count and maximum since the previous drain into {@code out} and clears them. */
	public synchronized void drainFrame(long[] out) {
		out[0] = this.frameTotal;
		out[1] = this.frameCount;
		out[2] = this.frameMax;
		this.frameTotal = 0L;
		this.frameCount = 0L;
		this.frameMax = 0L;
	}

	/** Values since the previous call, in completion order; clears them and the overflow count. */
	public synchronized long[] takeValues() {
		long[] out = Arrays.copyOf(this.values, this.size);
		this.size = 0;
		this.overflow = 0L;
		return out;
	}

	public synchronized long overflow() {
		return this.overflow;
	}
}
