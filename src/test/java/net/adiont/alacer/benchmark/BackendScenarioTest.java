package net.adiont.alacer.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BackendScenarioTest {
	@Test void backendSelectionRejectsMissingOrMistypedValues() {
		assertNull(BackendScenario.requestedVbo(null, false));
		assertEquals(Boolean.TRUE, BackendScenario.requestedVbo(" TRUE ", true));
		assertEquals(Boolean.FALSE, BackendScenario.requestedVbo("false", true));
		assertThrows(IllegalArgumentException.class, () -> BackendScenario.requestedVbo(null, true));
		assertThrows(IllegalArgumentException.class, () -> BackendScenario.requestedVbo("flase", false));
	}
	@Test void orbitLooksAtSceneAndReturnsWithoutProgressingAlongFlight() {
		double[] p = new double[5];
		for (double t = 0; t <= 16; t += .125) {
			BackendScenario.pose(BackendScenario.View.SORT, t, 100, 65, 200, p);
			assertEquals(144, Math.pow(p[0] - 100, 2) + Math.pow(p[2] - 216, 2), 1e-9);
			double yaw = Math.toRadians(p[3]);
			assertEquals((100 - p[0]) / 12, -Math.sin(yaw), 1e-9);
			assertEquals((216 - p[2]) / 12, Math.cos(yaw), 1e-9);
		}
	}
	@Test void singleFlightRetainsExistingDistanceSpeedAndCamera() {
		double[] p = new double[5];
		BackendScenario.pose(BackendScenario.View.FLY, 20, -200.5, 65, 267.5, p);
		assertArrayEquals(new double[] {199.5, 89, 267.5, -90, 20}, p);
		int flights = 0;
		for (BackendScenario.View view : BackendScenario.VIEWS) { if (view == BackendScenario.View.FLY) { flights++; } }
		assertEquals(1, flights);
	}
}
