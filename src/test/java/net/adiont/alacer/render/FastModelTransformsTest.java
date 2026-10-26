package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FastModelTransformsTest {

	@Test
	void zeroTranslateDetection() {
		assertTrue(FastModelTransforms.shouldSkipZeroTranslate(0.0f, 0.0f, 0.0f));
		assertFalse(FastModelTransforms.shouldSkipZeroTranslate(0.1f, 0.0f, 0.0f));
		assertFalse(FastModelTransforms.shouldSkipZeroTranslate(0.0f, -0.5f, 0.0f));
		assertFalse(FastModelTransforms.shouldSkipZeroTranslate(0.0f, 0.0f, 1.0f));
	}

	@Test
	void exceptionalOffsetsMustKeepTheTranslationCall() {
		for (float offset : new float[] {Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL,
			-Float.MIN_NORMAL, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
			assertFalse(FastModelTransforms.shouldSkipZeroTranslate(offset, 0, 0));
			assertFalse(FastModelTransforms.shouldSkipZeroTranslate(0, offset, 0));
			assertFalse(FastModelTransforms.shouldSkipZeroTranslate(0, 0, offset));
		}
	}

	@Test
	void mathematicalIdentityDoesNotCertifyRawBitsForArbitraryMatrices() {
		// CPU counterexamples, not a claim about a native driver's zero-translation shortcut.
		float negativeZero = -0.0F;
		assertNotEquals(Float.floatToRawIntBits(negativeZero), Float.floatToRawIntBits(negativeZero + 0.0F));
		assertTrue(Float.isNaN(Float.POSITIVE_INFINITY * 0.0F));
		assertTrue(Float.isNaN(Float.NaN * 0.0F));
		assertEquals(Float.floatToRawIntBits(Float.MIN_VALUE), Float.floatToRawIntBits(Float.MIN_VALUE + 0.0F));
	}

	@Test
	void modelPartOffsetPredicateDecisions() {
		// Simulating model part hierarchy (root body -> limb -> cuboid)
		float[][] partOffsets = {
			{0.0f, 0.0f, 0.0f},   // root: zero translation
			{2.0f, 4.0f, 0.0f},   // limb: non-zero
			{0.0f, 0.0f, 0.0f},   // sub-part: zero translation
			{0.0f, 0.0f, 0.0f}    // cuboid offset: zero translation
		};

		boolean[] expectedSkips = {true, false, true, true};
		for (int i = 0; i < partOffsets.length; i++) {
			boolean skip = FastModelTransforms.shouldSkipZeroTranslate(partOffsets[i][0], partOffsets[i][1], partOffsets[i][2]);
			org.junit.jupiter.api.Assertions.assertEquals(expectedSkips[i], skip);
		}
	}
}
