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
import net.minecraft.util.math.MathHelper;

/** Staged, diagnostic-only census. No world/shape/list writes and no per-element hooks. */
public final class ParticleMovementCensus {

	public static final boolean ENABLED = flag("profiler.particleMovementCensus", false)
		&& flag("benchmark", false) && flag("profiler.enabled", true);
	/** Session 25: region boundaries and structural scan counts; requires the census gates. */
	public static final boolean REGIONS = ENABLED && flag("profiler.particleMovementRegions", false);
	private static final int PHASE_LIMIT = 16, CLASS_LIMIT = 64, MOVE_DEPTH_LIMIT = 16;
	/** Census clocks plus the six region marks taken per ordinary (non-noClip) move. */
	private static final int REGION_CLOCKS_PER_MOVE = 10;
	private static volatile Session session;
	private static final Metric[] REGION_METRICS = {Metric.REGION_PRE_NS, Metric.REGION_GATHER_BLOCKS_NS,
		Metric.REGION_GATHER_ENTITIES_NS, Metric.REGION_CLIP_PUBLISH_NS, Metric.REGION_UNDER_FLAGS_NS,
		Metric.REGION_BLOCK_COLLISIONS_NS, Metric.REGION_WATER_RAIN_NS, Metric.REGION_FIRE_NS, Metric.REGION_TAIL_NS};

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
		m.noClipEntry = entity.noClip;
		m.startNs = System.nanoTime();
		if (REGIONS) { m.beginRegions(m.startNs); }
	}

	public static void endMove(Entity entity) {
		Session s = onOwner();
		if (s == null || !(entity instanceof Particle) || s.current == null) { return; }
		if (s.overflowDepth != 0) { s.overflowDepth--; return; }
		Move m = active(entity);
		if (m == null) { s.unmatchedMoveReturns++; return; }
		long endNs = System.nanoTime();
		long elapsed = endNs - m.startNs;
		record(m, Metric.MOVES_RETURNED, 1);
		if (REGIONS) { finishRegions(m, endNs); }
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
		if (m.queryDepth++ == 0) {
			m.queryStartNs = System.nanoTime();
			// Only the move's first (ordinary) query opens the gather regions; a step query stays in clip/publish.
			if (REGIONS && m.region == Region.PRE) { m.mark(Region.PRE, Region.GATHER_BLOCKS, m.queryStartNs); }
		}
	}

	/** RETURN only: no replacement, iteration or retention of the result. Nested gather clocks use their union. */
	public static void endCollision(Entity entity, List<Box> result) {
		Move m = active(entity);
		if (m == null) { return; }
		if (m.queryDepth == 0) { session.unmatchedQueryReturns++; return; }
		long endNs = System.nanoTime();
		if (--m.queryDepth == 0) {
			m.gatherNs += endNs - m.queryStartNs;
			if (REGIONS && m.region == Region.GATHER_ENTITIES) {
				m.mark(Region.GATHER_ENTITIES, Region.CLIP_PUBLISH, endNs);
				int size = knownSize(result);
				if (size >= 0 && m.blockBoxes >= 0) { record(m, Metric.GATHER_ENTITY_BOXES, size - m.blockBoxes); }
			}
		}
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

	// Session 25 region hooks. Each reads arguments, shapes or returned sizes only: no world, shape or list writes,
	// no element iteration and no extra virtual calls into the entity or world. Callers return vanilla's values.

	/** getCollisions HEAD, before or after beginCollision: structural scan sizes of the move's first query only. */
	public static void regionGatherShape(Entity entity, Box shape) {
		Move m = REGIONS ? active(entity) : null;
		if (m == null || m.gatherCounted || (m.region != Region.PRE && m.region != Region.GATHER_BLOCKS)) { return; }
		m.gatherCounted = true;
		record(m, Metric.GATHER_COLUMNS_STRUCTURAL, gatherColumns(shape.minX, shape.maxX, shape.minZ, shape.maxZ));
		record(m, Metric.GATHER_CELLS_STRUCTURAL, gatherColumns(shape.minX, shape.maxX, shape.minZ, shape.maxZ)
			* gatherLayers(shape.minY, shape.maxY, false));
		record(m, Metric.GATHER_IN_HEIGHT_CELLS_STRUCTURAL, gatherColumns(shape.minX, shape.maxX, shape.minZ, shape.maxZ)
			* gatherLayers(shape.minY, shape.maxY, true));
		double g = 0.25;
		record(m, Metric.ENTITY_QUERY_CHUNKS_STRUCTURAL, entityQueryChunks(shape.minX - g, shape.maxX + g, shape.minZ - g, shape.maxZ + g));
		record(m, Metric.ENTITY_QUERY_SECTIONS_STRUCTURAL, entityQueryChunks(shape.minX - g, shape.maxX + g, shape.minZ - g, shape.maxZ + g)
			* entityQuerySections(shape.minY - g, shape.maxY + g));
	}

	/** Before getCollisions computes the grown entity-query bounds: ends block gathering, reads the list's size. */
	public static void regionEntityQuery(Entity entity, List<Box> blockPart) {
		Move m = REGIONS ? active(entity) : null;
		if (m == null || m.region != Region.GATHER_BLOCKS || m.queryDepth != 1) { return; }
		m.mark(Region.GATHER_BLOCKS, Region.GATHER_ENTITIES, System.nanoTime());
		m.blockBoxes = knownSize(blockPart);
		if (m.blockBoxes >= 0) { record(m, Metric.GATHER_BLOCK_BOXES, m.blockBoxes); }
	}

	/** getCollisions' getEntities result, returned unchanged by the caller. */
	public static void regionEntityResults(Entity entity, List<Entity> found) {
		Move m = REGIONS ? active(entity) : null;
		if (m == null || m.region != Region.GATHER_ENTITIES || m.queryDepth != 1) { return; }
		int size = knownSize(found);
		record(m, size >= 0 ? Metric.ENTITY_QUERY_RESULTS : Metric.UNKNOWN_ENTITY_QUERY_LISTS, size >= 0 ? size : 1);
	}

	/** Entity.move's second setPositionFromShape call: the clip/publish region (and any step trial) has ended. */
	public static void regionRest(Entity entity) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) { m.mark(Region.CLIP_PUBLISH, Region.UNDER_FLAGS, System.nanoTime()); }
	}

	public static void regionSecondUnderRead(Entity entity) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) { record(m, Metric.UNDER_BLOCK_SECOND_READS, 1); }
	}

	public static void regionVerticalCallback(Entity entity) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) { record(m, Metric.VERTICAL_COLLISION_CALLBACKS, 1); }
	}

	/** Before checkBlockCollisions. {@code shape} is the entity's current shape field, read without a getter call. */
	public static void regionBlockCollisionsBegin(Entity entity, Box shape) {
		Move m = REGIONS ? active(entity) : null;
		if (m == null) { return; }
		m.mark(Region.UNDER_FLAGS, Region.BLOCK_COLLISIONS, System.nanoTime());
		record(m, Metric.BLOCK_COLLISION_CELLS_STRUCTURAL, blockCollisionCells(shape.minX, shape.minY, shape.minZ,
			shape.maxX, shape.maxY, shape.maxZ));
	}

	public static void regionBlockCollisionsEnd(Entity entity) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) { m.mark(Region.BLOCK_COLLISIONS, Region.WATER_RAIN, System.nanoTime()); }
	}

	public static void regionWaterEnd(Entity entity) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) { m.mark(Region.WATER_RAIN, Region.FIRE, System.nanoTime()); }
	}

	/** containsFireSource's argument, passed on unchanged by the caller. */
	public static void regionFireBounds(Entity entity, Box bounds) {
		Move m = REGIONS ? active(entity) : null;
		if (m != null) {
			record(m, Metric.FIRE_CELLS_STRUCTURAL, fireCells(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ));
		}
	}

	/** containsFireSource's result, returned unchanged by the caller. */
	public static void regionFireResult(Entity entity, boolean found) {
		Move m = REGIONS ? active(entity) : null;
		if (m == null) { return; }
		m.mark(Region.FIRE, Region.TAIL, System.nanoTime());
		record(m, Metric.FIRE_SOURCES_FOUND, found ? 1 : 0);
	}

	private static void finishRegions(Move m, long endNs) {
		if (m.regionAnomaly) {
			record(m, Metric.REGION_ORDER_ANOMALIES, 1);
		} else if (m.region == Region.TAIL) {
			m.regionNs[Region.TAIL.ordinal()] += endNs - m.regionMarkNs;
			record(m, Metric.REGION_MOVES, 1);
			for (int r = 0; r < REGION_METRICS.length; r++) { record(m, REGION_METRICS[r], m.regionNs[r]); }
		} else if (m.region == Region.PRE && m.noClipEntry && !m.gatherCounted) {
			record(m, Metric.REGION_NO_CLIP_MOVES, 1);
			record(m, Metric.REGION_NO_CLIP_NS, endNs - m.regionMarkNs);
		} else {
			record(m, Metric.REGION_ORDER_ANOMALIES, 1);
		}
	}

	// Structural scan sizes. They reproduce the loop bounds of World.getCollisions, World.getEntities,
	// Entity.checkBlockCollisions and World.containsFireSource for the given bounds, assuming the client's
	// chunk source reports every column loaded (ClientChunkCache.hasChunk is constant true). Not observed counts.

	/** getCollisions' x/z columns that pass World.contains at y = 64. */
	public static long gatherColumns(double minX, double maxX, double minZ, double maxZ) {
		return rangeWithin(MathHelper.floor(minX), MathHelper.floor(maxX + 1.0), -30000000, 30000000)
			* rangeWithin(MathHelper.floor(minZ), MathHelper.floor(maxZ + 1.0), -30000000, 30000000);
	}

	/** getCollisions' y loop per column (from floor(minY) - 1); {@code inHeight} counts the reads reaching a chunk. */
	public static long gatherLayers(double minY, double maxY, boolean inHeight) {
		long lo = (long) MathHelper.floor(minY) - 1, hi = MathHelper.floor(maxY + 1.0);
		return inHeight ? rangeWithin(lo, hi, 0, 256) : Math.max(0, hi - lo);
	}

	/** World.getEntities' chunk columns for already grown bounds (both ends inclusive). */
	public static long entityQueryChunks(double minX, double maxX, double minZ, double maxZ) {
		long x = (long) MathHelper.floor((maxX + 2.0) / 16.0) - MathHelper.floor((minX - 2.0) / 16.0) + 1;
		long z = (long) MathHelper.floor((maxZ + 2.0) / 16.0) - MathHelper.floor((minZ - 2.0) / 16.0) + 1;
		return Math.max(0, x) * Math.max(0, z);
	}

	/** WorldChunk.getEntities' clamped section range for already grown bounds. */
	public static long entityQuerySections(double minY, double maxY) {
		int lo = MathHelper.clamp(MathHelper.floor((minY - 2.0) / 16.0), 0, 15);
		int hi = MathHelper.clamp(MathHelper.floor((maxY + 2.0) / 16.0), 0, 15);
		return Math.max(0, hi - lo + 1);
	}

	/** Entity.checkBlockCollisions' cells: 0.001 inset, inclusive BlockPos bounds, isAreaLoaded's height test. */
	public static long blockCollisionCells(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		int x0 = MathHelper.floor(minX + 0.001), y0 = MathHelper.floor(minY + 0.001), z0 = MathHelper.floor(minZ + 0.001);
		int x1 = MathHelper.floor(maxX - 0.001), y1 = MathHelper.floor(maxY - 0.001), z1 = MathHelper.floor(maxZ - 0.001);
		if (y1 < 0 || y0 >= 256) { return 0; }
		return Math.max(0, (long) x1 - x0 + 1) * Math.max(0, (long) y1 - y0 + 1) * Math.max(0, (long) z1 - z0 + 1);
	}

	/** World.containsFireSource's cells for the bounds it receives, after its isAreaLoaded height test. */
	public static long fireCells(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		int i = MathHelper.floor(minX), j = MathHelper.floor(maxX + 1.0), k = MathHelper.floor(minY);
		int l = MathHelper.floor(maxY + 1.0), m = MathHelper.floor(minZ), n = MathHelper.floor(maxZ + 1.0);
		if (l < 0 || k >= 256) { return 0; }
		return Math.max(0, (long) j - i) * Math.max(0, (long) l - k) * Math.max(0, (long) n - m);
	}

	private static long rangeWithin(long lo, long hi, long min, long max) {
		return Math.max(0, Math.min(hi, max) - Math.max(lo, min));
	}

	private static int knownSize(List<?> result) {
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
				+ "Hooks before first phase(null) are ignored, not included in excluded count. No allocation/byte counts measured.");
		if (REGIONS) {
			// Measured after capture closes, between moves: the mean cost of one System.nanoTime call on this thread.
			long calls = 2_000_000, sink = 0, t0 = System.nanoTime();
			for (long i = 0; i < calls; i++) { sink += System.nanoTime(); }
			double clockNs = (System.nanoTime() - t0) / (double) calls;
			out.field("regions_enabled", true).field("regions_schema", 2)
				.field("region_clock_calls_per_region_move", REGION_CLOCKS_PER_MOVE)
				.field("clock_call_mean_ns_after_capture", clockNs).field("clock_calibration_sink_parity", sink & 1)
				.field("region_caveats", "Region times are instrumented host wall between consecutive marks and include the "
					+ "marks' own clock costs; the hooks change compiled code of Entity.move and World.getCollisions. "
					+ "Regions: pre (entry to first gather), gather_blocks (to the entity-query bounds), gather_entities (to "
					+ "the query's return), clip_publish (axis clipping, the three setShape publications, any step trial), "
					+ "under_flags (setPositionFromShape, flags, block-under reads, fall damage, velocity, vertical callback, "
					+ "makesSteps), block_collisions (checkBlockCollisions), water_rain (isInWaterOrRain), fire (contract and "
					+ "containsFireSource), tail (to return). Structural counts reproduce vanilla loop bounds assuming every "
					+ "column is loaded (client hasChunk true); they are not observed per-cell calls.");
		}
		out.beginObject("phases");
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
		// Without regions the output is session 24's metric set, unchanged.
		for (Metric m : Metric.values()) {
			if (!REGIONS && m.ordinal() >= Metric.REGION_MOVES.ordinal()) { break; }
			out.field(m.name().toLowerCase(Locale.ROOT), values[m.ordinal()]);
		}
		out.endObject();
	}

	private enum Metric {
		MOVES_STARTED, MOVES_RETURNED, DX_NONZERO, DY_NONZERO, DZ_NONZERO, ZERO_VECTOR, NONFINITE_INPUT,
		NO_CLIP_AT_ENTRY, POSITIVE_STEP_HEIGHT_AT_ENTRY, NESTED_MOVES,
		WORLD_QUERY_CALLS, WORLD_QUERY_RETURNS, WORLD_RESULT_SIZE_SUM, EMPTY_WORLD_RESULTS, MAX_WORLD_RESULT_SIZE,
		UNKNOWN_WORLD_LIST_RESULTS, ORDINARY_QUERY_RETURNS, ORDINARY_RESULT_SIZE_SUM,
		STEP_QUERY_RETURNS, STEP_RESULT_SIZE_SUM, UNKNOWN_SITE_LIST_RESULTS,
		SCAN_ESTIMATE_COMPLETE_MOVES, ORDINARY_AXIS_SCAN_LENGTH_SUM_EXPECTED, STEP_AXIS_SCAN_LENGTH_SUM_EXPECTED,
		TIMED_MOVES, MOVE_WALL_NS, COLLISION_GATHER_WALL_NS, REST_MOVE_WALL_NS,
		// Session 25 regions (written only when REGIONS). REGION_METRICS lists the *_NS entries in Region order.
		REGION_MOVES, REGION_ORDER_ANOMALIES, REGION_NO_CLIP_MOVES, REGION_NO_CLIP_NS,
		REGION_PRE_NS, REGION_GATHER_BLOCKS_NS, REGION_GATHER_ENTITIES_NS, REGION_CLIP_PUBLISH_NS, REGION_UNDER_FLAGS_NS,
		REGION_BLOCK_COLLISIONS_NS, REGION_WATER_RAIN_NS, REGION_FIRE_NS, REGION_TAIL_NS,
		GATHER_COLUMNS_STRUCTURAL, GATHER_CELLS_STRUCTURAL, GATHER_IN_HEIGHT_CELLS_STRUCTURAL, GATHER_BLOCK_BOXES,
		GATHER_ENTITY_BOXES, ENTITY_QUERY_CHUNKS_STRUCTURAL, ENTITY_QUERY_SECTIONS_STRUCTURAL, ENTITY_QUERY_RESULTS,
		UNKNOWN_ENTITY_QUERY_LISTS, UNDER_BLOCK_SECOND_READS, VERTICAL_COLLISION_CALLBACKS,
		BLOCK_COLLISION_CELLS_STRUCTURAL, FIRE_CELLS_STRUCTURAL, FIRE_SOURCES_FOUND
	}

	/** Consecutive Entity.move regions between session 25's marks, in execution order. */
	private enum Region { PRE, GATHER_BLOCKS, GATHER_ENTITIES, CLIP_PUBLISH, UNDER_FLAGS, BLOCK_COLLISIONS, WATER_RAIN, FIRE, TAIL }

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
		// Session 25 regions.
		final long[] regionNs = new long[Region.values().length];
		Region region = Region.PRE;
		long regionMarkNs;
		int blockBoxes;
		boolean noClipEntry, gatherCounted, regionAnomaly;
		void clear() { entity = null; phase = null; queryDepth = 0; }
		void beginRegions(long now) {
			java.util.Arrays.fill(regionNs, 0);
			region = Region.PRE;
			regionMarkNs = now;
			blockBoxes = -1;
			gatherCounted = false;
			regionAnomaly = false;
		}
		/** Out-of-order marks flag the move; it is then counted as an anomaly, not timed. */
		void mark(Region expected, Region next, long now) {
			if (regionAnomaly || region != expected) { regionAnomaly = true; return; }
			regionNs[region.ordinal()] += now - regionMarkNs;
			region = next;
			regionMarkNs = now;
		}
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
