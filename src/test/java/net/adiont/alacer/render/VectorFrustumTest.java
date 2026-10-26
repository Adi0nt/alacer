package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.FrustumData;

class VectorFrustumTest {
	@Test
	void preservesVanillaRoundingAtCancellationBoundariesOnEveryPlane() {
		FrustumData vanilla = new FrustumData();
		VectorFrustum fast = new VectorFrustum();
		for (int selected = 0; selected < 6; selected++) {
			for (float[] plane : vanilla.frustum) { java.util.Arrays.fill(plane, 0); plane[3] = 1; }
			vanilla.frustum[selected] = new float[] {1, 1, 0x1p-55f, 0};
			assertTrue(fast.load(vanilla.frustum, 0, 0, 0));
			assertTrue(vanilla.contains(1, -1, 1, 1, -1, 1));
			assertEquals(VectorFrustum.VISIBLE, fast.test(1, -1, 1, 1, -1, 1));
		}
	}

	@Test
	void exceptionalCoordinatesDelegateInsteadOfAnswering() {
		VectorFrustum fast = new VectorFrustum();
		assertTrue(fast.load(cube(), 0, 0, 0));
		for (double x : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0x1p900}) {
			assertEquals(VectorFrustum.UNDECIDED, fast.test(x, 0, 0, x, 16, 16));
		}
	}

	private static float[][] cube() {
		return new float[][]{
			{1, 0, 0, 16}, {-1, 0, 0, 16},
			{0, 1, 0, 16}, {0, -1, 0, 16},
			{0, 0, 1, 16}, {0, 0, -1, 16}
		};
	}

	@Test
	void strictPlaneBoundaryAndTranslatedReversedBoxes() {
		VectorFrustum fast = new VectorFrustum();
		assertTrue(fast.load(cube(), 100, -200, 300));
		assertEquals(VectorFrustum.VISIBLE, fast.test(99, -201, 299, 101, -199, 301));
		assertEquals(VectorFrustum.VISIBLE, fast.test(101, -199, 301, 99, -201, 299));
		assertEquals(VectorFrustum.OUTSIDE, fast.test(116, -200, 300, 132, -184, 316));
		assertEquals(VectorFrustum.VISIBLE, fast.test(Math.nextDown(116.0), -200, 300, 132, -184, 316));
		assertEquals(VectorFrustum.OUTSIDE, fast.test(84, -200, 300, 84, -200, 300));
	}

	@Test
	void matchesVanillaContainsForRandomPlanesAndChunkBoxes() {
		Random random = new Random(1789);
		FrustumData vanilla = new FrustumData();
		VectorFrustum fast = new VectorFrustum();
		for (int view = 0; view < 500; view++) {
			for (float[] plane : vanilla.frustum) {
				for (int c = 0; c < 4; c++) {
					plane[c] = (random.nextFloat() * 2 - 1) * (c == 3 ? 512 : 1);
				}
			}
			double ox = random.nextDouble() * 1000 - 500;
			double oy = random.nextDouble() * 512 - 128;
			double oz = random.nextDouble() * 1000 - 500;
			assertTrue(fast.load(vanilla.frustum, ox, oy, oz));
			for (int box = 0; box < 500; box++) {
				double x0 = Math.floor((ox + random.nextInt(1024) - 512) / 16) * 16;
				double y0 = random.nextInt(16) * 16;
				double z0 = Math.floor((oz + random.nextInt(1024) - 512) / 16) * 16;
				double x1 = x0 + (random.nextBoolean() ? 16 : -16);
				double y1 = y0 + (random.nextBoolean() ? 16 : -16);
				double z1 = z0 + (random.nextBoolean() ? 16 : -16);
				boolean expected = vanilla.contains(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz);
				boolean actual = (fast.test(x0, y0, z0, x1, y1, z1) == VectorFrustum.VISIBLE);
				assertEquals(expected, actual);
			}
		}
	}

	@Test
	void isChunkVisibleMatchesVanillaContainsAcrossRandomViews() {
		Random random = new Random(4321);
		FrustumData vanilla = new FrustumData();
		VectorFrustum fast = new VectorFrustum();
		for (int view = 0; view < 500; view++) {
			for (float[] plane : vanilla.frustum) {
				for (int c = 0; c < 4; c++) {
					plane[c] = (random.nextFloat() * 2 - 1) * (c == 3 ? 512 : 1);
				}
			}
			double ox = random.nextDouble() * 1000 - 500;
			double oy = random.nextDouble() * 512 - 128;
			double oz = random.nextDouble() * 1000 - 500;
			assertTrue(fast.load(vanilla.frustum, ox, oy, oz));
			for (int box = 0; box < 500; box++) {
				int cx = (int) Math.floor((ox + random.nextInt(1024) - 512) / 16) * 16;
				int cy = random.nextInt(16) * 16;
				int cz = (int) Math.floor((oz + random.nextInt(1024) - 512) / 16) * 16;
				boolean expected = vanilla.contains(cx - ox, cy - oy, cz - oz, cx + 16 - ox, cy + 16 - oy, cz + 16 - oz);
				boolean actual = fast.isChunkVisible(cx, cy, cz);
				assertEquals(expected, actual);
			}
		}
	}

	@Test
	void syntheticCameraRotationsMatchVanillaPredicate() {
		VectorFrustum fast = new VectorFrustum();
		FrustumData vanilla = new FrustumData();

		double camX = 128.5;
		double camY = 64.0;
		double camZ = -256.5;

		// Sweep yaw through full 360 degrees in 15-degree steps
		// Synthetic matrices; this is not a native camera/rendering qualification.
		for (float yaw = -180.0f; yaw <= 180.0f; yaw += 15.0f) {
			for (float pitch = -85.0f; pitch <= 85.0f; pitch += 17.0f) {
				computeFrustumPlanes(pitch, yaw, 70.0f, 16.0f / 9.0f, 0.05f, 256.0f, vanilla.frustum);
				assertTrue(fast.load(vanilla.frustum, camX, camY, camZ));

				// Test chunks in a 16x16x16 grid around the camera
				for (int cx = -8; cx <= 8; cx++) {
					for (int cy = -4; cy <= 8; cy++) {
						for (int cz = -8; cz <= 8; cz++) {
							double minX = (Math.floor(camX / 16.0) + cx) * 16.0;
							double minY = Math.max(0, Math.min(240, (Math.floor(camY / 16.0) + cy) * 16.0));
							double minZ = (Math.floor(camZ / 16.0) + cz) * 16.0;
							double maxX = minX + 16.0;
							double maxY = minY + 16.0;
							double maxZ = minZ + 16.0;

							boolean expected = vanilla.contains(minX - camX, minY - camY, minZ - camZ,
								maxX - camX, maxY - camY, maxZ - camZ);
							boolean actual = (fast.test(minX, minY, minZ, maxX, maxY, maxZ) == VectorFrustum.VISIBLE);
							assertEquals(expected, actual, "Frustum decision mismatch at yaw=" + yaw + " pitch=" + pitch
								+ " chunk=(" + minX + "," + minY + "," + minZ + ")");
						}
					}
				}
			}
		}
	}

	private static void computeFrustumPlanes(float pitch, float yaw, float fov, float aspect, float zNear, float zFar, float[][] planes) {
		float fovRad = (float) Math.toRadians(fov);
		float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

		// Perspective projection matrix
		float[] proj = new float[16];
		proj[0] = f / aspect;
		proj[5] = f;
		proj[10] = (zFar + zNear) / (zNear - zFar);
		proj[11] = -1.0f;
		proj[14] = (2.0f * zFar * zNear) / (zNear - zFar);

		// Modelview matrix from pitch and yaw
		float pitchRad = (float) Math.toRadians(pitch);
		float yawRad = (float) Math.toRadians(yaw + 180.0f);
		float cosP = (float) Math.cos(pitchRad);
		float sinP = (float) Math.sin(pitchRad);
		float cosY = (float) Math.cos(yawRad);
		float sinY = (float) Math.sin(yawRad);

		float[] model = new float[16];
		// Rotation: R_pitch * R_yaw
		model[0] = cosY;
		model[1] = sinP * sinY;
		model[2] = -cosP * sinY;
		model[5] = cosP;
		model[6] = sinP;
		model[8] = sinY;
		model[9] = -sinP * cosY;
		model[10] = cosP * cosY;
		model[15] = 1.0f;

		// clip = proj * model
		float[] clip = new float[16];
		for (int r = 0; r < 4; r++) {
			for (int c = 0; c < 4; c++) {
				clip[c * 4 + r] = model[c * 4] * proj[r]
								+ model[c * 4 + 1] * proj[4 + r]
								+ model[c * 4 + 2] * proj[8 + r]
								+ model[c * 4 + 3] * proj[12 + r];
			}
		}

		// Extract 6 planes (Right, Left, Bottom, Top, Far, Near)
		// Right: row 3 - row 0
		setPlane(planes[0], clip[3] - clip[0], clip[7] - clip[4], clip[11] - clip[8], clip[15] - clip[12]);
		// Left: row 3 + row 0
		setPlane(planes[1], clip[3] + clip[0], clip[7] + clip[4], clip[11] + clip[8], clip[15] + clip[12]);
		// Bottom: row 3 + row 1
		setPlane(planes[2], clip[3] + clip[1], clip[7] + clip[5], clip[11] + clip[9], clip[15] + clip[13]);
		// Top: row 3 - row 1
		setPlane(planes[3], clip[3] - clip[1], clip[7] - clip[5], clip[11] - clip[9], clip[15] - clip[13]);
		// Far: row 3 - row 2
		setPlane(planes[4], clip[3] - clip[2], clip[7] - clip[6], clip[11] - clip[10], clip[15] - clip[14]);
		// Near: row 3 + row 2
		setPlane(planes[5], clip[3] + clip[2], clip[7] + clip[6], clip[11] + clip[10], clip[15] + clip[14]);
	}

	@Test
	void rotatedCameraSeesForwardButNotBackwardOrBeyondFarPlane() {
		FrustumData data = new FrustumData();
		for (float yaw : new float[] {-180, -90, 0, 90}) {
			computeFrustumPlanes(0, yaw, 70, 16.0f / 9.0f, 0.05f, 256, data.frustum);
			double angle = Math.toRadians(yaw);
			double x = -Math.sin(angle), z = Math.cos(angle);
			assertTrue(data.contains(x * 32, 0, z * 32, x * 32, 0, z * 32));
			assertFalse(data.contains(-x * 32, 0, -z * 32, -x * 32, 0, -z * 32));
			assertFalse(data.contains(x * 300, 0, z * 300, x * 300, 0, z * 300));
		}
	}

	private static void setPlane(float[] plane, float a, float b, float c, float d) {
		float len = (float) Math.sqrt(a * a + b * b + c * c);
		if (len != 0.0f) {
			plane[0] = a / len;
			plane[1] = b / len;
			plane[2] = c / len;
			plane[3] = d / len;
		}
	}
}
