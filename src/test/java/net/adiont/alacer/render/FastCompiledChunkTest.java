package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.CompiledChunk;

class FastCompiledChunkTest {

	@Test
	void emptyCompiledChunkHasZeroMasksAndReportsEmpty() {
		CompiledChunk compiled = new CompiledChunk();
		assertTrue(compiled.isEmpty());
		if (compiled instanceof FastCompiledChunk fcc) {
			assertEquals(0, fcc.alacer$layerMask());
			assertEquals(0, fcc.alacer$builtLayerMask());
		}
		for (BlockLayer layer : BlockLayer.values()) {
			assertTrue(compiled.hasBlock(layer)); // vanilla hasBlock returns true if empty
			assertFalse(compiled.hasLayer(layer));
		}
	}

	@Test
	void settingBlocksAndLayersUpdatesBitmasksAndMatchesVanillaQueries() {
		CompiledChunk compiled = new CompiledChunk();
		compiled.setLayer(BlockLayer.SOLID);
		compiled.setLayer(BlockLayer.TRANSLUCENT);

		// setBlock is protected, test hasBlock / hasLayer
		for (BlockLayer layer : BlockLayer.values()) {
			if (layer == BlockLayer.SOLID || layer == BlockLayer.TRANSLUCENT) {
				assertTrue(compiled.hasLayer(layer));
			} else {
				assertFalse(compiled.hasLayer(layer));
			}
		}

		if (compiled instanceof FastCompiledChunk fcc) {
			int expectedBuiltMask = (1 << BlockLayer.SOLID.ordinal()) | (1 << BlockLayer.TRANSLUCENT.ordinal());
			assertEquals(expectedBuiltMask, fcc.alacer$builtLayerMask());
		}
	}

	@Test
	void uncompiledChunkReportsEmptyLayers() {
		CompiledChunk uncompiled = CompiledChunk.UNCOMPILED;
		assertTrue(uncompiled.isEmpty());
		if (uncompiled instanceof FastCompiledChunk fcc) {
			assertEquals(0, fcc.alacer$layerMask());
			assertEquals(0, fcc.alacer$builtLayerMask());
		}
		for (BlockLayer layer : BlockLayer.values()) {
			assertTrue(uncompiled.hasBlock(layer));
			assertFalse(uncompiled.hasLayer(layer));
		}
	}

	@Test
	void uncompiledAndDefaultChunksHaveZeroOcclusionMask() {
		CompiledChunk compiled = new CompiledChunk();
		if (compiled instanceof FastCompiledChunk fcc) {
			assertEquals(0L, fcc.alacer$occlusionMask());
			assertFalse(fcc.alacer$hasOcclusionMask());
		}
		if (CompiledChunk.UNCOMPILED instanceof FastCompiledChunk fcc) {
			assertEquals(0L, fcc.alacer$occlusionMask());
			assertTrue(fcc.alacer$hasOcclusionMask());
		}
	}
}
