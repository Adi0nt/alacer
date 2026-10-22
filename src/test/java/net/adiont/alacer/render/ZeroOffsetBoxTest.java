package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ZeroOffsetBoxTest {
	@Test
	void safeFiniteBoundsAndBothOffsetZeroSigns() {
		assertTrue(ZeroOffsetBox.canReuse(-2, Double.MIN_VALUE, -Double.MAX_VALUE,
			-1, Double.MAX_VALUE, -Double.MIN_VALUE, -0.0, 0.0, -0.0));
		assertFalse(ZeroOffsetBox.canReuse(1, 1, 1, 2, 2, 2, 0, Double.MIN_VALUE, 0));
	}

	@Test
	void unsafeCoordinatesAndInvertedBoundsFallBack() {
		for (double bad : new double[]{0.0, -0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertFalse(ZeroOffsetBox.canReuse(bad, 1, 1, 2, 2, 2, 0, 0, 0));
			assertFalse(ZeroOffsetBox.canReuse(1, 1, 1, bad, 2, 2, 0, 0, 0));
		}
		assertFalse(ZeroOffsetBox.canReuse(2, 1, 1, 1, 2, 2, 0, 0, 0));
		assertNotEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(-0.0 + 0.0));
	}

	@Test
	void randomIeeeBoundsAcceptedByGuardSurviveRealArithmeticAndNormalization() {
		Random random = new Random(180018);
		int accepted = 0;
		for (int i = 0; i < 100_000; i++) {
			double a = Double.longBitsToDouble(random.nextLong()), b = Double.longBitsToDouble(random.nextLong());
			double min = Math.min(a, b), max = Math.max(a, b);
			for (double offset : new double[]{0.0, -0.0}) {
				if (ZeroOffsetBox.canReuse(min, min, min, max, max, max, offset, offset, offset)) {
					assertEquals(Double.doubleToRawLongBits(min), Double.doubleToRawLongBits(Math.min(min + offset, max + offset)));
					assertEquals(Double.doubleToRawLongBits(max), Double.doubleToRawLongBits(Math.max(min + offset, max + offset)));
					accepted++;
				}
			}
		}
		assertTrue(accepted > 190_000);
	}
}
