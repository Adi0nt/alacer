package net.adiont.alacer.render;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkNeighboursTest {
	private static final int[] X = {0, 0, 0, 0, -16, 16};
	private static final int[] Y = {-16, 16, 0, 0, 0, 0};
	private static final int[] Z = {0, 0, -16, 16, 0, 0};
	private static int slot(int x, int y, int z, int sx, int sy, int sz) {
		int cy = Math.floorDiv(y, 16);
		return cy < 0 || cy >= sy ? -1 : (Math.floorMod(Math.floorDiv(z, 16), sz) * sy + cy) * sx + Math.floorMod(Math.floorDiv(x, 16), sx);
	}
	@Test void everyEdgeMatchesOriginalAddressMathAcrossCameraRingRepositioning() {
		Random random = new Random(2148927);
		for (int sx : new int[] {1, 3, 5, 25, 65}) {
			int sy = 16, sz = sx == 25 ? 17 : sx;
			ChunkNeighbours grid = new ChunkNeighbours(sx, sy, sz, X, Y, Z);
			for (int i = 0; i < 40_000; i++) {
				int x = (random.nextInt(3_750_001) - 1_875_000) * 16;
				int z = (random.nextInt(3_750_001) - 1_875_000) * 16;
				int y = random.nextInt(16) * 16;
				int current = slot(x, y, z, sx, sy, sz);
				for (int d = 0; d < 6; d++) {
					assertEquals(slot(x + X[d], y + Y[d], z + Z[d], sx, sy, sz), grid.next(current, d));
				}
			}
		}
	}
	@Test void rejectsMalformedAndOverflowingStorage() {
		assertThrows(IllegalArgumentException.class, () -> new ChunkNeighbours(0, 16, 5, X, Y, Z));
		assertThrows(IllegalArgumentException.class, () -> new ChunkNeighbours(5, 16, 5, new int[5], Y, Z));
		assertThrows(IllegalArgumentException.class, () -> new ChunkNeighbours(5, 16, 5, new int[] {1, 0, 0, 0, 0, 0}, Y, Z));
		assertThrows(ArithmeticException.class, () -> new ChunkNeighbours(Integer.MAX_VALUE, 16, 5, X, Y, Z));
	}
}
