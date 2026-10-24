package net.adiont.alacer.render;

/**
 * Chunk update scheduling choices. Vanilla gives the client thread a quarter of a frame (at the current frame rate,
 * at least 60 FPS) to upload finished chunks and queue rebuilds, and builds chunks on two worker threads.
 */
public final class ChunkScheduling {
	public static final int VANILLA_BUDGET = 2, VANILLA_THREADS = 2, MAX_THREADS = 6;
	private static final int[] BUDGET_PERCENT = {25, 50, 100, 200, 400};
	/** Vanilla keeps three more chunk buffer sets than workers, for chunks waiting to upload. */
	private static final int SPARE_BUFFERS = 3;
	private ChunkScheduling() { }

	public static int budgetPercent(int choice) { return BUDGET_PERCENT[Math.max(0, Math.min(BUDGET_PERCENT.length - 1, choice))]; }

	/** Rescales the time left before the deadline. A frame already over budget keeps vanilla's deadline. */
	public static long deadline(long vanilla, long now, int choice) {
		if (choice == VANILLA_BUDGET) { return vanilla; }
		long remaining = vanilla - now;
		if (remaining <= 0) { return vanilla; }
		return now + remaining * budgetPercent(choice) / 100;
	}

	public static int threads(int configured) { return Math.max(1, Math.min(MAX_THREADS, configured)); }

	public static int buffers(int threads) { return threads(threads) + SPARE_BUFFERS; }
}
