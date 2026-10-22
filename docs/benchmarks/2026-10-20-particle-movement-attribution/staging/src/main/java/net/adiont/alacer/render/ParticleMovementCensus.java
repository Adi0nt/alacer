package net.adiont.alacer.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.adiont.alacer.profiler.JsonWriter;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/** Staged, diagnostic-only census. No world/shape/list writes and no per-element hooks. */
public final class ParticleMovementCensus {

	public static final boolean ENABLED = flag("profiler.particleMovementCensus", false)
		&& flag("benchmark", false) && flag("profiler.enabled", true);
	private static final int PHASE_LIMIT = 16, CLASS_LIMIT = 64, MOVE_DEPTH_LIMIT = 16;
	private static volatile Session session;

	private ParticleMovementCensus() { }

	private static boolean flag(String key, boolean fallback) {
		return Boolean.parseBoolean(System.getProperty("alacer." + key, Boolean.toString(fallback)).trim());
	}

	/** Client thread only. null excludes setup, probes, settle and warmups. Names are scored phases only. */
	public static void phase(String name) {
		if (!ENABLED) { return; }
		if (session == null) { session = new Session(); }
		Session s = session;
		s.checkThread();
		if (s.finished) { throw new IllegalStateException("Movement census already finished"); }
		s.closeScopes();
		s.current = null;
		if (name == null) { return; }
		if (name.isEmpty() || name.length() > 96) { throw new IllegalArgumentException("Invalid census phase name"); }
		if (name.toLowerCase(Locale.ROOT).contains("warmup")) { s.excludedWarmupActivations++; return; }
		for (int i = 0; i < s.phaseCount; i++) {
			if (s.phases[i].name.equals(name)) { s.current = s.phases[i]; return; }
		}
		if (s.phaseCount == PHASE_LIMIT) { s.droppedPhaseActivations++; return; }
		s.current = new Phase(name);
		s.phases[s.phaseCount++] = s.current;
	}

