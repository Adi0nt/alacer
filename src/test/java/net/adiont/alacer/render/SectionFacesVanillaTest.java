package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.world.ChunkOcclusionGraph;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Compares {@link SectionFaces} with vanilla's own {@code ChunkOcclusionGraph}, as used by {@code getOpenChunkFaces}. */
class SectionFacesVanillaTest {

	private static int mask(Set<Direction> faces) {
		int mask = 0;
		for (Direction direction : faces) {
			mask |= 1 << direction.ordinal();
		}
		return mask;
	}

	@Test
	void directionConstantsMatchMinecraft() {
		assertEquals(SectionFaces.DOWN, Direction.DOWN.ordinal());
		assertEquals(SectionFaces.UP, Direction.UP.ordinal());
		assertEquals(SectionFaces.NORTH, Direction.NORTH.ordinal());
		assertEquals(SectionFaces.SOUTH, Direction.SOUTH.ordinal());
		assertEquals(SectionFaces.WEST, Direction.WEST.ordinal());
		assertEquals(SectionFaces.EAST, Direction.EAST.ordinal());
		assertEquals(6, Direction.values().length);
	}

	@Test
	void matchesVanillaGraphInWorldCoordinates() {
		SplittableRandom random = new SplittableRandom(0xFACE5L);
		SectionFaces faces = new SectionFaces();
		double[] densities = {0.0, 0.1, 0.25, 0.32, 0.4, 0.6, 0.9, 1.0};
		int reusedChecked = 0;
		for (int grid = 0; grid < 160; grid++) {
			double density = densities[grid % densities.length];
			boolean[] opaque = new boolean[4096];
			// Sections anywhere, including negative coordinates, as getOpenChunkFaces receives them.
			int sx = (random.nextInt(4000) - 2000) << 4;
			int sy = random.nextInt(16) << 4;
			int sz = (random.nextInt(4000) - 2000) << 4;
			faces.clear();
			for (int i = 0; i < 4096; i++) {
				if (random.nextDouble() < density) {
					opaque[i] = true;
					faces.setOpaque(i & 15, i >> 8 & 15, i >> 4 & 15);
				}
			}
			for (int s = 0; s < 12; s++) {
				int x = random.nextInt(16);
				int y = random.nextInt(16);
				int z = random.nextInt(16);
				int answer = faces.fill(x, y, z);
				assertEquals(vanilla(opaque, sx, sy, sz, x, y, z), answer, "grid " + grid + " start " + x + "," + y + "," + z);
				// Spot-check reused answers against a fresh vanilla graph as well.
				for (int r = 0; r < 4; r++) {
					int cx = random.nextInt(16);
					int cy = random.nextInt(16);
					int cz = random.nextInt(16);
					if (faces.reuses(cx, cy, cz)) {
						assertEquals(vanilla(opaque, sx, sy, sz, cx, cy, cz), answer);
						reusedChecked++;
					}
				}
			}
		}
		// Dense grids rarely reuse; sparse ones nearly always do.
		assertEquals(true, reusedChecked > 500, "reused answers checked: " + reusedChecked);
	}

	/** {@code getOpenChunkFaces}' graph: close every opaque block of the section, then flood from the start. */
	private static int vanilla(boolean[] opaque, int sx, int sy, int sz, int x, int y, int z) {
		ChunkOcclusionGraph graph = new ChunkOcclusionGraph();
		for (int i = 0; i < 4096; i++) {
			if (opaque[i]) {
				graph.close(new BlockPos(sx + (i & 15), sy + (i >> 8 & 15), sz + (i >> 4 & 15)));
			}
		}
		return mask(graph.getFaces(new BlockPos(sx + x, sy + y, sz + z)));
	}
}
