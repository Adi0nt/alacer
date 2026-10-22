package net.adiont.alacer.benchmark;

/**
 * Fixed protocol for the opt-in chunk upload scenario (session 27); no game dependencies.
 *
 * <p>Phases: {@code still} (the default static view), {@code edit} (the same view while the
 * runner changes one block at a time at fixed cells along the view) and {@code fly} (the
 * default single fresh flight). Edits are spaced in phase time, wait until the renderer is quiet
 * and alternate between placing stone and restoring air, so every launch makes the same changes.
 */
public final class ChunkScenario {
	public static final String[] NAMES = {"still", "edit", "fly"};
	public static final double[] SECONDS = {10, 20, 20};
	public static final int EDIT_PHASE = 1;
	public static final int FLY_PHASE = 2;
	/** Phase time of the first edit and the spacing of later ones, in seconds; not time-scaled. */
	public static final double EDIT_START = 1.0;
	public static final double EDIT_INTERVAL = 0.5;
	/** An edit not published within this many seconds of being issued is recorded as timed out. */
	public static final double EDIT_TIMEOUT = 2.0;
	/** Frame starts with no dirty, queued or uploading chunk work required before issuing an edit. */
	public static final int QUIET_FRAMES = 10;
	public static final String[] CLASSES = {"near", "mid", "far"};
	/** Distances along the view per class: near cells are rebuilt synchronously, the others on workers. */
	public static final double[][] DISTANCES = {{4, 7}, {40, 56}, {96, 128}};
	public static final int CELLS = 6;
	private static final double YAW = 45.0;
	private static final double PITCH = 5.0;

	private ChunkScenario() { }

	/** Still and edit hold the default static pose; fly is the default 20 blocks/s flight. */
	public static void pose(int phase, double t, double x, double y, double z, double[] out) {
		if (phase == FLY_PHASE) {
			out[0] = x + 20.0 * t;
			out[1] = y + 24.0;
			out[2] = z;
			out[3] = -90.0;
			out[4] = 20.0;
		} else {
			out[0] = x;
			out[1] = y + 1.0;
			out[2] = z;
			out[3] = YAW;
			out[4] = PITCH;
		}
	}

	/** Cells are visited near a, mid a, far a, near b, mid b, far b, then again. */
	public static int cellForEdit(int edit) {
		int slot = edit % CELLS;
		return (slot % 3) * 2 + slot / 3;
	}

	public static int classOf(int cell) {
		return cell / 2;
	}

	/** Even visits of a cell place stone, odd visits restore air. */
	public static boolean placesBlock(int edit) {
		return (edit / CELLS) % 2 == 0;
	}

	/** Phase time at which an edit becomes due (it is issued later if the renderer is busy). */
	public static double dueAt(int edit) {
		return EDIT_START + edit * EDIT_INTERVAL;
	}

	/**
	 * Block column (x, z) of a cell: along the static view's horizontal direction from the anchor.
	 * Mid and far cells are moved to their render chunk's centre column so that the edit and its
	 * light change stay inside one render chunk horizontally.
	 */
	public static int[] column(int cell, double anchorX, double anchorZ) {
		double distance = DISTANCES[classOf(cell)][cell % 2];
		double yaw = Math.toRadians(YAW);
		int x = (int) Math.floor(anchorX - Math.sin(yaw) * distance);
		int z = (int) Math.floor(anchorZ + Math.cos(yaw) * distance);
		if (classOf(cell) > 0) {
			x = Math.floorDiv(x, 16) * 16 + 8;
			z = Math.floorDiv(z, 16) * 16 + 8;
		}
		return new int[] {x, z};
	}

	/** Index into vanilla's render chunk storage array, as its own getChunk computes it; -1 above or below. */
	public static int storageIndex(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
		int i = Math.floorDiv(x, 16);
		int j = Math.floorDiv(y, 16);
		int k = Math.floorDiv(z, 16);
		if (j < 0 || j >= sizeY) {
			return -1;
		}
		i = Math.floorMod(i, sizeX);
		k = Math.floorMod(k, sizeZ);
		return (k * sizeY + j) * sizeX + i;
	}
}
