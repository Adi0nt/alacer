package net.adiont.alacer.render;

/**
 * Eliminates redundant OpenGL texture parameter changes during texture atlas and sprite sub-image uploads.
 *
 * <p>Vanilla TextureUtil unconditionally issues 4 glTexParameter calls (min filter, mag filter, wrap S, wrap T)
 * for every sub-image slice of every mip level of every sprite uploaded. This tracks the active texture parameters
 * and skips identical calls on the bound texture.
 */
public final class FastTextureUpload {
	private static boolean filterKnown;
	private static boolean clampKnown;
	private static boolean lastBlur;
	private static boolean lastMipmap;
	private static boolean lastClamp;

	private FastTextureUpload() { }

	public static boolean shouldSetFilter(boolean blur, boolean mipmap) {
		if (!filterKnown || lastBlur != blur || lastMipmap != mipmap) {
			lastBlur = blur;
			lastMipmap = mipmap;
			filterKnown = true;
			return true;
		}
		return false;
	}

	public static boolean shouldSetClamp(boolean clamp) {
		if (!clampKnown || lastClamp != clamp) {
			lastClamp = clamp;
			clampKnown = true;
			return true;
		}
		return false;
	}

	public static void invalidate() {
		filterKnown = false;
		clampKnown = false;
	}
}
