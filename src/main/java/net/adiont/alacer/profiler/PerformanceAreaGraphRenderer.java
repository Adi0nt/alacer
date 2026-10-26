package net.adiont.alacer.profiler;

import net.minecraft.client.Minecraft;
import net.adiont.alacer.Alacer;
import java.util.Locale;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;

/** Minecraft-font display of sampled client wall times. Text refreshes at a bounded cadence. */
public final class PerformanceAreaGraphRenderer {

	private static long lastGeneration = -1;
	private static final ClipCache[] CLIPS = new ClipCache[12];
	static { for (int i = 0; i < CLIPS.length; i++) CLIPS[i] = new ClipCache(); }
	private static final class ClipCache {
		String input, clipped;
		int available = -1, measured = -1;
	}

	private static final float[] Y_PREV = new float[9];
	private static final float[] Y_CURR = new float[9];

	// Unified 8-channel color palette matching table and timeline graph
	private static final int[][] LAYER_COLORS = new int[][] {
			{59, 130, 246},  // 0: Swap / sync
			{6, 182, 212},   // 1: Weather & Sky (Cyan)
			{16, 185, 129},  // 2: Terrain (Emerald)
			{245, 158, 11},  // 3: Entities (Amber)
			{168, 85, 247},  // 4: Particles (Violet)
			{251, 146, 60},  // 5: Ticks & Tasks (Orange)
			{236, 72, 153},  // 6: Gui & Hud (Pink)
			{239, 68, 68}    // 7: Unmeasured frame wall time
	};

	// 8-channel color palette for Mode 3 (top subsystem detail)
	private static final int[][] TOP_DETAIL_COLORS = new int[][] {
			{16, 185, 129},  // 0: Setup & Cull (Emerald)
			{20, 184, 166},  // 1: Visibility BFS (Teal)
			{245, 158, 11},  // 2: Chunk Upload (Amber)
			{59, 130, 246},  // 3: Solid Layer (Blue)
			{132, 204, 22},  // 4: Cutout Mipped (Lime)
			{14, 165, 233},  // 5: Cutout Layer (Sky)
			{99, 102, 241},  // 6: Translucent (Indigo)
			{168, 85, 247}   // 7: Other Terrain (Purple)
	};

	// Stable refresh cadence to prevent rapid text flickering
	private static final long SNAPSHOT_INTERVAL_MS = 400L;
	private static long lastSnapshotTime = 0L;

	// Snapshot values; worst/low statistics are not smoothed away.
	private static double displayAvgMs = 0.0D;
	private static double displayAvgFps = 0.0D;
	private static double displayLowFps = 0.0D;
	private static double displayWorstMs = 0.0D;
	private static double displayAllocMb = 0.0D;

	private static String cachedHeader = "";
	private static String cachedStatsRow = "";
	private static boolean lastFrozenState = false;
	private static int areaGraphMode = 1; // 0 = Frame Time, 1 = Subsystem Time (ms), 2 = % Split
	private static int fixedScale = 0;    // 0 = adaptive, 1 = 50ms, 2 = 33.3ms, 3 = 100ms
	private static int lastMode = -1;
	private static int lastScale = -1;
	private static String cachedModeTitle = "";
	private static int lastMaxScaleInt = -1;
	private static String cachedMaxScaleStr = "";

	/**
	 * Statically pre-allocated display snapshot row. Zero per-frame memory allocation.
	 */
	public static final class DisplayRow {
		private String label;
		private String name;
		private int color;
		private double millis = 0.0D;
		private double percentage = 0.0D;
		private String percentageStr = "  0.0%";
		private String millisStr = " 0.00 ms";

		public DisplayRow(int index, String name, int color) {
			this.label = "[" + index + "] " + name;
			this.name = name;
			this.color = color;
		}

		public String getLabel() { return this.label; }
		public String getName() { return this.name; }
		public int getColor() { return this.color; }
		public double getMillis() { return this.millis; }
		public double getPercentage() { return this.percentage; }
		public String getPercentageStr() { return this.percentageStr; }
		public String getMillisStr() { return this.millisStr; }

		public void update(int index, String name, int color) {
			this.label = "[" + index + "] " + name;
			this.name = name;
			this.color = color;
		}

