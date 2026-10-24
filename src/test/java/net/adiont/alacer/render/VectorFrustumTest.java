package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.FrustumData;

class VectorFrustumTest {

	private static float[][] cube() {
		return new float[][]{
			{1, 0, 0, 16}, {-1, 0, 0, 16},
			{0, 1, 0, 16}, {0, -1, 0, 16},
			{0, 0, 1, 16}, {0, 0, -1, 16}
		};
	}

	@Test
	void strictPlaneBoundaryAndTranslatedReversedBoxes() {
		VectorFrustum fast = new VectorFrustum();
		assertTrue(fast.load(cube(), 100, -200, 300));
		assertEquals(VectorFrustum.VISIBLE, fast.test(99, -201, 299, 101, -199, 301));
		assertEquals(VectorFrustum.VISIBLE, fast.test(101, -199, 301, 99, -201, 299));
		assertEquals(VectorFrustum.OUTSIDE, fast.test(116, -200, 300, 132, -184, 316));
		assertEquals(VectorFrustum.VISIBLE, fast.test(Math.nextDown(116.0), -200, 300, 132, -184, 316));
		assertEquals(VectorFrustum.OUTSIDE, fast.test(84, -200, 300, 84, -200, 300));
	}

	@Test
	void matchesVanillaContainsForRandomPlanesAndChunkBoxes() {
		Random random = new Random(1789);
		FrustumData vanilla = new FrustumData();
		VectorFrustum fast = new VectorFrustum();
		for (int view = 0; view < 500; view++) {
			for (float[] plane : vanilla.frustum) {
				for (int c = 0; c < 4; c++) {
					plane[c] = (random.nextFloat() * 2 - 1) * (c == 3 ? 512 : 1);
				}
			}
			double ox = random.nextDouble() * 1000 - 500;
			double oy = random.nextDouble() * 512 - 128;
			double oz = random.nextDouble() * 1000 - 500;
			assertTrue(fast.load(vanilla.frustum, ox, oy, oz));
			for (int box = 0; box < 500; box++) {
				double x0 = Math.floor((ox + random.nextInt(1024) - 512) / 16) * 16;
				double y0 = random.nextInt(16) * 16;
				double z0 = Math.floor((oz + random.nextInt(1024) - 512) / 16) * 16;
				double x1 = x0 + (random.nextBoolean() ? 16 : -16);
				double y1 = y0 + (random.nextBoolean() ? 16 : -16);
				double z1 = z0 + (random.nextBoolean() ? 16 : -16);
				boolean expected = vanilla.contains(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz);
				boolean actual = (fast.test(x0, y0, z0, x1, y1, z1) == VectorFrustum.VISIBLE);
				assertEquals(expected, actual);
			}
		}
	}
}
