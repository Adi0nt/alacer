package net.adiont.alacer.render;

/** Pure conservative guard for Box's constructor normalization and moved's IEEE-754 additions. */
public final class ZeroOffsetBox {

	private ZeroOffsetBox() { }

	public static boolean canReuse(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ, double dx, double dy, double dz) {
		// Accept both signs of zero offsets. Coordinates equal to either zero are deliberately
		// excluded: -0 + +0 changes bits, and Math.min/max normalize mixed signed-zero bounds.
		return dx == 0.0 && dy == 0.0 && dz == 0.0
			&& safeAxis(minX, maxX) && safeAxis(minY, maxY) && safeAxis(minZ, maxZ);
	}

	private static boolean safeAxis(double min, double max) {
		return min != 0.0 && max != 0.0 && Double.isFinite(min) && Double.isFinite(max) && min <= max;
	}
}