		public void updateStrings() {
			this.percentageStr = String.format(Locale.ROOT, "%5.1f%%", this.percentage);
			this.millisStr = String.format(Locale.ROOT, "%5.2f ms", this.millis);
		}
	}

	// Rock-solid fixed category order: rows NEVER jump or swap positions
	private static final DisplayRow[] DISPLAY_ROWS = new DisplayRow[] {
			new DisplayRow(1, "World / sky", 0xFF06B6D4),
			new DisplayRow(2, "Terrain", 0xFF10B981),
			new DisplayRow(3, "Entities", 0xFFF59E0B),
			new DisplayRow(4, "Particles", 0xFFA855F7),
			new DisplayRow(5, "Ticks & Tasks", 0xFFFB923C),
			new DisplayRow(6, "Gui & Hud", 0xFFEC4899),
			new DisplayRow(7, "Swap / sync", 0xFF3B82F6),
			new DisplayRow(8, "Other / wait", 0xFFEF4444)
	};

	// Detail display rows for Mode 3 (top subsystem breakdown)
	private static final DisplayRow[] TOP_DETAIL_ROWS = new DisplayRow[] {
			new DisplayRow(1, "Setup & Cull", 0xFF10B981),
			new DisplayRow(2, "Visibility BFS", 0xFF14B8A6),
			new DisplayRow(3, "Chunk Upload", 0xFFF59E0B),
			new DisplayRow(4, "Solid Layer", 0xFF3B82F6),
			new DisplayRow(5, "Cutout Mipped", 0xFF84CC16),
			new DisplayRow(6, "Cutout Layer", 0xFF0EA5E9),
			new DisplayRow(7, "Translucent", 0xFF6366F1),
			new DisplayRow(8, "Other Terrain", 0xFFA855F7)
	};
	private static boolean snapshotInitialized = false;

	private PerformanceAreaGraphRenderer() {
	}

	public static int getMode() {
		return areaGraphMode;
	}

	public static void setMode(int mode) {
		areaGraphMode = (mode % 4 + 4) % 4;
	}

	public static void cycleMode() {
		areaGraphMode = (areaGraphMode + 1) % 4;
	}

	public static int getFixedScale() {
		return fixedScale;
	}

	public static void setFixedScale(int scale) {
		fixedScale = Math.max(0, Math.min(3, scale));
	}

	public static void cycleScale() { setFixedScale((fixedScale + 1) % 4); }

	public static boolean isVisible(Minecraft minecraft) {
		Alacer alacer = Alacer.get();
		return minecraft != null && minecraft.world != null && !minecraft.options.hideGui
			&& alacer != null && alacer.profiler() != null && !alacer.isBenchmarking()
			&& (alacer.isGraphVisible() || (minecraft.options.debugEnabled
				&& (minecraft.options.debugTpsEnabled || minecraft.options.debugProfilerEnabled)));
	}

	public static float adaptiveScale(double worstMillis) {
		return (float) Math.min(250.0D, Math.max(12.0D, worstMillis * 1.15D));
	}

	public static float adaptiveTopScale(double worstSubMillis) {
		return (float) Math.min(250.0D, Math.max(2.0D, worstSubMillis * 1.20D));
	}

	public static boolean referenceLabelFits(int y, int areaTop, int previousLabelY) {
		return y >= areaTop + 8 && previousLabelY - y >= 9;
	}

	private static int drawReference(TextRenderer text, int left, int right,
									 int top, int bottom, float pixelsPerMs,
									 float millis, String label, int rgb,
									 int previousLabelY) {
		int y = (int) (bottom - millis * pixelsPerMs);
		if (y < top || y > bottom) return previousLabelY;
		GuiElement.fill(left, y, right, y + 1, 0x40000000 | rgb);
		if (!referenceLabelFits(y, top, previousLabelY)) return previousLabelY;
		text.draw(label, right + 2, y - 3, 0x90000000 | rgb);
		return y;
	}

