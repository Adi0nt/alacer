package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

class GpuTranslucentSortTest {

	@BeforeEach
	void setUp() {
		GpuTranslucentSort.clearCounters();
	}

	@Test
	void sortsQuadsBackToFrontCorrectly() {
		// 3 quads at different distances from camera at (0, 0, 0)
		// Quad 0: at z = 10 (dist^2 = 100)
		// Quad 1: at z = 30 (dist^2 = 900)
		// Quad 2: at z = 20 (dist^2 = 400)
		// Expected back-to-front order: Quad 1 (z=30), Quad 2 (z=20), Quad 0 (z=10)
		FloatBuffer vBuf = ByteBuffer.allocateDirect(3 * GpuTranslucentSort.FLOATS_PER_QUAD * 4)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();

		// Quad 0: centered at (0, 0, 10)
		putQuad(vBuf, 0, 0, 10);
		// Quad 1: centered at (0, 0, 30)
		putQuad(vBuf, 0, 0, 30);
		// Quad 2: centered at (0, 0, 20)
		putQuad(vBuf, 0, 0, 20);
		vBuf.flip();

		IntBuffer iBuf = ByteBuffer.allocateDirect(3 * 4 * 4).order(ByteOrder.nativeOrder()).asIntBuffer();

		int sorted = GpuTranslucentSort.sortQuads(vBuf, 3, 0.0f, 0.0f, 0.0f, iBuf);
		assertEquals(3, sorted);
		iBuf.flip();

		// Quad 1 vertices (indices 4, 5, 6, 7) should come first
		assertEquals(4, iBuf.get());
		assertEquals(5, iBuf.get());
		assertEquals(6, iBuf.get());
		assertEquals(7, iBuf.get());

		// Quad 2 vertices (indices 8, 9, 10, 11) should come second
		assertEquals(8, iBuf.get());
		assertEquals(9, iBuf.get());
		assertEquals(10, iBuf.get());
		assertEquals(11, iBuf.get());

		// Quad 0 vertices (indices 0, 1, 2, 3) should come last
		assertEquals(0, iBuf.get());
		assertEquals(1, iBuf.get());
		assertEquals(2, iBuf.get());
		assertEquals(3, iBuf.get());
	}

	private static void putQuad(FloatBuffer buf, float cx, float cy, float cz) {
		for (int v = 0; v < 4; v++) {
			buf.put(cx).put(cy).put(cz); // position
			buf.put(1.0f); // color
			buf.put(0.0f).put(0.0f); // uv0
			buf.put(0.0f); // uv1
		}
	}

	@Test
	void emptyOrNullInputReturnsZero() {
		assertEquals(0, GpuTranslucentSort.sortQuads(null, 0, 0, 0, 0, null));
		IntBuffer iBuf = BufferUtils.createIntBuffer(16);
		assertEquals(0, GpuTranslucentSort.sortQuads(BufferUtils.createFloatBuffer(16), 0, 0, 0, 0, iBuf));
	}
}
