package net.adiont.alacer.profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/**
 * Client-thread rolling display telemetry, collected only while a debug graph or F3 is open.
 *
 * <p>Receives per-frame timings from {@link FrameProfiler} and records rolling history
 * of frame durations broken down into the 8 canonical subsystems:
 * <ul>
 *   <li>1: World / sky</li>
 *   <li>2: Terrain</li>
 *   <li>3: Entities</li>
 *   <li>4: Particles</li>
 *   <li>5: Ticks &amp; Tasks</li>
 *   <li>6: Gui &amp; Hud</li>
 *   <li>7: Swap / sync</li>
 *   <li>8: Other / wait</li>
 * </ul>
 *
 * <p>Also calculates rolling percentiles (average FPS, 1% lows, 5% lows, 10% lows, P99 ms, worst ms),
 * client-thread allocation rate (MiB/s), and JVM collection observations (not pause attribution).
 */
public final class PerformanceTracker {

	public static final int HISTORY_SIZE = 480;

	// Parallel ring buffers for zero-allocation recording
	private static final long[] FRAME_TOTAL = new long[HISTORY_SIZE];
	private static final long[] TIME_TICK = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITIES = new long[HISTORY_SIZE];
	private static final long[] TIME_PARTICLES = new long[HISTORY_SIZE];
	private static final long[] TIME_HUD = new long[HISTORY_SIZE];
	private static final long[] TIME_SKY = new long[HISTORY_SIZE];
	private static final long[] TIME_SWAP = new long[HISTORY_SIZE];
	private static final long[] TIME_GC = new long[HISTORY_SIZE];
	private static final long[] TIME_OTHER = new long[HISTORY_SIZE];
	private static final boolean[] HAD_GC = new boolean[HISTORY_SIZE];

	// Detail ring buffers for Mode 3 (top subsystem breakdown, e.g. Terrain)
	private static final long[] TIME_TERRAIN_SETUP = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_VIS = new long[HISTORY_SIZE];
	private static final long[] TIME_CHUNK_UPLOAD = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_SOLID = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_CUTOUT_MIPPED = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_CUTOUT = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_TRANSLUCENT = new long[HISTORY_SIZE];
	private static final long[] TIME_TERRAIN_OTHER = new long[HISTORY_SIZE];
	private static final int[] CHUNKS_VISIBLE = new int[HISTORY_SIZE];
	private static final int[] CHUNKS_PENDING = new int[HISTORY_SIZE];
	private static final int[] CHUNKS_FRUSTUM = new int[HISTORY_SIZE];

	// Detail ring buffers for Mode 3 when Entities is top
	private static final long[] TIME_ENTITY_LOOP = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_BLOCK = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_DRAW = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_VIS = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_PREP = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_GLOBAL = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_OUTLINE = new long[HISTORY_SIZE];
	private static final long[] TIME_ENTITY_OTHER = new long[HISTORY_SIZE];

	private static int headIndex = 0;
	private static int historyCount = 0;
	private static volatile boolean frozen = false;

	// JVM Garbage Collection monitoring
	private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
	private static long lastGcTimeMillis = getTotalGcTimeMillis();
	private static long lastGcCount = getTotalGcCount();
	private static boolean collecting;
	private static long generation;
	private static final long[] ALLOC_BYTES = new long[HISTORY_SIZE];

	private static double allocationRateMbPerSec = -1;

	// Rolling statistics scratchpad
	private static final long[] PERCENTILE_SCRATCH = new long[HISTORY_SIZE];
	private static double averageFps = 0.0D;
	private static double onePercentLowFps = 0.0D;
	private static double fivePercentLowFps = 0.0D;
	private static double tenPercentLowFps = 0.0D;
	private static double averageFrameMillis = 0.0D;
	private static double p99FrameMillis = 0.0D;
	private static double worstFrameMillis = 0.0D;
	private static double bestFrameMillis = 0.0D;

	// Subsystem averages
	private static double avgTickMillis = 0.0D;
	private static double avgTerrainMillis = 0.0D;
	private static double avgEntitiesMillis = 0.0D;
	private static double avgParticlesMillis = 0.0D;
	private static double avgHudMillis = 0.0D;
	private static double avgSkyMillis = 0.0D;
	private static double avgSwapMillis = 0.0D;
	private static double avgGcMillis = 0.0D;
	private static double avgOtherMillis = 0.0D;

