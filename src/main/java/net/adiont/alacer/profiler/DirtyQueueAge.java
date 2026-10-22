package net.adiont.alacer.profiler;

/** First observed dirty-set residency, per render slot. No chunk/world references or allocation. */
public final class DirtyQueueAge {
	private long first;
	private long frame;
	private int x, y, z;

	public long observe(long frame, long now, int x, int y, int z) {
		if (this.first == 0L || this.frame != frame - 1 || this.x != x || this.y != y || this.z != z) {
			this.first = now;
		}
		this.frame = frame;
		this.x = x; this.y = y; this.z = z;
		return now - this.first;
	}

	/** Negative if never sampled at this origin; otherwise a lower bound on time awaiting enqueue. */
	public long scheduled(long frame, long now, int x, int y, int z) {
		long age = this.first != 0L && this.frame == frame && this.x == x && this.y == y && this.z == z ? now - this.first : -1L;
		this.first = 0L;
		return age;
	}
}
