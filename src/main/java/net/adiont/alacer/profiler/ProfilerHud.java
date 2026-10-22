package net.adiont.alacer.profiler;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;

/**
 * Small text overlay summarizing the last few seconds of frames.
 *
 * <p>Statistics are recomputed twice per second into cached strings, so drawing the overlay
 * each frame only costs the text rendering itself. Scratch arrays are allocated once.
 */
public final class ProfilerHud {

	private static final long REFRESH_NANOS = 500_000_000L;
	private static final long WINDOW_NANOS = 5_000_000_000L;

	private final FrameProfiler profiler;
	private final long[] starts;
	private final long[] values;
	private final long[] window;
	private final long[] scratch;
	private final String[] lines = new String[5];
	private long nextRefresh;
	private int rightEdge;

	public ProfilerHud(FrameProfiler profiler) {
		this.profiler = profiler;
		int capacity = profiler.frames().capacity();
		this.starts = new long[capacity];
		this.values = new long[capacity];
		this.window = new long[capacity];
		this.scratch = new long[capacity];
	}

	public void render(Minecraft minecraft) {
		long previousRefresh = this.nextRefresh;
		lines(false);
		if (this.nextRefresh != previousRefresh || this.rightEdge == 0) {
			// Recompute the scaled width alongside the text; the GUI scale rarely changes.
			this.rightEdge = new Window(minecraft).getWidth() - 2;
		}

		TextRenderer text = minecraft.textRenderer;
		int y = 2;
		for (String line : this.lines) {
			text.drawWithShadow(line, this.rightEdge - text.getWidth(line), y, 0xE0E0E0);
			y += text.fontHeight + 1;
		}
	}

	/** Current summary lines, refreshed at most twice per second unless {@code force} is set. */
	public String[] lines(boolean force) {
		long now = System.nanoTime();
		if (force || now - this.nextRefresh >= 0 || this.lines[0] == null) {
			this.nextRefresh = now + REFRESH_NANOS;
			refresh();
		}
		return this.lines;
	}

	private void refresh() {
		MetricRing frames = this.profiler.frames();
		int n = frames.copyRecent(FrameProfiler.COL_START, frames.size(), this.starts);
		if (n == 0) {
			for (int i = 0; i < this.lines.length; i++) {
				this.lines[i] = i == 0 ? "Alacer: collecting..." : "";
			}
			return;
		}
		// Only the frames that started within the last few seconds.
		long cutoff = this.starts[n - 1] - WINDOW_NANOS;
		int first = n - 1;
		while (first > 0 && this.starts[first - 1] >= cutoff) {
			first--;
		}
		int count = n - first;

		SeriesStats frame = column(frames, FrameProfiler.COL_FRAME, n, first, count);
		SeriesStats render = column(frames, FrameProfiler.COL_RENDER, n, first, count);
		SeriesStats setup = column(frames, FrameProfiler.COL_TERRAIN_SETUP, n, first, count);
		SeriesStats upload = column(frames, FrameProfiler.COL_CHUNK_UPLOAD, n, first, count);
		SeriesStats visibility = column(frames, FrameProfiler.COL_VISIBILITY, n, first, count);
		SeriesStats entities = column(frames, FrameProfiler.COL_ENTITIES, n, first, count);
		SeriesStats swap = column(frames, FrameProfiler.COL_SWAP, n, first, count);
		SeriesStats tick = column(frames, FrameProfiler.COL_TICK, n, first, count);
		SeriesStats alloc = column(frames, FrameProfiler.COL_ALLOC_BYTES, n, first, count);
		JvmMonitor jvm = this.profiler.jvm();

		this.lines[0] = String.format(Locale.ROOT, "Alacer  %.0f fps  1%% low %.0f  0.1%% low %.0f",
			frame.averageFps(), frame.fpsAt(frame.worst1PercentMean), frame.fpsAt(frame.worst01PercentMean));
		this.lines[1] = String.format(Locale.ROOT, "frame ms  p50 %.2f  p99 %.2f  max %.2f",
			ms(frame.p50), ms(frame.p99), ms(frame.max));
		this.lines[2] = String.format(Locale.ROOT, "cpu ms  tick %.2f  render %.2f  swap %.2f",
			ms(tick.mean), ms(render.mean), ms(swap.mean));
		this.lines[3] = String.format(Locale.ROOT, "ms  setup %.2f (vis %.2f)  upload %.2f  entities %.2f",
			ms(setup.mean), ms(visibility.mean), ms(upload.mean), ms(entities.mean));
		this.lines[4] = String.format(Locale.ROOT, "alloc %s/frame  heap %d/%d MB  gc %d (%d ms)",
			alloc.min < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f KB", alloc.mean / 1024.0),
			jvm.heapUsed() >> 20, jvm.heapMax() >> 20, jvm.gcCount(), jvm.gcMillis());
	}

	private SeriesStats column(MetricRing frames, int column, int n, int first, int count) {
		frames.copyRecent(column, n, this.values);
		System.arraycopy(this.values, first, this.window, 0, count);
		return SeriesStats.compute(this.window, count, this.scratch);
	}

	private static double ms(double nanos) {
		return nanos / 1.0e6;
	}
}
