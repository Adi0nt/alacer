package net.adiont.alacer.render;

/** Quality selection only; the default delegates exactly to Minecraft's existing Graphics decision. */
public final class IndependentQuality {
	private IndependentQuality() { }
	public static boolean fancy(int quality, boolean original) {
		return quality == 1 ? false : quality == 2 ? true : original;
	}
}
