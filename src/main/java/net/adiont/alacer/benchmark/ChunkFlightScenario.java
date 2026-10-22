package net.adiont.alacer.benchmark;

/** Session 27: stationary warmup/reference followed by exactly one unrepeated fresh flight. */
public final class ChunkFlightScenario {
	public static final String[] NAMES = {"warmup-pan", "still", "fresh-flight"};
	public static final double[] SECONDS = {10, 10, 40};
	private ChunkFlightScenario() { }

	public static double height(String scene) {
		return switch (scene) {
			case "near" -> 24;
			case "high" -> 64;
			default -> throw new IllegalArgumentException("chunkFlight must be near or high");
		};
	}

	/** Warmup never translates; the scored flight moves east at 20 blocks/s without looping. */
	public static void pose(int phase, double t, double x, double y, double z, double height, double[] out) {
		out[0] = x + (phase == 2 ? 20 * t : 0);
		out[1] = y + height;
		out[2] = z;
		out[3] = phase == 0 ? -90 + 360 * t / 5 : -90;
		out[4] = 20;
	}
}
