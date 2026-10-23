package net.adiont.alacer.render;

/**
 * Camera settings. Each default returns vanilla's float untouched (bit-for-bit); other values scale or replace the
 * one number vanilla passes on.
 */
public final class CameraEffects {
	/** Vanilla's third-person camera distance in blocks ({@code GameRenderer}'s two camera fields hold 4.0). */
	public static final int VANILLA_DISTANCE = 4, MIN_DISTANCE = 1, MAX_DISTANCE = 16;
	/** How far the field of view moves towards its target per tick: Instant, Vanilla (0.5), Smooth, Very smooth. */
	private static final float[] FOV_TRANSITION = {1.0F, 0.5F, 0.25F, 0.125F};
	public static final int VANILLA_FOV_TRANSITION = 1;
	private static final int[] ZOOM = {2, 3, 4, 6, 8};
	private CameraEffects() { }

	/** Scales how far sprinting, flying, speed effects and bows move the field of view away from 1. */
	public static float fovModifier(float vanilla, int percent) {
		if (percent == 100) { return vanilla; }
		return 1f + (vanilla - 1f) * (percent / 100f);
	}

	public static float hurtTilt(float vanilla, int percent) {
		return percent == 100 ? vanilla : vanilla * (percent / 100f);
	}

	/** One offset or angle of the view-bobbing transform; each is proportional to the bob and tilt amounts. */
	public static float bobbing(float vanilla, int percent) {
		return percent == 100 ? vanilla : vanilla * (percent / 100f);
	}

	/** The nausea and portal stretch factor, moved towards 1 (no stretch). */
	public static float distortion(float stretch, int percent) {
		return percent == 100 ? stretch : 1f + (stretch - 1f) * (percent / 100f);
	}

	public static float thirdPersonDistance(float vanilla, int blocks) {
		return blocks == VANILLA_DISTANCE ? vanilla : vanilla * (blocks / (float) VANILLA_DISTANCE);
	}

	public static float fovTransition(float vanilla, int choice) {
		return choice == VANILLA_FOV_TRANSITION ? vanilla : FOV_TRANSITION[Math.max(0, Math.min(FOV_TRANSITION.length - 1, choice))];
	}

	public static int zoomFactor(int choice) {
		return ZOOM[Math.max(0, Math.min(ZOOM.length - 1, choice))];
	}

	/** The configured field of view while the zoom key is held. */
	public static float zoom(float fov, boolean active, int choice) {
		return active ? fov / zoomFactor(choice) : fov;
	}
}
