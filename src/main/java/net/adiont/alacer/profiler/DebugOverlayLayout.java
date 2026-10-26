package net.adiont.alacer.profiler;

/** Layout in Minecraft GUI pixels; compact charts reserve room for both text columns. */
public final class DebugOverlayLayout {
	private DebugOverlayLayout() { }
	public static boolean compact(int width, int height) { return width < 640 || height < 360; }
	public static int panelWidth(int width, int height) {
		return Math.max(1, Math.min(width - 4, compact(width, height) ? width - 4 : 310));
	}
	public static int panelHeight(int width, int height) {
		return Math.max(1, Math.min(height - 4, compact(width, height) ? Math.min(124, height / 2) : 206));
	}
	public static int panelTop(int width, int height) { return height - 2 - panelHeight(width, height); }
	public static int textRows(int width, int height, int fontHeight, boolean graph, boolean right) {
		int bottom = graph && (right || compact(width, height)) ? panelTop(width, height) - 3 : height - 3;
		return Math.max(1, (bottom - 2) / Math.max(1, fontHeight));
	}
}