	public static void render(Minecraft minecraft, Window window) {
		if (!isVisible(minecraft) || window == null || minecraft.textRenderer == null) return;
		int count = PerformanceTracker.getHistoryCount();
		if (count < 2) return;
		TextRenderer text = minecraft.textRenderer;
		int width = window.getWidth(), height = window.getHeight();
		if (width < 200 || height < 100) return;
		boolean compact = DebugOverlayLayout.compact(width, height);
		int panelWidth = DebugOverlayLayout.panelWidth(width, height);
		boolean breakdown = DebugOverlayLayout.panelHeight(width, height) >= 110;
		int x1 = width - 2, x0 = x1 - panelWidth;
		int y1 = height - 2, y0 = DebugOverlayLayout.panelTop(width, height);
		long now = System.nanoTime();
		boolean frozen = PerformanceTracker.isFrozen();
		if (!snapshotInitialized || lastGeneration != PerformanceTracker.generation() || frozen != lastFrozenState
			|| (!frozen && now - lastSnapshotTime >= SNAPSHOT_INTERVAL_MS * 1_000_000L)) {
			lastGeneration = PerformanceTracker.generation();
			lastSnapshotTime = now;
			snapshotInitialized = true;
			lastFrozenState = frozen;
			displayAvgMs = PerformanceTracker.getAverageFrameMillis();
			displayAvgFps = PerformanceTracker.getAverageFps();
			displayLowFps = PerformanceTracker.getOnePercentLowFps();
			displayWorstMs = PerformanceTracker.getWorstFrameMillis();
			displayAllocMb = PerformanceTracker.getAllocationRateMbPerSec();
			if (areaGraphMode == 3) {
				PerformanceTracker.SubsystemEntry[] detailEntries = PerformanceTracker.getTopDetailBreakdown();
				for (int i = 0; i < TOP_DETAIL_ROWS.length; i++) {
					TOP_DETAIL_ROWS[i].update(i + 1, detailEntries[i].getName(), detailEntries[i].getColor());
					TOP_DETAIL_ROWS[i].millis = detailEntries[i].getMillis();
					TOP_DETAIL_ROWS[i].percentage = detailEntries[i].getPercentage();
					TOP_DETAIL_ROWS[i].updateStrings();
				}
				String topName = PerformanceTracker.getTopSubsystemName();
				double topPct = PerformanceTracker.getTopSubsystemPercentage();
				double topMs = PerformanceTracker.getTopSubsystemMillis();
				int visChunks = PerformanceTracker.getAvgVisibleChunks();
				int pendChunks = PerformanceTracker.getAvgPendingChunks();
				int frustTests = PerformanceTracker.getAvgFrustumTests();
				cachedStatsRow = String.format(Locale.ROOT, "\u00a77Top: \u00a7f%s (%.1f%% / %.2fms) \u00a77| Vis \u00a7f%d \u00a77| Frust \u00a7f%d \u00a77| Pend \u00a7f%d",
					topName, topPct, topMs, visChunks, frustTests, pendChunks);
			} else {
				PerformanceTracker.SubsystemEntry[] entries = PerformanceTracker.getSubsystemBreakdown();
				for (int i = 0; i < DISPLAY_ROWS.length; i++) {
					DISPLAY_ROWS[i].millis = entries[i].getMillis();
					DISPLAY_ROWS[i].percentage = entries[i].getPercentage();
					DISPLAY_ROWS[i].updateStrings();
				}
				cachedStatsRow = String.format(Locale.ROOT, "\u00a771%% low \u00a7f%.0f \u00a77| Max \u00a7f%.1fms \u00a77| Alloc \u00a7f%s",
					displayLowFps, displayWorstMs, displayAllocMb < 0 ? "n/a"
					: String.format(Locale.ROOT, "%.1f MiB/s", displayAllocMb));
			}
			cachedHeader = String.format(Locale.ROOT, "\u00a76Alacer \u00a77| \u00a7f%.2f ms \u00a77/ \u00a7f%.0f FPS%s",
				displayAvgMs, displayAvgFps, frozen ? " \u00a7c[Frozen]" : "");
		}

		GlStateManager.disableDepthTest();
		try {
			GuiElement.fill(x0, y0, x1, y1, 0xC0303030);
			drawClipped(0, text, cachedHeader, x0 + 5, y0 + 3, panelWidth - 10, 0xFFFFFFFF);
			drawClipped(1, text, cachedStatsRow, x0 + 5, y0 + 13, panelWidth - 10, 0xFFD0D0D0);
			int columnWidth = panelWidth / 2;
			DisplayRow[] activeRows = (areaGraphMode == 3) ? TOP_DETAIL_ROWS : DISPLAY_ROWS;
			for (int i = 0; breakdown && i < activeRows.length; i++) {
				DisplayRow row = activeRows[i];
				int left = compact ? x0 + (i / 4) * columnWidth : x0;
				int y = y0 + 25 + (compact ? i % 4 : i) * 9;
				GuiElement.fill(left + 5, y + 1, left + 9, y + 7, row.color);
				if (compact) {
					drawClipped(4 + i, text, row.name, left + 12, y, columnWidth - 116, 0xFFE0E0E0);
					text.draw(row.percentageStr, left + columnWidth - 101, y, 0xFFAAAAAA);
					text.draw(row.millisStr, left + columnWidth - 63, y, 0xFFFFFFFF);
				} else {
					text.draw(row.label, left + 14, y, 0xFFE0E0E0);
					text.draw(row.percentageStr, left + 183, y, 0xFFAAAAAA);
					text.draw(row.millisStr, left + 232, y, 0xFFFFFFFF);
				}
			}
			int mode = areaGraphMode;
			String title = switch (mode) {
				case 1 -> "Subsystem ms";
				case 2 -> "Subsystem %";
				case 3 -> "Top: " + PerformanceTracker.getTopSubsystemName() + " ms";
				default -> "Frame ms";
			};
			if (mode != lastMode || fixedScale != lastScale) {
				lastMode = mode; lastScale = fixedScale;
				cachedModeTitle = "\u00a7e" + title + " \u00a77| " + (mode == 2 ? "100%"
					: switch (fixedScale) { case 1 -> "50ms"; case 2 -> "33.3ms"; case 3 -> "100ms"; default -> "Auto scale"; });
			}
			drawClipped(2, text, cachedModeTitle, x0 + 5, y0 + (!breakdown ? 25 : compact ? 63 : 100), panelWidth - 10, 0xFFFFFFFF);
			int areaLeft = x0 + 5, areaRight = x1 - 36;
			int areaTop = y0 + (!breakdown ? 36 : compact ? 74 : 111), areaBottom = y1 - 15;
			if (areaRight > areaLeft && areaBottom - areaTop >= 12)
				renderScrollingStackedArea(text, areaLeft, areaTop, areaRight, areaBottom, areaBottom - areaTop, count, mode);
			drawClipped(3, text, "\u00a77F4 mode | Ctrl+F4 scale | Shift+F4 freeze", x0 + 5, y1 - 10, panelWidth - 10, 0xFFAAAAAA);
		} finally {
			GlStateManager.enableTexture();
			GlStateManager.disableBlend();
			GlStateManager.enableDepthTest();
			GlStateManager.color4f(1, 1, 1, 1);
		}
	}

