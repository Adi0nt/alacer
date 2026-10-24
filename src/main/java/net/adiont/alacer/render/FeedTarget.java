package net.adiont.alacer.render;

/**
 * Queue depth for {@code render.feedChunkWorkers} (pure; client thread). Terrain setup cancels every rebuild still
 * waiting in the queue each frame, so a task helps only if a worker starts it before the next setup. The target is
 * what the workers started during the last frame plus one per worker; if they emptied the queue (and may have idled)
 * it doubles instead. It never drops below two per worker and stays well under the queue's 100 slots, leaving room
 * for the up to 15 translucency re-sorts the terrain pass may add.
 */
public final class FeedTarget {
	public static final int MAX = 64;
	private final int workers;
	private int target;
	private int queuedAfterFeed = -1;

	public FeedTarget(int workers) {
		this.workers = Math.max(1, workers);
		this.target = minimum();
	}

	private int minimum() { return Math.min(MAX, 2 * this.workers); }

	public int target() { return this.target; }

	public int workers() { return this.workers; }

	/** Called with the queue's size right before terrain setup clears it. */
	public void beforeClear(int unstarted) {
		if (this.queuedAfterFeed < 0) {
			return;
		}
		int started = Math.max(0, this.queuedAfterFeed - unstarted);
		int next = unstarted == 0 ? Math.max(2 * this.target, started + this.workers) : started + this.workers;
		this.target = Math.max(minimum(), Math.min(MAX, next));
		this.queuedAfterFeed = -1;
	}

	/** Called with the queue's size after this frame's rebuilds were queued; -1 when the option did not run. */
	public void afterFeed(int queued) {
		this.queuedAfterFeed = queued;
	}
}
