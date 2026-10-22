package net.adiont.alacer.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

/**
 * Flat key/value configuration stored in {@code config/alacer.properties}.
 *
 * <p>Every option has a safe default. Values can be overridden for a single launch with
 * a system property of the form {@code -Dalacer.<key>=<value>}, which is how benchmark runs
 * toggle features without touching the file on disk.
 *
 * <p>Options are read once at startup into plain fields. Fields that are documented as
 * runtime-toggleable are {@code volatile} and may be flipped while the game runs.
 */
public final class AlacerConfig {

	private static final String SYSTEM_PREFIX = "alacer.";
	/** Default of {@code render.fastParticleVertices}; the mixin plugin applies its hooks by the same default. */
	static final boolean FAST_PARTICLE_VERTICES = true;
	/** Default of {@code render.fastParticleLightEntry}; the mixin plugin applies its hooks by the same default. */
	static final boolean FAST_PARTICLE_LIGHT_ENTRY = false;
	/** Default of {@code render.reuseParticleLight}; the mixin plugin applies its hooks by the same default. */
	static final boolean REUSE_PARTICLE_LIGHT = false;

	/** Collect frame/tick/memory measurements. */
	public final boolean profilerEnabled;
	/** Number of frames retained by the profiler's ring buffers. */
	public final int profilerHistory;
	/** Seconds between profiler summaries written to the log; 0 disables logging. */
	public final int profilerLogInterval;
	/** Time individual calls inside entity rendering (chunk lookup, visibility check, draw). Costs CPU. */
	public final boolean profilerEntityDetail;
	/** Additional draw-stage timers; only used with entity detail enabled at startup. */
	public final boolean profilerEntityStages;
	/** Coarse timer and count for entity shadows; its hook is only installed when on at startup. */
	public final boolean profilerShadowTiming;
	/** Repeat each visibility search's frustum tests after it, timed through both tests. Startup only. */
	public final boolean profilerFrustumTiming;
	/** Chunk pipeline attribution, including worker wall times. Startup only; intrusive. */
	public final boolean profilerChunkDetail;
	/** Per-particle update, movement, light and removal timers. Startup only; intrusive. */
	public final boolean profilerParticleDetail;
	/** Count particle light queries and their distinct positions per render pass. Startup only; attribution. */
	public final boolean profilerParticleLightRepeats;
	/** Show the profiler overlay at startup (it can also be toggled with a key). */
	public volatile boolean hudVisible;
	/** Use Alacer's allocation-free chunk visibility search instead of vanilla's. Runtime-toggleable. */
	public volatile boolean fastChunkVisibility;
	/** Run both visibility searches and log any difference (vanilla's result is used). Debug only. */
	public volatile boolean verifyChunkVisibility;
	/** Experimental one-corner-per-plane chunk test; no dependable measured gain. Runtime-toggleable. */
	public volatile boolean fastChunkFrustum;
	/** Also run vanilla's frustum test for every box, use its answer and log differences. Debug only. */
	public volatile boolean verifyChunkFrustum;
	/** Reuse each render chunk's world-chunk reference in the entity loop. Runtime-toggleable. */
	public volatile boolean fastEntityChunkLookup;
	/** Compare every cached entity-loop chunk lookup with vanilla's lookup. Debug only. */
	public volatile boolean verifyEntityChunkLookup;
	/** Run vanilla's two per-frame GL error checks only about once per second. Runtime-toggleable. */
	public volatile boolean throttleGlErrorChecks;
	/** Raise a harmless GL error in skipped frames and confirm the next check reports it. Debug only. */
	public volatile boolean verifyGlErrorChecks;
	/** Answer the world render's matrix and viewport reads from CPU-side copies. Runtime-toggleable. */
	public volatile boolean avoidMatrixReads;
	/** Also read the real values every time, compare and use them. Debug only. */
	public volatile boolean verifyAvoidMatrixReads;
	/** Skip model parts' translations by (0, 0, 0), which do not change the matrix. Runtime-toggleable. */
	public volatile boolean skipZeroTranslates;
	/** Issue them anyway and confirm about once per second that OpenGL's matrix is unchanged. Debug only. */
	public volatile boolean verifySkipZeroTranslates;
	/** Draw fancy clouds from a display list rebuilt only when their shape changes. Runtime-toggleable. */
	public volatile boolean cacheClouds;
	/** Draw vanilla's clouds and compare every vertex with the cached geometry. Debug only. */
	public volatile boolean verifyCacheClouds;
	/** Answer entity shadows' world queries through cached chunk references. Runtime-toggleable. */
	public volatile boolean fastEntityShadows;
	/** Also run vanilla's shadow queries, use their results and log differences. Debug only. */
	public volatile boolean verifyFastEntityShadows;
	/** Answer particles' light queries through cached chunk references. Runtime-toggleable. */
	public volatile boolean fastParticleLight;
	/** Also run vanilla's particle light query, use its result and log differences. Debug only. */
	public volatile boolean verifyFastParticleLight;
	/** Remove dead particles in one pass by network id instead of removeAll's pairwise equals. Runtime-toggleable. */
	public volatile boolean fastParticleRemoval;
	/** Also run vanilla's removeAll, use its result and log differences. Debug only. */
	public volatile boolean verifyFastParticleRemoval;
	/** Write particle vertex elements directly instead of through BufferBuilder's generic element path. Runtime-toggleable. */
	public volatile boolean fastParticleVertices;
	/** Let vanilla write particle vertices, compare the direct writes' bytes and log differences. Debug only. */
	public volatile boolean verifyFastParticleVertices;
	/** Answer particles' chunk check before their light query without the chunk-source call or position copy. Runtime-toggleable. */
	public volatile boolean fastParticleLightEntry;
	/** Also run vanilla's chunk check, use its result and log differences. Debug only. */
	public volatile boolean verifyFastParticleLightEntry;
	/** Reuse particle light answers for repeated positions within one particle render pass. Runtime-toggleable. */
	public volatile boolean reuseParticleLight;
	/** Also run vanilla's light query for every reused or computed answer, use its result and log differences. Debug only. */
	public volatile boolean verifyReuseParticleLight;
	/** Experimental cached chunk reads during vanilla particle movement. Runtime-toggleable. */
	public volatile boolean fastParticleMovement;
	/** Compare movement block-state reads with vanilla; return vanilla's state. Debug only. */
	public volatile boolean verifyFastParticleMovement;
	/** Experimental immutable zero-offset Box reuse during vanilla particle movement. */
	public volatile boolean reuseParticleBoxes;
	/** Compare every eligible Box operation with vanilla, returning the candidate value. */
	public volatile boolean verifyReuseParticleBoxes;
	/** Experimental scalar nearby-entity query bounds; independent from movement/Box reuse. */
	public volatile boolean scalarParticleEntityQuery;
	/** Compare scalar bounds and ordered entity lists, returning the candidate. */
	public volatile boolean verifyScalarParticleEntityQuery;
	/** Session 28 candidate: one reused position for entity shadows' block-below query. Runtime-toggleable when installed. */
	public volatile boolean reuseShadowPositions;
	/** Also make vanilla's position, use it, and compare coordinates and the block state answered for both. Debug only. */
	public volatile boolean verifyReuseShadowPositions;

