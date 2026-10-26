package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GpuComputeCullingTest {

	@Test
	void boxPackingMatchesGpuShaderLayout() {
		FloatBuffer buffer = ByteBuffer.allocateDirect(GpuComputeCulling.BOX_STRIDE_BYTES * 2)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();

		GpuComputeCulling.packBox(buffer, 10.0f, 20.0f, 30.0f, 26.0f, 36.0f, 46.0f);
		buffer.flip();

		// vec4 minCorner: (10, 20, 30, 0)
		assertEquals(10.0f, buffer.get(0));
		assertEquals(20.0f, buffer.get(1));
		assertEquals(30.0f, buffer.get(2));
		assertEquals(0.0f, buffer.get(3));

		// vec4 maxCorner: (26, 36, 46, 0)
		assertEquals(26.0f, buffer.get(4));
		assertEquals(36.0f, buffer.get(5));
		assertEquals(46.0f, buffer.get(6));
		assertEquals(0.0f, buffer.get(7));
	}

	@Test
	void evaluateCpuMatchesExactFrustumLogic() {
		float[][] planes = new float[][]{
			{1, 0, 0, 100},  // x + 100 > 0  => x > -100
			{-1, 0, 0, 100}, // -x + 100 > 0 => x < 100
			{0, 1, 0, 100},  // y > -100
			{0, -1, 0, 100}, // y < 100
			{0, 0, 1, 100},  // z > -100
			{0, 0, -1, 100}  // z < 100
		};

		// Inside box [-50, -34]
		assertTrue(GpuComputeCulling.evaluateCpu(-50, 0, 0, -34, 16, 16, planes));

		// Outside box [110, 126] (exceeds x < 100)
		assertFalse(GpuComputeCulling.evaluateCpu(110, 0, 0, 126, 16, 16, planes));

		// Straddling boundary [90, 106] (visible because maxCorner.x = 106 doesn't fail, but wait: -1 * 90 + 100 = 10 > 0, so visible!)
		assertTrue(GpuComputeCulling.evaluateCpu(90, 0, 0, 106, 16, 16, planes));
	}

	@Test
	void evaluateCpuMatchesRandomVectorFrustumDecisions() {
		Random random = new Random(98765);
		ChunkFrustum frustum = new ChunkFrustum();
		float[][] planes = new float[6][4];

		for (int pass = 0; pass < 100; pass++) {
			for (int p = 0; p < 6; p++) {
				for (int c = 0; c < 4; c++) {
					planes[p][c] = (random.nextFloat() * 2 - 1) * (c == 3 ? 512 : 1);
				}
			}
			assertTrue(frustum.load(planes, 0, 0, 0));

			for (int box = 0; box < 200; box++) {
				float x0 = random.nextInt(1000) - 500;
				float y0 = random.nextInt(256);
				float z0 = random.nextInt(1000) - 500;
				float x1 = x0 + 16;
				float y1 = y0 + 16;
				float z1 = z0 + 16;

				boolean cpuSim = GpuComputeCulling.evaluateCpu(x0, y0, z0, x1, y1, z1, planes);
				boolean scalar = frustum.test(x0, y0, z0, x1, y1, z1) == ChunkFrustum.VISIBLE;
				assertEquals(scalar, cpuSim);
			}
		}
	}
}
