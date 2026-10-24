package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Zero-translation call reduction ({@code render.fastModelTransforms}).
 *
 * <p>Vanilla entity rendering pushes and pops OpenGL matrix state recursively per cuboid and part,
 * and executes redundant zero translations. The current implementation skips those
 * translations; it does not flatten hierarchies or remove matrix pushes/pops.
 */
public final class FastModelTransforms {

	private static long partsRendered;
	private static long zeroTransformsSkipped;
	private static long matrixPushesSaved;

	private FastModelTransforms() {
	}

	public static boolean isEnabled() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.fastModelTransforms;
	}

	public static boolean shouldSkipZeroTranslate(float x, float y, float z) {
		if (x == 0.0f && y == 0.0f && z == 0.0f) {
			zeroTransformsSkipped++;
			return true;
		}
		return false;
	}

	public static void recordPart() {
		partsRendered++;
	}

	public static void recordSavedPush() {
		matrixPushesSaved++;
	}

	public static String summary() {
		return partsRendered + " model parts rendered, " + zeroTransformsSkipped + " zero-translations skipped, "
			+ matrixPushesSaved + " matrix pushes avoided";
	}
}
