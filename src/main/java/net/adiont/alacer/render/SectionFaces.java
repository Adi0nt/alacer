package net.adiont.alacer.render;

import java.util.Arrays;

/**
 * Allocation-free equivalent of vanilla's camera-section flood fill
 * ({@code WorldRenderer.getOpenChunkFaces}: {@code ChunkOcclusionGraph} filled with the section's
 * opaque blocks, then flooded from the camera's block).
 *
 * <p>Vanilla floods with a linked queue and copies {@code Direction.values()} for every cell it
 * visits; in open air that is up to 4096 cells and about 24,000 array copies each time the camera
 * enters another block. This class keeps the opaque cells and the reached cells as two 4096-bit
 * sets and floods with a fixed {@code short[]} stack. The answer is a set, so the visiting order
 * does not matter: the reached cells are the start cell plus every non-opaque cell connected to it
 * through non-opaque face neighbours inside the section, exactly as in vanilla (which also enters
 * the start cell when it is opaque).
 *
 * <p>The reached cells are kept after a fill. When the fill started at a non-opaque cell they are
 * that cell's whole connected component, so the flood from any other cell of the component reaches
 * the same cells and the same faces: {@link #reuses} answers whether a new start can take the last
 * answer without filling again. The caller must clear the grid whenever the section's blocks may
 * have changed.
 *
 * <p>Pure and single-threaded; the caller owns one instance per thread.
 */
public final class SectionFaces {

	// Direction ordinals, as in Minecraft's Direction enum.
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int WEST = 4;
	public static final int EAST = 5;
	public static final int ALL_FACES = 0b111111;

	private static final int CELLS = 16 * 16 * 16;
	// Vanilla's cell index layout: x in bits 0-3, z in bits 4-7, y in bits 8-11.
	private static final int STEP_X = 1;
	private static final int STEP_Z = 16;
	private static final int STEP_Y = 256;

	private final long[] opaque = new long[CELLS / 64];
	private final long[] reached = new long[CELLS / 64];
	private final short[] stack = new short[CELLS];
	private int start = -1;
	private boolean component;
	private int faces;

	/** Cell index of section-relative coordinates (each 0-15), in vanilla's layout. */
	public static int index(int x, int y, int z) {
		return x * STEP_X | z * STEP_Z | y * STEP_Y;
	}

	/** Forgets the grid and the last answer; every cell becomes non-opaque. */
	public void clear() {
		Arrays.fill(this.opaque, 0L);
		this.start = -1;
		this.component = false;
	}

	/** Marks one cell (section-relative coordinates, each 0-15) opaque. */
	public void setOpaque(int x, int y, int z) {
		int i = index(x, y, z);
		this.opaque[i >>> 6] |= 1L << i;
		this.start = -1;
		this.component = false;
	}

	/** Whether the last fill's answer also holds when flooding from this cell of the unchanged grid. */
	public boolean reuses(int x, int y, int z) {
		int i = index(x, y, z);
		return i == this.start || this.component && (this.reached[i >>> 6] & 1L << i) != 0;
	}

	/** The faces answered by the last fill, as a mask of {@code 1 << direction ordinal}. */
	public int faces() {
		return this.faces;
	}

	/**
	 * Floods from one cell (section-relative coordinates, each 0-15) through non-opaque cells and
	 * returns the section faces reached, as a mask of {@code 1 << direction ordinal}.
	 */
	public int fill(int x, int y, int z) {
		long[] opaque = this.opaque;
		long[] reached = this.reached;
		short[] stack = this.stack;
		Arrays.fill(reached, 0L);
		int first = index(x, y, z);
		reached[first >>> 6] |= 1L << first;
		stack[0] = (short) first;
		int top = 1;
		int mask = 0;
		while (top > 0) {
			int i = stack[--top];
			int cx = i & 15;
			int cz = i >>> 4 & 15;
			int cy = i >>> 8;
			if (cx == 0) {
				mask |= 1 << WEST;
			} else if (cx == 15) {
				mask |= 1 << EAST;
			}
			if (cy == 0) {
				mask |= 1 << DOWN;
			} else if (cy == 15) {
				mask |= 1 << UP;
			}
			if (cz == 0) {
				mask |= 1 << NORTH;
			} else if (cz == 15) {
				mask |= 1 << SOUTH;
			}
			if (cy > 0) {
				top = push(i - STEP_Y, opaque, reached, stack, top);
			}
			if (cy < 15) {
				top = push(i + STEP_Y, opaque, reached, stack, top);
			}
			if (cz > 0) {
				top = push(i - STEP_Z, opaque, reached, stack, top);
			}
			if (cz < 15) {
				top = push(i + STEP_Z, opaque, reached, stack, top);
			}
			if (cx > 0) {
				top = push(i - STEP_X, opaque, reached, stack, top);
			}
			if (cx < 15) {
				top = push(i + STEP_X, opaque, reached, stack, top);
			}
		}
		this.start = first;
		this.component = (opaque[first >>> 6] & 1L << first) == 0;
		this.faces = mask;
		return mask;
	}

	/** Pushes a neighbour that is neither opaque nor reached yet; each cell is pushed at most once. */
	private static int push(int i, long[] opaque, long[] reached, short[] stack, int top) {
		int word = i >>> 6;
		long bit = 1L << i;
		if (((opaque[word] | reached[word]) & bit) != 0) {
			return top;
		}
		reached[word] |= bit;
		stack[top] = (short) i;
		return top + 1;
	}
}
