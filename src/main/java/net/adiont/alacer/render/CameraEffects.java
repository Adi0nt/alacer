package net.adiont.alacer.render;

/** Percent scales for camera motion effects; 100% leaves vanilla's float values bit-for-bit unchanged. */
public final class CameraEffects {
	private CameraEffects() { }

	/** Scales how far sprinting, flying, speed effects and bows move the field of view away from 1. */
	public static float fovModifier(float vanilla, int percent) {
		if (percent == 100) { return vanilla; }
		return 1f + (vanilla - 1f) * (percent / 100f);
	}

	public static float hurtTilt(float vanilla, int percent) {
		return percent == 100 ? vanilla : vanilla * (percent / 100f);
	}
}
