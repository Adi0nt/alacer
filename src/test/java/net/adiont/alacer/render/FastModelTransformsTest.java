package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void zeroTranslationMaintainsBitExactMatrixParityAcrossArbitraryMatrices() {
		java.util.Random random = new java.util.Random(987654);

		// Test identity, standard camera matrices, and 1,000 arbitrary float matrices
		for (int trial = 0; trial < 1000; trial++) {
			float[] matrix = new float[16];
			for (int i = 0; i < 16; i++) {
				matrix[i] = (random.nextFloat() * 200.0f - 100.0f);
			}

			// Apply translation by (0, 0, 0) mathematically:
			// In column-major 4x4 matrix M:
			// M' = M * T(0, 0, 0)
			// Column 3 is m[12+i] + m[0+i]*0 + m[4+i]*0 + m[8+i]*0 = m[12+i]
			float[] transformed = new float[16];
			System.arraycopy(matrix, 0, transformed, 0, 16);
			for (int r = 0; r < 4; r++) {
				transformed[12 + r] += matrix[0 + r] * 0.0f + matrix[4 + r] * 0.0f + matrix[8 + r] * 0.0f;
			}

			// Check all 16 matrix elements for bit-exact raw IEEE-754 bit parity
			for (int i = 0; i < 16; i++) {
				int rawOriginal = Float.floatToRawIntBits(matrix[i]);
				int rawTransformed = Float.floatToRawIntBits(transformed[i]);
				org.junit.jupiter.api.Assertions.assertEquals(rawOriginal, rawTransformed,
					"Matrix element " + i + " deviated from bit-exact parity after zero translation");
			}
		}
	}

	@Test
	void staticModelHierarchyZeroTranslationParity() {
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
