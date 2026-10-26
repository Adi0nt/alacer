package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FastDirtyChunkTest {

	static class DummyChunk implements FastDirtyChunk {
		private int epoch;
		@Override public int alacer$dirtyEpoch() { return epoch; }
		@Override public void alacer$setDirtyEpoch(int epoch) { this.epoch = epoch; }
	}

	@Test
	void dirtyEpochRoundTrip() {
		DummyChunk chunk = new DummyChunk();
		assertEquals(0, chunk.alacer$dirtyEpoch());
		chunk.alacer$setDirtyEpoch(42);
		assertEquals(42, chunk.alacer$dirtyEpoch());
		chunk.alacer$setDirtyEpoch(0);
		assertEquals(0, chunk.alacer$dirtyEpoch());
	}
}
