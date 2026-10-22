package net.adiont.alacer.render;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Section;

/** Optional, client-thread-only attribution within draws selected by the world entity loop. */
public final class EntityDrawDetails {

	public enum Stage {
		SETUP(Section.ENTITY_LIVING_SETUP), PREPARE(Section.ENTITY_MODEL_PREPARE),
		ANIMATION(Section.ENTITY_MODEL_ANIMATION), MODEL(Section.ENTITY_RENDER_MODEL),
		MODEL_DRAW(Section.ENTITY_MODEL_DRAW), LAYERS(Section.ENTITY_LAYERS),
		SHADOW(Section.ENTITY_SHADOW), SHADOW_BLOCK(Section.ENTITY_SHADOW_BLOCK),
		NAME_TAG(Section.ENTITY_NAME_TAG), OVERLAY(Section.ENTITY_OVERLAY),
		SHADOW_QUERY(Section.ENTITY_SHADOW_QUERY);

		final Section section;
		Stage(Section section) {
			this.section = section;
		}
	}

	private static final Stage[] STAGES = Stage.values();
	private static final int N = STAGES.length;
	private static final long[] START = new long[N];
	private static final int[] DEPTH = new int[N];
	private static final Map<Class<?>, long[]> BY_CLASS = new IdentityHashMap<>();
	private static FrameProfiler profiler;
	private static long[] current;
	private static long incompleteSpans;

	private EntityDrawDetails() {
	}

	public static void init(FrameProfiler value) {
		profiler = value;
	}

	public static void beginEntity(Object entity) {
		if (profiler == null) {
			return;
		}
		current = BY_CLASS.get(entity.getClass());
		if (current == null) {
			current = new long[N * 2 + 1];
			BY_CLASS.put(entity.getClass(), current);
		}
		current[N * 2]++;
	}

	public static void endEntity() {
		if (current == null) {
			return;
		}
		for (int i = 0; i < N; i++) {
			if (DEPTH[i] != 0) {
				incompleteSpans++;
				DEPTH[i] = 0;
			}
		}
		current = null;
	}

	public static void begin(Stage stage) {
		if (current != null && DEPTH[stage.ordinal()]++ == 0) {
			START[stage.ordinal()] = System.nanoTime();
		}
	}

	public static void end(Stage stage) {
		int i = stage.ordinal();
		if (current != null && DEPTH[i] > 0 && --DEPTH[i] == 0) {
			long duration = System.nanoTime() - START[i];
			current[i] += duration;
			current[N + i]++;
			profiler.add(stage.section, duration);
		}
	}

	public static void reset() {
		BY_CLASS.clear();
		current = null;
		Arrays.fill(DEPTH, 0);
		incompleteSpans = 0;
	}

	/** Inclusive durations: MODEL_DRAW is within MODEL, SHADOW_BLOCK within SHADOW, etc. */
	public static void log(String phase) {
		if (BY_CLASS.isEmpty()) {
			return;
		}
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Class<?>, long[]> entry : BY_CLASS.entrySet()) {
			long[] row = entry.getValue();
			out.append("\n  ").append(entry.getKey().getSimpleName()).append(" n=").append(row[N * 2]);
			for (Stage stage : STAGES) {
				int i = stage.ordinal();
				out.append(String.format(Locale.ROOT, " %s=%.3fus(%.2f calls)", stage.name().toLowerCase(Locale.ROOT),
					row[i] / 1000.0 / row[N * 2], (double) row[N + i] / row[N * 2]));
			}
		}
		Alacer.LOGGER.info("Entity draw detail in '{}' (inclusive us/drawn entity):{}\n  incomplete spans={}", phase, out, incompleteSpans);
	}
}
