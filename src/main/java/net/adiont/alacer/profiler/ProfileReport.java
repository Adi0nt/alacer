package net.adiont.alacer.profiler;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Turns captured frame columns into a JSON summary and a raw CSV.
 *
 * <p>The data is a detached copy, so a report can be written on a background thread
 * without touching the live profiler.
 */
public final class ProfileReport {

	private final String label;
	private final long[][] columns;
	private final int rows;
	private final long[] ticks;
	private final int tickCount;
	private final Map<String, String> environment;
	private final Map<String, String> config;

	public ProfileReport(String label, long[][] columns, int rows, long[] ticks, int tickCount,
			Map<String, String> environment, Map<String, String> config) {
		this.label = label;
		this.columns = columns;
		this.rows = rows;
		this.ticks = ticks;
		this.tickCount = tickCount;
		this.environment = environment;
		this.config = config;
	}

	/** Copies the most recent history out of a live profiler. Call on the client thread. */
	public static ProfileReport capture(String label, FrameProfiler profiler, Map<String, String> environment, Map<String, String> config) {
		MetricRing frames = profiler.frames();
		int rows = frames.size();
		long[][] columns = new long[FrameProfiler.COLUMNS][rows];
		for (int c = 0; c < FrameProfiler.COLUMNS; c++) {
			frames.copyRecent(c, rows, columns[c]);
		}
		MetricRing tickRing = profiler.ticks();
		long[] ticks = new long[tickRing.size()];
		int tickCount = tickRing.copyRecent(0, ticks.length, ticks);
		return new ProfileReport(label, columns, rows, ticks, tickCount, environment, config);
	}

	public int rows() {
		return this.rows;
	}

	public SeriesStats stats(int column) {
		return SeriesStats.compute(this.columns[column], this.rows, new long[this.rows]);
	}

	public String toJson() {
		JsonWriter json = new JsonWriter().beginObject();
		json.field("label", this.label);
		json.fields("environment", this.environment);
		json.fields("config", this.config);

		SeriesStats frame = stats(FrameProfiler.COL_FRAME);
		json.beginObject("frames");
		json.field("count", frame.count);
		json.field("duration_s", frame.total / 1.0e9);
		json.field("avg_fps", frame.averageFps());
		json.field("one_percent_low_fps", frame.fpsAt(frame.worst1PercentMean));
		json.field("point_one_percent_low_fps", frame.fpsAt(frame.worst01PercentMean));
		writeMillis(json, frame);
		json.endObject();

		// Sections nest (see FrameProfiler.Section), so these must not be summed.
		json.beginObject("sections_ms");
		for (FrameProfiler.Section section : FrameProfiler.Section.values()) {
			section(json, section.name().toLowerCase(Locale.ROOT), FrameProfiler.column(section));
		}
		json.endObject();

		json.beginObject("counters_per_frame");
		for (FrameProfiler.Counter counter : FrameProfiler.Counter.values()) {
			json.field(counter.name().toLowerCase(Locale.ROOT), stats(FrameProfiler.column(counter)).mean);
		}
		json.endObject();

		SeriesStats alloc = stats(FrameProfiler.COL_ALLOC_BYTES);
		json.beginObject("render_thread_alloc_bytes_per_frame");
		if (alloc.count > 0 && alloc.min >= 0) {
			json.field("mean", alloc.mean);
			json.field("p50", alloc.p50);
			json.field("p99", alloc.p99);
			json.field("max", alloc.max);
			json.field("total", alloc.total);
		} else {
			json.field("available", false);
		}
		json.endObject();

		SeriesStats visible = stats(FrameProfiler.COL_VISIBLE_CHUNKS);
		SeriesStats pending = stats(FrameProfiler.COL_PENDING_CHUNKS);
		json.beginObject("chunks");
		json.field("visible_mean", visible.mean);
		json.field("pending_mean", pending.mean);
		json.field("pending_max", pending.max);
		json.endObject();

		SeriesStats tick = SeriesStats.compute(this.ticks, this.tickCount, new long[this.tickCount]);
		json.beginObject("client_ticks_ms");
		json.field("count", tick.count);
		writeMillis(json, tick);
		json.endObject();

		return json.endObject().toString() + "\n";
	}

	private void section(JsonWriter json, String name, int column) {
		SeriesStats s = stats(column);
		json.beginObject(name);
		json.field("mean", s.mean / 1.0e6);
		json.field("p50", s.p50 / 1.0e6);
		json.field("p99", s.p99 / 1.0e6);
		json.field("max", s.max / 1.0e6);
		json.endObject();
	}

	private static void writeMillis(JsonWriter json, SeriesStats s) {
		json.field("mean_ms", s.mean / 1.0e6);
		json.field("stddev_ms", s.stdDev / 1.0e6);
		json.field("min_ms", s.min / 1.0e6);
		json.field("p50_ms", s.p50 / 1.0e6);
		json.field("p90_ms", s.p90 / 1.0e6);
		json.field("p95_ms", s.p95 / 1.0e6);
		json.field("p99_ms", s.p99 / 1.0e6);
		json.field("p99_9_ms", s.p999 / 1.0e6);
		json.field("max_ms", s.max / 1.0e6);
	}

	public void writeCsv(Path file) throws IOException {
		try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			for (int c = 0; c < FrameProfiler.COLUMNS; c++) {
				if (c > 0) {
					out.write(',');
				}
				out.write(FrameProfiler.COLUMN_NAMES[c]);
			}
			out.write('\n');
			StringBuilder line = new StringBuilder();
			for (int r = 0; r < this.rows; r++) {
				line.setLength(0);
				for (int c = 0; c < FrameProfiler.COLUMNS; c++) {
					if (c > 0) {
						line.append(',');
					}
					line.append(this.columns[c][r]);
				}
				line.append('\n');
				out.write(line.toString());
			}
		}
	}

	/** Writes {@code <base>.json} and {@code <base>.csv}. */
	public void write(Path directory, String baseName) throws IOException {
		Files.createDirectories(directory);
		Files.write(directory.resolve(baseName + ".json"), toJson().getBytes(StandardCharsets.UTF_8));
		writeCsv(directory.resolve(baseName + ".csv"));
	}
}
