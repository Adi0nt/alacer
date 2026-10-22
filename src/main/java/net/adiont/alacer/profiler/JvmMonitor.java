package net.adiont.alacer.profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Samples heap usage and garbage collection counters about once per second.
 *
 * <p>Sampling is driven by the client thread's frame boundary, so no extra thread is needed.
 * The MXBean queries are cheap but not free, which is why they are not done every frame.
 */
public final class JvmMonitor {

	private static final long INTERVAL_NANOS = 1_000_000_000L;

	public static final int COL_HEAP_USED = 0;
	public static final int COL_GC_COUNT = 1;
	public static final int COL_GC_MILLIS = 2;

	private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
	private final Runtime runtime = Runtime.getRuntime();
	/** Per-second history: heap used, GC count delta, GC time delta (ms). Ten minutes are kept. */
	private final MetricRing seconds = new MetricRing(3, 600);
	private final long heapMax = this.runtime.maxMemory();

	private long nextSample;
	private long lastGcCount = -1;
	private long lastGcMillis = -1;
	private long heapUsed;
	private long heapCommitted;

	void sampleIfDue(long now) {
		if (this.lastGcCount >= 0 && now - this.nextSample < 0) {
			return;
		}
		this.nextSample = now + INTERVAL_NANOS;

		long count = 0;
		long millis = 0;
		for (int i = 0; i < this.collectors.size(); i++) {
			GarbageCollectorMXBean gc = this.collectors.get(i);
			count += Math.max(0, gc.getCollectionCount());
			millis += Math.max(0, gc.getCollectionTime());
		}
		this.heapCommitted = this.runtime.totalMemory();
		this.heapUsed = this.heapCommitted - this.runtime.freeMemory();

		if (this.lastGcCount >= 0) {
			int row = this.seconds.append();
			this.seconds.set(row, COL_HEAP_USED, this.heapUsed);
			this.seconds.set(row, COL_GC_COUNT, count - this.lastGcCount);
			this.seconds.set(row, COL_GC_MILLIS, millis - this.lastGcMillis);
		}
		this.lastGcCount = count;
		this.lastGcMillis = millis;
	}

	public long heapUsed() {
		return this.heapUsed;
	}

	public long heapCommitted() {
		return this.heapCommitted;
	}

	public long heapMax() {
		return this.heapMax;
	}

	/** Total collections since JVM start, as of the last sample. */
	public long gcCount() {
		return Math.max(0, this.lastGcCount);
	}

	/** Total collection time in milliseconds since JVM start, as of the last sample. */
	public long gcMillis() {
		return Math.max(0, this.lastGcMillis);
	}

	public MetricRing seconds() {
		return this.seconds;
	}

	public String collectorNames() {
		StringBuilder names = new StringBuilder();
		for (GarbageCollectorMXBean gc : this.collectors) {
			if (names.length() > 0) {
				names.append(", ");
			}
			names.append(gc.getName());
		}
		return names.toString();
	}
}
