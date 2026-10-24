package net.adiont.alacer.benchmark;

/**
 * Block-edit scenario for chunk update latency (no game dependencies). The camera holds the chunk flight's "near"
 * height and view (24 blocks above spawn ground, facing east, pitch 20) so the edited terrain is in view. After the
 * still reference, {@code edit} issues a burst every second from 1 s on: one block in each of 16 render chunks ahead
 * of the camera (4 distances x 4 lateral offsets, each a render chunk's centre column), alternately placing stone on
 * the surface and restoring air. Bursts follow phase time, not the renderer, so a slower scheduler accumulates a
 * backlog exactly as it would under the same edits in play.
 */
public final class ChunkEditScenario {
	public static final String[] NAMES = {"warmup-pan", "still", "edit"};
	public static final double[] SECONDS = {10, 10, 30};
	public static final int EDIT_PHASE = 2;
	public static final double FIRST_BURST = 1.0;
	public static final double BURST_INTERVAL = 1.0;
	public static final int BURSTS = 28;
	/** Blocks east of the anchor (the view direction) and north/south of it. */
	public static final int[] AHEAD = {24, 48, 72, 96};
	public static final int[] ACROSS = {-36, -12, 12, 36};
	public static final int CELLS = AHEAD.length * ACROSS.length;
	public static final double HEIGHT = 24;

	private ChunkEditScenario() { }

	/** Warmup turns in place (never scored); still and edit hold the same pose. */
	public static void pose(int phase, double t, double x, double y, double z, double[] out) {
		out[0] = x;
		out[1] = y + HEIGHT;
		out[2] = z;
		out[3] = phase == 0 ? -90 + 360 * t / 5 : -90;
		out[4] = 20;
	}

	/** Number of bursts due by phase time {@code t}. */
	public static int burstsDue(double t) {
		if (t < FIRST_BURST) {
			return 0;
		}
		return Math.min(BURSTS, 1 + (int) Math.floor((t - FIRST_BURST) / BURST_INTERVAL));
	}

	/** Even bursts place stone, odd bursts restore air. */
	public static boolean places(int burst) {
		return burst % 2 == 0;
	}

	/** Block column (x, z) of a cell: the centre column of its render chunk, so every cell is in a different one. */
	public static int[] column(int cell, double anchorX, double anchorZ) {
		int x = (int) Math.floor(anchorX) + AHEAD[cell / ACROSS.length];
		int z = (int) Math.floor(anchorZ) + ACROSS[cell % ACROSS.length];
		return new int[] {Math.floorDiv(x, 16) * 16 + 8, Math.floorDiv(z, 16) * 16 + 8};
	}
}
