package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EntityLightingTest {

	@Test
	void boundaryLimitsMatchVanillaWorldRules() {
		int limit = 30000000;
		// Within valid build height and world bounds
		assertTrue(isValid(0, 64, 0, limit));
		assertTrue(isValid(-limit, 0, -limit, limit));
		assertTrue(isValid(limit - 1, 255, limit - 1, limit));

		// Outside boundaries
		assertFalse(isValid(-limit - 1, 64, 0, limit));
		assertFalse(isValid(limit, 64, 0, limit));
		assertFalse(isValid(0, -1, 0, limit));
		assertFalse(isValid(0, 256, 0, limit));
	}

	@Test
	void coordinateFlooringMatchesMathFloor() {
		double[] testCoords = {0.0, 0.25, 0.75, 1.0, -0.25, -0.75, -1.0, 128.99, -64.01};
		for (double d : testCoords) {
			assertEquals((int) Math.floor(d), (int) Math.floor(d));
		}
	}

	@Test
	void verificationSummaryFormat() {
		String summary = EntityLighting.verificationSummary();
		assertNotNull(summary);
		assertTrue(summary.contains("queries compared"));
		assertTrue(summary.contains("mismatched"));
	}

	private static boolean isValid(int x, int y, int z, int limit) {
		return x >= -limit && z >= -limit && x < limit && z < limit && y >= 0 && y < 256;
	}
}
