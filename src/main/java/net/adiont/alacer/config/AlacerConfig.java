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
	/** Visible-update latency (dirty and visible to published) and terrain holes. Startup only; light attribution. */
	public final boolean profilerChunkLatency;
	/** Per-particle update, movement, light and removal timers. Startup only; intrusive. */
	public final boolean profilerParticleDetail;
	/** Count particle light queries and their distinct positions per render pass. Startup only; attribution. */
	public final boolean profilerParticleLightRepeats;
	/** Show the profiler overlay at startup (it can also be toggled with a key). */
	public volatile boolean hudVisible;
	/** Show the performance area graph at startup (it can also be toggled with a key). */
	public volatile boolean graphVisible;
	/** Selected performance area graph mode: 0 = Frame Time, 1 = Subsystem Time (ms), 2 = % Split. */
	public volatile int graphMode;
	/** Use Alacer's allocation-free chunk visibility search instead of vanilla's. Runtime-toggleable. */
	public volatile boolean fastChunkVisibility;
	/** Run both visibility searches and log any difference (vanilla's result is used). Debug only. */
	public volatile boolean verifyChunkVisibility;
	/** Experimental one-corner-per-plane chunk test; no dependable measured gain. Runtime-toggleable. */
	public volatile boolean fastChunkFrustum;
	/** Also run vanilla's frustum test for every box, use its answer and log differences. Debug only. */
	public volatile boolean verifyChunkFrustum;
	/** Experimental allocation-free camera-section flood fill that reuses its answer within one pocket. Runtime-toggleable. */
	public volatile boolean fastCameraSectionFaces;
	/** Also run vanilla's camera-section flood fill, use its answer and log differences. Debug only. */
	public volatile boolean verifyCameraSectionFaces;
	/** Reuse each render chunk's world-chunk reference in the entity loop. Runtime-toggleable. */
	public volatile boolean fastEntityChunkLookup;
	/** Compare every cached entity-loop chunk lookup with vanilla's lookup. Debug only. */
	public volatile boolean verifyEntityChunkLookup;
	/** High-performance entity pipeline with active chunk stamping and direct array access. Runtime-toggleable. */
	public volatile boolean fastEntityPipeline;
	/** Compare fast entity pipeline rendered entities and count with vanilla. Debug only. */
	public volatile boolean verifyEntityPipeline;
	/** Unified direct single-pass terrain layer rendering and chunk-level layer bitmask filtering. Runtime-toggleable. */
	public volatile boolean fastTerrainPipeline;
	/** Compare fast terrain layer rendered chunk counts with vanilla. Debug only. */
	public volatile boolean verifyTerrainPipeline;
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
	/** Answer entity lighting queries without BlockPos allocations or chunk hash lookups. Runtime-toggleable. */
	public volatile boolean fastEntityLighting;
	/** Also run vanilla's entity lighting queries, use their results and log differences. Debug only. */
	public volatile boolean verifyFastEntityLighting;
	/** Cache entity renderer lookups to eliminate repeated map lookups during entity rendering. Runtime-toggleable. */
	public volatile boolean fastEntityRendererLookup;
	/** Also run vanilla's map lookup, use its result and log differences. Debug only. */
	public volatile boolean verifyEntityRendererLookup;
	/** Evaluate distance and frustum before entity state preparation and renderer invocation. Runtime-toggleable. */
	public volatile boolean fastEntityCulling;
	/** Also run vanilla's shouldRender decision, use its result and log differences. Debug only. */
	public volatile boolean verifyEntityCulling;
	/** Trace crosshair and entity rays without intermediate Vec3d or BlockPos allocations. Runtime-toggleable. */
	public volatile boolean fastRayTrace;
	/** Also run vanilla's ray trace, use its result and log differences. Debug only. */
	public volatile boolean verifyFastRayTrace;
	/** Reduce client ambient tick position and lookup allocations, preserving vanilla per-tick Random lifetime. Runtime-toggleable. */
	public volatile boolean fastRandomDisplayTicks;
	/** Also run vanilla verification on display tick block lookups. Debug only. */
	public volatile boolean verifyFastRandomDisplayTicks;
	/** Wake paulscode sound threads via lock-free parking instead of Thread.sleep and thread interruption. Runtime-toggleable. */
	public volatile boolean fastSoundThread;
	/** Verify sound thread wake timing, lifecycle and interruption handling against expected bounds. Debug only. */
	public volatile boolean verifyFastSoundThread;
	/** Reuse visibility only when every camera, frustum and compiled-chunk input remains unchanged. Runtime-toggleable. */
	public volatile boolean cameraHysteresis;
	/** Compare retained exact-input visibility against a fresh vanilla search. Debug only. */
	public volatile boolean verifyCameraHysteresis;
	/** Dense 36-bit packed occlusion bitmask per chunk slot to accelerate graph traversal. Runtime-toggleable. */
	public volatile boolean flatOcclusion;
	/** Also test chunk occlusion via vanilla CompiledChunk.isVisible and log differences. Debug only. */
	public volatile boolean verifyFlatOcclusion;
	/** SIMD vectorized frustum plane-box evaluation. Runtime-toggleable. */
	public volatile boolean vectorFrustum;
	/** Also run vanilla frustum culler and log differences. Debug only. */
	public volatile boolean verifyVectorFrustum;
	/** Pre-populated thread-local block state cache for worker ambient occlusion. Runtime-toggleable. */
	public volatile boolean fastWorkerAo;
	/** Also verify worker block state queries against vanilla chunk lookups. Debug only. */
	public volatile boolean verifyWorkerAo;
	/** Skip zero translations in model parts; hierarchy flattening is not implemented. Runtime-toggleable. */
	public volatile boolean fastModelTransforms;
	/** Also verify model part transformations against vanilla. Debug only. */
	public volatile boolean verifyFastModelTransforms;
	/** Answer particles' light queries through cached chunk references. Runtime-toggleable. */
	public volatile boolean fastParticleLight;
	/** Also run vanilla's particle light query, use its result and log differences. Debug only. */
	public volatile boolean verifyFastParticleLight;
	/** Fast LUT-based gamma mipmap generation and parallel sprite downsampling. Runtime-toggleable. */
	public volatile boolean fastMipmaps;
	/** Compare fast mipmap results against vanilla downsampling and log differences. Debug only. */
	public volatile boolean verifyFastMipmaps;
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
	/** Keep queueing chunk rebuilds for the workers after vanilla's frame deadline stops its loop. Runtime-toggleable. */
	public volatile boolean feedChunkWorkers;
	/** Give chunk uploads and rebuild queueing at least 1/32 of the previous frame. Runtime-toggleable. */
	public volatile boolean chunkTimeFloor;
	public volatile boolean fastChunkNeighbours, verifyChunkNeighbours, terrainShader, terrainVaos, directBufferUploads, verifyModernGl;
	/** Batch terrain draw calls via OpenGL 4.3 Multi-Draw Indirect over resident mesh arena. Runtime-toggleable. */
	public volatile boolean multiDrawIndirect;
	/** Validate indirect draw command structs, quads, and origin records against reference direct draws. Debug only. */
	public volatile boolean verifyMultiDrawIndirect;
	/** Vectorized block state unpacking and linear block counter scan during chunk packet handling. */
	public volatile boolean fastChunkUnpack;
	/** Debug: compare fast chunk unpacking and block counters with vanilla's scalar logic. */
	public volatile boolean verifyChunkUnpack;
	/** Session 28 candidate: one reused position for entity shadows' block-below query. Runtime-toggleable when installed. */
	public volatile boolean reuseShadowPositions;
	/** Also make vanilla's position, use it, and compare coordinates and the block state answered for both. Debug only. */
	public volatile boolean verifyReuseShadowPositions;

	/** Batch vanilla model-part face draws inside an alternate display list. Runtime-toggleable. */
	public volatile boolean batchModelLists;
	/** Compare native feedback from both lists at compilation. Debug only. */
	public volatile boolean verifyBatchModelLists;
	/** High-precision frame limiter and frame pacer using LockSupport.parkNanos and Thread.onSpinWait. Runtime-toggleable. */
	public volatile boolean fastDisplaySync;
	/** Also verify frame limiter sleep durations and pacing error bounds. Debug only. */
	public volatile boolean verifyFastDisplaySync;
	/** Early 3D pipeline flush to overlap CPU HUD/GUI rendering with GPU draw execution. Runtime-toggleable. */
	public volatile boolean fastSwapBuffers;
	/** Also verify pipeline flush execution and fence status. Debug only. */
	public volatile boolean verifyFastSwapBuffers;
	/** Suppress unconditional client-thread yield after buffer swap to eliminate OS context-switch latency spikes. Runtime-toggleable. */
	public volatile boolean suppressThreadYield;

	/** Independent quality selectors: 0 follows Graphics, 1 Fast, 2 Fancy; weather also supports 3 Off. */
	public volatile int leavesQuality;
	public volatile int weatherQuality;
	/** Rain splash particles: 0 follows Graphics, 1 Fast, 2 Fancy, 3 Off. Rain sounds remain. */
	public volatile int rainSplashQuality;
	/** HUD vignette: 0 follows Graphics, 1 Off (Fast), 2 On (Fancy). */
	public volatile int vignetteQuality;
	/** Visual choices independent of Graphics: 1 keeps vanilla, 0 omits stars, glint or extra dropped-item copies. */
	public volatile int starsQuality;
	public volatile int enchantGlintQuality;
	public volatile int droppedItemsQuality;
	/** 0 retains vanilla AO, 1 softens its midtones. Both use the original lightmap. */
	public volatile int smoothLightingStyle;
	/** Ambient-occlusion darkening only, as a percentage; 100 preserves full strength. */
	public volatile int ambientOcclusionStrength;
	/** Atmosphere controls default to Minecraft's existing appearance. */
	public volatile int cloudHeightOffset;
	public volatile int skyQuality;
	public volatile int starBrightness;
	public volatile int weatherOpacity;
	/** Entity and block-entity draw distances as a percentage of vanilla's; 100 is vanilla. */
	public volatile int entityDistance;
	public volatile int blockEntityDistance;
	/** Overworld fog start as a percentage of the render distance (vanilla 75); 100 moves the fog out of view. */
	public volatile int fogStart;
	/** 0 hides the sun and moon, 1 draws them. */
	public volatile int sunMoonQuality;
	/** Texture animation groups: 0 freezes the current frame, 1 animates. */
	public volatile int waterAnimation;
	public volatile int lavaAnimation;
	public volatile int fireAnimation;
	public volatile int portalAnimation;
	public volatile int otherAnimations;
	/** Choice index into ParticleLimit; 4 is vanilla's 4000 per list. */
	public volatile int particleLimit;
	/** Choice index into BiomeBlend; 1 is vanilla's 3x3. */
	public volatile int biomeBlend;
	/** Choice index into TextureFiltering; 0 is off. */
	public volatile int anisotropicFiltering;
	/** Mipmap minification filter: 0 Bilinear, 1 Trilinear. */
	public volatile int mipmapFiltering;
	/** Mipmap LOD bias percentage (-100 to 100 in steps of 25; 0 is standard). */
	public volatile int lodBias;
	/** Mipmap alpha cutout blending: 0 Vanilla, 1 Preserved (weighted color & coverage boost). */
	public volatile int mipmapCutouts;
	/** Camera effect strengths as percentages; 100 is vanilla. */
	public volatile int fovEffects;
	public volatile int damageTilt;
	/** View-bobbing and nausea/portal distortion strengths as percentages; 100 is vanilla. */
	public volatile int viewBobbing;
	public volatile int distortion;
	/** Third-person camera distance in blocks (vanilla 4) and whether it stops in front of blocks (1, vanilla). */
	public volatile int thirdPersonDistance;
	public volatile int cameraCollision;
	/** Choice index into CameraEffects' field-of-view approach rates (1 is vanilla's) and zoom factors (2 is 4x). */
	public volatile int fovTransition;
	public volatile int zoom;
	/** Choice index into ChunkScheduling's frame budgets; 2 is vanilla. */
	public volatile int chunkUpdateBudget;
	/** Chunk builder threads, read when the renderer is created at startup; 2 is vanilla. */
	public volatile int chunkThreads;
	private Path settingsFile;
	private final Map<String, String> pendingRestart = new LinkedHashMap<>();
	private final Map<String, String> effective = new LinkedHashMap<>();

	private AlacerConfig(Properties file, Properties system) {
		this.cloudHeightOffset = clamp(integer(file, system, "quality.cloudHeight", 0, "Cloud layer height offset in blocks (-64 to 128). Zero preserves the dimension's height."), -64, 128);
		this.skyQuality = clamp(integer(file, system, "quality.sky", 1, "Sky pass: 0 Off (including sun, moon and stars), 1 On. Clouds remain separate."), 0, 1);
		this.starBrightness = clamp(integer(file, system, "quality.starBrightness", 100, "Star brightness, 0-200 percent. Requires Sky and Stars enabled."), 0, 200);
		this.weatherOpacity = clamp(integer(file, system, "quality.weatherOpacity", 100, "Rain/snow geometry opacity, 0-100 percent. Does not change rain sounds or splash particles."), 0, 100);
		this.leavesQuality = clamp(integer(file, system, "quality.leaves", 0, "Leaves quality: 0 follows Graphics, 1 Fast, 2 Fancy."), 0, 2);
		this.weatherQuality = clamp(integer(file, system, "quality.weather", 0, "Weather geometry: 0 follows Graphics, 1 Fast, 2 Fancy, 3 Off. Rain sounds and splash particles remain."), 0, 3);
		this.rainSplashQuality = clamp(integer(file, system, "quality.rainSplashes", 0, "Rain splash particles: 0 follows Graphics, 1 Fast (about a quarter as many), 2 Fancy, 3 Off. Rain sounds remain."), 0, 3);
		this.vignetteQuality = clamp(integer(file, system, "quality.vignette", 0, "HUD vignette: 0 follows Graphics, 1 Off, 2 On."), 0, 2);
		this.starsQuality = clamp(integer(file, system, "quality.stars", 1, "Night-sky stars: 0 Off, 1 On."), 0, 1);
		this.enchantGlintQuality = clamp(integer(file, system, "quality.enchantGlint", 1, "Enchantment glint on items and armor: 0 Off, 1 On."), 0, 1);
		this.droppedItemsQuality = clamp(integer(file, system, "quality.droppedItems", 1, "Dropped item stacks: 0 Fast (one model per stack), 1 Fancy (up to five models by stack size)."), 0, 1);
		this.smoothLightingStyle = clamp(integer(file, system, "quality.lightingStyle", 0, "Smooth-lighting style: 0 Vanilla, 1 Soft. Requires smooth lighting; rebuild terrain after changes."), 0, 1);
		this.ambientOcclusionStrength = clamp(integer(file, system, "quality.aoStrength", 100, "Ambient-occlusion corner darkness, 0-100 percent. Does not change sky/block light or directional shading."), 0, 100);
		this.entityDistance = step(integer(file, system, "quality.entityDistance", 100, "Entity draw distance, 25-500 percent of vanilla's (multiples of 25). Entities still need visible terrain."), 25, 500, 25, 100);
		this.blockEntityDistance = step(integer(file, system, "quality.blockEntityDistance", 100, "Block entity (chests, signs, banners, heads) draw distance, 25-500 percent of vanilla's (multiples of 25)."), 25, 500, 25, 100);
		this.fogStart = step(integer(file, system, "quality.fogStart", 75, "Overworld distance fog start, 10-95 percent of the render distance (multiples of 5), or 100 for no distance fog. Blindness, water, lava and Nether fog are unchanged."), 10, 100, 5, 75);
		this.sunMoonQuality = clamp(integer(file, system, "quality.sunMoon", 1, "Sun and moon: 0 Off, 1 On."), 0, 1);
		this.waterAnimation = clamp(integer(file, system, "quality.waterAnimation", 1, "Water texture animation: 0 Off (frozen frame), 1 On."), 0, 1);
		this.lavaAnimation = clamp(integer(file, system, "quality.lavaAnimation", 1, "Lava texture animation: 0 Off (frozen frame), 1 On."), 0, 1);
		this.fireAnimation = clamp(integer(file, system, "quality.fireAnimation", 1, "Fire texture animation: 0 Off (frozen frame), 1 On."), 0, 1);
		this.portalAnimation = clamp(integer(file, system, "quality.portalAnimation", 1, "Nether portal texture animation: 0 Off (frozen frame), 1 On."), 0, 1);
		this.otherAnimations = clamp(integer(file, system, "quality.otherAnimations", 1, "Other block and item texture animations (clocks and compasses always update): 0 Off, 1 On."), 0, 1);
		this.particleLimit = clamp(integer(file, system, "quality.particleLimit", 4, "Particles kept per particle list before the oldest is removed: 0=250, 1=500, 2=1000, 3=2000, 4=4000 (vanilla)."), 0, 4);
		this.biomeBlend = clamp(integer(file, system, "quality.biomeBlend", 1, "Biome color blending: 0 Off (1x1), 1 3x3 (vanilla), 2 5x5, 3 7x7. Rebuild terrain after changes."), 0, 3);
		this.anisotropicFiltering = clamp(integer(file, system, "quality.anisotropicFiltering", 0, "Anisotropic filtering for block and item textures: 0 Off, 1=2x, 2=4x, 3=8x, 4=16x (limited by the driver)."), 0, 4);
		this.mipmapFiltering = clamp(integer(file, system, "quality.mipmapFiltering", 0, "Mipmap minification filter: 0 Bilinear, 1 Trilinear."), 0, 1);
		this.lodBias = step(integer(file, system, "quality.lodBias", 0, "Mipmap level-of-detail bias, -100 to 100 percent in steps of 25. Negative sharpens, positive softens."), -100, 100, 25, 0);
		this.mipmapCutouts = clamp(integer(file, system, "quality.mipmapCutouts", 0, "Cutout mipmap blending: 0 Vanilla, 1 Preserved (preserves edge colors and coverage)."), 0, 1);
		this.fovEffects = step(integer(file, system, "quality.fovEffects", 100, "Field-of-view changes from sprinting, flying, speed effects and bows, 0-100 percent (multiples of 5)."), 0, 100, 5, 100);
		this.damageTilt = step(integer(file, system, "quality.damageTilt", 100, "Camera tilt when taking damage, 0-100 percent (multiples of 5)."), 0, 100, 5, 100);
		this.viewBobbing = step(integer(file, system, "quality.viewBobbing", 100, "View-bobbing strength while View bobbing is on, 0-100 percent (multiples of 5)."), 0, 100, 5, 100);
		this.distortion = step(integer(file, system, "quality.distortion", 100, "Nausea and Nether portal view distortion, 0-100 percent (multiples of 5)."), 0, 100, 5, 100);
		this.thirdPersonDistance = clamp(integer(file, system, "quality.thirdPersonDistance", 4, "Third-person camera distance, 1-16 blocks (vanilla 4)."), 1, 16);
		this.cameraCollision = clamp(integer(file, system, "quality.cameraCollision", 1, "Third-person camera stops in front of blocks: 0 Off, 1 On (vanilla)."), 0, 1);
		this.fovTransition = clamp(integer(file, system, "quality.fovTransition", 1, "Field-of-view transitions: 0 Instant, 1 Vanilla, 2 Smooth, 3 Very smooth."), 0, 3);
		this.zoom = clamp(integer(file, system, "quality.zoom", 2, "Zoom key magnification: 0=2x, 1=3x, 2=4x, 3=6x, 4=8x. The key is unbound by default."), 0, 4);
		this.chunkUpdateBudget = clamp(integer(file, system, "advanced.chunkUpdateBudget", 2, "Client-thread time for chunk uploads and rebuild scheduling: 0=25%, 1=50%, 2=100% (vanilla), 3=200%, 4=400% of vanilla's budget."), 0, 4);
		this.chunkThreads = clamp(integer(file, system, "advanced.chunkThreads", 2, "Chunk builder threads, 1-6 (vanilla 2). Read at startup."), 1, 6);
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
		this.profilerChunkLatency = bool(file, system, "profiler.chunkLatency", false,
			"Measure how long each visible chunk update waits from the frame it is found dirty and visible until its rebuild is published, and count never-built chunks in view. Startup only; attribution.");
		this.profilerParticleDetail = bool(file, system, "profiler.particleDetail", false,
			"Time particle updates, their movement, dead-particle removal and light queries individually. Intrusive; startup only, not performance selection.");
		this.profilerParticleLightRepeats = bool(file, system, "profiler.particleLightRepeats", false,
			"Count particle light queries and their distinct positions in each particle render pass. Startup only; attribution, not performance selection.");
		this.hudVisible = bool(file, system, "profiler.hud", false,
			"Show the performance overlay at startup.");
		this.graphVisible = bool(file, system, "profiler.graph", false,
			"Show the performance area graph at startup.");
		this.graphMode = clamp(integer(file, system, "profiler.graphMode", 1,
			"Selected graph mode: 0 = Frame Time, 1 = Subsystem Time (ms), 2 = % Split, 3 = Top Subsystem Detail."), 0, 3);
		this.fastChunkVisibility = bool(file, system, "render.fastChunkVisibility", true,
			"Replace the per-frame chunk visibility search with an allocation-free equivalent.");
		this.verifyChunkVisibility = bool(file, system, "render.fastChunkVisibility.verify", false,
			"Debug: run vanilla and Alacer visibility searches side by side and log differences. Slower.");
		this.fastChunkUnpack = bool(file, system, "render.fastChunkUnpack", true,
			"Vectorized block state unpacking and linear block counter scan during chunk packet handling.");
		this.verifyChunkUnpack = bool(file, system, "render.fastChunkUnpack.verify", false,
			"Debug: compare fast chunk unpacking and block counters with vanilla's scalar logic.");
		this.fastChunkFrustum = bool(file, system, "render.fastChunkFrustum", false,
			"Experimental: test chunk boxes at one corner per frustum plane (same decisions as vanilla). No dependable measured performance gain; off by default.");
		this.verifyChunkFrustum = bool(file, system, "render.fastChunkFrustum.verify", false,
			"Debug: also run vanilla's frustum test for every chunk box, use its answer and log any difference.");
		this.fastCameraSectionFaces = bool(file, system, "render.fastCameraSectionFaces", false,
			"Experimental: in Alacer's chunk search, find the camera section's open faces with an allocation-free flood fill, and reuse the answer while the camera stays in the same connected pocket of an unchanged section (same faces as vanilla). Not yet measured; off by default.");
		this.verifyCameraSectionFaces = bool(file, system, "render.fastCameraSectionFaces.verify", false,
			"Debug: also run vanilla's camera-section flood fill for every answer, use its result and log any difference. Use with the Alacer chunk search enabled.");
		this.fastEntityChunkLookup = bool(file, system, "render.fastEntityChunkLookup", true,
			"Reuse each render chunk's world-chunk reference when gathering entities instead of a hash lookup per chunk per frame.");
		this.verifyEntityChunkLookup = bool(file, system, "render.fastEntityChunkLookup.verify", false,
			"Debug: also do vanilla's lookup for every cached one and log differences.");
		this.fastEntityPipeline = bool(file, system, "render.fastEntityPipeline", true,
			"High-performance entity pipeline override: skips empty chunk sections during entity and block entity loops while preserving exact vanilla rendering order and behavior.");
		this.verifyEntityPipeline = bool(file, system, "render.fastEntityPipeline.verify", false,
			"Debug: also run vanilla entity traversal and verify matching rendered counts and entities.");
		this.fastTerrainPipeline = bool(file, system, "render.fastTerrainPipeline", true,
			"Unified direct single-pass terrain layer rendering and chunk-level layer bitmask filtering.");
		this.verifyTerrainPipeline = bool(file, system, "render.fastTerrainPipeline.verify", false,
			"Debug: compare fast terrain layer rendered chunk counts with vanilla.");
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
		this.fastEntityLighting = bool(file, system, "render.fastEntityLighting", true,
			"Answer entity light and brightness queries without BlockPos allocations or chunk hash lookups.");
		this.verifyFastEntityLighting = bool(file, system, "render.fastEntityLighting.verify", false,
			"Debug: also run vanilla's entity light queries, use their results and log any difference.");
		this.fastEntityRendererLookup = bool(file, system, "render.fastEntityRendererLookup", true,
			"Cache entity renderer lookups to eliminate repeated map searches in entity rendering.");
		this.verifyEntityRendererLookup = bool(file, system, "render.fastEntityRendererLookup.verify", false,
			"Debug: also run vanilla's renderer map query, use its result and log any difference.");
		this.fastEntityCulling = bool(file, system, "render.fastEntityCulling", true,
			"Evaluate entity distance and frustum before entity state preparation and renderer invocation.");
		this.verifyEntityCulling = bool(file, system, "render.fastEntityCulling.verify", false,
			"Debug: also run vanilla's shouldRender check, use its result and log any difference.");
		this.fastRayTrace = bool(file, system, "render.fastRayTrace", true,
			"Trace crosshair and entity rays without intermediate Vec3d or BlockPos allocations.");
		this.verifyFastRayTrace = bool(file, system, "render.fastRayTrace.verify", false,
			"Debug: also run vanilla's ray trace, use its result and log any difference.");
		this.fastRandomDisplayTicks = bool(file, system, "render.fastRandomDisplayTicks", true,
			"Reduce client ambient tick position and lookup allocations, preserving vanilla per-tick Random lifetime.");
		this.verifyFastRandomDisplayTicks = bool(file, system, "render.fastRandomDisplayTicks.verify", false,
			"Debug: also verify fast display tick block queries against vanilla, logging differences.");
		this.fastSoundThread = bool(file, system, "render.fastSoundThread", true,
			"Wake paulscode sound threads via lock-free parking instead of Thread.sleep and thread interruption.");
		this.verifyFastSoundThread = bool(file, system, "render.fastSoundThread.verify", false,
			"Debug: verify sound thread wake timing, lifecycle and interruption handling against expected bounds.");
		this.cameraHysteresis = bool(file, system, "render.cameraHysteresis", true,
			"Reuse visibility only when all camera, frustum, storage and compiled-chunk inputs remain exactly unchanged.");
		this.verifyCameraHysteresis = bool(file, system, "render.cameraHysteresis.verify", false,
			"Debug: compare retained visibility against a fresh vanilla search and use vanilla's result.");
		this.flatOcclusion = bool(file, system, "render.flatOcclusion", true,
			"Pack section connectivity flags into a dense 36-bit bitmask per chunk section.");
		this.verifyFlatOcclusion = bool(file, system, "render.flatOcclusion.verify", false,
			"Debug: also test chunk occlusion via vanilla CompiledChunk.isVisible and log differences.");
		this.vectorFrustum = bool(file, system, "render.vectorFrustum", true,
			"Vectorized frustum testing of chunk bounding boxes against the 6 frustum planes.");
		this.verifyVectorFrustum = bool(file, system, "render.vectorFrustum.verify", false,
			"Debug: also run vanilla frustum culler and log differences.");
		this.fastWorkerAo = bool(file, system, "render.fastWorkerAo", true,
			"Cache chunk block states in thread-local flat memory during chunk compilation and ambient occlusion.");
		this.verifyWorkerAo = bool(file, system, "render.fastWorkerAo.verify", false,
			"Debug: also verify worker block state queries against vanilla chunk lookups.");
		this.fastModelTransforms = bool(file, system, "render.fastModelTransforms", true,
			"Skip zero translations in model parts; hierarchy flattening and push/pop removal are not implemented.");
		this.verifyFastModelTransforms = bool(file, system, "render.fastModelTransforms.verify", false,
			"Debug: retain vanilla translations and sample raw matrix bits before/after zero translations.");
		this.fastParticleLight = bool(file, system, "render.fastParticleLight", true,
			"Answer particles' per-frame light queries from cached chunk references instead of chunk hash lookups, without allocating neighbor positions.");
		this.verifyFastParticleLight = bool(file, system, "render.fastParticleLight.verify", false,
			"Debug: also run vanilla's particle light query, use its result and log any difference.");
		this.fastMipmaps = bool(file, system, "render.fastMipmaps", true,
			"Fast LUT-based gamma mipmap generation and parallel sprite downsampling.");
		this.verifyFastMipmaps = bool(file, system, "render.fastMipmaps.verify", false,
			"Debug: compare fast mipmap results against vanilla downsampling and log differences.");
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
		boolean modern = bool(file, system, "render.modernPipeline", false,
			"Modern OpenGL rendering pipeline (TerrainShader, VAO attribute binding, and precomputed chunk adjacency).");
		this.fastChunkNeighbours = bool(file, system, "render.fastChunkNeighbours", modern, "Experimental exact precomputed chunk storage adjacency; avoids per-edge integer division.");
		this.verifyChunkNeighbours = bool(file, system, "render.fastChunkNeighbours.verify", false, "Compare every adjacency result with the original slot math and use the original result.");
		this.terrainShader = bool(file, system, "render.terrainShader", modern, "Experimental terrain vertex shader pipeline; off pending native visual and performance qualification. Startup hooks.");
		this.terrainVaos = bool(file, system, "render.terrainVaos", this.terrainShader, "Legacy alias for render.terrainShader.");
		if (this.terrainVaos && !this.terrainShader) {
			this.terrainShader = true;
		}
		this.directBufferUploads = bool(file, system, "render.directBufferUploads", false, "Experimental OpenGL 4.5 named VBO creation/uploads; startup hooks, preserves vanilla buffer bytes and final unbind.");
		this.verifyModernGl = bool(file, system, "render.modernGl.verify", false, "Intrusive: validate terrain VAO state and read back every named VBO upload byte. Enables both GL experiments when supported.");
		this.multiDrawIndirect = bool(file, system, "render.multiDrawIndirect", false, "Experimental OpenGL 4.3 Multi-Draw Indirect submission over shared resident mesh storage.");
		this.verifyMultiDrawIndirect = bool(file, system, "render.multiDrawIndirect.verify", false, "Debug: validate indirect draw command structs, quads, and origin records against reference direct draws.");
		this.feedChunkWorkers = bool(file, system, "render.feedChunkWorkers", false,
			"Experimental: after Minecraft's per-frame chunk deadline stops queueing rebuilds, keep queueing visible dirty chunks (same order and calls) until the builder threads have about one frame of work. Uploads keep Minecraft's deadline. Off by default.");
		this.chunkTimeFloor = bool(file, system, "render.chunkTimeFloor", false,
			"Experimental: when Minecraft's per-frame chunk deadline leaves less than 1/32 of the previous frame's time (at most 4.2 ms), allow that much for uploading built chunks and queueing rebuilds. Off by default.");
		this.batchModelLists = bool(file, system, "render.batchModelLists", false,
			"Combine vanilla model-part face uploads in an alternate display list; experimental, off by default.");
		this.verifyBatchModelLists = bool(file, system, "render.batchModelLists.verify", false,
			"Debug: compare native OpenGL feedback from vanilla and batched model lists at compilation.");
		this.fastDisplaySync = bool(file, system, "render.fastDisplaySync", true,
			"High-precision frame limiter and frame pacer using LockSupport.parkNanos and Thread.onSpinWait.");
		this.verifyFastDisplaySync = bool(file, system, "render.fastDisplaySync.verify", false,
			"Debug: verify frame limiter sleep durations and pacing error bounds.");
		this.fastSwapBuffers = bool(file, system, "render.fastSwapBuffers", true,
			"Early 3D pipeline flush to overlap CPU HUD/GUI rendering with GPU draw execution.");
		this.verifyFastSwapBuffers = bool(file, system, "render.fastSwapBuffers.verify", false,
			"Debug: verify pipeline flush execution and fence status.");
		this.suppressThreadYield = bool(file, system, "render.suppressThreadYield", true,
			"Suppress unconditional client-thread yield after buffer swap to eliminate OS context-switch latency spikes.");
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
				this.fastChunkUnpack = value;
				this.fastChunkFrustum = false;
				this.fastCameraSectionFaces = false;
				this.fastEntityChunkLookup = value;
				this.fastEntityPipeline = value;
				this.fastTerrainPipeline = value;
				this.throttleGlErrorChecks = value;
				this.avoidMatrixReads = value;
				this.cacheClouds = value;
				this.fastEntityShadows = value;
				this.fastEntityLighting = value;
				this.fastEntityRendererLookup = value;
				this.fastEntityCulling = value;
				this.fastRayTrace = value;
				this.fastRandomDisplayTicks = value;
				this.fastSoundThread = value;
				this.cameraHysteresis = value;
				this.flatOcclusion = value;
				this.vectorFrustum = value;
				this.fastWorkerAo = value;
				this.fastModelTransforms = value;
				this.fastParticleLight = value;
				this.fastMipmaps = value;
				this.fastParticleRemoval = value;
				this.fastParticleVertices = value;
				this.fastParticleLightEntry = false;
				this.reuseParticleLight = false;
				this.fastParticleMovement = false;
				this.reuseParticleBoxes = false;
				this.scalarParticleEntityQuery = false;
				this.reuseShadowPositions = false;
				this.feedChunkWorkers = false;
				this.chunkTimeFloor = false;
				this.fastChunkNeighbours = false;
				this.terrainShader = false;
				this.terrainVaos = false;
				this.directBufferUploads = false;
				this.skipZeroTranslates = false;
				this.batchModelLists = false;
				this.fastDisplaySync = value;
				this.fastSwapBuffers = value;
				this.suppressThreadYield = value;
				break;
			case "render.fastChunkVisibility":
				this.fastChunkVisibility = value;
				break;
			case "render.fastChunkUnpack":
				this.fastChunkUnpack = value;
				break;
			case "render.fastChunkUnpack.verify":
				this.verifyChunkUnpack = value;
				break;
			case "render.fastChunkFrustum":
				this.fastChunkFrustum = value;
				break;
			case "render.fastCameraSectionFaces":
				this.fastCameraSectionFaces = value;
				break;
			case "render.fastEntityChunkLookup":
				this.fastEntityChunkLookup = value;
				break;
			case "render.fastEntityPipeline":
				this.fastEntityPipeline = value;
				break;
			case "render.fastTerrainPipeline":
				this.fastTerrainPipeline = value;
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
			case "render.fastEntityLighting":
				this.fastEntityLighting = value;
				break;
			case "render.fastEntityLighting.verify":
				this.verifyFastEntityLighting = value;
				break;
			case "render.fastEntityRendererLookup":
				this.fastEntityRendererLookup = value;
				break;
			case "render.fastEntityRendererLookup.verify":
				this.verifyEntityRendererLookup = value;
				break;
			case "render.fastEntityCulling":
				this.fastEntityCulling = value;
				break;
			case "render.fastEntityCulling.verify":
				this.verifyEntityCulling = value;
				break;
			case "render.fastRayTrace":
				this.fastRayTrace = value;
				break;
			case "render.fastRayTrace.verify":
				this.verifyFastRayTrace = value;
				break;
			case "render.fastRandomDisplayTicks":
				this.fastRandomDisplayTicks = value;
				break;
			case "render.fastRandomDisplayTicks.verify":
				this.verifyFastRandomDisplayTicks = value;
				break;
			case "render.fastSoundThread":
				this.fastSoundThread = value;
				break;
			case "render.fastSoundThread.verify":
				this.verifyFastSoundThread = value;
				break;
			case "render.cameraHysteresis":
				this.cameraHysteresis = value;
				break;
			case "render.cameraHysteresis.verify":
				this.verifyCameraHysteresis = value;
				break;
			case "render.flatOcclusion":
				this.flatOcclusion = value;
				break;
			case "render.flatOcclusion.verify":
				this.verifyFlatOcclusion = value;
				break;
			case "render.vectorFrustum":
				this.vectorFrustum = value;
				break;
			case "render.vectorFrustum.verify":
				this.verifyVectorFrustum = value;
				break;
			case "render.fastWorkerAo":
				this.fastWorkerAo = value;
				break;
			case "render.fastWorkerAo.verify":
				this.verifyWorkerAo = value;
				break;
			case "render.fastModelTransforms":
				this.fastModelTransforms = value;
				break;
			case "render.fastModelTransforms.verify":
				this.verifyFastModelTransforms = value;
				break;
			case "render.fastParticleLight":
				this.fastParticleLight = value;
				break;
			case "render.fastMipmaps":
				this.fastMipmaps = value;
				break;
			case "render.fastMipmaps.verify":
				this.verifyFastMipmaps = value;
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
			case "render.fastChunkNeighbours": this.fastChunkNeighbours = value; break;
			case "render.terrainShader":
			case "render.terrainVaos":
				this.terrainShader = value;
				this.terrainVaos = value;
				break;
			case "render.multiDrawIndirect":
				this.multiDrawIndirect = value;
				break;
			case "render.multiDrawIndirect.verify":
				this.verifyMultiDrawIndirect = value;
				break;
			case "render.directBufferUploads": this.directBufferUploads = value; break;
			case "render.modernPipeline":
				this.fastChunkNeighbours = value;
				this.terrainShader = value;
				this.terrainVaos = value;
				this.directBufferUploads = value;
				break;
			case "render.feedChunkWorkers":
				this.feedChunkWorkers = value;
				break;
			case "render.chunkTimeFloor":
				this.chunkTimeFloor = value;
				break;
			case "render.fastDisplaySync":
				this.fastDisplaySync = value;
				break;
			case "render.fastDisplaySync.verify":
				this.verifyFastDisplaySync = value;
				break;
			case "render.fastSwapBuffers":
				this.fastSwapBuffers = value;
				break;
			case "render.fastSwapBuffers.verify":
				this.verifyFastSwapBuffers = value;
				break;
			case "render.suppressThreadYield":
				this.suppressThreadYield = value;
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
			if (key.startsWith("quality.") || key.startsWith("advanced.")) {
				int number = Integer.parseInt(value);
				if (option.normalize(number) != number) { throw new IllegalArgumentException("Invalid quality: " + value); }
			} else if (!(key.startsWith("render.") || key.equals("profiler.hud")) || !("true".equals(value) || "false".equals(value))) {
				throw new IllegalArgumentException("Not a menu configuration option: " + key);
			}
		}
		if (changes.isEmpty()) { return; }
		SettingsFile.update(this.settingsFile, changes);
		for (Map.Entry<String,String> e : changes.entrySet()) {
			String key = e.getKey(), value = e.getValue();
			if (key.startsWith("quality.") || key.startsWith("advanced.")) { setVideoInt(key, Integer.parseInt(value)); }
			else if ("profiler.hud".equals(key)) { this.hudVisible = Boolean.parseBoolean(value); }
			else if ("profiler.graph".equals(key)) { this.graphVisible = Boolean.parseBoolean(value); }
			else if ("profiler.graphMode".equals(key)) { this.graphMode = clamp(Integer.parseInt(value), 0, 3); }
			else if ("render.fastParticleVertices".equals(key)) {
				if (Boolean.parseBoolean(value) == this.fastParticleVertices) { this.pendingRestart.remove(key); }
				else { this.pendingRestart.put(key,value); }
			} else { setToggle(key,Boolean.parseBoolean(value)); }
		}
	}

	/** Live update of a validated integer menu option. Startup-only options are recorded as pending instead. */
	private void setVideoInt(String key, int value) {
		switch (key) {
			case "quality.leaves" -> this.leavesQuality = value;
			case "quality.weather" -> this.weatherQuality = value;
			case "quality.rainSplashes" -> this.rainSplashQuality = value;
			case "quality.vignette" -> this.vignetteQuality = value;
			case "quality.stars" -> this.starsQuality = value;
			case "quality.enchantGlint" -> this.enchantGlintQuality = value;
			case "quality.droppedItems" -> this.droppedItemsQuality = value;
			case "quality.lightingStyle" -> this.smoothLightingStyle = value;
			case "quality.aoStrength" -> this.ambientOcclusionStrength = value;
			case "quality.entityDistance" -> this.entityDistance = value;
			case "quality.blockEntityDistance" -> this.blockEntityDistance = value;
			case "quality.fogStart" -> this.fogStart = value;
			case "quality.sunMoon" -> this.sunMoonQuality = value;
			case "quality.waterAnimation" -> this.waterAnimation = value;
			case "quality.lavaAnimation" -> this.lavaAnimation = value;
			case "quality.fireAnimation" -> this.fireAnimation = value;
			case "quality.portalAnimation" -> this.portalAnimation = value;
			case "quality.otherAnimations" -> this.otherAnimations = value;
			case "quality.particleLimit" -> this.particleLimit = value;
			case "quality.biomeBlend" -> this.biomeBlend = value;
			case "quality.anisotropicFiltering" -> this.anisotropicFiltering = value;
			case "quality.mipmapFiltering" -> this.mipmapFiltering = value;
			case "quality.lodBias" -> this.lodBias = value;
			case "quality.mipmapCutouts" -> this.mipmapCutouts = value;
			case "quality.fovEffects" -> this.fovEffects = value;
			case "quality.damageTilt" -> this.damageTilt = value;
			case "quality.viewBobbing" -> this.viewBobbing = value;
			case "quality.distortion" -> this.distortion = value;
			case "quality.thirdPersonDistance" -> this.thirdPersonDistance = value;
			case "quality.cameraCollision" -> this.cameraCollision = value;
			case "quality.fovTransition" -> this.fovTransition = value;
			case "quality.zoom" -> this.zoom = value;
			case "quality.cloudHeight" -> this.cloudHeightOffset = value;
			case "quality.sky" -> this.skyQuality = value;
			case "quality.starBrightness" -> this.starBrightness = value;
			case "quality.weatherOpacity" -> this.weatherOpacity = value;
			case "advanced.chunkUpdateBudget" -> this.chunkUpdateBudget = value;
			case "advanced.chunkThreads" -> {
				// The chunk dispatcher and its threads exist from startup; keep the running count truthful.
				if (value == this.chunkThreads) { this.pendingRestart.remove(key); }
				else { this.pendingRestart.put(key, Integer.toString(value)); }
			}
			default -> throw new IllegalArgumentException("Not a menu configuration option: " + key);
		}
	}

	/** Effective option values, for inclusion in profile exports. */
	public Map<String, String> describe() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("quality.leaves", Integer.toString(this.leavesQuality));
		values.put("quality.weather", Integer.toString(this.weatherQuality));
		values.put("quality.rainSplashes", Integer.toString(this.rainSplashQuality));
		values.put("quality.vignette", Integer.toString(this.vignetteQuality));
		values.put("quality.stars", Integer.toString(this.starsQuality));
		values.put("quality.enchantGlint", Integer.toString(this.enchantGlintQuality));
		values.put("quality.droppedItems", Integer.toString(this.droppedItemsQuality));
		values.put("quality.lightingStyle", Integer.toString(this.smoothLightingStyle));
		values.put("quality.aoStrength", Integer.toString(this.ambientOcclusionStrength));
		values.put("quality.cloudHeight", Integer.toString(this.cloudHeightOffset));
		values.put("quality.sky", Integer.toString(this.skyQuality));
		values.put("quality.starBrightness", Integer.toString(this.starBrightness));
		values.put("quality.weatherOpacity", Integer.toString(this.weatherOpacity));
		values.put("quality.entityDistance", Integer.toString(this.entityDistance));
		values.put("quality.blockEntityDistance", Integer.toString(this.blockEntityDistance));
		values.put("quality.fogStart", Integer.toString(this.fogStart));
		values.put("quality.sunMoon", Integer.toString(this.sunMoonQuality));
		values.put("quality.waterAnimation", Integer.toString(this.waterAnimation));
		values.put("quality.lavaAnimation", Integer.toString(this.lavaAnimation));
		values.put("quality.fireAnimation", Integer.toString(this.fireAnimation));
		values.put("quality.portalAnimation", Integer.toString(this.portalAnimation));
		values.put("quality.otherAnimations", Integer.toString(this.otherAnimations));
		values.put("quality.particleLimit", Integer.toString(this.particleLimit));
		values.put("quality.biomeBlend", Integer.toString(this.biomeBlend));
		values.put("quality.anisotropicFiltering", Integer.toString(this.anisotropicFiltering));
		values.put("quality.mipmapFiltering", Integer.toString(this.mipmapFiltering));
		values.put("quality.lodBias", Integer.toString(this.lodBias));
		values.put("quality.mipmapCutouts", Integer.toString(this.mipmapCutouts));
		values.put("quality.fovEffects", Integer.toString(this.fovEffects));
		values.put("quality.damageTilt", Integer.toString(this.damageTilt));
		values.put("quality.viewBobbing", Integer.toString(this.viewBobbing));
		values.put("quality.distortion", Integer.toString(this.distortion));
		values.put("quality.thirdPersonDistance", Integer.toString(this.thirdPersonDistance));
		values.put("quality.cameraCollision", Integer.toString(this.cameraCollision));
		values.put("quality.fovTransition", Integer.toString(this.fovTransition));
		values.put("quality.zoom", Integer.toString(this.zoom));
		values.put("advanced.chunkUpdateBudget", Integer.toString(this.chunkUpdateBudget));
		values.put("advanced.chunkThreads", Integer.toString(this.chunkThreads));
		values.put("profiler.enabled", String.valueOf(this.profilerEnabled));
		values.put("profiler.history", String.valueOf(this.profilerHistory));
		values.put("profiler.logInterval", String.valueOf(this.profilerLogInterval));
		values.put("profiler.entityDetail", String.valueOf(this.profilerEntityDetail));
		values.put("profiler.entityStages", String.valueOf(this.profilerEntityStages));
		values.put("profiler.shadowTiming", String.valueOf(this.profilerShadowTiming));
		values.put("profiler.frustumTiming", String.valueOf(this.profilerFrustumTiming));
		values.put("profiler.chunkDetail", String.valueOf(this.profilerChunkDetail));
		values.put("profiler.chunkLatency", String.valueOf(this.profilerChunkLatency));
		values.put("profiler.particleDetail", String.valueOf(this.profilerParticleDetail));
		values.put("profiler.particleLightRepeats", String.valueOf(this.profilerParticleLightRepeats));
		values.put("profiler.hud", String.valueOf(this.hudVisible));
		values.put("profiler.graph", String.valueOf(this.graphVisible));
		values.put("profiler.graphMode", String.valueOf(this.graphMode));
		values.put("render.fastChunkVisibility", String.valueOf(this.fastChunkVisibility));
		values.put("render.fastChunkVisibility.verify", String.valueOf(this.verifyChunkVisibility));
		values.put("render.fastChunkUnpack", String.valueOf(this.fastChunkUnpack));
		values.put("render.fastChunkUnpack.verify", String.valueOf(this.verifyChunkUnpack));
		values.put("render.fastChunkFrustum", String.valueOf(this.fastChunkFrustum));
		values.put("render.fastChunkFrustum.verify", String.valueOf(this.verifyChunkFrustum));
		values.put("render.fastCameraSectionFaces", String.valueOf(this.fastCameraSectionFaces));
		values.put("render.fastCameraSectionFaces.verify", String.valueOf(this.verifyCameraSectionFaces));
		values.put("render.fastEntityChunkLookup", String.valueOf(this.fastEntityChunkLookup));
		values.put("render.fastEntityChunkLookup.verify", String.valueOf(this.verifyEntityChunkLookup));
		values.put("render.fastEntityPipeline", String.valueOf(this.fastEntityPipeline));
		values.put("render.fastEntityPipeline.verify", String.valueOf(this.verifyEntityPipeline));
		values.put("render.fastTerrainPipeline", String.valueOf(this.fastTerrainPipeline));
		values.put("render.fastTerrainPipeline.verify", String.valueOf(this.verifyTerrainPipeline));
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
		values.put("render.fastEntityLighting", String.valueOf(this.fastEntityLighting));
		values.put("render.fastEntityLighting.verify", String.valueOf(this.verifyFastEntityLighting));
		values.put("render.fastEntityRendererLookup", String.valueOf(this.fastEntityRendererLookup));
		values.put("render.fastEntityRendererLookup.verify", String.valueOf(this.verifyEntityRendererLookup));
		values.put("render.fastEntityCulling", String.valueOf(this.fastEntityCulling));
		values.put("render.fastEntityCulling.verify", String.valueOf(this.verifyEntityCulling));
		values.put("render.fastRayTrace", String.valueOf(this.fastRayTrace));
		values.put("render.fastRayTrace.verify", String.valueOf(this.verifyFastRayTrace));
		values.put("render.fastRandomDisplayTicks", String.valueOf(this.fastRandomDisplayTicks));
		values.put("render.fastRandomDisplayTicks.verify", String.valueOf(this.verifyFastRandomDisplayTicks));
		values.put("render.fastSoundThread", String.valueOf(this.fastSoundThread));
		values.put("render.fastSoundThread.verify", String.valueOf(this.verifyFastSoundThread));
		values.put("render.cameraHysteresis", String.valueOf(this.cameraHysteresis));
		values.put("render.cameraHysteresis.verify", String.valueOf(this.verifyCameraHysteresis));
		values.put("render.flatOcclusion", String.valueOf(this.flatOcclusion));
		values.put("render.flatOcclusion.verify", String.valueOf(this.verifyFlatOcclusion));
		values.put("render.vectorFrustum", String.valueOf(this.vectorFrustum));
		values.put("render.vectorFrustum.verify", String.valueOf(this.verifyVectorFrustum));
		values.put("render.fastWorkerAo", String.valueOf(this.fastWorkerAo));
		values.put("render.fastWorkerAo.verify", String.valueOf(this.verifyWorkerAo));
		values.put("render.fastModelTransforms", String.valueOf(this.fastModelTransforms));
		values.put("render.fastModelTransforms.verify", String.valueOf(this.verifyFastModelTransforms));
		values.put("render.fastParticleLight", String.valueOf(this.fastParticleLight));
		values.put("render.fastParticleLight.verify", String.valueOf(this.verifyFastParticleLight));
		values.put("render.fastMipmaps", String.valueOf(this.fastMipmaps));
		values.put("render.fastMipmaps.verify", String.valueOf(this.verifyFastMipmaps));
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
		values.put("render.fastChunkNeighbours", String.valueOf(this.fastChunkNeighbours));
		values.put("render.fastChunkNeighbours.verify", String.valueOf(this.verifyChunkNeighbours));
		values.put("render.terrainShader", String.valueOf(this.terrainShader));
		values.put("render.terrainVaos", String.valueOf(this.terrainVaos));
		values.put("render.directBufferUploads", String.valueOf(this.directBufferUploads));
		values.put("render.modernGl.verify", String.valueOf(this.verifyModernGl));
		values.put("render.multiDrawIndirect", String.valueOf(this.multiDrawIndirect));
		values.put("render.multiDrawIndirect.verify", String.valueOf(this.verifyMultiDrawIndirect));
		values.put("render.feedChunkWorkers", String.valueOf(this.feedChunkWorkers));
		values.put("render.chunkTimeFloor", String.valueOf(this.chunkTimeFloor));
		values.put("render.batchModelLists", String.valueOf(this.batchModelLists));
		values.put("render.batchModelLists.verify", String.valueOf(this.verifyBatchModelLists));
		values.put("render.fastDisplaySync", String.valueOf(this.fastDisplaySync));
		values.put("render.fastDisplaySync.verify", String.valueOf(this.verifyFastDisplaySync));
		values.put("render.fastSwapBuffers", String.valueOf(this.fastSwapBuffers));
		values.put("render.fastSwapBuffers.verify", String.valueOf(this.verifyFastSwapBuffers));
		values.put("render.suppressThreadYield", String.valueOf(this.suppressThreadYield));
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

	/** Clamps, then rounds an off-grid hand-edited value to the menu's nearest step. */
	private static int step(int value, int min, int max, int step, int def) {
		int bounded = clamp(value, min, max);
		int snapped = min + Math.round((bounded - min) / (float) step) * step;
		return snapped > max ? def : snapped;
	}
}