	/** Batch vanilla model-part face draws inside an alternate display list. Runtime-toggleable. */
	public volatile boolean batchModelLists;
	/** Compare native feedback from both lists at compilation. Debug only. */
	public volatile boolean verifyBatchModelLists;

	/** Independent quality selectors: 0 follows Graphics, 1 Fast, 2 Fancy; weather also supports 3 Off. */
	public volatile int leavesQuality;
	public volatile int weatherQuality;
	/** 0 retains vanilla AO, 1 softens its midtones. Both use the original lightmap. */
	public volatile int smoothLightingStyle;
	/** Ambient-occlusion darkening only, as a percentage; 100 preserves full strength. */
	public volatile int ambientOcclusionStrength;
	private Path settingsFile;
	private final Map<String, String> pendingRestart = new LinkedHashMap<>();
	private final Map<String, String> effective = new LinkedHashMap<>();

	private AlacerConfig(Properties file, Properties system) {
		this.leavesQuality = clamp(integer(file, system, "quality.leaves", 0, "Leaves quality: 0 follows Graphics, 1 Fast, 2 Fancy."), 0, 2);
		this.weatherQuality = clamp(integer(file, system, "quality.weather", 0, "Weather geometry: 0 follows Graphics, 1 Fast, 2 Fancy, 3 Off. Rain sounds and splash particles remain."), 0, 3);
		this.smoothLightingStyle = clamp(integer(file, system, "quality.lightingStyle", 0, "Smooth-lighting style: 0 Vanilla, 1 Soft. Requires smooth lighting; rebuild terrain after changes."), 0, 1);
		this.ambientOcclusionStrength = clamp(integer(file, system, "quality.aoStrength", 100, "Ambient-occlusion corner darkness, 0-100 percent. Does not change sky/block light or directional shading."), 0, 100);
		this.profilerEnabled = bool(file, system, "profiler.enabled", true,
			"Collect frame-time, tick-time and memory statistics.");
		this.profilerHistory = clamp(integer(file, system, "profiler.history", 8192,
			"Frames kept in the profiler history (bounded memory)."), 256, 1 << 20);
		this.profilerLogInterval = Math.max(0, integer(file, system, "profiler.logInterval", 0,
			"Seconds between performance summaries in the log (0 = off)."));
		this.profilerEntityDetail = bool(file, system, "profiler.entityDetail", false,
			"Time individual calls inside entity rendering. Adds measurable overhead; for profiling only.");
		this.profilerEntityStages = bool(file, system, "profiler.entityStages", true,
			"With entityDetail, time model setup/animation/drawing, layers, overlays, name tags and shadows. Disable to measure the extra timers' overhead.");
		this.profilerShadowTiming = bool(file, system, "profiler.shadowTiming", false,
			"Time entity shadow rendering as one section and count shadows drawn (two nanoTime calls per shadow).");
		this.profilerFrustumTiming = bool(file, system, "profiler.frustumTiming", false,
			"After each chunk visibility search, repeat its frustum tests through vanilla's test and Alacer's, timing each. Adds frame time; for attribution only.");
		this.profilerChunkDetail = bool(file, system, "profiler.chunkDetail", false,
			"Attribute chunk build, queue, buffer wait and GL upload wall times. Intrusive; startup only, not performance selection.");
		this.profilerParticleDetail = bool(file, system, "profiler.particleDetail", false,
			"Time particle updates, their movement, dead-particle removal and light queries individually. Intrusive; startup only, not performance selection.");
		this.profilerParticleLightRepeats = bool(file, system, "profiler.particleLightRepeats", false,
			"Count particle light queries and their distinct positions in each particle render pass. Startup only; attribution, not performance selection.");
		this.hudVisible = bool(file, system, "profiler.hud", false,
			"Show the performance overlay at startup.");
		this.fastChunkVisibility = bool(file, system, "render.fastChunkVisibility", true,
			"Replace the per-frame chunk visibility search with an allocation-free equivalent.");
		this.verifyChunkVisibility = bool(file, system, "render.fastChunkVisibility.verify", false,
			"Debug: run vanilla and Alacer visibility searches side by side and log differences. Slower.");
		this.fastChunkFrustum = bool(file, system, "render.fastChunkFrustum", false,
			"Experimental: test chunk boxes at one corner per frustum plane (same decisions as vanilla). No dependable measured performance gain; off by default.");
		this.verifyChunkFrustum = bool(file, system, "render.fastChunkFrustum.verify", false,
			"Debug: also run vanilla's frustum test for every chunk box, use its answer and log any difference.");
		this.fastEntityChunkLookup = bool(file, system, "render.fastEntityChunkLookup", true,
			"Reuse each render chunk's world-chunk reference when gathering entities instead of a hash lookup per chunk per frame.");
		this.verifyEntityChunkLookup = bool(file, system, "render.fastEntityChunkLookup.verify", false,
			"Debug: also do vanilla's lookup for every cached one and log differences.");
		this.throttleGlErrorChecks = bool(file, system, "render.throttleGlErrorChecks", true,
			"Check for OpenGL errors about once per second instead of twice per frame. Errors are still reported, with a less precise location.");
		this.verifyGlErrorChecks = bool(file, system, "render.throttleGlErrorChecks.verify", false,
			"Debug: deliberately raise a harmless OpenGL error between checks and log whether it is reported.");
		this.avoidMatrixReads = bool(file, system, "render.avoidMatrixReads", true,
			"Compute the camera, frustum and render chunk matrices on the CPU instead of reading them back from OpenGL every frame.");
		this.verifyAvoidMatrixReads = bool(file, system, "render.avoidMatrixReads.verify", false,
			"Debug: also read the matrices from OpenGL, use those, and log any difference.");
		this.skipZeroTranslates = bool(file, system, "render.skipZeroTranslates", false,
			"Skip the translations by zero that every entity model part issues twice (about 20% of the OpenGL calls of drawing entities).");
		this.verifySkipZeroTranslates = bool(file, system, "render.skipZeroTranslates.verify", false,
			"Debug: issue the translations anyway and check about once per second that they leave OpenGL's matrix unchanged.");
		this.cacheClouds = bool(file, system, "render.cacheClouds", true,
			"Compile fancy cloud geometry into a display list, rebuilt only when its shape changes, instead of rebuilding about 10,000 vertices every frame.");
		this.verifyCacheClouds = bool(file, system, "render.cacheClouds.verify", false,
			"Debug: draw vanilla's clouds and compare every vertex with the cached geometry, logging differences.");
		this.fastEntityShadows = bool(file, system, "render.fastEntityShadows", true,
			"Answer entity shadows' block and light queries from cached chunk references instead of a chunk hash lookup per query.");
		this.verifyFastEntityShadows = bool(file, system, "render.fastEntityShadows.verify", false,
			"Debug: also run vanilla's shadow queries, use their results and log any difference.");
		this.fastParticleLight = bool(file, system, "render.fastParticleLight", true,
			"Answer particles' per-frame light queries from cached chunk references instead of chunk hash lookups, without allocating neighbor positions.");
		this.verifyFastParticleLight = bool(file, system, "render.fastParticleLight.verify", false,
			"Debug: also run vanilla's particle light query, use its result and log any difference.");
		this.fastParticleRemoval = bool(file, system, "render.fastParticleRemoval", true,
			"Remove dead particles each tick in one pass by network id instead of comparing every live particle with every dead one (same particles, order and list).");
		this.verifyFastParticleRemoval = bool(file, system, "render.fastParticleRemoval.verify", false,
			"Debug: also run vanilla's dead-particle removal, use its result and log any difference.");
		this.fastParticleVertices = bool(file, system, "render.fastParticleVertices", FAST_PARTICLE_VERTICES,
			"Write each particle vertex's position, texture, color and light bytes directly at their fixed offsets instead of through BufferBuilder's per-element format lookups (same bytes). Its hooks are installed only when on at startup (or verified, or benchmark-toggled).");
		this.verifyFastParticleVertices = bool(file, system, "render.fastParticleVertices.verify", false,
			"Debug: let vanilla write particle vertices, compare the direct writes' bytes with them and log any difference.");
		this.fastParticleLightEntry = bool(file, system, "render.fastParticleLightEntry", FAST_PARTICLE_LIGHT_ENTRY,
			"Answer the loaded-chunk check before each particle's light query with vanilla's client-world range test, and reuse one position instead of copying it when the light query is answered by render.fastParticleLight (same results). Its hooks are installed only when on at startup (or verified, or benchmark-toggled).");
		this.verifyFastParticleLightEntry = bool(file, system, "render.fastParticleLightEntry.verify", false,
			"Debug: keep vanilla's position copy, also run vanilla's chunk check, use its result and log any difference.");
		this.reuseParticleLight = bool(file, system, "render.reuseParticleLight", REUSE_PARTICLE_LIGHT,
			"Within one particle render pass, answer render.fastParticleLight's light queries for a block position already answered in that pass from that answer (the client world cannot change during the pass; same results). Its hooks are installed only when on at startup (or verified, or benchmark-toggled).");
		this.verifyReuseParticleLight = bool(file, system, "render.reuseParticleLight.verify", false,
			"Debug: also run vanilla's light query for every reused or computed answer, use its result and log any difference.");
		this.fastParticleMovement = bool(file, system, "render.fastParticleMovement", false,
			"Experimental: read particle movement block states through cached chunk references. Off until measured and verified.");
		this.verifyFastParticleMovement = bool(file, system, "render.fastParticleMovement.verify", false,
			"Debug: compare cached-chunk movement block states with vanilla and use vanilla's state.");
		this.reuseParticleBoxes = bool(file, system, "render.reuseParticleBoxes", false,
			"Experimental: reuse immutable zero-offset particle movement boxes with bit-exact safe coordinates. Off by default.");
		this.verifyReuseParticleBoxes = bool(file, system, "render.reuseParticleBoxes.verify", false,
			"Debug: compare particle movement Box coordinates against vanilla using raw double bits.");
		this.scalarParticleEntityQuery = bool(file, system, "render.scalarParticleEntityQuery", false,
			"Experimental: scalar particle collision nearby-entity query bounds. Off by default.");
		this.verifyScalarParticleEntityQuery = bool(file, system, "render.scalarParticleEntityQuery.verify", false,
			"Debug: compare scalar bounds and ordered entity query results with vanilla.");
		this.reuseShadowPositions = bool(file, system, "render.reuseShadowPositions", false,
			"Experimental: pass one reused position instead of a new BlockPos to each entity shadow's block-below query (same query and answer). Off by default; its hook is installed only when on at startup (or verified, or benchmark-toggled).");
		this.verifyReuseShadowPositions = bool(file, system, "render.reuseShadowPositions.verify", false,
			"Debug: keep vanilla's new position, compare the reused one's coordinates and the block state answered for each, and log any difference.");
		this.batchModelLists = bool(file, system, "render.batchModelLists", false,
			"Combine vanilla model-part face uploads in an alternate display list; experimental, off by default.");
		this.verifyBatchModelLists = bool(file, system, "render.batchModelLists.verify", false,
			"Debug: compare native OpenGL feedback from vanilla and batched model lists at compilation.");
	}

