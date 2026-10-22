package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SmoothLightingTest {
	@Test void vanillaAtFullStrengthRetainsEveryInputBit() {
		float[] factors = {0.2f, 0.4f, 0.8f, 1f, -0f, Float.intBitsToFloat(0x7fc01234), -2f, Float.POSITIVE_INFINITY};
		int[] bits = new int[factors.length];
		for (int i = 0; i < factors.length; i++) { bits[i] = Float.floatToRawIntBits(factors[i]); }
		SmoothLighting.apply(factors, 0, 100);
		for (int i = 0; i < factors.length; i++) { assertEquals(bits[i], Float.floatToRawIntBits(factors[i])); }
	}
	@Test void softLiftsMidtonesWithoutMovingEndpoints() {
		float[] factors = {0f, 0.2f, 0.6f, 1f};
		SmoothLighting.apply(factors, 1, 100);
		assertArrayEquals(new float[]{0f, 0.28f, 0.72f, 1f}, factors, 0.000001f);
	}
	@Test void zeroStrengthRemovesOnlyTheAoFactorsAndIntermediateStrengthBlends() {
		for (int style = 0; style <= 1; style++) {
			float[] zero = {0.2f, 0.4f, 0.7f, 1f};
			SmoothLighting.apply(zero, style, 0);
			assertArrayEquals(new float[]{1f, 1f, 1f, 1f}, zero);
		}
		float[] half = {0.2f, 0.4f, 0.7f, 1f};
		SmoothLighting.apply(half, 0, 50);
		assertArrayEquals(new float[]{0.6f, 0.7f, 0.85f, 1f}, half, 0.000001f);
	}
	@Test void shadingStaysOrderedBoundedAndNeverDarkensTheVanillaFactor() {
		for (int style = 0; style <= 1; style++) {
			for (int strength = 0; strength <= 100; strength += 5) {
				float previous = -1f;
				for (int n = 0; n <= 1000; n++) {
					float original = n/1000f;
					float[] factor = {original};
					SmoothLighting.apply(factor, style, strength);
					assertTrue(factor[0] >= previous && factor[0] >= original && factor[0] <= 1f);
					previous = factor[0];
				}
			}
		}
	}
	@Test void customOutOfRangeAndNonfiniteValuesAreRetained() {
		float[] factors = {-1f, 2f, Float.NEGATIVE_INFINITY, Float.intBitsToFloat(0x7fc01234)};
		float[] original = factors.clone();
		SmoothLighting.apply(factors, 1, 50);
		for (int i = 0; i < factors.length; i++) {
			assertEquals(Float.floatToRawIntBits(original[i]), Float.floatToRawIntBits(factors[i]));
		}
	}
}
