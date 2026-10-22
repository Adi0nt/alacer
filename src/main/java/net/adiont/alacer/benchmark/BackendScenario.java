package net.adiont.alacer.benchmark;

/** Fixed camera protocol for the opt-in terrain backend investigation; no game dependencies. */
public final class BackendScenario {
	public enum View { STILL, PAN, SORT, FLY }
	public static final String[] NAMES = {"warmup-pan", "warmup-sort", "steady", "pan", "sort", "fly"};
	public static final double[] SECONDS = {15, 16, 10, 15, 16, 20};
	public static final View[] VIEWS = {View.PAN, View.SORT, View.STILL, View.PAN, View.SORT, View.FLY};
	public static final int GLASS_BLOCKS = 4 * 13 * 7;

	private BackendScenario() { }

	public static Boolean requestedVbo(String value, boolean required) {
		if (value == null && !required) { return null; }
		if (value != null && "true".equalsIgnoreCase(value.trim())) { return true; }
		if (value != null && "false".equalsIgnoreCase(value.trim())) { return false; }
		throw new IllegalArgumentException("benchmark.vbo must be explicit true/false for backendScenario");
	}

	/** Sort orbit stays near the same built chunks; the flight is scored once per copied world. */
	public static void pose(View view, double t, double x, double y, double z, double[] out) {
		out[0] = x;
		out[1] = y + 1;
		out[2] = z;
		out[3] = 45;
		out[4] = 5;
		if (view == View.PAN) {
			out[3] = 45 + 360 * t / 7.5;
		} else if (view == View.SORT) {
			double angle = 2 * Math.PI * t / 8;
			out[0] = x + 12 * Math.sin(angle);
			out[1] = y + 5;
			out[2] = z + 16 - 12 * Math.cos(angle);
			out[3] = Math.toDegrees(angle);
			out[4] = 5;
		} else if (view == View.FLY) {
			out[0] = x + 20 * t;
			out[1] = y + 24;
			out[3] = -90;
			out[4] = 20;
		}
	}
}