	public static AlacerConfig load(Path file, Logger logger) {
		// Benchmark controls start from the defaults in this build plus explicit JVM overrides.
		// Never let a previous measured build's persisted defaults change the next experiment,
		// and never rewrite the shared file while running a benchmark.
		if (Boolean.getBoolean("alacer.benchmark")) {
			logger.info("Benchmark config: build defaults plus explicit JVM overrides; ignoring {} (no writes)", file);
			return new AlacerConfig(new Properties(), System.getProperties());
		}
		Properties stored = new Properties();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				stored.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				logger.warn("Could not read {}, using defaults", file, e);
			}
		}

		AlacerConfig config = new AlacerConfig(stored, System.getProperties());
		config.settingsFile = file;

		// Rewrite the file so newly added options appear with their defaults. System property
		// overrides are deliberately not persisted.
		AlacerConfig persisted = new AlacerConfig(stored, new Properties());
		try {
			persisted.write(file);
		} catch (IOException e) {
			logger.warn("Could not write {}", file, e);
		}

		return config;
	}

	private void write(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		StringBuilder out = new StringBuilder("# Alacer configuration. Any option can be overridden per launch with -Dalacer.<key>=<value>.\n");
		for (Map.Entry<String, String> e : this.effective.entrySet()) {
			out.append('\n').append(e.getValue()).append('\n');
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writer.write(out.toString());
		}
	}

	/**
	 * Flips one of the runtime-toggleable options by key, for in-process A/B benchmarking.
	 *
	 * @throws IllegalArgumentException if the key is not runtime-toggleable
	 */
	public void setToggle(String key, boolean value) {
		switch (key) {
			// Benchmark preset: vanilla paths vs the shipped defaults, not every experiment on.
			case "render.defaults":
				this.fastChunkVisibility = value;
				this.fastChunkFrustum = false;
				this.fastEntityChunkLookup = value;
				this.throttleGlErrorChecks = value;
				this.avoidMatrixReads = value;
				this.cacheClouds = value;
				this.fastEntityShadows = value;
				this.fastParticleLight = value;
				this.fastParticleRemoval = value;
				this.fastParticleVertices = value;
				this.fastParticleLightEntry = false;
				this.reuseParticleLight = false;
				this.fastParticleMovement = false;
				this.reuseParticleBoxes = false;
				this.scalarParticleEntityQuery = false;
				this.reuseShadowPositions = false;
				this.skipZeroTranslates = false;
				this.batchModelLists = false;
				break;
			case "render.fastChunkVisibility":
				this.fastChunkVisibility = value;
				break;
			case "render.fastChunkFrustum":
				this.fastChunkFrustum = value;
				break;
			case "render.fastEntityChunkLookup":
				this.fastEntityChunkLookup = value;
				break;
			case "render.throttleGlErrorChecks":
				this.throttleGlErrorChecks = value;
				break;
			case "render.avoidMatrixReads":
				this.avoidMatrixReads = value;
				break;
			case "render.skipZeroTranslates":
				this.skipZeroTranslates = value;
				break;
			case "render.batchModelLists":
				this.batchModelLists = value;
				break;
			case "render.cacheClouds":
				this.cacheClouds = value;
				break;
			case "render.fastEntityShadows":
				this.fastEntityShadows = value;
				break;
			case "render.fastParticleLight":
				this.fastParticleLight = value;
				break;
			case "render.fastParticleRemoval":
				this.fastParticleRemoval = value;
				break;
			case "render.fastParticleVertices":
				this.fastParticleVertices = value;
				break;
			case "render.fastParticleLightEntry":
				this.fastParticleLightEntry = value;
				break;
			case "render.reuseParticleLight":
				this.reuseParticleLight = value;
				break;
			case "render.fastParticleMovement":
				this.fastParticleMovement = value;
				break;
			case "render.scalarParticleEntityQuery":
				this.scalarParticleEntityQuery = value;
				break;
			case "render.reuseParticleBoxes":
				this.reuseParticleBoxes = value;
				break;
			case "render.reuseShadowPositions":
				this.reuseShadowPositions = value;
				break;
			default:
				throw new IllegalArgumentException("Not a runtime-toggleable option: " + key);
		}
	}

	/** A restart-only menu edit is saved without pretending startup-selected hooks changed in this JVM. */
	public String videoValue(String key) { return this.pendingRestart.getOrDefault(key, describe().get(key)); }
	public boolean videoRestartRequired() { return !this.pendingRestart.isEmpty(); }

	/** Persist explicit user edits first, then update live fields. Unknown settings and JVM overrides are retained. */
	public void saveVideoChanges(Map<String, String> changes) throws IOException {
		if (Boolean.getBoolean("alacer.benchmark") || this.settingsFile == null) {
			throw new IOException("Settings are read-only during benchmark launches");
		}
		for (Map.Entry<String,String> e : changes.entrySet()) {
			String key = e.getKey(), value = e.getValue();
			net.adiont.alacer.settings.VideoSetting option = net.adiont.alacer.settings.VideoSettingsCatalog.byKey(key);
			if (System.getProperty(SYSTEM_PREFIX + key) != null) { throw new IOException("A launch override controls " + key); }
			if (key.startsWith("quality.")) {
				int number = Integer.parseInt(value);
				if (option.normalize(number) != number) { throw new IllegalArgumentException("Invalid quality: " + value); }
			} else if (!key.startsWith("render.") || !("true".equals(value) || "false".equals(value))) {
				throw new IllegalArgumentException("Not a menu configuration option: " + key);
			}
		}
		if (changes.isEmpty()) { return; }
		SettingsFile.update(this.settingsFile, changes);
		for (Map.Entry<String,String> e : changes.entrySet()) {
			String key = e.getKey(), value = e.getValue();
			if ("quality.leaves".equals(key)) { this.leavesQuality = Integer.parseInt(value); }
			else if ("quality.weather".equals(key)) { this.weatherQuality = Integer.parseInt(value); }
			else if ("quality.lightingStyle".equals(key)) { this.smoothLightingStyle = Integer.parseInt(value); }
			else if ("quality.aoStrength".equals(key)) { this.ambientOcclusionStrength = Integer.parseInt(value); }
			else if ("render.fastParticleVertices".equals(key)) {
				if (Boolean.parseBoolean(value) == this.fastParticleVertices) { this.pendingRestart.remove(key); }
				else { this.pendingRestart.put(key,value); }
			} else { setToggle(key,Boolean.parseBoolean(value)); }
		}
	}

	/** Effective option values, for inclusion in profile exports. */
	public Map<String, String> describe() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("quality.leaves", Integer.toString(this.leavesQuality));
		values.put("quality.weather", Integer.toString(this.weatherQuality));
		values.put("quality.lightingStyle", Integer.toString(this.smoothLightingStyle));
		values.put("quality.aoStrength", Integer.toString(this.ambientOcclusionStrength));
		values.put("profiler.enabled", String.valueOf(this.profilerEnabled));
		values.put("profiler.history", String.valueOf(this.profilerHistory));
		values.put("profiler.logInterval", String.valueOf(this.profilerLogInterval));
		values.put("profiler.entityDetail", String.valueOf(this.profilerEntityDetail));
		values.put("profiler.entityStages", String.valueOf(this.profilerEntityStages));
		values.put("profiler.shadowTiming", String.valueOf(this.profilerShadowTiming));
		values.put("profiler.frustumTiming", String.valueOf(this.profilerFrustumTiming));
		values.put("profiler.chunkDetail", String.valueOf(this.profilerChunkDetail));
		values.put("profiler.particleDetail", String.valueOf(this.profilerParticleDetail));
		values.put("profiler.particleLightRepeats", String.valueOf(this.profilerParticleLightRepeats));
		values.put("profiler.hud", String.valueOf(this.hudVisible));
		values.put("render.fastChunkVisibility", String.valueOf(this.fastChunkVisibility));
		values.put("render.fastChunkVisibility.verify", String.valueOf(this.verifyChunkVisibility));
		values.put("render.fastChunkFrustum", String.valueOf(this.fastChunkFrustum));
		values.put("render.fastChunkFrustum.verify", String.valueOf(this.verifyChunkFrustum));
		values.put("render.fastEntityChunkLookup", String.valueOf(this.fastEntityChunkLookup));
		values.put("render.fastEntityChunkLookup.verify", String.valueOf(this.verifyEntityChunkLookup));
		values.put("render.throttleGlErrorChecks", String.valueOf(this.throttleGlErrorChecks));
		values.put("render.throttleGlErrorChecks.verify", String.valueOf(this.verifyGlErrorChecks));
		values.put("render.avoidMatrixReads", String.valueOf(this.avoidMatrixReads));
		values.put("render.avoidMatrixReads.verify", String.valueOf(this.verifyAvoidMatrixReads));
		values.put("render.skipZeroTranslates", String.valueOf(this.skipZeroTranslates));
		values.put("render.skipZeroTranslates.verify", String.valueOf(this.verifySkipZeroTranslates));
		values.put("render.cacheClouds", String.valueOf(this.cacheClouds));
		values.put("render.cacheClouds.verify", String.valueOf(this.verifyCacheClouds));
		values.put("render.fastEntityShadows", String.valueOf(this.fastEntityShadows));
		values.put("render.fastEntityShadows.verify", String.valueOf(this.verifyFastEntityShadows));
		values.put("render.fastParticleLight", String.valueOf(this.fastParticleLight));
		values.put("render.fastParticleLight.verify", String.valueOf(this.verifyFastParticleLight));
		values.put("render.fastParticleRemoval", String.valueOf(this.fastParticleRemoval));
		values.put("render.fastParticleRemoval.verify", String.valueOf(this.verifyFastParticleRemoval));
		values.put("render.fastParticleVertices", String.valueOf(this.fastParticleVertices));
		values.put("render.fastParticleVertices.verify", String.valueOf(this.verifyFastParticleVertices));
		values.put("render.fastParticleLightEntry", String.valueOf(this.fastParticleLightEntry));
		values.put("render.fastParticleLightEntry.verify", String.valueOf(this.verifyFastParticleLightEntry));
		values.put("render.reuseParticleLight", String.valueOf(this.reuseParticleLight));
		values.put("render.reuseParticleLight.verify", String.valueOf(this.verifyReuseParticleLight));
		values.put("render.fastParticleMovement", String.valueOf(this.fastParticleMovement));
		values.put("render.fastParticleMovement.verify", String.valueOf(this.verifyFastParticleMovement));
		values.put("render.scalarParticleEntityQuery", String.valueOf(this.scalarParticleEntityQuery));
		values.put("render.scalarParticleEntityQuery.verify", String.valueOf(this.verifyScalarParticleEntityQuery));
		values.put("render.reuseShadowPositions", String.valueOf(this.reuseShadowPositions));
		values.put("render.reuseShadowPositions.verify", String.valueOf(this.verifyReuseShadowPositions));
		values.put("render.reuseParticleBoxes", String.valueOf(this.reuseParticleBoxes));
		values.put("render.reuseParticleBoxes.verify", String.valueOf(this.verifyReuseParticleBoxes));
		values.put("render.batchModelLists", String.valueOf(this.batchModelLists));
		values.put("render.batchModelLists.verify", String.valueOf(this.verifyBatchModelLists));
		return values;
	}

	private String raw(Properties file, Properties system, String key, String def, String comment) {
		String value = system.getProperty(SYSTEM_PREFIX + key);
		if (value == null) {
			value = file.getProperty(key);
		}
		if (value == null) {
			value = def;
		}
		value = value.trim();
		this.effective.put(key, "# " + comment + " Default: " + def + "\n" + key + "=" + value);
		return value;
	}

	private boolean bool(Properties file, Properties system, String key, boolean def, String comment) {
		String value = raw(file, system, key, String.valueOf(def), comment);
		if (value.equalsIgnoreCase("true")) {
			return true;
		}
		if (value.equalsIgnoreCase("false")) {
			return false;
		}
		this.effective.put(key, "# " + comment + " Default: " + def + "\n" + key + "=" + def);
		return def;
	}

	private int integer(Properties file, Properties system, String key, int def, String comment) {
		String value = raw(file, system, key, String.valueOf(def), comment);
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			this.effective.put(key, "# " + comment + " Default: " + def + "\n" + key + "=" + def);
			return def;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
