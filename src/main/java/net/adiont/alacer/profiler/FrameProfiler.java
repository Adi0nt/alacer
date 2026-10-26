package net.adiont.alacer.profiler;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * Records per-frame timings on the client (render) thread.
 *
 * <p>A frame is the interval between two consecutive starts of {@code Minecraft.runGame()},
 * so it includes ticking, rendering, the buffer swap and any frame-rate limiter wait. Within
 * a frame, a fixed set of {@link Section}s is timed with {@link System#nanoTime()} and a fixed
 * set of {@link Counter}s counts operations.
 *
 * <p>All methods must be called from the client thread. Recording a frame performs no
 * allocation; the cost is a handful of {@code nanoTime()} calls plus one per-thread
 * allocation counter read.
 */
public final class FrameProfiler {

	/**
	 * Timed regions within a frame, in nanoseconds. Values are summed if a region runs more
	 * than once per frame. Sections nest (for example the entity sections are inside
	 * {@link #RENDER}), so they must not be added together.
	 */
	public enum Section {
		/** All client ticks run during the frame. */
		TICK,
		/** {@code GameRenderer.render}: world and GUI rendering, CPU side. */
		RENDER,
		/** {@code WorldRenderer.setupRender}: visibility search plus rebuild scheduling. */
		TERRAIN_SETUP,
		/** Only the chunk visibility search inside terrain setup (vanilla's or Alacer's). */
		VISIBILITY,
		/** {@code WorldRenderer.compileChunksUntil}: uploading finished chunk builds. */
		CHUNK_UPLOAD,
		/** All of {@code WorldRenderer.renderEntities}. */
		ENTITIES,
		/** Entity phase: dispatcher preparation before any entity is drawn. */
		ENTITY_PREPARE,
		/** Entity phase: global entities (lightning). */
		ENTITY_GLOBAL,
		/** Entity phase: spectator outline pass (only while it is active). */
		ENTITY_OUTLINE,
		/** Entity phase: the per-visible-chunk entity loop, including everything below. */
		ENTITY_LOOP,
		/** Detail: world-chunk lookups inside the entity loop. */
		ENTITY_CHUNK_LOOKUP,
		/** Detail: {@code EntityRenderDispatcher.shouldRender} (distance and frustum). */
		ENTITY_VISIBILITY_CHECK,
		/** Detail: {@code EntityRenderDispatcher.render} for entities in the loop. */
		ENTITY_DRAW,
		/** Entity phase: block entities, global block entities and mining progress. */
		BLOCK_ENTITY_LOOP,
		/** Detail: drawing block entities from the visible chunks' lists. */
		BLOCK_ENTITY_DRAW,
		/** {@code Display.update}: buffer swap; long values indicate waiting on the GPU or vsync. */
		SWAP,
		/** Vanilla's two per-frame {@code glGetError} checks ("Pre render", "Post render"). */
		GL_ERROR_CHECK,
		/** {@code Camera.setup} in the world render: reads the modelview, projection and viewport. */
		CAMERA_SETUP,
		/** The world render's {@code Frustum.getInstance()}: reads the projection and modelview. */
		FRUSTUM,
		/** {@code new FrustumCuller()} in the world render, which reads the frustum a second time. */
		FRUSTUM_CULLER,
		/** {@code RenderChunk.glTransform} (on every chunk reposition): one modelview read each. */
		CHUNK_MATRIX,
		/** {@code GameRenderer.renderWorld}: {@link #RENDER} without mouse input, post-processing and the GUI. */
		WORLD,
		/** World render: {@code GameRenderer.updateLightMap} (recomputes and uploads the lightmap when it changed). */
		LIGHTMAP,
		/** World render: {@code GameRenderer.pick}, the crosshair target ray trace. */
		PICK,
		/** {@code WorldRenderer.renderSky}. */
		SKY,
		/** {@code WorldRenderer.renderClouds}. */
		CLOUDS,
		/** {@code WorldRenderer.render} for the solid terrain layer (empty-chunk filter and draw). */
		TERRAIN_SOLID,
		/** The same for the cutout-mipped layer (leaves, grass). */
		TERRAIN_CUTOUT_MIPPED,
		/** The same for the cutout layer. */
		TERRAIN_CUTOUT,
		/** The same for the translucent layer, including resorting chunks after the camera moved a block. */
		TERRAIN_TRANSLUCENT,
		/** {@code ParticleManager.renderLit} and {@code render}. */
		PARTICLES,
		/** {@code GameRenderer.renderSnowAndRain} (returns at once in clear weather). */
		WEATHER,
		/** {@code GameRenderer.renderItemInHand}: the first-person hand or held item and screen overlays. */
		HAND,
		/** {@code GameGui.render}: the in-game HUD, without Alacer's own overlay. */
		GUI,
		/** Detail: LivingEntityRenderer entry through transforms, before Model.prepare. */
		ENTITY_LIVING_SETUP,
		/** Detail: Model.prepare in the primary living-entity draw. */
		ENTITY_MODEL_PREPARE,
		/** Detail: setupAnimation at the living renderer and quadruped/wolf model call sites. */
		ENTITY_MODEL_ANIMATION,
		/** Detail: primary renderModel (texture/visibility setup plus Model.render). */
		ENTITY_RENDER_MODEL,
		/** Detail: primary Model.render, including its repeated animation setup. */
		ENTITY_MODEL_DRAW,
		/** Detail: renderLayers, including overlay state and layer model draws. */
		ENTITY_LAYERS,
		/** Detail: EntityRenderer.renderShadow (world queries, geometry and upload). */
		ENTITY_SHADOW,
		/** Detail: renderShadowOnBlock, within the shadow section. */
		ENTITY_SHADOW_BLOCK,
		/** Detail: virtual name-tag call, including eligibility checks. */
		ENTITY_NAME_TAG,
		/** Detail: living-entity overlay setup/teardown, including layers. */
		ENTITY_OVERLAY,
		/** Detail: shadow block-state, raw-brightness and brightness queries (nested). */
		ENTITY_SHADOW_QUERY,
		/** Optional coarse timer ({@code profiler.shadowTiming}): all of {@code EntityRenderer.renderShadow}. */
		SHADOWS,
		/**
		 * With {@code profiler.frustumTiming}: the visibility search's frustum tests repeated through
		 * the vanilla culler after the search, outside {@link #VISIBILITY}.
		 */
		FRUSTUM_REPLAY_VANILLA,
		/** The same boxes repeated through {@code render.fastChunkFrustum}'s test. */
		FRUSTUM_REPLAY_PLANES,
		/** Chunk detail: rebuildSync, nested inside terrain setup; includes build and GL upload. */
		CHUNK_SYNC,
		/** Chunk detail: runTasksUntil, inside chunk_upload; GL uploads and future callbacks. */
		CHUNK_DRAIN,
		/** Chunk detail: uploadGlList, including glNewList, buffer draw and glEndList. Client only. */
		CHUNK_GL_LIST,
		/** Chunk detail: uploadBuffer (VBO path). Client only. */
		CHUNK_VBO,
		/** Chunk detail: rebuildAsync (task creation, locks, enqueue). Client only. */
		CHUNK_ENQUEUE,
		/** Chunk detail: RenderChunk.compile on client, inside chunk_sync; excludes upload. */
		CHUNK_BUILD_CLIENT,
		/** Chunk detail: RenderChunk.rebuildTranslucent on client. */
		CHUNK_RESORT_CLIENT,
		/** Chunk detail: sum of worker compile wall times COMPLETED during frame, not client work. */
		CHUNK_BUILD_WORKER,
		/** Chunk detail: sum of worker transparency-sort wall times completed during frame. */
		CHUNK_RESORT_WORKER,
		/** Chunk detail: worker waits for a shared buffer, summed at completion; not CPU time. */
		CHUNK_BUFFER_WAIT,
		/** Chunk detail: task construction to COMPILING (queue/scheduling/locks), summed at start. */
		CHUNK_QUEUE_WAIT,
		/** Chunk detail: UPLOADING to successful DONE, summed at completion; not client work. */
		CHUNK_UPLOAD_LATENCY,
		/** Chunk detail: sum of observed dirty-set ages at frame boundary, not work time. */
		CHUNK_DIRTY_AGE,
		/** Chunk detail: maximum observed dirty-set age at frame boundary, not work time. */
		CHUNK_DIRTY_AGE_MAX,
		/** Chunk detail: observed dirty-set age of accepted async rebuilds (lower bound). */
		CHUNK_SCHEDULE_WAIT,
		/** Chunk detail: terrain backend submission, nested within all four terrain layers. No GPU timer. */
		TERRAIN_SUBMIT,
		/** {@code ParticleManager.tick}, inside {@link #TICK}: particle physics, removal and emitters. */
		PARTICLE_TICK,
		/** {@code Tesselator.end} calls in {@code ParticleManager.render}, inside {@link #PARTICLES}: vertex upload and draw. */
		PARTICLE_DRAW,
		/** Benchmark particle scenario: emitting its fixed particle schedule, inside {@link #TICK}. */
		PARTICLE_SPAWN,
		/** Particle detail: individual {@code Particle.tick} calls, inside {@link #PARTICLE_TICK}. */
		PARTICLE_UPDATE,
		/** Particle detail: {@code Entity.move} called by particles, inside {@link #PARTICLE_UPDATE}. */
		PARTICLE_MOVE,
		/** Particle detail: the {@code removeAll} of dead particles, inside {@link #PARTICLE_TICK}. */
		PARTICLE_REMOVE,
		/** Particle detail: {@code Entity.getLightLevel} for particles while drawing, inside {@link #PARTICLES}. */
		PARTICLE_LIGHT,
		/**
		 * {@code profiler.chunkLatency}: waits of the rebuilds published during the frame, each from the frame its chunk
		 * was first eligible (dirty and visible) to publication, summed at completion. Not work time.
		 */
		CHUNK_UPDATE_LATENCY,
		/** {@code profiler.chunkLatency}: the longest of those waits. */
		CHUNK_UPDATE_LATENCY_MAX
	}

	/** Per-frame operation counts. */
	public enum Counter {
		/** Chunk detail: per-layer chunks submitted (not unique chunks or GPU draw calls). */
		TERRAIN_SOLID_CHUNKS, TERRAIN_CUTOUT_MIPPED_CHUNKS, TERRAIN_CUTOUT_CHUNKS, TERRAIN_TRANSLUCENT_CHUNKS,
		/** Visibility searches run (0 or 1 per frame normally). */
		VISIBILITY_SEARCHES,
		/** Detail: world-chunk lookups in the entity loop. */
		ENTITY_CHUNK_LOOKUPS,
		/** Detail: lookups answered from the per-render-chunk cache. */
		ENTITY_CHUNK_CACHE_HITS,
		/** Detail: entity sections that contained at least one entity. */
		ENTITY_SECTIONS_OCCUPIED,
		/** Detail: entities passed to the visibility check. */
		ENTITIES_CHECKED,
		/** Detail: entities drawn by the entity loop. */
		ENTITIES_DRAWN,
		/** Detail: block entities drawn from the visible chunks' lists. */
		BLOCK_ENTITIES_DRAWN,
		/** {@code RenderChunk.glTransform} calls (render chunk repositions). */
		CHUNK_MATRIX_READS,
		/** Detail: OpenGL calls made while drawing entities in the entity loop (all kinds below). */
		GL_CALLS,
		/** Detail: matrix operations in entity draws (push, pop, translate, rotate, scale, ...). */
		GL_MATRIX_OPS,
		/** Detail: translations by (0, 0, 0) among them. */
		GL_ZERO_TRANSLATES,
		/** Detail: display lists called in entity draws (model parts). */
		GL_CALL_LISTS,
		/** Detail: {@code GlStateManager} state setter calls in entity draws, deduplicated or not. */
		GL_STATE_REQUESTS,
		/** Detail: those that reached OpenGL because the cached value differed. */
		GL_STATE_CHANGES,
		/** Detail: lightmap coordinates set per entity ({@code GLX.multiTexCoord2f}). */
		GL_LIGHTMAP_SETS,
		/** Detail: immediate-mode draws ({@code Tesselator.end}) in entity draws, such as shadows. */
		GL_IMMEDIATE_DRAWS,
		/** Rebuilds of the cached fancy cloud geometry ({@code render.cacheClouds}). */
		CLOUD_REBUILDS,
		/** With {@code profiler.shadowTiming}: {@code renderShadow} calls (entities drawing a shadow). */
		SHADOWS_DRAWN,
		/** Frustum tests (chunk boxes) in Alacer's visibility search. */
		FRUSTUM_TESTS,
		CHUNK_SYNC_CALLS, CHUNK_DRAIN_CALLS, CHUNK_GL_LIST_CALLS, CHUNK_VBO_CALLS,
		CHUNK_ENQUEUE_CALLS, CHUNK_BUILD_CLIENT_CALLS, CHUNK_RESORT_CLIENT_CALLS,
		CHUNK_BUILD_WORKER_CALLS, CHUNK_RESORT_WORKER_CALLS, CHUNK_BUFFER_WAITS,
		CHUNK_TASK_STARTS, CHUNK_TASK_COMPLETIONS,
		/** Chunk detail: separate end-of-frame queue gauges; not unique chunks or in-flight builds. */
		CHUNK_DIRTY, CHUNK_QUEUED, CHUNK_READY_UPLOADS, CHUNK_AVAILABLE_BUFFERS,
		CHUNK_EXPIRED_DEADLINES, CHUNK_ENQUEUE_ACCEPTED, CHUNK_OBSERVED_SCHEDULES,
		/** Particles in the manager's lists when it ticks (all atlas and alpha layers), summed over the frame's ticks. */
		PARTICLES_TICKED,
		/** Particles handed to the lit and unlit render passes (list sizes at each pass start). */
		PARTICLES_RENDERED,
		/** Particles added by the benchmark particle scenario. */
		PARTICLES_SPAWNED,
		/** Particle detail: dead particles passed to {@code removeAll}. */
		PARTICLES_REMOVED,
		/** {@code profiler.particleLightRepeats}: particle light queries in the unlit render pass. */
		PARTICLE_LIGHT_QUERIES,
		/** {@code profiler.particleLightRepeats}: distinct (x, y, z, blockLight) arguments per pass, summed. */
		PARTICLE_LIGHT_KEYS,
		/** {@code profiler.particleLightRepeats}: distinct (x, y, z) positions per pass, summed. */
		PARTICLE_LIGHT_POSITIONS,
		/** {@code profiler.particleLightRepeats}: queries whose arguments equal the previous query's. */
		PARTICLE_LIGHT_SAME_AS_PREVIOUS,
		/** {@code profiler.chunkLatency}: rebuilds published during the frame that had an update waiting. */
		CHUNK_UPDATES_PUBLISHED,
		/** {@code profiler.chunkLatency}: end-of-frame gauge of render chunks with an update waiting to be published. */
		CHUNK_UPDATES_WAITING,
		/** {@code profiler.chunkLatency}: chunks in the visible set that have never been built at their position (holes). */
		CHUNK_VISIBLE_UNCOMPILED,
		/** {@code render.feedChunkWorkers}: rebuilds queued after vanilla's frame deadline stopped its loop. */
		CHUNK_FED_TASKS,
		/** {@code render.feedChunkWorkers}: queued rebuilds still waiting for a worker when terrain setup cleared the queue. */
		CHUNK_UNSTARTED_TASKS
	}

	private static final Section[] SECTIONS = Section.values();
	private static final Counter[] COUNTERS = Counter.values();

	public static final int COL_FRAME = 0;
	private static final int SECTION_BASE = 1;
	public static final int COL_ALLOC_BYTES = SECTION_BASE + SECTIONS.length;
	public static final int COL_VISIBLE_CHUNKS = COL_ALLOC_BYTES + 1;
	public static final int COL_PENDING_CHUNKS = COL_ALLOC_BYTES + 2;
	private static final int COUNTER_BASE = COL_ALLOC_BYTES + 3;
	public static final int COL_START = COUNTER_BASE + COUNTERS.length;
	public static final int COLUMNS = COL_START + 1;

	public static final int COL_TICK = column(Section.TICK);
	public static final int COL_RENDER = column(Section.RENDER);
	public static final int COL_TERRAIN_SETUP = column(Section.TERRAIN_SETUP);
	public static final int COL_VISIBILITY = column(Section.VISIBILITY);
	public static final int COL_CHUNK_UPLOAD = column(Section.CHUNK_UPLOAD);
	public static final int COL_ENTITIES = column(Section.ENTITIES);
	public static final int COL_SWAP = column(Section.SWAP);

	public static final String[] COLUMN_NAMES = new String[COLUMNS];

	static {
		COLUMN_NAMES[COL_FRAME] = "frame_ns";
		for (Section s : SECTIONS) {
			COLUMN_NAMES[column(s)] = s.name().toLowerCase(Locale.ROOT) + "_ns";
		}
		COLUMN_NAMES[COL_ALLOC_BYTES] = "alloc_bytes";
		COLUMN_NAMES[COL_VISIBLE_CHUNKS] = "visible_chunks";
		COLUMN_NAMES[COL_PENDING_CHUNKS] = "pending_chunks";
		for (Counter c : COUNTERS) {
			COLUMN_NAMES[column(c)] = c.name().toLowerCase(Locale.ROOT);
		}
		COLUMN_NAMES[COL_START] = "start_ns";
	}

	public static int column(Section section) {
		return SECTION_BASE + section.ordinal();
	}

	public static int column(Counter counter) {
		return COUNTER_BASE + counter.ordinal();
	}

	/** Receives every completed frame row, e.g. to capture a benchmark phase. */
	public interface FrameListener {
		void onFrame(MetricRing frames, int row);
	}

	/** Supplies chunk counters sampled at the end of each frame. */
	public interface ChunkCounters {
		int visibleChunks();

		int pendingChunks();
	}

	private final MetricRing frames;
	private final MetricRing ticks;
	private final JvmMonitor jvm = new JvmMonitor();
	private final long[] sectionStart = new long[SECTIONS.length];
	private final long[] sectionTotal = new long[SECTIONS.length];
	private final long[] counterTotal = new long[COUNTERS.length];
	private final com.sun.management.ThreadMXBean allocationBean;
	private final long epoch = System.nanoTime();
	private final long epochWallMillis = System.currentTimeMillis();
	private final long epochWallUncertainty = System.nanoTime() - this.epoch + 1_000_000L;

	private long frameStart;
	private long frameStartAlloc;
	private long tickStart;
	private long threadId = -1;
	private Section phase;
	private FrameListener listener;
	private ChunkCounters chunkCounters;

	public FrameProfiler(int history) {
		this.frames = new MetricRing(COLUMNS, history);
		this.ticks = new MetricRing(1, Math.max(64, history / 4));
		this.allocationBean = allocationBean();
	}

	private static com.sun.management.ThreadMXBean allocationBean() {
		try {
			ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			if (bean instanceof com.sun.management.ThreadMXBean) {
				com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) bean;
				if (sun.isThreadAllocatedMemorySupported()) {
					if (!sun.isThreadAllocatedMemoryEnabled()) {
						sun.setThreadAllocatedMemoryEnabled(true);
					}
					return sun;
				}
			}
		} catch (Throwable ignored) {
			// Non-HotSpot JVMs may not expose per-thread allocation counters.
		}
		return null;
	}

	/** Called at the start of every {@code runGame()} iteration; closes the previous frame. */
	public void frameBoundary() {
		long now = System.nanoTime();
		long alloc = threadAllocatedBytes();

		if (this.frameStart != 0L) {
			int row = this.frames.append();
			long frameNs = now - this.frameStart;
			this.frames.set(row, COL_FRAME, frameNs);
			for (int i = 0; i < this.sectionTotal.length; i++) {
				this.frames.set(row, SECTION_BASE + i, this.sectionTotal[i]);
			}
			for (int i = 0; i < this.counterTotal.length; i++) {
				this.frames.set(row, COUNTER_BASE + i, this.counterTotal[i]);
			}
			long allocBytes = alloc < 0 || this.frameStartAlloc < 0 ? -1 : alloc - this.frameStartAlloc;
			this.frames.set(row, COL_ALLOC_BYTES, allocBytes);
			if (this.chunkCounters != null) {
				this.frames.set(row, COL_VISIBLE_CHUNKS, this.chunkCounters.visibleChunks());
				this.frames.set(row, COL_PENDING_CHUNKS, this.chunkCounters.pendingChunks());
			}
			this.frames.set(row, COL_START, this.frameStart - this.epoch);
			if (this.listener != null) {
				this.listener.onFrame(this.frames, row);
			}

			if (PerformanceTracker.isCollecting()) {
				long skyNs = this.sectionTotal[Section.SKY.ordinal()] + this.sectionTotal[Section.CLOUDS.ordinal()]
					+ this.sectionTotal[Section.WEATHER.ordinal()] + this.sectionTotal[Section.LIGHTMAP.ordinal()]
					+ this.sectionTotal[Section.PICK.ordinal()] + this.sectionTotal[Section.HAND.ordinal()];
				long terrainNs = this.sectionTotal[Section.TERRAIN_SETUP.ordinal()] + this.sectionTotal[Section.CHUNK_UPLOAD.ordinal()]
					+ this.sectionTotal[Section.TERRAIN_SOLID.ordinal()] + this.sectionTotal[Section.TERRAIN_CUTOUT_MIPPED.ordinal()]
					+ this.sectionTotal[Section.TERRAIN_CUTOUT.ordinal()] + this.sectionTotal[Section.TERRAIN_TRANSLUCENT.ordinal()];

				long visNs = this.sectionTotal[Section.VISIBILITY.ordinal()];
				long setupOtherNs = Math.max(0L, this.sectionTotal[Section.TERRAIN_SETUP.ordinal()] - visNs);
				long uploadNs = this.sectionTotal[Section.CHUNK_UPLOAD.ordinal()];
				long solidNs = this.sectionTotal[Section.TERRAIN_SOLID.ordinal()];
				long cutoutMippedNs = this.sectionTotal[Section.TERRAIN_CUTOUT_MIPPED.ordinal()];
				long cutoutNs = this.sectionTotal[Section.TERRAIN_CUTOUT.ordinal()];
				long translucentNs = this.sectionTotal[Section.TERRAIN_TRANSLUCENT.ordinal()];
				int visChunks = this.chunkCounters != null ? this.chunkCounters.visibleChunks() : 0;
				int pendChunks = this.chunkCounters != null ? this.chunkCounters.pendingChunks() : 0;
				int frustumTests = (int) this.counterTotal[Counter.FRUSTUM_TESTS.ordinal()];
				long entityPrep = this.sectionTotal[Section.ENTITY_PREPARE.ordinal()];
				long entityGlobal = this.sectionTotal[Section.ENTITY_GLOBAL.ordinal()];
				long entityOutline = this.sectionTotal[Section.ENTITY_OUTLINE.ordinal()];
				long entityBlock = this.sectionTotal[Section.BLOCK_ENTITY_LOOP.ordinal()];
				long entityDraw = this.sectionTotal[Section.ENTITY_DRAW.ordinal()];
				long entityVis = this.sectionTotal[Section.ENTITY_VISIBILITY_CHECK.ordinal()];
				long entityLoopRaw = this.sectionTotal[Section.ENTITY_LOOP.ordinal()];
				long entityLoopNs = Math.max(0L, entityLoopRaw - entityDraw - entityVis);

				PerformanceTracker.recordFrame(
					frameNs,
					this.sectionTotal[Section.SWAP.ordinal()],
					skyNs,
					terrainNs,
					this.sectionTotal[Section.ENTITIES.ordinal()],
					this.sectionTotal[Section.PARTICLES.ordinal()],
					this.sectionTotal[Section.TICK.ordinal()],
					this.sectionTotal[Section.GUI.ordinal()],
					allocBytes,
					setupOtherNs,
					visNs,
					uploadNs,
					solidNs,
					cutoutMippedNs,
					cutoutNs,
					translucentNs,
					visChunks,
					pendChunks,
					frustumTests,
					entityLoopNs,
					entityBlock,
					entityGlobal,
					entityDraw,
					entityVis,
					entityPrep,
					entityOutline
				);
			}
		}

		for (int i = 0; i < this.sectionTotal.length; i++) {
			this.sectionTotal[i] = 0L;
		}
		for (int i = 0; i < this.counterTotal.length; i++) {
			this.counterTotal[i] = 0L;
		}
		this.phase = null;
		this.jvm.sampleIfDue(now);
		// Start the next frame after the bookkeeping above so its cost is not charged to it twice.
		this.frameStart = now;
		this.frameStartAlloc = threadAllocatedBytes();
	}

	public void begin(Section section) {
		this.sectionStart[section.ordinal()] = System.nanoTime();
	}

	public void end(Section section) {
		int i = section.ordinal();
		this.sectionTotal[i] += System.nanoTime() - this.sectionStart[i];
	}

	/** Adds a duration measured by the caller, for timings wrapped around single calls. */
	public void add(Section section, long nanos) {
		this.sectionTotal[section.ordinal()] += nanos;
	}

	public void count(Counter counter) {
		this.counterTotal[counter.ordinal()]++;
	}

	public void count(Counter counter, int n) {
		this.counterTotal[counter.ordinal()] += n;
	}

	/**
	 * Ends the current phase (if any) and begins {@code next}. Phases are consecutive,
	 * non-overlapping sections, such as the stages of {@code renderEntities}.
	 */
	public void switchPhase(Section next) {
		long now = System.nanoTime();
		if (this.phase != null) {
			int i = this.phase.ordinal();
			this.sectionTotal[i] += now - this.sectionStart[i];
		}
		this.phase = next;
		this.sectionStart[next.ordinal()] = now;
	}

	public void endPhase() {
		if (this.phase != null) {
			int i = this.phase.ordinal();
			this.sectionTotal[i] += System.nanoTime() - this.sectionStart[i];
			this.phase = null;
		}
	}

	public void tickStart() {
		this.tickStart = System.nanoTime();
	}

	public void tickEnd() {
		long duration = System.nanoTime() - this.tickStart;
		this.sectionTotal[Section.TICK.ordinal()] += duration;
		int row = this.ticks.append();
		this.ticks.set(row, 0, duration);
	}

	private long threadAllocatedBytes() {
		if (this.allocationBean == null) {
			return -1L;
		}
		if (this.threadId < 0) {
			this.threadId = Thread.currentThread().getId();
		}
		return this.allocationBean.getThreadAllocatedBytes(this.threadId);
	}

	public void setListener(FrameListener listener) {
		this.listener = listener;
	}

	public void setChunkCounters(ChunkCounters counters) {
		this.chunkCounters = counters;
	}

	public boolean measuresAllocation() {
		return this.allocationBean != null;
	}

	public MetricRing frames() {
		return this.frames;
	}

	/** Wall-clock anchor for start_ns / JFR alignment; resolution and sampling gap are explicit. */
	public long epochWallMillis() { return this.epochWallMillis; }

	public long epochWallUncertaintyNanos() { return this.epochWallUncertainty; }

	public MetricRing ticks() {
		return this.ticks;
	}

	public JvmMonitor jvm() {
		return this.jvm;
	}

	/** Discards recorded history, e.g. after a world change so stats describe the new world. */
	public void reset() {
		this.frames.clear();
		this.ticks.clear();
		this.frameStart = 0L;
		PerformanceTracker.reset();
	}
}
