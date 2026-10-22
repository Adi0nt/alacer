package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;

import org.junit.jupiter.api.Test;

class MatrixTrackerTest {

	private static final float EPSILON = 1.0e-6F;

	private static float[] get(MatrixTracker tracker, int mode) {
		float[] out = new float[16];
		tracker.get(mode, out);
		return out;
	}

	private static float[] identity() {
		return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	@Test
	void unknownUntilLoadedWithIdentity() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.translate(1, 2, 3);
		assertFalse(tracker.isKnown(MatrixTracker.MODELVIEW));
		tracker.loadIdentity();
		assertTrue(tracker.isKnown(MatrixTracker.MODELVIEW));
		assertFalse(tracker.isKnown(MatrixTracker.PROJECTION));
		assertArrayEquals(identity(), get(tracker, MatrixTracker.MODELVIEW));
	}

	@Test
	void operationsMultiplyOnTheRightLikeGl() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		tracker.translate(1, 2, 3);
		tracker.scale(2, 2, 2);
		// Point (1, 0, 0) is scaled first, then translated: (3, 2, 3).
		float[] expected = {2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 1, 2, 3, 1};
		assertArrayEquals(expected, get(tracker, MatrixTracker.MODELVIEW), EPSILON);

		tracker.translate(1, 0, 0);
		expected[12] = 3;
		assertArrayEquals(expected, get(tracker, MatrixTracker.MODELVIEW), EPSILON);
	}

	@Test
	void rotationFollowsTheGlSpecification() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		// Not normalized on purpose: GL normalizes the axis.
		tracker.rotate(90, 0, 2, 0);
		// x -> -z and z -> x.
		float[] expected = {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1};
		assertArrayEquals(expected, get(tracker, MatrixTracker.MODELVIEW), EPSILON);

		tracker.loadIdentity();
		tracker.rotate(90, 0, 0, 1);
		// x -> y.
		float[] aroundZ = {0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		assertArrayEquals(aroundZ, get(tracker, MatrixTracker.MODELVIEW), EPSILON);
	}

	@Test
	void perspectiveMatchesGluPerspective() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.matrixMode(MatrixTracker.PROJECTION);
		tracker.loadIdentity();
		tracker.perspective(90, 2, 1, 3);
		// cot(45 degrees) = 1.
		float[] expected = {0.5F, 0, 0, 0, 0, 1, 0, 0, 0, 0, -2, -1, 0, 0, -3, 0};
		assertArrayEquals(expected, get(tracker, MatrixTracker.PROJECTION), EPSILON);
		assertFalse(tracker.isKnown(MatrixTracker.MODELVIEW));
	}

	@Test
	void orthoAndMultiply() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		tracker.ortho(0, 2, 2, 0, 1, 3);
		float[] expected = {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, -1, 0, -1, 1, -2, 1};
		assertArrayEquals(expected, get(tracker, MatrixTracker.MODELVIEW), EPSILON);

		tracker.loadIdentity();
		FloatBuffer buffer = FloatBuffer.allocate(20);
		buffer.position(4);
		buffer.put(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 5, 6, 7, 1});
		buffer.position(4);
		tracker.multiply(buffer);
		float[] translated = identity();
		translated[12] = 5;
		translated[13] = 6;
		translated[14] = 7;
		assertArrayEquals(translated, get(tracker, MatrixTracker.MODELVIEW), EPSILON);
	}

	@Test
	void stackRestoresAndForgetsLevelsItNeverSaw() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		tracker.push();
		tracker.translate(1, 0, 0);
		tracker.pop();
		assertArrayEquals(identity(), get(tracker, MatrixTracker.MODELVIEW));
		tracker.pop();
		assertFalse(tracker.isKnown(MatrixTracker.MODELVIEW));

		// Too deep to track: unknown until popped back down.
		tracker.reset();
		tracker.loadIdentity();
		for (int i = 0; i < 40; i++) {
			tracker.push();
		}
		assertFalse(tracker.isKnown(MatrixTracker.MODELVIEW));
		for (int i = 0; i < 40; i++) {
			tracker.pop();
		}
		assertTrue(tracker.isKnown(MatrixTracker.MODELVIEW));
	}

	@Test
	void otherMatrixModesAreIgnored() {
		MatrixTracker tracker = new MatrixTracker();
		tracker.loadIdentity();
		tracker.matrixMode(0x1702);
		tracker.loadIdentity();
		tracker.translate(4, 4, 4);
		tracker.matrixMode(MatrixTracker.MODELVIEW);
		assertArrayEquals(identity(), get(tracker, MatrixTracker.MODELVIEW));
	}
}
