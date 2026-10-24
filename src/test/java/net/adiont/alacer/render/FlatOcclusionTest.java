package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.util.math.Direction;

class FlatOcclusionTest {

	private static final Direction[] DIRECTIONS = Direction.values();

	private static class MockCompiledChunk extends CompiledChunk {
		final boolean[][] visible = new boolean[6][6];

		@Override
		public boolean isVisible(Direction face, Direction otherFace) {
			return visible[face.ordinal()][otherFace.ordinal()];
		}
	}

	@Test
	void uncompiledChunkYieldsZeroBitmask() {
		assertEquals(0L, ChunkVisibility.packOcclusion(null));
		assertEquals(0L, ChunkVisibility.packOcclusion(CompiledChunk.UNCOMPILED));
	}

	@Test
	void fullVisibilityBitmaskHasAll36BitsSet() {
		MockCompiledChunk chunk = new MockCompiledChunk();
		for (int f = 0; f < 6; f++) {
			for (int t = 0; t < 6; t++) {
				chunk.visible[f][t] = true;
			}
		}
		long mask = ChunkVisibility.packOcclusion(chunk);
		assertEquals((1L << 36) - 1L, mask);

		for (int from = 0; from < 6; from++) {
			int allowedFaces = (int) ((mask >>> (from * 6)) & 0x3F);
			assertEquals(0x3F, allowedFaces);
			for (int to = 0; to < 6; to++) {
				assertTrue((allowedFaces & (1 << to)) != 0);
			}
		}
	}

	@Test
	void randomOcclusionTopologiesMatchBitExactVanillaDecisions() {
		Random random = new Random(42891);
		for (int trial = 0; trial < 1000; trial++) {
			MockCompiledChunk chunk = new MockCompiledChunk();
			for (int f = 0; f < 6; f++) {
				for (int t = 0; t < 6; t++) {
					chunk.visible[f][t] = random.nextBoolean();
				}
			}

			long mask = ChunkVisibility.packOcclusion(chunk);

			// Check all 36 face transitions
			for (int f = 0; f < 6; f++) {
				int allowedFaces = (int) ((mask >>> (f * 6)) & 0x3F);
				for (int t = 0; t < 6; t++) {
					boolean fastVisible = (allowedFaces & (1 << t)) != 0;
					boolean vanillaVisible = chunk.isVisible(DIRECTIONS[f], DIRECTIONS[t]);
					assertEquals(vanillaVisible, fastVisible, "Mismatch at face " + f + " -> " + t);
				}
			}
		}
	}

	@Test
	void singleFaceTransitionsMatchDiscreteBits() {
		for (int f = 0; f < 6; f++) {
			for (int t = 0; t < 6; t++) {
				MockCompiledChunk chunk = new MockCompiledChunk();
				chunk.visible[f][t] = true;

				long mask = ChunkVisibility.packOcclusion(chunk);
				assertEquals(1L << (f * 6 + t), mask);

				for (int checkF = 0; checkF < 6; checkF++) {
					int allowed = (int) ((mask >>> (checkF * 6)) & 0x3F);
					for (int checkT = 0; checkT < 6; checkT++) {
						boolean expected = (checkF == f && checkT == t);
						boolean actual = (allowed & (1 << checkT)) != 0;
						assertEquals(expected, actual);
					}
				}
			}
		}
	}
}
