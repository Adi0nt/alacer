package net.adiont.alacer.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkEditScenarioTest {
	@Test
	void everyCellIsTheCentreColumnOfItsOwnRenderChunkAhead() {
		Set<Long> chunks = new HashSet<>();
		for (int cell = 0; cell < ChunkEditScenario.CELLS; cell++) {
			int[] c = ChunkEditScenario.column(cell, -200.5, 267.5);
			assertEquals(8, Math.floorMod(c[0], 16));
			assertEquals(8, Math.floorMod(c[1], 16));
			assertTrue(c[0] > -200, "east of the camera, in the view direction");
			assertTrue(chunks.add(((long) Math.floorDiv(c[0], 16) << 32) ^ Math.floorDiv(c[1], 16)));
		}
		assertEquals(16, chunks.size());
	}

	@Test
	void burstsFollowPhaseTimeAndAlternatePlacingAndRestoring() {
		assertEquals(0, ChunkEditScenario.burstsDue(0.99));
		assertEquals(1, ChunkEditScenario.burstsDue(1.0));
		assertEquals(2, ChunkEditScenario.burstsDue(2.5));
		assertEquals(ChunkEditScenario.BURSTS, ChunkEditScenario.burstsDue(1000));
		assertTrue(ChunkEditScenario.FIRST_BURST + (ChunkEditScenario.BURSTS - 1) * ChunkEditScenario.BURST_INTERVAL
			< ChunkEditScenario.SECONDS[ChunkEditScenario.EDIT_PHASE] - 1, "the last burst has time to finish");
		assertTrue(ChunkEditScenario.places(0));
		assertFalse(ChunkEditScenario.places(1));
		assertEquals(0, ChunkEditScenario.BURSTS % 2, "the world ends as it started");
	}

	@Test
	void stillAndEditHoldOnePoseAndWarmupOnlyTurns() {
		double[] p = new double[5];
		for (double t = 0; t <= 30; t += 0.5) {
			for (int phase = 0; phase < 3; phase++) {
				ChunkEditScenario.pose(phase, t, -200.5, 65, 267.5, p);
				assertEquals(-200.5, p[0]);
				assertEquals(89, p[1]);
				assertEquals(267.5, p[2]);
				assertEquals(20, p[4]);
				if (phase > 0) { assertEquals(-90, p[3]); }
			}
		}
	}
}