	private static void drawClipped(int slot, TextRenderer text, String line, int x, int y, int width, int color) {
		ClipCache cache = CLIPS[slot];
		int measured = text.getWidth(line);
		if (cache.input != line || cache.available != width || cache.measured != measured) {
			cache.input = line; cache.available = width; cache.measured = measured;
			cache.clipped = measured <= width ? line : text.trim(line, Math.max(0, width));
		}
		text.draw(cache.clipped, x, y, color);
	}

	/**
	 * Renders a real-time scrolling graph supporting three modes:
	 * - Mode 0 (Frame Time): Total frame-time history with FPS reference lines.
	 * - Mode 1 (Subsystem Time): Stacked subsystem frame-time history in milliseconds.
	 * - Mode 2 (% Split): 100% normalized stacked-area proportion history.
	 */
	private static void renderScrollingStackedArea(
			TextRenderer textRenderer,
			int areaLeft, int areaTop,
			int areaRight, int areaBottom,
			int areaHeight, int count,
			int mode
	) {
		// Inset panel behind the graph area
		GuiElement.fill(areaLeft - 1, areaTop - 1, areaRight + 1, areaBottom + 1, 0xA0202020);

		final int areaWidth = areaRight - areaLeft;
		final int available = Math.min(count, areaWidth);

		// Determine Y-axis scaling for millisecond modes (Mode 0, Mode 1 & Mode 3)
		float maxScaleMillis;
		if (fixedScale == 1) {
			maxScaleMillis = 50.0F;
		} else if (fixedScale == 2) {
			maxScaleMillis = 33.333F;
		} else if (fixedScale == 3) {
			maxScaleMillis = 100.0F;
		} else if (mode == 3) {
			maxScaleMillis = adaptiveTopScale(PerformanceTracker.getTopSubsystemWorstMillis());
		} else {
			maxScaleMillis = adaptiveScale(displayWorstMs);
		}

		if (mode == 0 || mode == 1 || mode == 3) {
			final float pixelsPerMs = (float) areaHeight / maxScaleMillis;

			int lastLabelY = areaBottom - 2;
			if (maxScaleMillis <= 4.0F) {
				lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 1.0F, "1.0ms", 0x38BDF8, lastLabelY);
				lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 2.0F, "2.0ms", 0x22C55E, lastLabelY);
				lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 3.0F, "3.0ms", 0xEAB308, lastLabelY);
			} else {
				if (maxScaleMillis <= 25.0F) {
					lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
							pixelsPerMs, 8.333F, "8.3ms", 0x38BDF8, lastLabelY);
				}
				lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 16.666F, "16.7ms", 0x22C55E, lastLabelY);
				lastLabelY = drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 33.333F, "33.3ms", 0xEAB308, lastLabelY);
				drawReference(textRenderer, areaLeft, areaRight, areaTop, areaBottom,
						pixelsPerMs, 50.0F, "50.0ms", 0xEF4444, lastLabelY);
			}

			final int maxScaleInt = Math.round(maxScaleMillis);
			if (maxScaleInt != lastMaxScaleInt || cachedMaxScaleStr.isEmpty()) {
				lastMaxScaleInt = maxScaleInt;
				cachedMaxScaleStr = maxScaleInt + "ms";
			}
			textRenderer.draw(cachedMaxScaleStr, areaRight + 2, areaTop - 3, 0x7090A0B0);
			textRenderer.draw("0ms", areaRight + 2, areaBottom - 5, 0x7090A0B0);
		} else {
			// Percentage mode reference gridlines at 75%, 50%, 25%
			final int y75 = areaBottom - (int) (areaHeight * 0.75F);
			final int y50 = areaBottom - (int) (areaHeight * 0.50F);
			final int y25 = areaBottom - (int) (areaHeight * 0.25F);

			GuiElement.fill(areaLeft, y75, areaRight, y75 + 1, 0x20FFFFFF);
			GuiElement.fill(areaLeft, y50, areaRight, y50 + 1, 0x20FFFFFF);
			GuiElement.fill(areaLeft, y25, areaRight, y25 + 1, 0x20FFFFFF);

			textRenderer.draw("100%", areaRight + 2, areaTop - 3, 0x7090A0B0);
			textRenderer.draw("75%", areaRight + 2, y75 - 3, 0x7090A0B0);
			textRenderer.draw("50%", areaRight + 2, y50 - 3, 0x7090A0B0);
			textRenderer.draw("25%", areaRight + 2, y25 - 3, 0x7090A0B0);
			textRenderer.draw("0%", areaRight + 2, areaBottom - 5, 0x7090A0B0);
		}

		if (available < 2) {
			return;
		}

		final int head = PerformanceTracker.getHeadIndex();
		final int startX = Math.max(areaLeft, areaRight - available);
		final float pixelsPerMs = (mode == 2) ? 0.0F : ((float) areaHeight / maxScaleMillis);

		final Tesselator tesselator = Tesselator.getInstance();
		final BufferBuilder buffer = tesselator.getBuffer();

		GlStateManager.enableBlend();
		GlStateManager.disableTexture();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);

		buffer.begin(7, DefaultVertexFormat.POSITION_COLOR);

		int prevX = startX;

		for (int i = 0; i < available; i++) {
			final int barIndex = (head - available + i + PerformanceTracker.HISTORY_SIZE) % PerformanceTracker.HISTORY_SIZE;
			final int currX = startX + i;

			final long s0 = PerformanceTracker.getTimeSwap(barIndex);
			final long s1 = PerformanceTracker.getTimeSky(barIndex);
			final long s2 = PerformanceTracker.getTimeTerrain(barIndex);
			final long s3 = PerformanceTracker.getTimeEntities(barIndex);
			final long s4 = PerformanceTracker.getTimeParticles(barIndex);
			final long s5 = PerformanceTracker.getTimeTick(barIndex);
			final long s6 = PerformanceTracker.getTimeHud(barIndex);
			final long s7 = PerformanceTracker.getTimeOther(barIndex);

			final double frameTotalNanos = Math.max(1.0, (double) PerformanceTracker.getFrameTotal(barIndex));
			final float frameTotalMs = (float) (frameTotalNanos / 1_000_000.0D);
			final boolean hadGc = PerformanceTracker.getHadGc(barIndex);

			Y_CURR[0] = areaBottom;

			if (mode == 0) {
				// Mode 0: Total Frame Time mode
				final float h = Math.min((float) areaHeight, frameTotalMs * pixelsPerMs);
				Y_CURR[1] = Math.max((float) areaTop, areaBottom - h);
			} else if (mode == 1) {
				// Mode 1: Subsystem Time (ms) mode - stack in actual milliseconds
				final float h0 = (s0 / 1_000_000.0F) * pixelsPerMs;
				final float h1 = (s1 / 1_000_000.0F) * pixelsPerMs;
				final float h2 = (s2 / 1_000_000.0F) * pixelsPerMs;
				final float h3 = (s3 / 1_000_000.0F) * pixelsPerMs;
				final float h4 = (s4 / 1_000_000.0F) * pixelsPerMs;
				final float h5 = (s5 / 1_000_000.0F) * pixelsPerMs;
				final float h6 = (s6 / 1_000_000.0F) * pixelsPerMs;
				final float h7 = (s7 / 1_000_000.0F) * pixelsPerMs;

				Y_CURR[1] = Math.max(areaTop, Y_CURR[0] - h0);
				Y_CURR[2] = Math.max(areaTop, Y_CURR[1] - h1);
				Y_CURR[3] = Math.max(areaTop, Y_CURR[2] - h2);
				Y_CURR[4] = Math.max(areaTop, Y_CURR[3] - h3);
				Y_CURR[5] = Math.max(areaTop, Y_CURR[4] - h4);
				Y_CURR[6] = Math.max(areaTop, Y_CURR[5] - h5);
				Y_CURR[7] = Math.max(areaTop, Y_CURR[6] - h6);
				Y_CURR[8] = Math.max(areaTop, Y_CURR[7] - h7);
			} else if (mode == 3) {
				// Mode 3: Top Subsystem Detail mode - stack 8 sub-components of top subsystem
				for (int layer = 0; layer < 8; layer++) {
					long subNanos = PerformanceTracker.getTopDetailChannel(layer, barIndex);
					float h = (subNanos / 1_000_000.0F) * pixelsPerMs;
					Y_CURR[layer + 1] = Math.max(areaTop, Y_CURR[layer] - h);
				}
			} else {
				// Mode 2: % Split (100% Normalized percentage mode)
				Y_CURR[1] = Y_CURR[0] - (float) ((s0 / frameTotalNanos) * areaHeight);
				Y_CURR[2] = Y_CURR[1] - (float) ((s1 / frameTotalNanos) * areaHeight);
				Y_CURR[3] = Y_CURR[2] - (float) ((s2 / frameTotalNanos) * areaHeight);
				Y_CURR[4] = Y_CURR[3] - (float) ((s3 / frameTotalNanos) * areaHeight);
				Y_CURR[5] = Y_CURR[4] - (float) ((s4 / frameTotalNanos) * areaHeight);
				Y_CURR[6] = Y_CURR[5] - (float) ((s5 / frameTotalNanos) * areaHeight);
				Y_CURR[7] = Y_CURR[6] - (float) ((s6 / frameTotalNanos) * areaHeight);
				Y_CURR[8] = areaTop;
			}

			if (i > 0) {
				if (mode == 0) {
					// Mode 0: Total Frame Time filled area under curve
					final int r, g, b;
					if (frameTotalMs <= 16.666F) {
						r = 59; g = 130; b = 246; // Blue (60+ FPS)
					} else if (frameTotalMs <= 33.333F) {
						r = 245; g = 158; b = 11; // Amber (30-60 FPS)
					} else {
						r = 239; g = 68; b = 68;  // Crimson Red (<30 FPS)
					}

					final float yBotPrev = Y_PREV[0];
					final float yTopPrev = Y_PREV[1];
					final float yBotCurr = Y_CURR[0];
					final float yTopCurr = Y_CURR[1];

					// Emit connecting quad
					buffer.vertex(prevX, yBotPrev, 0.0D).color(r, g, b, 210).nextVertex();
					buffer.vertex(currX, yBotCurr, 0.0D).color(r, g, b, 210).nextVertex();
					buffer.vertex(currX, yTopCurr, 0.0D).color(r, g, b, 210).nextVertex();
					buffer.vertex(prevX, yTopPrev, 0.0D).color(r, g, b, 210).nextVertex();

					// Dynamic spike indicator: yellow crown for JVM GC, red crown for frame drop
					if (hadGc) {
						final float spikeTop = Math.max(areaTop, Y_CURR[1] - 2.0F);
						buffer.vertex(currX, Y_CURR[1], 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX + 1, Y_CURR[1], 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX + 1, spikeTop, 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX, spikeTop, 0.0D).color(254, 240, 138, 255).nextVertex();
					} else if (frameTotalMs > 33.333F) {
						final float spikeTop = Math.max(areaTop, Y_CURR[1] - 2.0F);
						buffer.vertex(currX, Y_CURR[1], 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX + 1, Y_CURR[1], 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX + 1, spikeTop, 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX, spikeTop, 0.0D).color(248, 113, 113, 255).nextVertex();
					}
				} else {
					// Modes 1, 2 & 3: Stacked Subsystems or Top Detail
					for (int layer = 0; layer < 8; layer++) {
						final int r, g, b;
						if (mode == 3) {
							int col = TOP_DETAIL_ROWS[layer].getColor();
							r = (col >> 16) & 0xFF;
							g = (col >> 8) & 0xFF;
							b = col & 0xFF;
						} else {
							final int[] rgb = LAYER_COLORS[layer];
							r = rgb[0];
							g = rgb[1];
							b = rgb[2];
						}

						final float yBotPrev = Y_PREV[layer];
						final float yTopPrev = Y_PREV[layer + 1];
						final float yBotCurr = Y_CURR[layer];
						final float yTopCurr = Y_CURR[layer + 1];

						// Emit connecting quad
						buffer.vertex(prevX, yBotPrev, 0.0D).color(r, g, b, 230).nextVertex();
						buffer.vertex(currX, yBotCurr, 0.0D).color(r, g, b, 230).nextVertex();
						buffer.vertex(currX, yTopCurr, 0.0D).color(r, g, b, 230).nextVertex();
						buffer.vertex(prevX, yTopPrev, 0.0D).color(r, g, b, 230).nextVertex();
					}

					// Dynamic spike indicator at top of stack
					if (hadGc) {
						// Bright yellow indicator crown for observed JVM collection
						final float spikeTop = (mode == 1 || mode == 3) ? Math.max(areaTop, Y_CURR[8] - 2.0F) : areaTop;
						buffer.vertex(currX, Y_CURR[8], 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX + 1, Y_CURR[8], 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX + 1, spikeTop, 0.0D).color(254, 240, 138, 255).nextVertex();
						buffer.vertex(currX, spikeTop, 0.0D).color(254, 240, 138, 255).nextVertex();
					} else if (frameTotalMs > 33.333F) {
						// Amber/Red indicator marker for frame spikes (>33.3ms / <30 FPS)
						final float spikeTop = (mode == 1 || mode == 3) ? Math.max(areaTop, Y_CURR[8] - 2.0F) : areaTop;
						buffer.vertex(currX, Y_CURR[8], 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX + 1, Y_CURR[8], 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX + 1, spikeTop, 0.0D).color(248, 113, 113, 255).nextVertex();
						buffer.vertex(currX, spikeTop, 0.0D).color(248, 113, 113, 255).nextVertex();
					}
				}
			}

			prevX = currX;
			System.arraycopy(Y_CURR, 0, Y_PREV, 0, 9);
		}

		tesselator.end();

		GlStateManager.enableTexture();
		GlStateManager.disableBlend();
	}
}
