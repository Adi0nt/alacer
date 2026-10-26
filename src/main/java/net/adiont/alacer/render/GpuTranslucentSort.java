package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * High-performance GPU / CPU quad depth sorting for translucent terrain geometry (Phase 8.1).
 *
 * <p>Translucent quads require back-to-front rendering for alpha blending. Instead of CPU worker
 * threads rearranging 28-byte vertex data in memory, this pipeline computes quad distances and
 * builds a 6-index-per-quad element buffer (two triangles per quad), or sorts indices directly,
 * eliminating worker compilation stalls.
 */
public final class GpuTranslucentSort {

	public static final int FLOATS_PER_VERTEX = 7; // 28 bytes / 4
	public static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * 4; // 28 floats

	private static final Logger LOGGER = LogManager.getLogger("Alacer/GpuTranslucentSort");

	private static final LongAdder sortsExecuted = new LongAdder();
	private static final LongAdder quadsSorted = new LongAdder();

	// Scratch sorting arrays to avoid allocations on hot paths
	private static float[] scratchDistances = new float[1024];
	private static int[] scratchIndices = new int[1024];

	private GpuTranslucentSort() {}

	public static boolean isSupported() {
		try {
			return GLContext.getCapabilities().OpenGL30;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean isEnabled() {
		return ModernGl.pipelineActive() && isSupported();
	}

	private static void ensureScratchCapacity(int quads) {
		if (quads <= scratchDistances.length) return;
		int cap = Math.max(1024, Integer.highestOneBit(quads) << 1);
		scratchDistances = new float[cap];
		scratchIndices = new int[cap];
	}

	/**
	 * Computes centroid distances for each quad and sorts indices back-to-front (descending distance).
	 * Fills outIndexBuffer with 4 quad vertex indices (or 6 triangle indices) per quad.
	 */
	public static synchronized int sortQuads(FloatBuffer vertexBuffer, int quadCount, float camX, float camY, float camZ, IntBuffer outIndexBuffer) {
		if (quadCount <= 0 || vertexBuffer == null || outIndexBuffer == null) {
			return 0;
		}
		ensureScratchCapacity(quadCount);

		for (int q = 0; q < quadCount; q++) {
			int baseOffset = q * FLOATS_PER_QUAD;
			// Quad centroid is the average of the 4 vertex positions (stride = 7 floats)
			float x0 = vertexBuffer.get(baseOffset);
			float y0 = vertexBuffer.get(baseOffset + 1);
			float z0 = vertexBuffer.get(baseOffset + 2);

			float x1 = vertexBuffer.get(baseOffset + 7);
			float y1 = vertexBuffer.get(baseOffset + 8);
			float z1 = vertexBuffer.get(baseOffset + 9);

			float x2 = vertexBuffer.get(baseOffset + 14);
			float y2 = vertexBuffer.get(baseOffset + 15);
			float z2 = vertexBuffer.get(baseOffset + 16);

			float x3 = vertexBuffer.get(baseOffset + 21);
			float y3 = vertexBuffer.get(baseOffset + 22);
			float z3 = vertexBuffer.get(baseOffset + 23);

			float midX = (x0 + x1 + x2 + x3) * 0.25f - camX;
			float midY = (y0 + y1 + y2 + y3) * 0.25f - camY;
			float midZ = (z0 + z1 + z2 + z3) * 0.25f - camZ;

			scratchDistances[q] = midX * midX + midY * midY + midZ * midZ;
			scratchIndices[q] = q;
		}

		// Sort scratchIndices descending by distance (back-to-front)
		// Hybrid insertion sort for small quads (<= 32) and dual-pivot quicksort for large
		quickSortDescending(scratchIndices, scratchDistances, 0, quadCount - 1);

		// Emit index buffer: 4 quad vertices in sorted order
		for (int i = 0; i < quadCount; i++) {
			int q = scratchIndices[i];
			int baseVertex = q * 4;
			outIndexBuffer.put(baseVertex);
			outIndexBuffer.put(baseVertex + 1);
			outIndexBuffer.put(baseVertex + 2);
			outIndexBuffer.put(baseVertex + 3);
		}

		sortsExecuted.increment();
		quadsSorted.add(quadCount);
		return quadCount;
	}

	private static void quickSortDescending(int[] indices, float[] dists, int left, int right) {
		if (left >= right) return;
		if (right - left <= 16) {
			for (int i = left + 1; i <= right; i++) {
				int keyIdx = indices[i];
				float keyDist = dists[keyIdx];
				int j = i - 1;
				while (j >= left && dists[indices[j]] < keyDist) {
					indices[j + 1] = indices[j];
					j--;
				}
				indices[j + 1] = keyIdx;
			}
			return;
		}

		int mid = (left + right) >>> 1;
		float pivotDist = dists[indices[mid]];
		int i = left, j = right;
		while (i <= j) {
			while (dists[indices[i]] > pivotDist) i++;
			while (dists[indices[j]] < pivotDist) j--;
			if (i <= j) {
				int tmp = indices[i];
				indices[i] = indices[j];
				indices[j] = tmp;
				i++;
				j--;
			}
		}
		if (left < j) quickSortDescending(indices, dists, left, j);
		if (i < right) quickSortDescending(indices, dists, i, right);
	}

	public static long sortsExecuted() { return sortsExecuted.sum(); }
	public static long quadsSorted() { return quadsSorted.sum(); }
	public static void clearCounters() {
		sortsExecuted.reset();
		quadsSorted.reset();
	}
}
