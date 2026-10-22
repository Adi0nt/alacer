package net.adiont.alacer.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Opt-in benchmark-stability options (session 31's A/A pilot); no game dependencies.
 *
 * <p>Every option is absent by default, and then the benchmark's phases, names, durations and
 * output are exactly as before. Durations given here are recorded seconds and ignore
 * {@code alacer.benchmark.timeScale}, so a protocol states them literally.
 *
 * <ul>
 * <li>{@code alacer.benchmark.reference=<seconds>}: a {@code reference-pre} phase before the
 * scenario's phases, at the default static view (anchor, ground + 1, yaw 45, pitch 5) in every
 * scene, so each launch measures the same reproducible view before anything is scored.</li>
 * <li>{@code alacer.benchmark.referencePost=true}: also a {@code reference-post} phase at the
 * same view after the scored phases (requires a reference).</li>
 * <li>{@code alacer.benchmark.phaseSeconds=static:60,pan:30}: recorded seconds for named
 * scenario phases (for example a still phase of 60 s, or about 100 s for a tick-frame p99
 * that rests on 20 tick frames). Every name must exist.</li>
 * <li>{@code alacer.benchmark.repeatSegments=<n>} with {@code repeatSegmentSeconds} (default 10):
 * n consecutive, identically configured {@code repeat-NN} segments at the reference view after
 * the scored phases, to measure in-process (within-launch) stability with no option changed.</li>
 * <li>{@code alacer.benchmark.toggleBlocks=<n>} with {@code toggleSegmentSeconds} (default 5),
 * only with {@code alacer.benchmark.toggle}: the static view runs n alternating blocks of four
 * short segments, {@code static-NN-a1, -b1, -b2, -a2} for odd blocks and {@code -b1, -a1, -a2,
 * -b2} for even ones, instead of the usual static and pan segments.</li>
 * <li>{@code alacer.benchmark.processorSampling=true}: records which logical processor the
 * client thread ran each recorded frame on ({@link ProcessorSampler}).</li>
 * <li>{@code alacer.benchmark.mobPoseCheck=true}: records how far the client's mobs moved and
 * turned between the start and end of each recorded phase.</li>
 * <li>{@code alacer.benchmark.freezeMobs=true}: before every phase settles, sets NoAI on every mob the
 * server has loaded, including the animals the world generated (which otherwise walk and turn
 * during "still" phases even with {@code benchmark.entities=0}); the benchmark's own mobs are NoAI
 * already.</li>
 * </ul>
 */
public final class StabilityOptions {
	public static final String REFERENCE_PRE = "reference-pre";
	public static final String REFERENCE_POST = "reference-post";
	public static final String REPEAT_PREFIX = "repeat-";
	private static final double MAX_SECONDS = 600.0;
	private static final int MAX_SEGMENTS = 64;

	public final double referenceSeconds;
	public final boolean referencePost;
	public final Map<String, Double> phaseSeconds;
	public final int repeatSegments;
	public final double repeatSegmentSeconds;
	public final int toggleBlocks;
	public final double toggleSegmentSeconds;
	public final boolean processorSampling;
	public final boolean mobPoseCheck;
	public final boolean freezeMobs;

	private StabilityOptions(double referenceSeconds, boolean referencePost, Map<String, Double> phaseSeconds,
			int repeatSegments, double repeatSegmentSeconds, int toggleBlocks, double toggleSegmentSeconds,
			boolean processorSampling, boolean mobPoseCheck, boolean freezeMobs) {
		this.referenceSeconds = referenceSeconds;
		this.referencePost = referencePost;
		this.phaseSeconds = phaseSeconds;
		this.repeatSegments = repeatSegments;
		this.repeatSegmentSeconds = repeatSegmentSeconds;
		this.toggleBlocks = toggleBlocks;
		this.toggleSegmentSeconds = toggleSegmentSeconds;
		this.processorSampling = processorSampling;
		this.mobPoseCheck = mobPoseCheck;
		this.freezeMobs = freezeMobs;
	}

	public static StabilityOptions fromSystemProperties() {
		return parse(System::getProperty);
	}

	/** Parses the options from {@code alacer.benchmark.*} properties; malformed values are errors, never defaults. */
	public static StabilityOptions parse(Function<String, String> properties) {
		double reference = seconds(properties, "alacer.benchmark.reference", 0.0, true);
		boolean post = bool(properties, "alacer.benchmark.referencePost");
		if (post && reference == 0.0) {
			throw new IllegalArgumentException("alacer.benchmark.referencePost requires alacer.benchmark.reference");
		}
		Map<String, Double> durations = new LinkedHashMap<>();
		String list = properties.apply("alacer.benchmark.phaseSeconds");
		if (list != null && !list.trim().isEmpty()) {
			for (String entry : list.split(",")) {
				String[] parts = entry.trim().split(":");
				if (parts.length != 2 || parts[0].trim().isEmpty()) {
					throw new IllegalArgumentException("alacer.benchmark.phaseSeconds entries must be name:seconds: '" + entry + "'");
				}
				String name = parts[0].trim();
				if (durations.put(name, checkedSeconds("alacer.benchmark.phaseSeconds " + name, parts[1], false)) != null) {
					throw new IllegalArgumentException("alacer.benchmark.phaseSeconds names " + name + " twice");
				}
			}
		}
		int repeats = count(properties, "alacer.benchmark.repeatSegments");
		double repeatSeconds = seconds(properties, "alacer.benchmark.repeatSegmentSeconds", 10.0, false);
		int blocks = count(properties, "alacer.benchmark.toggleBlocks");
		double blockSeconds = seconds(properties, "alacer.benchmark.toggleSegmentSeconds", 5.0, false);
		return new StabilityOptions(reference, post, durations, repeats, repeatSeconds, blocks, blockSeconds,
			bool(properties, "alacer.benchmark.processorSampling"), bool(properties, "alacer.benchmark.mobPoseCheck"),
			bool(properties, "alacer.benchmark.freezeMobs"));
	}

	/** True if any option is set; otherwise the benchmark behaves exactly as without this class. */
	public boolean any() {
		return this.referenceSeconds > 0 || !this.phaseSeconds.isEmpty() || this.repeatSegments > 0
			|| this.toggleBlocks > 0 || this.processorSampling || this.mobPoseCheck || this.freezeMobs;
	}

	/** True if the option changes the phase list (reference, durations, segments or blocks). */
	public boolean changesPhases() {
		return this.referenceSeconds > 0 || !this.phaseSeconds.isEmpty() || this.repeatSegments > 0 || this.toggleBlocks > 0;
	}

	/** Checks that every overridden name is one of the scenario's phases. */
	public void checkPhaseNames(Collection<String> scenarioPhases) {
		for (String name : this.phaseSeconds.keySet()) {
			if (!scenarioPhases.contains(name)) {
				throw new IllegalArgumentException("alacer.benchmark.phaseSeconds names unknown phase '" + name + "'; phases are " + scenarioPhases);
			}
		}
	}

	/** Recorded seconds for a scenario phase: the override if given, else its scenario length. */
	public double secondsFor(String name, double scenarioSeconds) {
		Double override = this.phaseSeconds.get(name);
		return override == null ? scenarioSeconds : override;
	}

	/** Names of the in-process repeat segments, in recording order. */
	public List<String> repeatNames() {
		List<String> names = new ArrayList<>();
		for (int i = 1; i <= this.repeatSegments; i++) {
			names.add(String.format(Locale.ROOT, "%s%02d", REPEAT_PREFIX, i));
		}
		return names;
	}

	/**
	 * Static toggle segments in blocks: odd blocks A, B, B, A and even blocks B, A, A, B, so a linear drift
	 * cancels within every pair of blocks. Suffixes keep the existing {@code -a1/-b1/-b2/-a2} convention.
	 */
	public List<String> toggleBlockNames() {
		List<String> names = new ArrayList<>();
		for (int block = 1; block <= this.toggleBlocks; block++) {
			String[] order = block % 2 == 1 ? new String[] {"a1", "b1", "b2", "a2"} : new String[] {"b1", "a1", "a2", "b2"};
			for (String segment : order) {
				names.add(String.format(Locale.ROOT, "static-%02d-%s", block, segment));
			}
		}
		return names;
	}

	/** True for a toggle segment that runs with the option on (B). */
	public static boolean isVariant(String segmentName) {
		return segmentName.endsWith("-b1") || segmentName.endsWith("-b2");
	}

	/** Mobs at start, at end, present at both, largest movement (blocks) and largest body or head turn (degrees). */
	public static double[] compareMobPoses(Map<Integer, double[]> start, Map<Integer, double[]> end) {
		int both = 0;
		double moved = 0.0;
		double turned = 0.0;
		for (Map.Entry<Integer, double[]> e : start.entrySet()) {
			double[] b = end.get(e.getKey());
			if (b == null) {
				continue;
			}
			double[] a = e.getValue();
			both++;
			moved = Math.max(moved, Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2])));
			for (int i = 3; i < 5; i++) {
				double d = Math.abs(((b[i] - a[i]) % 360.0 + 540.0) % 360.0 - 180.0);
				turned = Math.max(turned, d);
			}
		}
		return new double[] {start.size(), end.size(), both, moved, turned};
	}

	/** Exported in the benchmark environment, only when {@link #any()} is true. */
	public Map<String, String> describe() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put("benchmark.reference", fmt(this.referenceSeconds));
		out.put("benchmark.referencePost", String.valueOf(this.referencePost));
		StringBuilder durations = new StringBuilder();
		for (Map.Entry<String, Double> e : this.phaseSeconds.entrySet()) {
			durations.append(durations.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(fmt(e.getValue()));
		}
		out.put("benchmark.phaseSeconds", durations.toString());
		out.put("benchmark.repeatSegments", String.valueOf(this.repeatSegments));
		out.put("benchmark.repeatSegmentSeconds", fmt(this.repeatSegmentSeconds));
		out.put("benchmark.toggleBlocks", String.valueOf(this.toggleBlocks));
		out.put("benchmark.toggleSegmentSeconds", fmt(this.toggleSegmentSeconds));
		out.put("benchmark.processorSampling", String.valueOf(this.processorSampling));
		out.put("benchmark.mobPoseCheck", String.valueOf(this.mobPoseCheck));
		out.put("benchmark.freezeMobs", String.valueOf(this.freezeMobs));
		return out;
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static boolean bool(Function<String, String> properties, String name) {
		String value = properties.apply(name);
		if (value == null || value.trim().isEmpty() || "false".equalsIgnoreCase(value.trim())) {
			return false;
		}
		if ("true".equalsIgnoreCase(value.trim())) {
			return true;
		}
		throw new IllegalArgumentException("Invalid value for " + name + ": '" + value + "' (true or false)");
	}

	private static int count(Function<String, String> properties, String name) {
		String value = properties.apply(name);
		if (value == null || value.trim().isEmpty()) {
			return 0;
		}
		try {
			int n = Integer.parseInt(value.trim());
			if (n < 0 || n > MAX_SEGMENTS) {
				throw new IllegalArgumentException(name + " must be 0 to " + MAX_SEGMENTS + ": " + value);
			}
			return n;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid value for " + name + ": '" + value + "'", e);
		}
	}

	private static double seconds(Function<String, String> properties, String name, double def, boolean zeroAllowed) {
		String value = properties.apply(name);
		return value == null || value.trim().isEmpty() ? def : checkedSeconds(name, value, zeroAllowed);
	}

	private static double checkedSeconds(String name, String value, boolean zeroAllowed) {
		double seconds;
		try {
			seconds = Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid value for " + name + ": '" + value + "'", e);
		}
		if (!Double.isFinite(seconds) || seconds < 0 || (seconds == 0 && !zeroAllowed) || seconds > MAX_SECONDS) {
			throw new IllegalArgumentException(name + " must be " + (zeroAllowed ? "0" : "above 0") + " to " + MAX_SECONDS + " s: " + value);
		}
		return seconds;
	}
}
