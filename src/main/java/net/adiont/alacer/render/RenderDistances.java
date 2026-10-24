package net.adiont.alacer.render;

/** Percent scales for vanilla's entity and block-entity draw distances; 100% returns vanilla's value untouched. */
public final class RenderDistances {
	private RenderDistances() { }

	/** Vanilla compares an entity's squared camera distance with its own limit; scaling that distance scales every limit. */
	public static double entitySquaredDistance(double squaredDistance, int percent) {
		if (percent == 100) { return squaredDistance; }
		double scale = percent / 100.0;
		return squaredDistance / (scale * scale);
	}

	/** Block entities report a squared limit (64 blocks, beacons 256); scale it by the square of the distance factor. */
	public static double blockEntitySquaredLimit(double squaredLimit, int percent) {
		if (percent == 100) { return squaredLimit; }
		double scale = percent / 100.0;
		return squaredLimit * scale * scale;
	}
}