	// Terrain sub-timing averages and top metrics
	private static double avgTerrainSetupMillis = 0.0D;
	private static double avgTerrainVisMillis = 0.0D;
	private static double avgTerrainUploadMillis = 0.0D;
	private static double avgTerrainSolidMillis = 0.0D;
	private static double avgTerrainCutoutMippedMillis = 0.0D;
	private static double avgTerrainCutoutMillis = 0.0D;
	private static double avgTerrainTranslucentMillis = 0.0D;
	private static double avgTerrainOtherMillis = 0.0D;
	private static double topSubsystemWorstMillis = 0.0D;
	private static int avgVisibleChunks = 0;
	private static int avgPendingChunks = 0;
	private static int avgFrustumTests = 0;

	// Entity sub-averages
	private static double avgEntityLoopMillis = 0.0D;
	private static double avgEntityBlockMillis = 0.0D;
	private static double avgEntityDrawMillis = 0.0D;
	private static double avgEntityVisMillis = 0.0D;
	private static double avgEntityPrepMillis = 0.0D;
	private static double avgEntityGlobalMillis = 0.0D;
	private static double avgEntityOutlineMillis = 0.0D;
	private static double avgEntityOtherMillis = 0.0D;

	private static int topSubsystemIndex = 1; // default Terrain

	private static boolean statsDirty = true;

	private PerformanceTracker() {
	}

