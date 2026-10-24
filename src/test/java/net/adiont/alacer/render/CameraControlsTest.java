package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CameraControlsTest {
	private static void same(float vanilla, float actual) {
		assertEquals(Float.floatToRawIntBits(vanilla), Float.floatToRawIntBits(actual), vanilla + " vs " + actual);
	}

	@Test
	void everyDefaultReturnsVanillasFloatBitForBit() {
		Random random = new Random(42);
		for (int i = 0; i < 10_000; i++) {
			float f = Float.intBitsToFloat(random.nextInt());
			if (Float.isNaN(f)) { continue; }
			same(f, CameraEffects.bobbing(f, 100));
			same(f, CameraEffects.distortion(f, 100));
			same(f, CameraEffects.thirdPersonDistance(f, CameraEffects.VANILLA_DISTANCE));
			same(f, CameraEffects.fovTransition(f, CameraEffects.VANILLA_FOV_TRANSITION));
			same(f, CameraEffects.zoom(f, false, 2));
		}
		same(-0.0f, CameraEffects.bobbing(-0.0f, 100));
	}

	@Test
	void bobbingScalesEveryOffsetAndAngleLinearly() {
		assertEquals(0f, CameraEffects.bobbing(0.37f, 0));
		assertEquals(0.185f, CameraEffects.bobbing(0.37f, 50), 1e-7f);
		assertEquals(-2.5f, CameraEffects.bobbing(-5f, 50), 0f);
	}

	@Test
	void distortionInterpolatesTheStretchTowardsNone() {
		assertEquals(1f, CameraEffects.distortion(1.8f, 0), 0f);
		assertEquals(1.4f, CameraEffects.distortion(1.8f, 50), 1e-6f);
		assertEquals(0.9f, CameraEffects.distortion(0.8f, 50), 1e-6f);
	}

	@Test
	void thirdPersonDistanceIsExactForWholeBlocks() {
		for (int blocks = CameraEffects.MIN_DISTANCE; blocks <= CameraEffects.MAX_DISTANCE; blocks++) {
			assertEquals((float) blocks, CameraEffects.thirdPersonDistance(4.0f, blocks), 0f, "vanilla's camera fields hold 4.0");
		}
	}

	@Test
	void fovTransitionChoicesAndZoomFactors() {
		assertEquals(1f, CameraEffects.fovTransition(0.5f, 0));
		assertEquals(0.25f, CameraEffects.fovTransition(0.5f, 2));
		assertEquals(0.125f, CameraEffects.fovTransition(0.5f, 3));
		assertEquals(0.125f, CameraEffects.fovTransition(0.5f, 99), "clamped");
		assertEquals(35f, CameraEffects.zoom(70f, true, 0), 0f);
		assertEquals(17.5f, CameraEffects.zoom(70f, true, 2), 0f);
		assertEquals(8.75f, CameraEffects.zoom(70f, true, 4), 0f);
		assertEquals(70f, CameraEffects.zoom(70f, false, 4), 0f);
		assertEquals(4, CameraEffects.zoomFactor(2));
	}
}
