package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AtmosphereTest {
	@Test void defaultsPreserveEveryFloatBitIncludingExtensionValues() {
		for (float value : new float[] {0f, -0f, .75f, 128f, -4f, Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7fc01234)}) {
			int bits = Float.floatToRawIntBits(value);
			assertEquals(bits, Float.floatToRawIntBits(Atmosphere.fogStart(value, 75)));
			assertEquals(bits, Float.floatToRawIntBits(Atmosphere.cloudHeight(value, 0)));
			assertEquals(bits, Float.floatToRawIntBits(Atmosphere.brightness(value, 100)));
		}
	}

	@Test void fogAndCloudAdjustmentsStayWithinTheirSupportedRanges() {
		assertEquals(.25f, Atmosphere.fogStart(.75f, 25));
		assertEquals(0f, Atmosphere.fogStart(.75f, -100));
		assertEquals(.95f, Atmosphere.fogStart(.75f, 200));
		assertEquals(192f, Atmosphere.cloudHeight(128f, 64));
		assertEquals(64f, Atmosphere.cloudHeight(128f, -200));
		assertEquals(256f, Atmosphere.cloudHeight(128f, 200));
		// The cached and original geometry must calculate the same layer-relative height.
		float height = 128f, camera = 138.25f;
		assertEquals(Atmosphere.cloudHeight(height, 32) - camera + .33f, (height + 32) - camera + .33f);
	}

	@Test void brightnessChangesIntensityWithoutCreatingDaytimeStars() {
		assertEquals(0f, Atmosphere.brightness(0f, 200));
		assertEquals(.2f, Atmosphere.brightness(.4f, 50));
		assertEquals(.8f, Atmosphere.brightness(.4f, 200));
		assertEquals(1f, Atmosphere.brightness(.8f, 200));
		assertEquals(0f, Atmosphere.brightness(.8f, 0));
		assertEquals(-2f, Atmosphere.brightness(-2f, 50));
	}
}
