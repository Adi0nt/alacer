package net.adiont.alacer.render;

/** Biome tint averaging over a square of columns; choice 1 is vanilla's 3x3. */
public final class BiomeBlend {
	public static final int VANILLA = 1;
	private BiomeBlend() { }

	/** Columns sampled on each side of the block: 0 (1x1), 1 (3x3), 2 (5x5), 3 (7x7). */
	public static int radius(int choice) { return Math.max(0, Math.min(3, choice)); }

	public static int samples(int choice) {
		int side = 2 * radius(choice) + 1;
		return side * side;
	}
}
