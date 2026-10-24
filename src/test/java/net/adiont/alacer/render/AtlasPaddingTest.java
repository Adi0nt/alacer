package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AtlasPaddingTest {
	@AfterEach void reset() { AtlasPadding.stitched(0); }

	@Test void borderIsOffWithoutFilteringAndOneTexelAtTheCoarsestLevel() {
		for (int mip = 0; mip <= 4; mip++) {
			assertEquals(0,AtlasPadding.border(0,mip));
			int border = AtlasPadding.border(3,mip);
			assertEquals(1,border >> mip); assertEquals(0,border % (1 << mip));
		}
	}

	@Test void padRepeatsEdgeTexelsAtEveryLevelAndKeepsTheInterior() {
		// 4x2 level 0 and 2x1 level 1, border 2 (1 at level 1).
		int[][] levels = {{1,2,3,4, 5,6,7,8}, {10,20}};
		int[][] out = AtlasPadding.pad(levels,4,2,2,null);
		int[] l0 = out[0];
		assertEquals(8*6,l0.length);
		int[][] expected0 = {
			{1,1,1,2,3,4,4,4}, {1,1,1,2,3,4,4,4},
			{1,1,1,2,3,4,4,4}, {5,5,5,6,7,8,8,8},
			{5,5,5,6,7,8,8,8}, {5,5,5,6,7,8,8,8}};
		for (int y = 0; y < 6; y++) { for (int x = 0; x < 8; x++) { assertEquals(expected0[y][x],l0[y*8+x],x+","+y); } }
		assertArrayEquals(new int[] {10,10,20,20, 10,10,20,20, 10,10,20,20},out[1]);
		assertSame(out,AtlasPadding.pad(levels,4,2,2,out));
		assertEquals(20,AtlasPadding.padded(16,2));
	}

	@Test void onlyTurningFilteringOnOrOffChangesTheLayout() {
		AtlasPadding.stitched(0);
		assertFalse(AtlasPadding.layoutChanges(0)); assertTrue(AtlasPadding.layoutChanges(4));
		AtlasPadding.stitched(16);
		assertFalse(AtlasPadding.layoutChanges(1)); assertFalse(AtlasPadding.layoutChanges(4)); assertTrue(AtlasPadding.layoutChanges(0));
	}
}
