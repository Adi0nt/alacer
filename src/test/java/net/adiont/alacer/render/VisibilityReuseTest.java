package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisibilityReuseTest {
	@Test void rejectsSubThresholdMotionAndProjectionChangesWithoutAdvancingTheSavedPose() {
		VisibilityReuse reuse = new VisibilityReuse();
		Object camera = new Object(), storage = new Object();
		double[] input = new double[64];
		reuse.remember(camera, storage, input);
		assertTrue(reuse.matches(camera, storage, input));
		input[0] = 0.005;
		assertFalse(reuse.matches(camera, storage, input));
		input[0] = 0.01;
		assertFalse(reuse.matches(camera, storage, input));
		input[0] = 0;
		input[20] = Math.nextUp(0.0);
		assertFalse(reuse.matches(camera, storage, input));
	}
	@Test void invalidationIdentitySignedZeroAndNonFiniteInputsAreConservative() {
		VisibilityReuse reuse = new VisibilityReuse();
		Object camera = new Object(), storage = new Object();
		double[] input = new double[64];
		reuse.remember(camera, storage, input);
		assertFalse(reuse.matches(new Object(), storage, input));
		assertFalse(reuse.matches(camera, new Object(), input));
		input[0] = -0.0;
		assertFalse(reuse.matches(camera, storage, input));
		input[0] = Double.NaN;
		assertFalse(reuse.matches(camera, storage, input));
		input[0] = 0;
		reuse.invalidate();
		assertFalse(reuse.matches(camera, storage, input));
	}
}
