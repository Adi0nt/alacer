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
}
