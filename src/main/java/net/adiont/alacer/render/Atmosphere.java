package net.adiont.alacer.render;

/** Small atmosphere adjustments; the defaults return the original float without rounding it. */
public final class Atmosphere {
	private Atmosphere() { }

	public static float fogStart(float original, int percent) {
		return percent == 75 ? original : Math.max(0, Math.min(95, percent)) / 100f;
	}

	public static float cloudHeight(float original, int offset) {
		return offset == 0 ? original : original + Math.max(-64, Math.min(128, offset));
	}

	public static float brightness(float original, int percent) {
		if (percent == 100 || !Float.isFinite(original) || original < 0f) { return original; }
		return Math.min(1f, original * Math.max(0, Math.min(200, percent)) / 100f);
	}
}
