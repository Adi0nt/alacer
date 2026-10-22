package net.adiont.alacer.render;

/** Shapes only the final ambient-occlusion factors, before block tint is applied. */
public final class SmoothLighting {
	private SmoothLighting() { }

	public static void apply(float[] brightness, int style, int strength) {
		int boundedStrength = Math.max(0, Math.min(100, strength));
		boolean soft = style == 1;
		// Retain every original float bit, including custom values, for the vanilla control.
		if (!soft && boundedStrength == 100) { return; }
		float amount = boundedStrength / 100f;
		for (int i = 0; i < brightness.length; i++) {
			float value = brightness[i];
			// Other render extensions may supply factors outside vanilla's normalized range.
			if (!Float.isFinite(value) || value < 0f || value > 1f) { continue; }
			if (boundedStrength == 0) { brightness[i] = 1f; continue; }
			// A monotone, endpoint-preserving lift of the midtones; no neighbor/face averaging.
			float shaped = soft ? value + 0.5f * value * (1f - value) : value;
			brightness[i] = boundedStrength == 100 ? shaped : 1f - (1f - shaped) * amount;
		}
	}
}
