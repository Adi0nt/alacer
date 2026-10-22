package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;

/**
 * Counts the OpenGL calls made while entities in the entity loop are drawn. Profiling only:
 * the hooks that feed it are applied just when {@code profiler.entityDetail} is enabled, and
 * count only between {@link #beginEntity} and {@link #endEntity}.
 *
 * <p>Per frame, the counts go to the profiler's counters. Per benchmark phase, a table by
 * entity class and by kind of state change is logged, which is where the per-entity cost
 * comes from.
 *
 * <p>Calls are counted where they reach OpenGL: {@code GlStateManager}'s matrix operations and
 * {@code callList} always do; its state setters only when the cached value differs. Immediate
 * draws ({@code Tesselator.end}) are counted with the calls {@code BufferUploader} makes for
 * their vertex format. Direct {@code GL11} calls other than the lightmap coordinate (for
 * example the texture environment of the hurt overlay) are not counted.
 */
public final class GlCallStats {

	/** Kinds of state change, for the per-phase table. */
	public enum State {
		CULL, RESCALE_NORMAL, LIGHTING, BLEND, ALPHA_TEST, DEPTH_TEST, TEXTURE_2D, COLOR_MATERIAL, NORMALIZE,
		POLYGON_OFFSET, OTHER_CAP, ACTIVE_TEXTURE, BIND_TEXTURE, COLOR, BLEND_FUNC, DEPTH_MASK, DEPTH_FUNC,
		ALPHA_FUNC, CULL_FACE, SHADE_MODEL, COLOR_MASK
	}

	private static final State[] STATES = State.values();

	// Per entity class: entities, GL calls, matrix ops, zero translates, call lists, state
	// changes, immediate draws, state requests.
	private static final int ENTITIES = 0, CALLS = 1, MATRIX = 2, ZERO = 3, LISTS = 4, CHANGES = 5, DRAWS = 6, REQUESTS = 7;
	private static final String[] FIELDS = {"entities", "calls", "matrix", "zeroTranslate", "callList", "stateChanges", "draws", "stateRequests"};

	/** True only while an entity in the entity loop is being drawn (detail runs only). */
	public static boolean counting;

	private static FrameProfiler profiler;
	private static final Map<Class<?>, long[]> BY_CLASS = new IdentityHashMap<>();
	private static final long[] BY_STATE = new long[STATES.length];
	private static long[] current;

	private GlCallStats() {
	}

	public static void init(FrameProfiler frameProfiler) {
		profiler = frameProfiler;
	}

	public static void beginEntity(Object entity) {
		if (profiler == null) {
			return;
		}
		long[] row = BY_CLASS.get(entity.getClass());
		if (row == null) {
			row = new long[FIELDS.length];
			BY_CLASS.put(entity.getClass(), row);
		}
		row[ENTITIES]++;
		current = row;
		counting = true;
	}

	public static void endEntity() {
		counting = false;
		current = null;
	}

	public static void matrix() {
		add(Counter.GL_MATRIX_OPS, MATRIX);
	}

	public static void translate(double x, double y, double z) {
		add(Counter.GL_MATRIX_OPS, MATRIX);
		if (x == 0.0 && y == 0.0 && z == 0.0) {
			profiler.count(Counter.GL_ZERO_TRANSLATES);
			current[ZERO]++;
		}
	}

	public static void callList() {
		add(Counter.GL_CALL_LISTS, LISTS);
	}

	public static void stateRequest() {
		profiler.count(Counter.GL_STATE_REQUESTS);
		current[REQUESTS]++;
	}

	public static void stateChange(State state) {
		add(Counter.GL_STATE_CHANGES, CHANGES);
		BY_STATE[state.ordinal()]++;
	}

	/** A capability toggle through {@code GlStateManager.GlState}; {@code changes} is whether it reaches GL. */
	public static void capability(int cap, boolean changes) {
		stateRequest();
		if (changes) {
			stateChange(capState(cap));
		}
	}

	public static void lightmap() {
		add(Counter.GL_LIGHTMAP_SETS, -1);
	}

	/** {@code BufferUploader.end}: one draw with the client-array setup for its format. */
	public static void draw(VertexFormat format, int vertices) {
		if (vertices <= 0) {
			return;
		}
		int calls = 1;
		for (VertexFormatElement element : format.getElements()) {
			// UV elements switch the client active texture around enable and disable.
			calls += element.getUsage() == VertexFormatElement.Usage.UV ? 7 : 3;
		}
		profiler.count(Counter.GL_IMMEDIATE_DRAWS);
		current[DRAWS]++;
		profiler.count(Counter.GL_CALLS, calls);
		current[CALLS] += calls;
	}

	/** One GL call of the given kind; {@code field} is its per-class column, or -1. */
	private static void add(Counter counter, int field) {
		profiler.count(counter);
		profiler.count(Counter.GL_CALLS);
		current[CALLS]++;
		if (field >= 0) {
			current[field]++;
		}
	}

	private static State capState(int cap) {
		switch (cap) {
			case GL11.GL_CULL_FACE:
				return State.CULL;
			case 32826: // GL_RESCALE_NORMAL
				return State.RESCALE_NORMAL;
			case GL11.GL_LIGHTING:
				return State.LIGHTING;
			case GL11.GL_BLEND:
				return State.BLEND;
			case GL11.GL_ALPHA_TEST:
				return State.ALPHA_TEST;
			case GL11.GL_DEPTH_TEST:
				return State.DEPTH_TEST;
			case GL11.GL_TEXTURE_2D:
				return State.TEXTURE_2D;
			case GL11.GL_COLOR_MATERIAL:
				return State.COLOR_MATERIAL;
			case GL11.GL_NORMALIZE:
				return State.NORMALIZE;
			case GL11.GL_POLYGON_OFFSET_FILL:
				return State.POLYGON_OFFSET;
			default:
				return State.OTHER_CAP;
		}
	}

	/** Clears the tables, at the start of a benchmark phase. */
	public static void reset() {
		BY_CLASS.clear();
		Arrays.fill(BY_STATE, 0L);
	}

	/** Logs the per-class and per-state tables collected since {@link #reset()}. */
	public static void log(String phase) {
		if (profiler == null || BY_CLASS.isEmpty()) {
			return;
		}
		long entities = 0;
		long[] total = new long[FIELDS.length];
		List<String> lines = new ArrayList<>();
		for (Map.Entry<Class<?>, long[]> e : BY_CLASS.entrySet()) {
			long[] row = e.getValue();
			entities += row[ENTITIES];
			for (int i = 0; i < row.length; i++) {
				total[i] += row[i];
			}
			lines.add(describe(e.getKey().getSimpleName(), row));
		}
		lines.add(describe("all", total));
		StringBuilder states = new StringBuilder();
		for (State state : STATES) {
			long n = BY_STATE[state.ordinal()];
			if (n > 0) {
				states.append(String.format(Locale.ROOT, " %s=%.2f", state.name().toLowerCase(Locale.ROOT), (double) n / entities));
			}
		}
		Alacer.LOGGER.info("GL calls per drawn entity in '{}':\n  {}\n  state changes per entity:{}", phase, String.join("\n  ", lines), states);
	}

	private static String describe(String name, long[] row) {
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%-14s n=%d", name, row[ENTITIES]));
		for (int i = 1; i < row.length; i++) {
			out.append(String.format(Locale.ROOT, " %s=%.2f", FIELDS[i], row[ENTITIES] == 0 ? 0.0 : (double) row[i] / row[ENTITIES]));
		}
		return out.toString();
	}
}
