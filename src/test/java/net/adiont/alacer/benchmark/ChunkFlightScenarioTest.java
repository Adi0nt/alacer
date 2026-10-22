package net.adiont.alacer.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkFlightScenarioTest {
	@Test void warmupDoesNotPrebuildTheFlightAndFlightNeverLoops() {
		double[] p = new double[5];
		for (int phase = 0; phase < 2; phase++) {
			for (double t = 0; t <= 10; t += .25) {
				ChunkFlightScenario.pose(phase, t, -200.5, 65, 267.5, 24, p);
				assertEquals(-200.5, p[0]); assertEquals(267.5, p[2]);
			}
		}
		for (double t = 0; t <= 40; t += .25) {
			ChunkFlightScenario.pose(2, t, -200.5, 65, 267.5, 24, p);
			assertArrayEquals(new double[] {-200.5 + 20*t, 89, 267.5, -90, 20}, p);
		}
		assertEquals(3, ChunkFlightScenario.NAMES.length);
		assertEquals(40, ChunkFlightScenario.SECONDS[2]);
	}
	@Test void scenesHaveExplicitDistinctHeightsAndRejectTypos() {
		assertEquals(24, ChunkFlightScenario.height("near"));
		assertEquals(64, ChunkFlightScenario.height("high"));
		assertThrows(IllegalArgumentException.class, () -> ChunkFlightScenario.height("near2"));
	}
}
