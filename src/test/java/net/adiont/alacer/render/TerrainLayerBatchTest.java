package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.BlockPos;

class TerrainLayerBatchTest {

	private static Constructor<WorldRenderer.RenderChunkInfo> INFO_CONSTRUCTOR;

	@BeforeAll
	@SuppressWarnings("unchecked")
	static void setupConstructor() throws Exception {
		for (Constructor<?> c : WorldRenderer.RenderChunkInfo.class.getDeclaredConstructors()) {
			if (c.getParameterCount() == 4) {
				c.setAccessible(true);
				INFO_CONSTRUCTOR = (Constructor<WorldRenderer.RenderChunkInfo>) c;
				break;
			}
		}
	}

	private static class MockRenderChunk extends RenderChunk implements FastRenderChunk {
		private final int id;
		private int layerMask;
		private int builtLayerMask;
		private int ox, oy, oz;

		MockRenderChunk(int id) {
			super(null, null, new BlockPos(0, 0, 0), 0);
			this.id = id;
		}

		@Override
		public void setOrigin(BlockPos pos) {
			// Override to avoid OpenGL glTransform in headless unit tests
		}

		@Override public int alacer$layerMask() { return this.layerMask; }
		@Override public int alacer$builtLayerMask() { return this.builtLayerMask; }
		@Override public void alacer$setLayerMasks(int layerMask, int builtLayerMask) {
			this.layerMask = layerMask;
			this.builtLayerMask = builtLayerMask;
		}
		@Override public int alacer$originX() { return this.ox; }
		@Override public int alacer$originY() { return this.oy; }
		@Override public int alacer$originZ() { return this.oz; }
		@Override public VertexBuffer alacer$getBuffer(int layerOrdinal) { return null; }
		@Override public void alacer$glMultMatrix() {}
		@Override public RenderChunk alacer$renderChunk() { return this; }
	}

	private static MockRenderChunk createMockChunk(int id, int layerMask, int builtLayerMask) {
		MockRenderChunk chunk = new MockRenderChunk(id);
		chunk.alacer$setLayerMasks(layerMask, builtLayerMask);
		return chunk;
	}

	private static WorldRenderer.RenderChunkInfo createInfo(RenderChunk chunk) throws Exception {
		return INFO_CONSTRUCTOR.newInstance(null, chunk, null, 0);
	}

	@Test
	void layerQueueExpandsAndClearsSafely() {
		TerrainLayerBatch.LayerQueue queue = new TerrainLayerBatch.LayerQueue();
		assertTrue(queue.isEmpty());
		assertEquals(0, queue.size());

		MockRenderChunk sample = createMockChunk(1, 0, 0);
		for (int i = 0; i < 1000; i++) {
			queue.add(sample);
		}
		assertEquals(1000, queue.size());
		assertFalse(queue.isEmpty());
		assertSame(sample, queue.get(999));
		assertNotNull(queue.array());
		assertSame(sample, queue.array()[999]);

		queue.clear();
		assertEquals(0, queue.size());
		assertTrue(queue.isEmpty());
		assertNull(queue.get(0));
	}

	@Test
	void batchSegregatesLayersAndTranslucentResort() throws Exception {
		TerrainLayerBatch batch = new TerrainLayerBatch();

		int solidBit = 1 << BlockLayer.SOLID.ordinal();
		int mippedBit = 1 << BlockLayer.CUTOUT_MIPPED.ordinal();
		int cutoutBit = 1 << BlockLayer.CUTOUT.ordinal();
		int translucentBit = 1 << BlockLayer.TRANSLUCENT.ordinal();

		MockRenderChunk c1 = createMockChunk(1, solidBit, solidBit);
		MockRenderChunk c2 = createMockChunk(2, mippedBit | cutoutBit, mippedBit | cutoutBit);
		MockRenderChunk c3 = createMockChunk(3, solidBit | translucentBit, solidBit | translucentBit);
		MockRenderChunk c4 = createMockChunk(4, 0, 0); // Empty

		List<WorldRenderer.RenderChunkInfo> compiled = new ArrayList<>();
		compiled.add(createInfo(c1));
		compiled.add(createInfo(c2));
		compiled.add(createInfo(c3));
		compiled.add(createInfo(c4));

		assertFalse(batch.isBuiltFor(compiled));
		batch.build(compiled);
		assertTrue(batch.isBuiltFor(compiled));

		// Visible layer bits must include SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT
		int expectedVisible = solidBit | mippedBit | cutoutBit | translucentBit;
		assertEquals(expectedVisible, batch.getVisibleLayerBits());

		// Queue sizes
		TerrainLayerBatch.LayerQueue solidQueue = batch.getQueue(BlockLayer.SOLID);
		assertEquals(2, solidQueue.size()); // c1, c3
		assertSame(c1, solidQueue.get(0));
		assertSame(c3, solidQueue.get(1));

		TerrainLayerBatch.LayerQueue mippedQueue = batch.getQueue(BlockLayer.CUTOUT_MIPPED);
		assertEquals(1, mippedQueue.size()); // c2
		assertSame(c2, mippedQueue.get(0));

		TerrainLayerBatch.LayerQueue cutoutQueue = batch.getQueue(BlockLayer.CUTOUT);
		assertEquals(1, cutoutQueue.size()); // c2
		assertSame(c2, cutoutQueue.get(0));

		TerrainLayerBatch.LayerQueue transQueue = batch.getQueue(BlockLayer.TRANSLUCENT);
		assertEquals(1, transQueue.size()); // c3
		assertSame(c3, transQueue.get(0));

		TerrainLayerBatch.LayerQueue resortQueue = batch.getTranslucentResortQueue();
		assertEquals(1, resortQueue.size()); // c3
		assertSame(c3, resortQueue.get(0));

		// Test cache invalidation when list size changes
		compiled.remove(compiled.size() - 1);
		assertFalse(batch.isBuiltFor(compiled));
	}

	@Test
	void emptyBatchProducesZeroVisibleBitsAndEmptyQueues() throws Exception {
		TerrainLayerBatch batch = new TerrainLayerBatch();
		List<WorldRenderer.RenderChunkInfo> compiled = new ArrayList<>();

		MockRenderChunk c1 = createMockChunk(1, 0, 0);
		MockRenderChunk c2 = createMockChunk(2, 0, 0);
		compiled.add(createInfo(c1));
		compiled.add(createInfo(c2));

		batch.build(compiled);
		assertEquals(0, batch.getVisibleLayerBits());
		assertTrue(batch.getQueue(BlockLayer.SOLID).isEmpty());
		assertTrue(batch.getQueue(BlockLayer.CUTOUT_MIPPED).isEmpty());
		assertTrue(batch.getQueue(BlockLayer.CUTOUT).isEmpty());
		assertTrue(batch.getQueue(BlockLayer.TRANSLUCENT).isEmpty());
		assertTrue(batch.getTranslucentResortQueue().isEmpty());
	}
}
