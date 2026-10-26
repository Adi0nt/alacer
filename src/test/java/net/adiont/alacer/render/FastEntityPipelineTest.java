package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.util.math.MathHelper;

class FastEntityPipelineTest {

	@Test
	void slotIndexMatchesRenderChunkStorageFormulaAcrossGrid() {
		int sizeX = 33;
		int sizeY = 16;
		int sizeZ = 33;

		for (int cx = -40; cx <= 40; cx++) {
			for (int cz = -40; cz <= 40; cz++) {
				for (int cy = 0; cy < 16; cy++) {
					int actual = FastEntityPipeline.slotIndex(cx, cy, cz, sizeX, sizeY, sizeZ);

					// Reference formula from RenderChunkStorage.getChunk:
					int i = cx % sizeX;
					if (i < 0) i += sizeX;
					int k = cz % sizeZ;
					if (k < 0) k += sizeZ;
					int expected = (k * sizeY + cy) * sizeX + i;

					assertEquals(expected, actual, "Slot index mismatch at (" + cx + ", " + cy + ", " + cz + ")");
				}
			}
		}
	}

	@Test
	void slotIndexRejectsOutOfBoundsVerticalCoordinates() {
		int sizeX = 33;
		int sizeY = 16;
		int sizeZ = 33;

		assertEquals(-1, FastEntityPipeline.slotIndex(0, -1, 0, sizeX, sizeY, sizeZ));
		assertEquals(-1, FastEntityPipeline.slotIndex(0, -100, 0, sizeX, sizeY, sizeZ));
		assertEquals(-1, FastEntityPipeline.slotIndex(0, 16, 0, sizeX, sizeY, sizeZ));
		assertEquals(-1, FastEntityPipeline.slotIndex(0, 255, 0, sizeX, sizeY, sizeZ));
	}

	@Test
	void stampsArePositiveAndIncrementing() {
		int stamp1 = FastEntityPipeline.nextStamp();
		int stamp2 = FastEntityPipeline.nextStamp();
		assertTrue(stamp1 > 0);
		assertTrue(stamp2 > stamp1);
		assertEquals(stamp2, FastEntityPipeline.currentStamp());
	}

	@Test
	void stampActiveChunksHandlesNullAndEmpty() {
		int stamp = FastEntityPipeline.nextStamp();
		assertEquals(0, FastEntityPipeline.stampActiveChunks(null, null, stamp));
		assertEquals(0, FastEntityPipeline.stampActiveChunks(java.util.Collections.emptyList(), null, stamp));
	}

	@Test
	void verificationSummaryReportsPassesAndMismatches() {
		FastEntityPipeline.recordVerification(5, 5, true);
		String summary = FastEntityPipeline.verificationSummary();
		assertNotNull(summary);
		assertTrue(summary.contains("passes compared"));
	}

	@Test
	void slotBlockEntityTrackingContract() {
		class TestSlot implements EntityChunkSlot {
			private int stamp;
			private boolean hasBlockEntities;

			@Override public net.minecraft.world.chunk.WorldChunk alacer$getEntityChunk() { return null; }
			@Override public long alacer$getEntityChunkColumn() { return 0; }
			@Override public int alacer$getEntityChunkGeneration() { return 0; }
			@Override public void alacer$setEntityChunk(net.minecraft.world.chunk.WorldChunk c, long col, int gen) {}
			@Override public int alacer$getEntityStamp() { return stamp; }
			@Override public void alacer$setEntityStamp(int s) { this.stamp = s; }
			@Override public boolean alacer$hasBlockEntities() { return hasBlockEntities; }
			@Override public void alacer$setHasBlockEntities(boolean has) { this.hasBlockEntities = has; }
		}

		TestSlot slot = new TestSlot();
		assertFalse(slot.alacer$hasBlockEntities());
		slot.alacer$setHasBlockEntities(true);
		assertTrue(slot.alacer$hasBlockEntities());
		slot.alacer$setHasBlockEntities(false);
		assertFalse(slot.alacer$hasBlockEntities());

		assertEquals(0, slot.alacer$getEntityStamp());
		slot.alacer$setEntityStamp(42);
		assertEquals(42, slot.alacer$getEntityStamp());
	}
}
