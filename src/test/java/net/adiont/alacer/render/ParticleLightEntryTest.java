package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;

/** The reused position's coordinates against vanilla's BlockPos(double, double, double), and the range test. */
class ParticleLightEntryTest {

	private static final double[] EDGES = {0.0, -0.0, 0.5, -0.5, 1.0, -1.0, 0.9999999999999999, -0.9999999999999999, 1e-300, -1e-300,
		Double.MIN_VALUE, -Double.MIN_VALUE, 255.17, 256.0, -0.17, 29999999.5, -30000000.0, -30000000.5, 30000000.0,
		2147483647.0, 2147483648.5, -2147483648.0, -2147483649.5, 1e300, -1e300, Double.MAX_VALUE, -Double.MAX_VALUE,
		Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

	@Test
	void reusedPositionHasVanillaCoordinates() {
		BlockPos.Mutable reused = new BlockPos.Mutable();
		for (double x : EDGES) {
			for (double y : EDGES) {
				for (double z : EDGES) {
					check(reused, x, y, z);
				}
			}
		}
		Random random = new Random(21);
		for (int i = 0; i < 1_000_000; i++) {
			double scale = i % 3 == 0 ? 1e8 : i % 3 == 1 ? 300.0 : 4.0;
			check(reused, (random.nextDouble() - 0.5) * scale, random.nextDouble() * 300.0 - 20.0 + random.nextGaussian() * 1e-9,
				(random.nextDouble() - 0.5) * scale);
			long bits = random.nextLong();
			check(reused, Double.longBitsToDouble(bits), Double.longBitsToDouble(bits >>> 7), Double.longBitsToDouble(~bits));
		}
	}

	private static void check(BlockPos.Mutable reused, double x, double y, double z) {
		BlockPos vanilla = new BlockPos(x, y, z);
		ParticleLightEntry.floored(reused, x, y, z);
		assertEquals(vanilla.getX(), reused.getX());
		assertEquals(vanilla.getY(), reused.getY());
		assertEquals(vanilla.getZ(), reused.getZ());
	}

	@Test
	void rangeIsTheWorldLimitsAndBuildHeight() {
		assertTrue(ParticleLightEntry.contains(0, 0, 0));
		assertTrue(ParticleLightEntry.contains(-30000000, 255, 29999999));
		assertTrue(ParticleLightEntry.contains(29999999, 64, -30000000));
		assertFalse(ParticleLightEntry.contains(0, -1, 0));
		assertFalse(ParticleLightEntry.contains(0, 256, 0));
		assertFalse(ParticleLightEntry.contains(30000000, 64, 0));
		assertFalse(ParticleLightEntry.contains(0, 64, 30000000));
		assertFalse(ParticleLightEntry.contains(-30000001, 64, 0));
		assertFalse(ParticleLightEntry.contains(0, 64, -30000001));
		assertFalse(ParticleLightEntry.contains(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
		assertFalse(ParticleLightEntry.contains(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
	}
}
