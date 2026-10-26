package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PerformanceTrackerTest {

	@BeforeEach
	void setUp() {
		PerformanceTracker.reset();
		PerformanceTracker.setFrozen(false);
	}

	@Test
	void initialStateIsEmpty() {
		assertEquals(0, PerformanceTracker.getHistoryCount());
		assertEquals(0.0D, PerformanceTracker.getAverageFps());
		assertEquals(0.0D, PerformanceTracker.getAverageFrameMillis());
		assertFalse(PerformanceTracker.isFrozen());
	}

	@Test
	void recordsFramesAndComputesAverages() {
		// Record 20 identical 10ms (10,000,000ns) frames
		for (int i = 0; i < 20; i++) {
			PerformanceTracker.recordFrame(
					10_000_000L, // frameNs (10ms)
					1_000_000L,  // swapNs (1ms)
					1_000_000L,  // skyNs (1ms)
					3_000_000L,  // terrainNs (3ms)
					2_000_000L,  // entitiesNs (2ms)
					1_000_000L,  // particlesNs (1ms)
					1_000_000L,  // tickNs (1ms)
					500_000L,    // hudNs (0.5ms)
					1024L        // allocBytes
			);
		}

		assertEquals(20, PerformanceTracker.getHistoryCount());
		assertEquals(10.0D, PerformanceTracker.getAverageFrameMillis(), 0.001D);
		assertEquals(100.0D, PerformanceTracker.getAverageFps(), 0.1D);
		assertEquals(100.0D, PerformanceTracker.getOnePercentLowFps(), 0.1D);

		assertEquals(1.0D, PerformanceTracker.getAvgSwapMillis(), 0.001D);
		assertEquals(1.0D, PerformanceTracker.getAvgSkyMillis(), 0.001D);
		assertEquals(3.0D, PerformanceTracker.getAvgTerrainMillis(), 0.001D);
		assertEquals(2.0D, PerformanceTracker.getAvgEntitiesMillis(), 0.001D);
		assertEquals(1.0D, PerformanceTracker.getAvgParticlesMillis(), 0.001D);
		assertEquals(1.0D, PerformanceTracker.getAvgTickMillis(), 0.001D);
		assertEquals(0.5D, PerformanceTracker.getAvgHudMillis(), 0.001D);
		// Remaining 0.5ms is unaccounted/other (GC & stalls)
		assertEquals(0.5D, PerformanceTracker.getAvgOtherMillis(), 0.001D);
	}

	@Test
	void computesSubsystemBreakdown() {
		for (int i = 0; i < 10; i++) {
			PerformanceTracker.recordFrame(
					20_000_000L, // 20ms
					2_000_000L,  // 2ms (10%)
					2_000_000L,  // 2ms (10%)
					8_000_000L,  // 8ms (40%)
					4_000_000L,  // 4ms (20%)
					1_000_000L,  // 1ms (5%)
					1_000_000L,  // 1ms (5%)
					1_000_000L,  // 1ms (5%)
					0L
			);
		}

		PerformanceTracker.SubsystemEntry[] breakdown = PerformanceTracker.getSubsystemBreakdown();
		assertEquals(8, breakdown.length);

		double totalPct = 0.0D;
		for (PerformanceTracker.SubsystemEntry entry : breakdown) {
			totalPct += entry.getPercentage();
		}
		// Sum of percentages across all 8 subsystems must equal ~100%
		assertEquals(100.0D, totalPct, 0.5D);
	}

	@Test
	void respectsFrozenState() {
		PerformanceTracker.recordFrame(10_000_000L, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(1, PerformanceTracker.getHistoryCount());

		PerformanceTracker.toggleFrozen();
		assertTrue(PerformanceTracker.isFrozen());

		// Recording while frozen should not alter history
		PerformanceTracker.recordFrame(50_000_000L, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(1, PerformanceTracker.getHistoryCount());
		assertEquals(10.0D, PerformanceTracker.getAverageFrameMillis(), 0.001D);

		PerformanceTracker.toggleFrozen();
		assertFalse(PerformanceTracker.isFrozen());

		PerformanceTracker.recordFrame(20_000_000L, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(2, PerformanceTracker.getHistoryCount());
	}

	@Test
	void resetsHistory() {
		PerformanceTracker.recordFrame(16_666_666L, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(1, PerformanceTracker.getHistoryCount());

		PerformanceTracker.reset();
		assertEquals(0, PerformanceTracker.getHistoryCount());
	}

	@Test void lowsUseSlowTailMeanRatherThanInversePercentile() {
		for (int i = 0; i < 199; i++) PerformanceTracker.recordFrame(10_000_000, 0, 0, 0, 0, 0, 0, 0, 0);
		PerformanceTracker.recordFrame(100_000_000, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(10.0, PerformanceTracker.getP99FrameMillis(), 0.0001);
		assertEquals(1000.0 / 55.0, PerformanceTracker.getOnePercentLowFps(), 0.0001);
	}

	@Test void invalidOrOverlappingSectionsCannotExceedFrameDuration() {
		PerformanceTracker.recordFrame(20_000_000, Long.MAX_VALUE, -1, 20_000_000, 20_000_000, 0, 0, 0, 0);
		double total = 0;
		for (PerformanceTracker.SubsystemEntry entry : PerformanceTracker.getSubsystemBreakdown()) {
			assertTrue(entry.getMillis() >= 0);
			total += entry.getPercentage();
		}
		assertEquals(100, total, 0.0001);
	}

	@Test void zeroAllocationIsMeasuredAndUnavailableDoesNotUseHeapGrowth() {
		PerformanceTracker.recordFrame(1_000_000_000, 0, 0, 0, 0, 0, 0, 0, 1_048_576);
		assertEquals(1, PerformanceTracker.getAllocationRateMbPerSec(), 0.0001);
		PerformanceTracker.recordFrame(1_000_000_000, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(0.5, PerformanceTracker.getAllocationRateMbPerSec(), 0.0001);
		PerformanceTracker.reset();
		PerformanceTracker.recordFrame(1_000_000_000, 0, 0, 0, 0, 0, 0, 0, -1);
		assertEquals(-1, PerformanceTracker.getAllocationRateMbPerSec());
	}

	@Test void ringWrapEvictsOldSpikesAndResetClearsFrozenStatistics() {
		PerformanceTracker.recordFrame(100_000_000, 0, 0, 0, 0, 0, 0, 0, 0);
		for (int i = 0; i < PerformanceTracker.HISTORY_SIZE; i++)
			PerformanceTracker.recordFrame(10_000_000, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(PerformanceTracker.HISTORY_SIZE, PerformanceTracker.getHistoryCount());
		assertEquals(10, PerformanceTracker.getWorstFrameMillis(), 0.0001);
		PerformanceTracker.setFrozen(true);
		PerformanceTracker.reset();
		assertFalse(PerformanceTracker.isFrozen());
		assertEquals(0, PerformanceTracker.getAverageFrameMillis());
		assertEquals(-1, PerformanceTracker.getAllocationRateMbPerSec());
	}

	@Test void frozenSnapshotSurvivesTemporarilyHidingTheGraph() {
		PerformanceTracker.setCollecting(true);
		try {
			PerformanceTracker.recordFrame(20_000_000, 0, 0, 0, 0, 0, 0, 0, 1024);
			PerformanceTracker.setFrozen(true);
			PerformanceTracker.setCollecting(false);
			PerformanceTracker.setCollecting(true);
			assertTrue(PerformanceTracker.isFrozen());
			assertEquals(20, PerformanceTracker.getAverageFrameMillis());
		} finally {
			PerformanceTracker.setCollecting(false);
			PerformanceTracker.reset();
		}
	}

	@Test void tracksTopSubsystemAndDetailBreakdown() {
		for (int i = 0; i < 10; i++) {
			PerformanceTracker.recordFrame(
					10_000_000L, // frame: 10ms
					1_000_000L,  // swap: 1ms
					500_000L,    // sky: 0.5ms
					5_000_000L,  // terrain: 5ms (TOP)
					1_500_000L,  // entities: 1.5ms
					500_000L,    // particles: 0.5ms
					500_000L,    // ticks: 0.5ms
					500_000L,    // hud: 0.5ms
					0L,
					500_000L,    // terrain setup: 0.5ms
					1_000_000L,  // terrain vis: 1.0ms
					500_000L,    // terrain upload: 0.5ms
					2_000_000L,  // terrain solid: 2.0ms
					400_000L,    // terrain cutout mipped: 0.4ms
					300_000L,    // terrain cutout: 0.3ms
					200_000L,    // terrain translucent: 0.2ms
					850,         // visible chunks
					4            // pending chunks
			);
		}

		assertEquals(1, PerformanceTracker.getTopSubsystemIndex());
		assertEquals("Terrain", PerformanceTracker.getTopSubsystemName());
		assertEquals(5.0D, PerformanceTracker.getTopSubsystemMillis(), 0.001D);
		assertEquals(50.0D, PerformanceTracker.getTopSubsystemPercentage(), 0.5D);
		assertEquals(5.0D, PerformanceTracker.getTopSubsystemWorstMillis(), 0.001D);
		assertEquals(850, PerformanceTracker.getAvgVisibleChunks());
		assertEquals(4, PerformanceTracker.getAvgPendingChunks());

		assertEquals(0.5D, PerformanceTracker.getAvgTerrainSetupMillis(), 0.001D);
		assertEquals(1.0D, PerformanceTracker.getAvgTerrainVisMillis(), 0.001D);
		assertEquals(0.5D, PerformanceTracker.getAvgTerrainUploadMillis(), 0.001D);
		assertEquals(2.0D, PerformanceTracker.getAvgTerrainSolidMillis(), 0.001D);
		assertEquals(0.4D, PerformanceTracker.getAvgTerrainCutoutMippedMillis(), 0.001D);
		assertEquals(0.3D, PerformanceTracker.getAvgTerrainCutoutMillis(), 0.001D);
		assertEquals(0.2D, PerformanceTracker.getAvgTerrainTranslucentMillis(), 0.001D);
		assertEquals(0.1D, PerformanceTracker.getAvgTerrainOtherMillis(), 0.001D);

		PerformanceTracker.SubsystemEntry[] detail = PerformanceTracker.getTopDetailBreakdown();
		assertEquals(8, detail.length);
		double sumPct = 0;
		for (PerformanceTracker.SubsystemEntry e : detail) {
			sumPct += e.getPercentage();
		}
		assertEquals(100.0D, sumPct, 0.5D);

		// Test channel getters
		assertEquals(500_000L, PerformanceTracker.getTopDetailChannel(0, 0));
		assertEquals(1_000_000L, PerformanceTracker.getTopDetailChannel(1, 0));
		assertEquals(500_000L, PerformanceTracker.getTopDetailChannel(2, 0));
		assertEquals(2_000_000L, PerformanceTracker.getTopDetailChannel(3, 0));
	}

	@Test void boundsTerrainDetailsToTotalTerrainTime() {
		// Pass enormous detail times that exceed terrain
		PerformanceTracker.recordFrame(
				10_000_000L, 0, 0,
				4_000_000L, // terrain: 4ms
				0, 0, 0, 0, 0,
				10_000_000L, 10_000_000L, 10_000_000L, 10_000_000L,
				0, 0, 0, 0, 0
		);

		double sum = PerformanceTracker.getAvgTerrainSetupMillis()
				+ PerformanceTracker.getAvgTerrainVisMillis()
				+ PerformanceTracker.getAvgTerrainUploadMillis()
				+ PerformanceTracker.getAvgTerrainSolidMillis()
				+ PerformanceTracker.getAvgTerrainCutoutMippedMillis()
				+ PerformanceTracker.getAvgTerrainCutoutMillis()
				+ PerformanceTracker.getAvgTerrainTranslucentMillis()
				+ PerformanceTracker.getAvgTerrainOtherMillis();
		assertEquals(4.0D, sum, 0.001D);
	}

	@Test void tracksFrustumTestsAndEntityTopDetailBreakdown() {
		for (int i = 0; i < 10; i++) {
			PerformanceTracker.recordFrame(
					10_000_000L, // frame: 10ms
					1_000_000L,  // swap: 1ms
					500_000L,    // sky: 0.5ms
					2_000_000L,  // terrain: 2ms
					5_000_000L,  // entities: 5ms (TOP!)
					500_000L,    // particles: 0.5ms
					500_000L,    // ticks: 0.5ms
					500_000L,    // hud: 0.5ms
					0L,
					200_000L, 500_000L, 100_000L, 800_000L, 200_000L, 100_000L, 100_000L, // terrain details
					2400, 10, 4800, // vis, pend, frustum tests
					500_000L,  // entity loop overhead: 0.5ms
					1_000_000L, // block entities: 1.0ms
					200_000L,  // global entities: 0.2ms
					2_000_000L, // entity draw: 2.0ms
					800_000L,  // entity vis / cull: 0.8ms
					300_000L,  // entity prep: 0.3ms
					100_000L   // entity outline: 0.1ms
			);
		}

		assertEquals(2, PerformanceTracker.getTopSubsystemIndex());
		assertEquals("Entities", PerformanceTracker.getTopSubsystemName());
		assertEquals(5.0D, PerformanceTracker.getTopSubsystemMillis(), 0.001D);
		assertEquals(50.0D, PerformanceTracker.getTopSubsystemPercentage(), 0.5D);
		assertEquals(4800, PerformanceTracker.getAvgFrustumTests());

		assertEquals(2.0D, PerformanceTracker.getAvgEntityDrawMillis(), 0.001D);
		assertEquals(0.8D, PerformanceTracker.getAvgEntityVisMillis(), 0.001D);
		assertEquals(0.5D, PerformanceTracker.getAvgEntityLoopMillis(), 0.001D);
		assertEquals(1.0D, PerformanceTracker.getAvgEntityBlockMillis(), 0.001D);
		assertEquals(0.3D, PerformanceTracker.getAvgEntityPrepMillis(), 0.001D);
		assertEquals(0.2D, PerformanceTracker.getAvgEntityGlobalMillis(), 0.001D);
		assertEquals(0.1D, PerformanceTracker.getAvgEntityOutlineMillis(), 0.001D);
		assertEquals(0.1D, PerformanceTracker.getAvgEntityOtherMillis(), 0.001D);

		PerformanceTracker.SubsystemEntry[] detail = PerformanceTracker.getTopDetailBreakdown();
		assertEquals(8, detail.length);
		assertEquals("Entity Draw", detail[0].getName());
		assertEquals("Cull / Frust", detail[1].getName());
		assertEquals("Chunk Loop", detail[2].getName());
		assertEquals("Block Entities", detail[3].getName());

		double sumPct = 0;
		for (PerformanceTracker.SubsystemEntry e : detail) {
			sumPct += e.getPercentage();
		}
		assertEquals(100.0D, sumPct, 0.5D);

		// Test channel getters when Entities is top
		assertEquals(2_000_000L, PerformanceTracker.getTopDetailChannel(0, 0));
		assertEquals(800_000L, PerformanceTracker.getTopDetailChannel(1, 0));
		assertEquals(500_000L, PerformanceTracker.getTopDetailChannel(2, 0));
		assertEquals(1_000_000L, PerformanceTracker.getTopDetailChannel(3, 0));
	}
}
