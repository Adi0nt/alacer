package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.FrustumCuller;

class ChunkFrustumTest {

	private static float[][] cube() {
		return new float[][]{{1, 0, 0, 16}, {-1, 0, 0, 16}, {0, 1, 0, 16},
			{0, -1, 0, 16}, {0, 0, 1, 16}, {0, 0, -1, 16}};
	}

	@Test
	void strictPlaneBoundaryAndTranslatedReversedBoxes() {
		ChunkFrustum fast = new ChunkFrustum();
		assertTrue(fast.load(cube(), 100, -200, 300));
		assertEquals(ChunkFrustum.VISIBLE, fast.test(99, -201, 299, 101, -199, 301));
		assertEquals(ChunkFrustum.VISIBLE, fast.test(101, -199, 301, 99, -201, 299));
		assertEquals(ChunkFrustum.OUTSIDE, fast.test(116, -200, 300, 132, -184, 316));
		assertEquals(ChunkFrustum.VISIBLE, fast.test(Math.nextDown(116.0), -200, 300, 132, -184, 316));
		assertEquals(ChunkFrustum.OUTSIDE, fast.test(84, -200, 300, 84, -200, 300));
	}

	@Test
	void matchesActualVanillaContainsForRandomPlanesAndChunkBoxes() {
		Random random = new Random(1789);
		FrustumData vanilla = new FrustumData();
		ChunkFrustum fast = new ChunkFrustum();
		for (int view = 0; view < 1000; view++) {
			for (float[] plane : vanilla.frustum) {
				for (int c = 0; c < 4; c++) {
					plane[c] = (random.nextFloat() * 2 - 1) * (c == 3 ? 512 : 1);
				}
			}
			double ox = random.nextDouble() * 6e7 - 3e7;
			double oy = random.nextDouble() * 512 - 128;
			double oz = random.nextDouble() * 6e7 - 3e7;
			assertTrue(fast.load(vanilla.frustum, ox, oy, oz));
			for (int box = 0; box < 1000; box++) {
				double x0 = Math.floor((ox + random.nextInt(1024) - 512) / 16) * 16;
				double y0 = random.nextInt(16) * 16;
				double z0 = Math.floor((oz + random.nextInt(1024) - 512) / 16) * 16;
				double x1 = x0 + (random.nextBoolean() ? 16 : -16);
				double y1 = y0 + (random.nextBoolean() ? 16 : -16);
				double z1 = z0 + (random.nextBoolean() ? 16 : -16);
				assertEquals(vanilla.contains(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz),
					fast.test(x0, y0, z0, x1, y1, z1) == ChunkFrustum.VISIBLE);
			}
		}
	}

	@Test
	void matchesVanillaAcrossFiniteExponentsAndSignedZero() {
		Random random = new Random(42);
		FrustumData vanilla = new FrustumData();
		ChunkFrustum fast = new ChunkFrustum();
		for (int i = 0; i < 100_000; i++) {
			for (float[] plane : vanilla.frustum) {
				for (int c = 0; c < 4; c++) {
					float f;
					do {
						f = Float.intBitsToFloat(random.nextInt());
					} while (!Float.isFinite(f));
					plane[c] = i % 7 == c ? -0.0F : f;
				}
			}
			assertTrue(fast.load(vanilla.frustum, 0, 0, 0));
			double[] b = new double[6];
			for (int c = 0; c < 6; c++) {
				b[c] = Math.scalb(random.nextDouble() * 2 - 1, random.nextInt(1800) - 1000);
			}
			assertEquals(vanilla.contains(b[0], b[1], b[2], b[3], b[4], b[5]),
				fast.test(b[0], b[1], b[2], b[3], b[4], b[5]) == ChunkFrustum.VISIBLE);
		}
	}

	@Test
	void malformedPlanesAndUnsafeCoordinatesDeclineOptimization() {
		ChunkFrustum fast = new ChunkFrustum();
		assertFalse(fast.load(null, 0, 0, 0));
		assertFalse(fast.load(new float[5][4], 0, 0, 0));
		float[][] planes = cube();
		planes[0] = null;
		assertFalse(fast.load(planes, 0, 0, 0));
		planes[0] = new float[3];
		assertFalse(fast.load(planes, 0, 0, 0));
		for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
			planes = cube();
			planes[2][3] = value;
			assertFalse(fast.load(planes, 0, 0, 0));
		}
		assertTrue(fast.load(cube(), 0, 0, 0));
		for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE}) {
			assertEquals(ChunkFrustum.UNDECIDED, fast.test(value, 0, 0, 16, 16, 16));
			assertEquals(ChunkFrustum.UNDECIDED, fast.test(0, 0, 0, 16, 16, value));
		}
		assertTrue(fast.load(cube(), Double.NaN, 0, 0));
		assertEquals(ChunkFrustum.UNDECIDED, fast.test(0, 0, 0, 16, 16, 16));
	}

	@Test
	void customCullersAreNeverBypassed() {
		ChunkFrustum fast = new ChunkFrustum();
		assertFalse(fast.prepare(null));
		assertFalse(fast.prepare(new FrustumCuller(new FrustumData()) {}));
	}
}
