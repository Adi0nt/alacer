package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.render.block.BlockLayer;

class FastRenderChunkTest {

	private static class DummyFastRenderChunk implements FastRenderChunk {
		private int layerMask;
		private int builtLayerMask;
		private int originX, originY, originZ;

		@Override public int alacer$layerMask() { return this.layerMask; }
		@Override public int alacer$builtLayerMask() { return this.builtLayerMask; }
		@Override public void alacer$setLayerMasks(int layerMask, int builtLayerMask) {
			this.layerMask = layerMask;
			this.builtLayerMask = builtLayerMask;
		}
		@Override public int alacer$originX() { return this.originX; }
		@Override public int alacer$originY() { return this.originY; }
		@Override public int alacer$originZ() { return this.originZ; }
		@Override public net.minecraft.client.render.vertex.VertexBuffer alacer$getBuffer(int layerOrdinal) { return null; }
		@Override public void alacer$glMultMatrix() {}
		@Override public net.minecraft.client.render.world.RenderChunk alacer$renderChunk() { return null; }
	}

	@Test
	void fastRenderChunkTracksAndQueriesLayerMasks() {
		DummyFastRenderChunk chunk = new DummyFastRenderChunk();
		assertEquals(0, chunk.alacer$layerMask());
		assertEquals(0, chunk.alacer$builtLayerMask());

		int solidBit = 1 << BlockLayer.SOLID.ordinal();
		int translucentBit = 1 << BlockLayer.TRANSLUCENT.ordinal();
		int combinedMask = solidBit | translucentBit;

		chunk.alacer$setLayerMasks(combinedMask, combinedMask);

		assertEquals(combinedMask, chunk.alacer$layerMask());
		assertEquals(combinedMask, chunk.alacer$builtLayerMask());

		assertTrue((chunk.alacer$layerMask() & solidBit) != 0);
		assertTrue((chunk.alacer$layerMask() & translucentBit) != 0);

		int cutoutBit = 1 << BlockLayer.CUTOUT.ordinal();
		int cutoutMippedBit = 1 << BlockLayer.CUTOUT_MIPPED.ordinal();

		assertFalse((chunk.alacer$layerMask() & cutoutBit) != 0);
		assertFalse((chunk.alacer$layerMask() & cutoutMippedBit) != 0);
	}

	@Test
	void visibleLayerBitsAccumulationCorrectlyTracksAllLayers() {
		DummyFastRenderChunk chunk1 = new DummyFastRenderChunk();
		chunk1.alacer$setLayerMasks(1 << BlockLayer.SOLID.ordinal(), 1 << BlockLayer.SOLID.ordinal());

		DummyFastRenderChunk chunk2 = new DummyFastRenderChunk();
		chunk2.alacer$setLayerMasks(1 << BlockLayer.CUTOUT.ordinal(), 1 << BlockLayer.CUTOUT.ordinal());

		int visibleLayerBits = 0;
		visibleLayerBits |= chunk1.alacer$layerMask();
		visibleLayerBits |= chunk2.alacer$layerMask();

		assertTrue((visibleLayerBits & (1 << BlockLayer.SOLID.ordinal())) != 0);
		assertTrue((visibleLayerBits & (1 << BlockLayer.CUTOUT.ordinal())) != 0);
		assertFalse((visibleLayerBits & (1 << BlockLayer.CUTOUT_MIPPED.ordinal())) != 0);
		assertFalse((visibleLayerBits & (1 << BlockLayer.TRANSLUCENT.ordinal())) != 0);
	}

	@Test
	void fastRenderChunkDefaultGlListReturnsNegativeOne() {
		DummyFastRenderChunk chunk = new DummyFastRenderChunk();
		assertEquals(-1, chunk.alacer$glList(0));
	}
}
