package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IndependentQualityTest {
	@Test void defaultPreservesVanillaAndOverridesAreIndependentOfGraphics() {
		for (boolean original : new boolean[] {false,true}) {
			assertEquals(original,IndependentQuality.fancy(0,original));
			assertFalse(IndependentQuality.fancy(1,original));
			assertTrue(IndependentQuality.fancy(2,original));
		}
	}
}
