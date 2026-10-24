package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Model part transformation flattening and matrix call reduction ({@code render.fastModelTransforms}).
 *
 * <p>Vanilla entity rendering pushes and pops OpenGL matrix state recursively per cuboid and part,
 * and executes redundant zero translations and state changes for static parts. This class provides
 * flattened matrix transformations and eliminates redundant translation inverses for non-rotating
 * child hierarchies.
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