	public static void recordFrame(
			long frameNs,
			long swapNs,
			long skyNs,
			long terrainNs,
			long entitiesNs,
			long particlesNs,
			long tickNs,
			long hudNs,
			long allocBytes
	) {
		recordFrame(frameNs, swapNs, skyNs, terrainNs, entitiesNs, particlesNs, tickNs, hudNs, allocBytes,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static void recordFrame(
			long frameNs,
			long swapNs,
			long skyNs,
			long terrainNs,
			long entitiesNs,
			long particlesNs,
			long tickNs,
			long hudNs,
			long allocBytes,
			long terrainSetupNs,
			long terrainVisNs,
			long terrainUploadNs,
			long terrainSolidNs,
			long terrainCutoutMippedNs,
			long terrainCutoutNs,
			long terrainTranslucentNs,
			int visibleChunks,
			int pendingChunks
	) {
		recordFrame(frameNs, swapNs, skyNs, terrainNs, entitiesNs, particlesNs, tickNs, hudNs, allocBytes,
				terrainSetupNs, terrainVisNs, terrainUploadNs, terrainSolidNs, terrainCutoutMippedNs,
				terrainCutoutNs, terrainTranslucentNs, visibleChunks, pendingChunks,
				0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static void recordFrame(
			long frameNs,
			long swapNs,
			long skyNs,
			long terrainNs,
			long entitiesNs,
			long particlesNs,
			long tickNs,
			long hudNs,
			long allocBytes,
			long terrainSetupNs,
			long terrainVisNs,
			long terrainUploadNs,
			long terrainSolidNs,
			long terrainCutoutMippedNs,
			long terrainCutoutNs,
			long terrainTranslucentNs,
			int visibleChunks,
			int pendingChunks,
			int frustumTests,
			long entityLoopNs,
			long entityBlockNs,
			long entityGlobalNs,
			long entityDrawNs,
			long entityVisNs,
			long entityPrepNs,
			long entityOutlineNs
	) {
		if (frozen) return;
		final long currentGcTime = getTotalGcTimeMillis();
		final long currentGcCount = getTotalGcCount();
		final long gcDeltaMillis = lastGcTimeMillis < 0 ? 0 : Math.max(0L, currentGcTime - lastGcTimeMillis);
		lastGcTimeMillis = currentGcTime;
		final long gcNanos = gcDeltaMillis * 1_000_000L;
		final boolean hadGc = currentGcCount > lastGcCount || gcDeltaMillis > 0;
		lastGcCount = currentGcCount;

		// Section wall times already include any concurrent GC pause. Never add JVM
		// collection time to the stack or label unmeasured time as a diagnosed stall.
		frameNs = Math.max(1L, frameNs);
		long remaining = frameNs;
		swapNs = bounded(swapNs, remaining); remaining -= swapNs;
		skyNs = bounded(skyNs, remaining); remaining -= skyNs;
		terrainNs = bounded(terrainNs, remaining); remaining -= terrainNs;
		entitiesNs = bounded(entitiesNs, remaining); remaining -= entitiesNs;
		particlesNs = bounded(particlesNs, remaining); remaining -= particlesNs;
		tickNs = bounded(tickNs, remaining); remaining -= tickNs;
		hudNs = bounded(hudNs, remaining); remaining -= hudNs;
		final long otherNanos = remaining;

		// Disjoint, bounded sub-components for Terrain: their sum cannot exceed terrainNs
		long remTerrain = terrainNs;
		long tSetup = bounded(terrainSetupNs, remTerrain); remTerrain -= tSetup;
		long tVis = bounded(terrainVisNs, remTerrain); remTerrain -= tVis;
		long tUpload = bounded(terrainUploadNs, remTerrain); remTerrain -= tUpload;
		long tSolid = bounded(terrainSolidNs, remTerrain); remTerrain -= tSolid;
		long tCutoutMipped = bounded(terrainCutoutMippedNs, remTerrain); remTerrain -= tCutoutMipped;
		long tCutout = bounded(terrainCutoutNs, remTerrain); remTerrain -= tCutout;
		long tTranslucent = bounded(terrainTranslucentNs, remTerrain); remTerrain -= tTranslucent;
		long tOther = remTerrain;

		// Disjoint, bounded sub-components for Entities: their sum cannot exceed entitiesNs
		long remEntity = entitiesNs;
		long eDraw = bounded(entityDrawNs, remEntity); remEntity -= eDraw;
		long eVis = bounded(entityVisNs, remEntity); remEntity -= eVis;
		long eLoop = bounded(entityLoopNs, remEntity); remEntity -= eLoop;
		long eBlock = bounded(entityBlockNs, remEntity); remEntity -= eBlock;
		long ePrep = bounded(entityPrepNs, remEntity); remEntity -= ePrep;
		long eGlobal = bounded(entityGlobalNs, remEntity); remEntity -= eGlobal;
		long eOutline = bounded(entityOutlineNs, remEntity); remEntity -= eOutline;
		long eOther = remEntity;

		if (!frozen) {
			FRAME_TOTAL[headIndex] = frameNs;
			ALLOC_BYTES[headIndex] = allocBytes;
			TIME_TICK[headIndex] = tickNs;
			TIME_TERRAIN[headIndex] = terrainNs;
			TIME_ENTITIES[headIndex] = entitiesNs;
			TIME_PARTICLES[headIndex] = particlesNs;
			TIME_HUD[headIndex] = hudNs;
			TIME_SKY[headIndex] = skyNs;
			TIME_SWAP[headIndex] = swapNs;
			TIME_GC[headIndex] = gcNanos;
			TIME_OTHER[headIndex] = otherNanos;
			HAD_GC[headIndex] = hadGc;

			TIME_TERRAIN_SETUP[headIndex] = tSetup;
			TIME_TERRAIN_VIS[headIndex] = tVis;
			TIME_CHUNK_UPLOAD[headIndex] = tUpload;
			TIME_TERRAIN_SOLID[headIndex] = tSolid;
			TIME_TERRAIN_CUTOUT_MIPPED[headIndex] = tCutoutMipped;
			TIME_TERRAIN_CUTOUT[headIndex] = tCutout;
			TIME_TERRAIN_TRANSLUCENT[headIndex] = tTranslucent;
			TIME_TERRAIN_OTHER[headIndex] = tOther;
			CHUNKS_VISIBLE[headIndex] = visibleChunks;
			CHUNKS_PENDING[headIndex] = pendingChunks;
			CHUNKS_FRUSTUM[headIndex] = frustumTests;

			TIME_ENTITY_LOOP[headIndex] = eLoop;
			TIME_ENTITY_BLOCK[headIndex] = eBlock;
			TIME_ENTITY_DRAW[headIndex] = eDraw;
			TIME_ENTITY_VIS[headIndex] = eVis;
			TIME_ENTITY_PREP[headIndex] = ePrep;
			TIME_ENTITY_GLOBAL[headIndex] = eGlobal;
			TIME_ENTITY_OUTLINE[headIndex] = eOutline;
			TIME_ENTITY_OTHER[headIndex] = eOther;

			headIndex = (headIndex + 1) % HISTORY_SIZE;
			if (historyCount < HISTORY_SIZE) {
				historyCount++;
			}
			statsDirty = true;
		}
	}

	private static long bounded(long nanos, long remaining) { return Math.min(remaining, Math.max(0, nanos)); }

	private static void updateStatistics() {
		if (!statsDirty) return;
		if (historyCount == 0) {
			averageFps = onePercentLowFps = fivePercentLowFps = tenPercentLowFps = 0.0D;
			averageFrameMillis = p99FrameMillis = worstFrameMillis = bestFrameMillis = 0.0D;
			avgTickMillis = avgTerrainMillis = avgEntitiesMillis = avgParticlesMillis = 0.0D;
			avgHudMillis = avgSkyMillis = avgSwapMillis = avgGcMillis = avgOtherMillis = 0.0D;
			avgTerrainSetupMillis = avgTerrainVisMillis = avgTerrainUploadMillis = 0.0D;
			avgTerrainSolidMillis = avgTerrainCutoutMippedMillis = avgTerrainCutoutMillis = 0.0D;
			avgTerrainTranslucentMillis = avgTerrainOtherMillis = 0.0D;
			avgVisibleChunks = avgPendingChunks = 0;
			topSubsystemWorstMillis = 0.0D;
			topSubsystemIndex = 1;
			allocationRateMbPerSec = -1;
			statsDirty = false;
			return;
		}

		long sumTotal = 0L;
		long sumTick = 0L;
		long sumTerrain = 0L;
		long sumEntities = 0L;
		long sumParticles = 0L;
		long sumHud = 0L;
		long sumSky = 0L;
		long sumSwap = 0L;
		long sumGc = 0L;
		long sumOther = 0L;
		double sumAllocated = 0, allocationNanos = 0;

		long sumTerrainSetup = 0L;
		long sumTerrainVis = 0L;
		long sumTerrainUpload = 0L;
		long sumTerrainSolid = 0L;
		long sumTerrainCutoutMipped = 0L;
		long sumTerrainCutout = 0L;
		long sumTerrainTranslucent = 0L;
		long sumTerrainOther = 0L;
		long sumVisibleChunks = 0L;
		long sumPendingChunks = 0L;
		long sumFrustumTests = 0L;

		long sumEntityDraw = 0L;
		long sumEntityVis = 0L;
		long sumEntityLoop = 0L;
		long sumEntityBlock = 0L;
		long sumEntityPrep = 0L;
		long sumEntityGlobal = 0L;
		long sumEntityOutline = 0L;
		long sumEntityOther = 0L;

		for (int i = 0; i < historyCount; i++) {
			sumTotal += FRAME_TOTAL[i];
			if (ALLOC_BYTES[i] >= 0) { sumAllocated += ALLOC_BYTES[i]; allocationNanos += FRAME_TOTAL[i]; }
			sumTick += TIME_TICK[i];
			sumTerrain += TIME_TERRAIN[i];
			sumEntities += TIME_ENTITIES[i];
			sumParticles += TIME_PARTICLES[i];
			sumHud += TIME_HUD[i];
			sumSky += TIME_SKY[i];
			sumSwap += TIME_SWAP[i];
			sumGc += TIME_GC[i];
			sumOther += TIME_OTHER[i];

			sumTerrainSetup += TIME_TERRAIN_SETUP[i];
			sumTerrainVis += TIME_TERRAIN_VIS[i];
			sumTerrainUpload += TIME_CHUNK_UPLOAD[i];
			sumTerrainSolid += TIME_TERRAIN_SOLID[i];
			sumTerrainCutoutMipped += TIME_TERRAIN_CUTOUT_MIPPED[i];
			sumTerrainCutout += TIME_TERRAIN_CUTOUT[i];
			sumTerrainTranslucent += TIME_TERRAIN_TRANSLUCENT[i];
			sumTerrainOther += TIME_TERRAIN_OTHER[i];
			sumVisibleChunks += CHUNKS_VISIBLE[i];
			sumPendingChunks += CHUNKS_PENDING[i];
			sumFrustumTests += CHUNKS_FRUSTUM[i];

			sumEntityDraw += TIME_ENTITY_DRAW[i];
			sumEntityVis += TIME_ENTITY_VIS[i];
			sumEntityLoop += TIME_ENTITY_LOOP[i];
			sumEntityBlock += TIME_ENTITY_BLOCK[i];
			sumEntityPrep += TIME_ENTITY_PREP[i];
			sumEntityGlobal += TIME_ENTITY_GLOBAL[i];
			sumEntityOutline += TIME_ENTITY_OUTLINE[i];
			sumEntityOther += TIME_ENTITY_OTHER[i];
		}

		allocationRateMbPerSec = allocationNanos <= 0 ? -1 : sumAllocated * 1_000_000_000.0 / allocationNanos / 1048576.0;
		final double countD = (double) historyCount;
		averageFrameMillis = (sumTotal / countD) / 1_000_000.0D;
		averageFps = averageFrameMillis <= 0.0D ? 0.0D : 1000.0D / averageFrameMillis;

		avgTickMillis = (sumTick / countD) / 1_000_000.0D;
		avgTerrainMillis = (sumTerrain / countD) / 1_000_000.0D;
		avgEntitiesMillis = (sumEntities / countD) / 1_000_000.0D;
		avgParticlesMillis = (sumParticles / countD) / 1_000_000.0D;
		avgHudMillis = (sumHud / countD) / 1_000_000.0D;
		avgSkyMillis = (sumSky / countD) / 1_000_000.0D;
		avgSwapMillis = (sumSwap / countD) / 1_000_000.0D;
		avgGcMillis = (sumGc / countD) / 1_000_000.0D;
		avgOtherMillis = (sumOther / countD) / 1_000_000.0D;

		avgTerrainSetupMillis = (sumTerrainSetup / countD) / 1_000_000.0D;
		avgTerrainVisMillis = (sumTerrainVis / countD) / 1_000_000.0D;
		avgTerrainUploadMillis = (sumTerrainUpload / countD) / 1_000_000.0D;
		avgTerrainSolidMillis = (sumTerrainSolid / countD) / 1_000_000.0D;
		avgTerrainCutoutMippedMillis = (sumTerrainCutoutMipped / countD) / 1_000_000.0D;
		avgTerrainCutoutMillis = (sumTerrainCutout / countD) / 1_000_000.0D;
		avgTerrainTranslucentMillis = (sumTerrainTranslucent / countD) / 1_000_000.0D;
		avgTerrainOtherMillis = (sumTerrainOther / countD) / 1_000_000.0D;
		avgVisibleChunks = (int) Math.round(sumVisibleChunks / countD);
		avgPendingChunks = (int) Math.round(sumPendingChunks / countD);
		avgFrustumTests = (int) Math.round(sumFrustumTests / countD);

		avgEntityDrawMillis = (sumEntityDraw / countD) / 1_000_000.0D;
		avgEntityVisMillis = (sumEntityVis / countD) / 1_000_000.0D;
		avgEntityLoopMillis = (sumEntityLoop / countD) / 1_000_000.0D;
		avgEntityBlockMillis = (sumEntityBlock / countD) / 1_000_000.0D;
		avgEntityPrepMillis = (sumEntityPrep / countD) / 1_000_000.0D;
		avgEntityGlobalMillis = (sumEntityGlobal / countD) / 1_000_000.0D;
		avgEntityOutlineMillis = (sumEntityOutline / countD) / 1_000_000.0D;
		avgEntityOtherMillis = (sumEntityOther / countD) / 1_000_000.0D;

		// Subsystem order: 0: World/sky, 1: Terrain, 2: Entities, 3: Particles, 4: Ticks, 5: Gui, 6: Swap, 7: Other
		topSubsystemIndex = 1;
		double maxSubMs = avgTerrainMillis;
		if (avgSkyMillis > maxSubMs) { maxSubMs = avgSkyMillis; topSubsystemIndex = 0; }
		if (avgEntitiesMillis > maxSubMs) { maxSubMs = avgEntitiesMillis; topSubsystemIndex = 2; }
		if (avgParticlesMillis > maxSubMs) { maxSubMs = avgParticlesMillis; topSubsystemIndex = 3; }
		if (avgTickMillis > maxSubMs) { maxSubMs = avgTickMillis; topSubsystemIndex = 4; }
		if (avgHudMillis > maxSubMs) { maxSubMs = avgHudMillis; topSubsystemIndex = 5; }
		if (avgSwapMillis > maxSubMs) { maxSubMs = avgSwapMillis; topSubsystemIndex = 6; }
		if (avgOtherMillis > maxSubMs) { maxSubMs = avgOtherMillis; topSubsystemIndex = 7; }

		long maxSubNanos = 0L;
		for (int i = 0; i < historyCount; i++) {
			long subNanos = switch (topSubsystemIndex) {
				case 0 -> TIME_SKY[i];
				case 1 -> TIME_TERRAIN[i];
				case 2 -> TIME_ENTITIES[i];
				case 3 -> TIME_PARTICLES[i];
				case 4 -> TIME_TICK[i];
				case 5 -> TIME_HUD[i];
				case 6 -> TIME_SWAP[i];
				default -> TIME_OTHER[i];
			};
			if (subNanos > maxSubNanos) maxSubNanos = subNanos;
		}
		topSubsystemWorstMillis = maxSubNanos / 1_000_000.0D;

		System.arraycopy(FRAME_TOTAL, 0, PERCENTILE_SCRATCH, 0, historyCount);
		Arrays.sort(PERCENTILE_SCRATCH, 0, historyCount);

		bestFrameMillis = PERCENTILE_SCRATCH[0] / 1_000_000.0D;
		worstFrameMillis = PERCENTILE_SCRATCH[historyCount - 1] / 1_000_000.0D;
		final int p99Index = Math.max(0, (int) Math.ceil(historyCount * 0.99D) - 1);
		p99FrameMillis = PERCENTILE_SCRATCH[p99Index] / 1_000_000.0D;
		onePercentLowFps = tailFps(0.01);
		fivePercentLowFps = tailFps(0.05);
		tenPercentLowFps = tailFps(0.10);

		populateBreakdown();

		statsDirty = false;
	}

	private static void populateBreakdown() {
		double total = Math.max(0.001D, averageFrameMillis);
		BREAKDOWN[0].set(avgSkyMillis, (avgSkyMillis / total) * 100.0D);
		BREAKDOWN[1].set(avgTerrainMillis, (avgTerrainMillis / total) * 100.0D);
		BREAKDOWN[2].set(avgEntitiesMillis, (avgEntitiesMillis / total) * 100.0D);
		BREAKDOWN[3].set(avgParticlesMillis, (avgParticlesMillis / total) * 100.0D);
		BREAKDOWN[4].set(avgTickMillis, (avgTickMillis / total) * 100.0D);
		BREAKDOWN[5].set(avgHudMillis, (avgHudMillis / total) * 100.0D);
		BREAKDOWN[6].set(avgSwapMillis, (avgSwapMillis / total) * 100.0D);
		BREAKDOWN[7].set(avgOtherMillis, (avgOtherMillis / total) * 100.0D);
	}

	/** Reciprocal of the mean duration of the slowest fraction, not inverse percentile. */
	private static double tailFps(double fraction) {
		int count = Math.max(1, (int) Math.ceil(historyCount * fraction));
		double sum = 0;
		for (int i = historyCount - count; i < historyCount; i++) sum += PERCENTILE_SCRATCH[i];
		return count * 1_000_000_000.0 / sum;
	}

	public static long generation() { return generation; }
	public static boolean isCollecting() { return collecting; }
	public static void setCollecting(boolean value) {
		if (collecting != value) {
			if (value && !frozen) reset();
			collecting = value;
		}
	}

	public static double getAverageFps() { updateStatistics(); return averageFps; }
	public static double getOnePercentLowFps() { updateStatistics(); return onePercentLowFps; }
	public static double getFivePercentLowFps() { updateStatistics(); return fivePercentLowFps; }
	public static double getTenPercentLowFps() { updateStatistics(); return tenPercentLowFps; }
	public static double getAverageFrameMillis() { updateStatistics(); return averageFrameMillis; }
	public static double getP99FrameMillis() { updateStatistics(); return p99FrameMillis; }
	public static double getWorstFrameMillis() { updateStatistics(); return worstFrameMillis; }
	public static double getBestFrameMillis() { updateStatistics(); return bestFrameMillis; }

	public static boolean isFrozen() { return frozen; }
	public static void setFrozen(final boolean val) {
		if (frozen == val) return;
		frozen = val;
		lastGcTimeMillis = getTotalGcTimeMillis();
		lastGcCount = getTotalGcCount();
	}
	public static void toggleFrozen() { setFrozen(!frozen); }

	public static double getAvgTickMillis() { updateStatistics(); return avgTickMillis; }
	public static double getAvgTerrainMillis() { updateStatistics(); return avgTerrainMillis; }
	public static double getAvgEntitiesMillis() { updateStatistics(); return avgEntitiesMillis; }
	public static double getAvgParticlesMillis() { updateStatistics(); return avgParticlesMillis; }
	public static double getAvgHudMillis() { updateStatistics(); return avgHudMillis; }
	public static double getAvgSkyMillis() { updateStatistics(); return avgSkyMillis; }
	public static double getAvgSwapMillis() { updateStatistics(); return avgSwapMillis; }
	public static double getAvgGcMillis() { updateStatistics(); return avgGcMillis; }
	public static double getAvgOtherMillis() { updateStatistics(); return avgOtherMillis; }

	public static double getAllocationRateMbPerSec() {
		updateStatistics();
		return allocationRateMbPerSec;
	}

	public static long getTotalGcTimeMillis() {
		long total = 0;
		for (int i = 0; i < GC_BEANS.size(); i++) {
			long time = GC_BEANS.get(i).getCollectionTime();
			if (time > 0) total += time;
		}
		return total;
	}

	public static long getTotalGcCount() {
		long total = 0;
		for (int i = 0; i < GC_BEANS.size(); i++) {
			long count = GC_BEANS.get(i).getCollectionCount();
			if (count > 0) total += count;
		}
		return total;
	}

	public static long getCurrentlyUsedMemoryBytes() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	public static int getHistoryCount() {
		return historyCount;
	}

	public static int getHeadIndex() {
		return headIndex;
	}

	public static long getFrameTotal(int index) {
		return FRAME_TOTAL[index];
	}

	public static long getTimeTick(int index) {
		return TIME_TICK[index];
	}

	public static long getTimeTerrain(int index) {
		return TIME_TERRAIN[index];
	}

	public static long getTimeEntities(int index) {
		return TIME_ENTITIES[index];
	}

	public static long getTimeParticles(int index) {
		return TIME_PARTICLES[index];
	}

	public static long getTimeHud(int index) {
		return TIME_HUD[index];
	}

	public static long getTimeSky(int index) {
		return TIME_SKY[index];
	}

	public static long getTimeSwap(int index) {
		return TIME_SWAP[index];
	}

	public static long getTimeGc(int index) {
		return TIME_GC[index];
	}

	public static long getTimeOther(int index) {
		return TIME_OTHER[index];
	}

	public static boolean getHadGc(int index) {
		return HAD_GC[index];
	}

	public static long getTopDetailChannel(int channel, int barIndex) {
		if (topSubsystemIndex == 2) {
			return switch (channel) {
				case 0 -> TIME_ENTITY_DRAW[barIndex];
				case 1 -> TIME_ENTITY_VIS[barIndex];
				case 2 -> TIME_ENTITY_LOOP[barIndex];
				case 3 -> TIME_ENTITY_BLOCK[barIndex];
				case 4 -> TIME_ENTITY_PREP[barIndex];
				case 5 -> TIME_ENTITY_GLOBAL[barIndex];
				case 6 -> TIME_ENTITY_OUTLINE[barIndex];
				default -> TIME_ENTITY_OTHER[barIndex];
			};
		}
		return switch (channel) {
			case 0 -> TIME_TERRAIN_SETUP[barIndex];
			case 1 -> TIME_TERRAIN_VIS[barIndex];
			case 2 -> TIME_CHUNK_UPLOAD[barIndex];
			case 3 -> TIME_TERRAIN_SOLID[barIndex];
			case 4 -> TIME_TERRAIN_CUTOUT_MIPPED[barIndex];
			case 5 -> TIME_TERRAIN_CUTOUT[barIndex];
			case 6 -> TIME_TERRAIN_TRANSLUCENT[barIndex];
			default -> TIME_TERRAIN_OTHER[barIndex];
		};
	}

	public static int getTopSubsystemIndex() { updateStatistics(); return topSubsystemIndex; }
	public static String getTopSubsystemName() { updateStatistics(); return BREAKDOWN[topSubsystemIndex].getName(); }
	public static double getTopSubsystemMillis() { updateStatistics(); return BREAKDOWN[topSubsystemIndex].getMillis(); }
	public static double getTopSubsystemPercentage() { updateStatistics(); return BREAKDOWN[topSubsystemIndex].getPercentage(); }
	public static double getTopSubsystemWorstMillis() { updateStatistics(); return topSubsystemWorstMillis; }
	public static int getAvgVisibleChunks() { updateStatistics(); return avgVisibleChunks; }
	public static int getAvgPendingChunks() { updateStatistics(); return avgPendingChunks; }
	public static int getAvgFrustumTests() { updateStatistics(); return avgFrustumTests; }

	public static double getAvgTerrainSetupMillis() { updateStatistics(); return avgTerrainSetupMillis; }
	public static double getAvgTerrainVisMillis() { updateStatistics(); return avgTerrainVisMillis; }
	public static double getAvgTerrainUploadMillis() { updateStatistics(); return avgTerrainUploadMillis; }
	public static double getAvgTerrainSolidMillis() { updateStatistics(); return avgTerrainSolidMillis; }
	public static double getAvgTerrainCutoutMippedMillis() { updateStatistics(); return avgTerrainCutoutMippedMillis; }
	public static double getAvgTerrainCutoutMillis() { updateStatistics(); return avgTerrainCutoutMillis; }
	public static double getAvgTerrainTranslucentMillis() { updateStatistics(); return avgTerrainTranslucentMillis; }
	public static double getAvgTerrainOtherMillis() { updateStatistics(); return avgTerrainOtherMillis; }

	public static double getAvgEntityDrawMillis() { updateStatistics(); return avgEntityDrawMillis; }
	public static double getAvgEntityVisMillis() { updateStatistics(); return avgEntityVisMillis; }
	public static double getAvgEntityLoopMillis() { updateStatistics(); return avgEntityLoopMillis; }
	public static double getAvgEntityBlockMillis() { updateStatistics(); return avgEntityBlockMillis; }
	public static double getAvgEntityPrepMillis() { updateStatistics(); return avgEntityPrepMillis; }
	public static double getAvgEntityGlobalMillis() { updateStatistics(); return avgEntityGlobalMillis; }
	public static double getAvgEntityOutlineMillis() { updateStatistics(); return avgEntityOutlineMillis; }
	public static double getAvgEntityOtherMillis() { updateStatistics(); return avgEntityOtherMillis; }

	public static final class SubsystemEntry implements Comparable<SubsystemEntry> {
		private final String name;
		private final int color;
		private double millis;
		private double percentage;

		public SubsystemEntry(String name, int color) {
			this.name = name;
			this.color = color;
		}

		public String getName() { return this.name; }
		public int getColor() { return this.color; }
		public double getMillis() { return this.millis; }
		public double getPercentage() { return this.percentage; }

		public void set(double millis, double percentage) {
			this.millis = millis;
			this.percentage = percentage;
		}

		@Override
		public int compareTo(SubsystemEntry o) {
			return Double.compare(o.millis, this.millis);
		}
	}

	private static final SubsystemEntry[] BREAKDOWN = new SubsystemEntry[] {
		new SubsystemEntry("World / sky", 0xFF06B6D4),
		new SubsystemEntry("Terrain", 0xFF10B981),
		new SubsystemEntry("Entities", 0xFFF59E0B),
		new SubsystemEntry("Particles", 0xFFA855F7),
		new SubsystemEntry("Ticks & Tasks", 0xFFFB923C),
		new SubsystemEntry("Gui & Hud", 0xFFEC4899),
		new SubsystemEntry("Swap / sync", 0xFF3B82F6),
		new SubsystemEntry("Other / wait", 0xFFEF4444)
	};

	public static SubsystemEntry[] getSubsystemBreakdown() {
		updateStatistics();
		return BREAKDOWN;
	}

	private static final SubsystemEntry[] TOP_TERRAIN_BREAKDOWN = new SubsystemEntry[] {
		new SubsystemEntry("Setup & Cull", 0xFF10B981),
		new SubsystemEntry("Visibility BFS", 0xFF14B8A6),
		new SubsystemEntry("Chunk Upload", 0xFFF59E0B),
		new SubsystemEntry("Solid Layer", 0xFF3B82F6),
		new SubsystemEntry("Cutout Mipped", 0xFF84CC16),
		new SubsystemEntry("Cutout Layer", 0xFF0EA5E9),
		new SubsystemEntry("Translucent", 0xFF6366F1),
		new SubsystemEntry("Other Terrain", 0xFFA855F7)
	};

	private static final SubsystemEntry[] TOP_ENTITY_BREAKDOWN = new SubsystemEntry[] {
		new SubsystemEntry("Entity Draw", 0xFFF59E0B),
		new SubsystemEntry("Cull / Frust", 0xFF10B981),
		new SubsystemEntry("Chunk Loop", 0xFF14B8A6),
		new SubsystemEntry("Block Entities", 0xFF3B82F6),
		new SubsystemEntry("Setup & Prep", 0xFF84CC16),
		new SubsystemEntry("Global Ents", 0xFF0EA5E9),
		new SubsystemEntry("Outline Pass", 0xFF6366F1),
		new SubsystemEntry("Other Entities", 0xFFA855F7)
	};

	public static SubsystemEntry[] getTopDetailBreakdown() {
		updateStatistics();
		if (topSubsystemIndex == 2) {
			double total = Math.max(0.0001D, avgEntitiesMillis);
			TOP_ENTITY_BREAKDOWN[0].set(avgEntityDrawMillis, (avgEntityDrawMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[1].set(avgEntityVisMillis, (avgEntityVisMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[2].set(avgEntityLoopMillis, (avgEntityLoopMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[3].set(avgEntityBlockMillis, (avgEntityBlockMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[4].set(avgEntityPrepMillis, (avgEntityPrepMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[5].set(avgEntityGlobalMillis, (avgEntityGlobalMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[6].set(avgEntityOutlineMillis, (avgEntityOutlineMillis / total) * 100.0D);
			TOP_ENTITY_BREAKDOWN[7].set(avgEntityOtherMillis, (avgEntityOtherMillis / total) * 100.0D);
			return TOP_ENTITY_BREAKDOWN;
		}
		double total = Math.max(0.0001D, avgTerrainMillis);
		TOP_TERRAIN_BREAKDOWN[0].set(avgTerrainSetupMillis, (avgTerrainSetupMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[1].set(avgTerrainVisMillis, (avgTerrainVisMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[2].set(avgTerrainUploadMillis, (avgTerrainUploadMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[3].set(avgTerrainSolidMillis, (avgTerrainSolidMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[4].set(avgTerrainCutoutMippedMillis, (avgTerrainCutoutMippedMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[5].set(avgTerrainCutoutMillis, (avgTerrainCutoutMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[6].set(avgTerrainTranslucentMillis, (avgTerrainTranslucentMillis / total) * 100.0D);
		TOP_TERRAIN_BREAKDOWN[7].set(avgTerrainOtherMillis, (avgTerrainOtherMillis / total) * 100.0D);
		return TOP_TERRAIN_BREAKDOWN;
	}

	public static void reset() {
		generation++;
		headIndex = 0;
		historyCount = 0;
		statsDirty = true;
		frozen = false;
		allocationRateMbPerSec = -1;
		topSubsystemIndex = 1;
		topSubsystemWorstMillis = 0.0D;
		avgVisibleChunks = 0;
		avgPendingChunks = 0;
		avgFrustumTests = 0;
		lastGcTimeMillis = getTotalGcTimeMillis();
		lastGcCount = getTotalGcCount();
	}
}
