package net.adiont.alacer.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.DebugOverlayLayout;
import net.adiont.alacer.profiler.PerformanceAreaGraphRenderer;
import net.adiont.alacer.profiler.PerformanceTracker;
import net.adiont.alacer.render.TerrainShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GLX;

/** Native F3 columns, refreshed at 5 Hz, with an optional docked performance chart. */
@Mixin(DebugOverlay.class)
public abstract class DebugOverlayMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow protected abstract List<String> getGameInfo();
	@Shadow protected abstract List<String> getSystemInfo();
	@Shadow private boolean showReducedInfo() { throw new AssertionError(); }
	@Unique private List<String> alacer$game = List.of(), alacer$system = List.of();
	@Unique private long alacer$nextRefresh;
	@Unique private Object alacer$world;
	@Unique private int alacer$width, alacer$height;
	@Unique private boolean alacer$graph, alacer$reduced, alacer$frozen;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void alacer$render(Window window, CallbackInfo ci) {
		if (this.minecraft.world == null || this.minecraft.player == null || Alacer.get() == null || Alacer.get().profiler() == null) return;
		boolean graph = PerformanceAreaGraphRenderer.isVisible(this.minecraft);
		boolean reduced = showReducedInfo();
		long now = System.nanoTime();
		if (now >= this.alacer$nextRefresh || this.alacer$world != this.minecraft.world
			|| this.alacer$width != window.getWidth() || this.alacer$height != window.getHeight()
			|| this.alacer$graph != graph || this.alacer$reduced != reduced || this.alacer$frozen != PerformanceTracker.isFrozen()) {
			this.alacer$nextRefresh = now + 200_000_000L;
			this.alacer$world = this.minecraft.world;
			this.alacer$width = window.getWidth(); this.alacer$height = window.getHeight();
			this.alacer$graph = graph; this.alacer$reduced = reduced; this.alacer$frozen = PerformanceTracker.isFrozen();
			alacer$refresh(window, graph);
		}
		this.minecraft.profiler.push("debug");
		try {
			alacer$drawColumn(this.alacer$game, window, false, graph);
			alacer$drawColumn(this.alacer$system, window, true, graph);
			if (graph) PerformanceAreaGraphRenderer.render(this.minecraft, window);
		} finally { this.minecraft.profiler.pop(); }
		ci.cancel();
	}

	@Unique private void alacer$refresh(Window window, boolean graph) {
		// Vanilla owns reduced-debug filtering and resolved target-block properties. Do not
		// synthesize world coordinates or target details that its lists intentionally omit.
		this.alacer$game = new ArrayList<>(getGameInfo());
		this.alacer$system = new ArrayList<>(getSystemInfo());
		boolean compact = graph && DebugOverlayLayout.compact(window.getWidth(), window.getHeight());
		for (int i = this.alacer$game.size() - 1; i >= 0; i--) {
			String line = this.alacer$game.get(i);
			if (compact && !alacer$coreLine(line)) { this.alacer$game.remove(i); continue; }
			if (line.startsWith("Minecraft")) line = "\u00a7b" + line;
			else if (line.contains("fps")) {
				int fps = Minecraft.getCurrentFps();
				String low = Alacer.get().isBenchmarking() ? "sampling off" : PerformanceTracker.getHistoryCount() < 100 ? "warming up"
					: String.format(Locale.ROOT, "%.0f FPS", PerformanceTracker.getOnePercentLowFps());
				line = (fps >= 60 ? "\u00a7a" : fps >= 30 ? "\u00a7e" : "\u00a7c") + fps + " FPS \u00a77| 1% low \u00a7f" + low;
			} else if (line.startsWith("XYZ: ")) {
				var camera = this.minecraft.getCamera();
				line = String.format(Locale.ROOT, "XYZ: \u00a7c%.3f \u00a7a%.3f \u00a79%.3f", camera.x, camera.getShape().minY, camera.z);
			} else if (line.startsWith("Biome: ")) line = "Biome: \u00a7a" + line.substring(7);
			else if (line.startsWith("Light: ")) line = "Light: \u00a7e" + line.substring(7);
			else if (line.startsWith("Facing: ")) line = "Facing: \u00a76" + line.substring(8);
			this.alacer$game.set(i, line);
		}
		Alacer alacer = Alacer.get();
		if (alacer.isBenchmarking()) this.alacer$game.add("Alacer: live display sampling off during benchmark");
		else if (alacer.profiler() != null) {
			boolean vbo = GLX.useVbo();
			boolean shader = vbo && (alacer.config().terrainShader || alacer.config().terrainVaos || alacer.config().verifyModernGl)
				&& TerrainShader.initializedAndSupported();
			this.alacer$game.add("\u00a76Alacer: \u00a7f" + (vbo ? "VBO" : "Display lists") + " \u00a77| shader \u00a7f"
				+ (shader ? TerrainShader.isAttribBindingActive() ? "VAO" : "classic" : "off"));
			this.alacer$game.add(String.format(Locale.ROOT, "\u00a77Frame \u00a7f%.2fms \u00a77| p99 \u00a7f%.2fms%s",
				PerformanceTracker.getAverageFrameMillis(), PerformanceTracker.getP99FrameMillis(),
				PerformanceTracker.isFrozen() ? " \u00a7c[Frozen]" : ""));
			this.alacer$game.add("\u00a77" + alacer.graphKeyName() + " graph | F4 mode | Shift+F4 freeze");
		}
		for (int i = this.alacer$system.size() - 1; i >= 0; i--) {
			String line = this.alacer$system.get(i);
			if (line.isEmpty() && compact) { this.alacer$system.remove(i); continue; }
			if (line.startsWith("Mem: ")) {
				Runtime runtime = Runtime.getRuntime();
				line = String.format(Locale.ROOT, "Heap: \u00a7f%d / %d MiB", (runtime.totalMemory() - runtime.freeMemory()) / 1048576,
					runtime.maxMemory() / 1048576);
			} else if (line.startsWith("Allocated: ")) {
				double allocation = PerformanceTracker.getAllocationRateMbPerSec();
				line = allocation < 0 ? "Client alloc: \u00a77n/a" : String.format(Locale.ROOT, "Client alloc: \u00a7e%.1f MiB/s", allocation);
			} else if (line.startsWith("Java: ") || line.startsWith("CPU: ") || line.startsWith("Display: ")) line = "\u00a7b" + line;
			this.alacer$system.set(i, line);
		}
		this.alacer$system.add(Math.min(3, this.alacer$system.size()), String.format(Locale.ROOT, "GC total: \u00a7f%dms \u00a77/ \u00a7f%d collections",
			PerformanceTracker.getTotalGcTimeMillis(), PerformanceTracker.getTotalGcCount()));
		int columnWidth = window.getWidth() / 2 - 6;
		alacer$clip(this.alacer$game, columnWidth);
		alacer$clip(this.alacer$system, columnWidth);
	}

	@Unique private static boolean alacer$coreLine(String line) {
		return line.startsWith("Minecraft") || line.contains("fps") || line.startsWith("XYZ:")
			|| line.startsWith("Block:") || line.startsWith("Chunk:") || line.startsWith("Chunk-relative:")
			|| line.startsWith("Facing:") || line.startsWith("Biome:") || line.startsWith("Light:") || line.startsWith("Looking at:");
	}
	@Unique private void alacer$clip(List<String> lines, int width) {
		TextRenderer text = this.minecraft.textRenderer;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (text.getWidth(line) > width) lines.set(i, text.trim(line, Math.max(0, width - text.getWidth("..."))) + "\u00a77...");
		}
	}
	@Unique private void alacer$drawColumn(List<String> lines, Window window, boolean right, boolean graph) {
		TextRenderer text = this.minecraft.textRenderer;
		int rows = DebugOverlayLayout.textRows(window.getWidth(), window.getHeight(), text.fontHeight, graph, right);
		for (int i = 0; i < Math.min(rows, lines.size()); i++) {
			String line = i == rows - 1 && lines.size() > rows ? (graph ? "\u00a77... hide graph for more" : "...") : lines.get(i);
			if (line.isEmpty()) continue;
			int width = text.getWidth(line), x = right ? window.getWidth() - 2 - width : 2;
			int y = 2 + i * text.fontHeight;
			GuiElement.fill(x - 1, y - 1, x + width + 1, y + text.fontHeight - 1, 0x90505050);
			text.draw(line, x, y, 0xFFE0E0E0);
		}
	}
}
