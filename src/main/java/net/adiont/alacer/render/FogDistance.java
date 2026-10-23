package net.adiont.alacer.render;

/**
 * Overworld distance fog only. Vanilla starts linear fog at 75% of the render distance and ends it there;
 * blindness, water, lava and the Nether's thick fog are separate branches and keep their values.
 */
public final class FogDistance {
	/** Percent value meaning "no distance fog": the fog band is moved past the far clipping plane. */
	public static final int OFF = 100;
	/** The far plane is at most twice the render distance, so fog starting beyond it is never visible. */
	private static final float OFF_START = 2f, OFF_END = 3f;
	private FogDistance() { }

	public static float startFraction(float vanilla, int percent) {
		if (percent == 75) { return vanilla; }
		return percent >= OFF ? OFF_START : percent / 100f;
	}

	public static float end(float vanilla, int percent) {
		return percent >= OFF ? vanilla * OFF_END : vanilla;
	}
}