	/** Entry arguments, before vanilla's cobweb scaling; signed zeros both compare equal to zero. */
	public static void beginMove(Entity entity, double dx, double dy, double dz) {
		Session s = onOwner();
		if (s == null || !(entity instanceof Particle)) { return; }
		if (s.current == null) { s.excludedMoves++; return; }
		if (s.depth == MOVE_DEPTH_LIMIT || s.overflowDepth != 0) {
			s.overflowDepth++;
			s.moveDepthOverflows++;
			return;
		}
		Move m = s.moves[s.depth++];
		m.entity = entity;
		m.phase = s.current;
		m.classSlot = s.classSlot(entity.getClass());
		m.gatherNs = 0;
		m.queryDepth = 0;
		m.ordinaryLength = 0;
		m.stepLength = 0;
		m.sitesKnown = true;
		record(m, Metric.MOVES_STARTED, 1);
		record(m, Metric.DX_NONZERO, dx != 0.0 ? 1 : 0);
		record(m, Metric.DY_NONZERO, dy != 0.0 ? 1 : 0);
		record(m, Metric.DZ_NONZERO, dz != 0.0 ? 1 : 0);
		record(m, Metric.ZERO_VECTOR, dx == 0.0 && dy == 0.0 && dz == 0.0 ? 1 : 0);
		record(m, Metric.NONFINITE_INPUT, !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz) ? 1 : 0);
		record(m, Metric.NO_CLIP_AT_ENTRY, entity.noClip ? 1 : 0);
		record(m, Metric.POSITIVE_STEP_HEIGHT_AT_ENTRY, entity.stepHeight > 0.0F ? 1 : 0);
		record(m, Metric.NESTED_MOVES, s.depth > 1 ? 1 : 0);
		m.startNs = System.nanoTime();
	}

	public static void endMove(Entity entity) {
		Session s = onOwner();
		if (s == null || !(entity instanceof Particle) || s.current == null) { return; }
		if (s.overflowDepth != 0) { s.overflowDepth--; return; }
		Move m = active(entity);
		if (m == null) { s.unmatchedMoveReturns++; return; }
		long elapsed = System.nanoTime() - m.startNs;
		record(m, Metric.MOVES_RETURNED, 1);
		if (m.queryDepth == 0) {
			record(m, Metric.TIMED_MOVES, 1);
			record(m, Metric.MOVE_WALL_NS, elapsed);
			record(m, Metric.COLLISION_GATHER_WALL_NS, m.gatherNs);
			record(m, Metric.REST_MOVE_WALL_NS, elapsed - m.gatherNs);
		} else { s.unclosedQueries += m.queryDepth; }
		// Structural work implied by returned list lengths, not observed per-element clip invocations.
		if (m.sitesKnown) {
			record(m, Metric.SCAN_ESTIMATE_COMPLETE_MOVES, 1);
			record(m, Metric.ORDINARY_AXIS_SCAN_LENGTH_SUM_EXPECTED, 3L * m.ordinaryLength);
			record(m, Metric.STEP_AXIS_SCAN_LENGTH_SUM_EXPECTED, 7L * m.stepLength);
		}
		m.clear();
		s.depth--;
	}

	/** World.getCollisions HEAD, restricted to the current particle move's entity. */
	public static void beginCollision(Entity entity) {
		Move m = active(entity);
		if (m == null) { return; }
		record(m, Metric.WORLD_QUERY_CALLS, 1);
		if (m.queryDepth++ == 0) { m.queryStartNs = System.nanoTime(); }
	}

	/** RETURN only: no replacement, iteration or retention of the result. Nested gather clocks use their union. */
	public static void endCollision(Entity entity, List<Box> result) {
		Move m = active(entity);
		if (m == null) { return; }
		if (m.queryDepth == 0) { session.unmatchedQueryReturns++; return; }
		long endNs = System.nanoTime();
		if (--m.queryDepth == 0) { m.gatherNs += endNs - m.queryStartNs; }
		record(m, Metric.WORLD_QUERY_RETURNS, 1);
		int size = knownSize(result);
		if (size < 0) { record(m, Metric.UNKNOWN_WORLD_LIST_RESULTS, 1); return; }
		record(m, Metric.WORLD_RESULT_SIZE_SUM, size);
		record(m, Metric.EMPTY_WORLD_RESULTS, size == 0 ? 1 : 0);
		maximum(m, Metric.MAX_WORLD_RESULT_SIZE, size);
	}

	/** Called only at Entity.move's sliced ordinary/step query result expressions. */
	public static void queryResult(Entity entity, List<Box> result, boolean step) {
		Move m = active(entity);
		if (m == null) { return; }
		record(m, step ? Metric.STEP_QUERY_RETURNS : Metric.ORDINARY_QUERY_RETURNS, 1);
		int size = knownSize(result);
		if (size < 0) {
			m.sitesKnown = false;
			record(m, Metric.UNKNOWN_SITE_LIST_RESULTS, 1);
			return;
		}
		record(m, step ? Metric.STEP_RESULT_SIZE_SUM : Metric.ORDINARY_RESULT_SIZE_SUM, size);
		if (step) { m.stepLength += size; } else { m.ordinaryLength += size; }
	}

	private static int knownSize(List<Box> result) {
		// Vanilla returns exactly ArrayList. Avoid invoking potentially observable custom-list size().
		return result != null && result.getClass() == ArrayList.class ? result.size() : -1;
	}

	private static Session onOwner() {
		if (!ENABLED) { return null; }
		Session s = session;
		return s != null && Thread.currentThread() == s.owner && !s.finished ? s : null;
	}

	private static Move active(Entity entity) {
		Session s = onOwner();
		if (s == null || s.current == null || s.depth == 0 || s.overflowDepth != 0) { return null; }
		Move m = s.moves[s.depth - 1];
		return m.entity == entity ? m : null;
	}

	private static void record(Move m, Metric metric, long value) {
		int i = metric.ordinal();
		m.phase.total[i] += value;
		m.phase.byClass[m.classSlot][i] += value;
	}

	private static void maximum(Move m, Metric metric, long value) {
		int i = metric.ordinal();
		m.phase.total[i] = Math.max(m.phase.total[i], value);
		m.phase.byClass[m.classSlot][i] = Math.max(m.phase.byClass[m.classSlot][i], value);
	}

	/** Call between moves on the owner thread. Creates only this new diagnostic file; refuses overwrite. */
	public static void finish(Path directory) throws IOException {
		if (!ENABLED || session == null) { return; }
		Session s = session;
		s.checkThread();
		s.closeScopes();
		s.current = null;
		s.finished = true;
		JsonWriter out = new JsonWriter().beginObject().field("schema_version", 1)
			.field("attribution_only", true).field("enabled", ENABLED)
			.field("phase_limit", PHASE_LIMIT).field("class_limit", CLASS_LIMIT).field("move_depth_limit", MOVE_DEPTH_LIMIT)
			.field("excluded_particle_moves_seen_on_owner", s.excludedMoves)
			.field("excluded_warmup_activations", s.excludedWarmupActivations)
			.field("dropped_phase_activations", s.droppedPhaseActivations).field("move_depth_overflows", s.moveDepthOverflows)
			.field("incomplete_moves_at_boundary", s.incompleteMoves).field("unclosed_queries", s.unclosedQueries)
			.field("unmatched_move_returns", s.unmatchedMoveReturns).field("unmatched_query_returns", s.unmatchedQueryReturns)
			.field("scope", "Captured Entity.move(DDD)V particle invocations on phase owner; World.getCollisions body only")
			.field("caveats", "Entry offsets precede cobweb scaling. Positive stepHeight is eligibility only; step_query_returns "
				+ "observes the step call site returning, not step success. Axis scan sums are structural 3*N/7*N estimates "
				+ "for normally returned moves with known lists, not measured clip counts. Unknown lists are not queried. "
				+ "Times are instrumented host wall, not CPU. Rest includes clipping, callbacks, fire checks and probes. "
				+ "Nested moves have inclusive overlapping times; nested same-move gather clocks use their union. "
				+ "HEAD/RETURN cannot unwind exceptions; boundary cleanup reports incomplete scopes. Reject anomalous captures. "
				+ "Hooks before first phase(null) are ignored, not included in excluded count. No allocation/byte counts measured.")
			.beginObject("phases");
		for (int i = 0; i < s.phaseCount; i++) {
			Phase p = s.phases[i];
			out.beginObject(p.name);
			writeMetrics(out, "total", p.total);
			out.beginObject("classes");
			for (int c = 0; c <= CLASS_LIMIT; c++) {
				if (p.byClass[c][Metric.MOVES_STARTED.ordinal()] == 0) { continue; }
				// Slot keys avoid conflating distinct Class identities with the same binary name.
				out.beginObject(Integer.toString(c)).field("class", c == CLASS_LIMIT ? "<overflow>" : s.classes[c].getName());
				writeMetrics(out, "metrics", p.byClass[c]);
				out.endObject();
			}
			out.endObject().endObject();
		}
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("particle-movement-census.json"), out.endObject().endObject() + "\n",
			StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	private static void writeMetrics(JsonWriter out, String name, long[] values) {
		out.beginObject(name);
		for (Metric m : Metric.values()) { out.field(m.name().toLowerCase(Locale.ROOT), values[m.ordinal()]); }
		out.endObject();
	}

	private enum Metric {
		MOVES_STARTED, MOVES_RETURNED, DX_NONZERO, DY_NONZERO, DZ_NONZERO, ZERO_VECTOR, NONFINITE_INPUT,
		NO_CLIP_AT_ENTRY, POSITIVE_STEP_HEIGHT_AT_ENTRY, NESTED_MOVES,
		WORLD_QUERY_CALLS, WORLD_QUERY_RETURNS, WORLD_RESULT_SIZE_SUM, EMPTY_WORLD_RESULTS, MAX_WORLD_RESULT_SIZE,
		UNKNOWN_WORLD_LIST_RESULTS, ORDINARY_QUERY_RETURNS, ORDINARY_RESULT_SIZE_SUM,
		STEP_QUERY_RETURNS, STEP_RESULT_SIZE_SUM, UNKNOWN_SITE_LIST_RESULTS,
		SCAN_ESTIMATE_COMPLETE_MOVES, ORDINARY_AXIS_SCAN_LENGTH_SUM_EXPECTED, STEP_AXIS_SCAN_LENGTH_SUM_EXPECTED,
		TIMED_MOVES, MOVE_WALL_NS, COLLISION_GATHER_WALL_NS, REST_MOVE_WALL_NS
	}

	private static final class Phase {
		final String name;
		final long[] total = new long[Metric.values().length];
		final long[][] byClass = new long[CLASS_LIMIT + 1][Metric.values().length];
		Phase(String name) { this.name = name; }
	}

	private static final class Move {
		Entity entity;
		Phase phase;
		int classSlot, queryDepth;
		long startNs, queryStartNs, gatherNs, ordinaryLength, stepLength;
		boolean sitesKnown;
		void clear() { entity = null; phase = null; queryDepth = 0; }
	}

	private static final class Session {
		final Thread owner = Thread.currentThread();
		final Phase[] phases = new Phase[PHASE_LIMIT];
		final Class<?>[] classes = new Class<?>[CLASS_LIMIT];
		final Move[] moves = new Move[MOVE_DEPTH_LIMIT];
		int phaseCount, classCount, depth, overflowDepth;
		long excludedMoves, excludedWarmupActivations, droppedPhaseActivations, moveDepthOverflows, incompleteMoves, unclosedQueries;
		long unmatchedMoveReturns, unmatchedQueryReturns;
		Phase current;
		boolean finished;
		Session() { for (int i = 0; i < moves.length; i++) { moves[i] = new Move(); } }
		void checkThread() {
			if (Thread.currentThread() != owner) { throw new IllegalStateException("Movement census owner thread required"); }
		}
		int classSlot(Class<?> type) {
			for (int i = 0; i < classCount; i++) { if (classes[i] == type) { return i; } }
			if (classCount == CLASS_LIMIT) { return CLASS_LIMIT; }
			classes[classCount] = type;
			return classCount++;
		}
		void closeScopes() {
			incompleteMoves += depth + (long) overflowDepth;
			for (int i = 0; i < depth; i++) { unclosedQueries += moves[i].queryDepth; moves[i].clear(); }
			depth = 0;
			overflowDepth = 0;
		}
	}
}
