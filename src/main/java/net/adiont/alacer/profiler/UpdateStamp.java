package net.adiont.alacer.profiler;

/**
 * Earliest unserved update time of one render slot: set by the client when a dirty chunk is first eligible for a
 * rebuild, taken by whichever thread publishes the next completed rebuild. No world or chunk references.
 */
public final class UpdateStamp {
	private long since;

	/** Starts waiting at {@code now} unless an earlier update is already waiting; true if it started. */
	public synchronized boolean eligible(long now) {
		if (this.since != 0L) {
			return false;
		}
		this.since = now == 0L ? 1L : now;
		return true;
	}

	/**
	 * A rebuild was published. Returns the waiting time, or -1 if nothing was waiting. An update that arrived while
	 * the rebuild ran starts waiting when it is next found eligible (a lower bound), so a chunk invalidated again
	 * after leaving the view never carries its earlier start into a much later publication.
	 */
	public synchronized long publish(long now) {
		if (this.since == 0L) {
			return -1L;
		}
		long waited = now - this.since;
		this.since = 0L;
		return waited;
	}

	/** The slot now shows another section; any waiting update is dropped. True if one was waiting. */
	public synchronized boolean clear() {
		boolean waiting = this.since != 0L;
		this.since = 0L;
		return waiting;
	}

	public synchronized boolean waiting() {
		return this.since != 0L;
	}
}
