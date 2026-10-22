package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class SectionFacesTest {

	/**
	 * The structure of vanilla's camera-section flood fill: one bit set holding the opaque cells
	 * and, as the fill proceeds, the visited ones; a FIFO queue; the start cell entered even when
	 * it is opaque; faces added per visited cell from its coordinates.
	 */
	private static int reference(BitSet opaqueCells, int x, int y, int z) {
		BitSet set = (BitSet) opaqueCells.clone();
		int start = SectionFaces.index(x, y, z);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		queue.add(start);
		set.set(start, true);
		int faces = 0;
		while (!queue.isEmpty()) {
			int i = queue.poll();
			faces |= cellFaces(i);
			for (int d = 0; d < 6; d++) {
				int j = neighbour(i, d);
				if (j >= 0 && !set.get(j)) {
					set.set(j, true);
					queue.add(j);
				}
			}
		}
		return faces;
	}

	private static int cellFaces(int i) {
		int faces = 0;
		int x = i & 15;
		if (x == 0) {
			faces |= 1 << SectionFaces.WEST;
		} else if (x == 15) {
			faces |= 1 << SectionFaces.EAST;
		}
		int y = i >> 8 & 15;
		if (y == 0) {
			faces |= 1 << SectionFaces.DOWN;
		} else if (y == 15) {
			faces |= 1 << SectionFaces.UP;
		}
		int z = i >> 4 & 15;
		if (z == 0) {
			faces |= 1 << SectionFaces.NORTH;
		} else if (z == 15) {
			faces |= 1 << SectionFaces.SOUTH;
		}
		return faces;
	}

	/** Neighbour index in direction order DOWN, UP, NORTH, SOUTH, WEST, EAST; -1 outside the section. */
	private static int neighbour(int i, int direction) {
		int x = i & 15;
		int y = i >> 8 & 15;
		int z = i >> 4 & 15;
		switch (direction) {
			case SectionFaces.DOWN: return y == 0 ? -1 : i - 256;
			case SectionFaces.UP: return y == 15 ? -1 : i + 256;
			case SectionFaces.NORTH: return z == 0 ? -1 : i - 16;
			case SectionFaces.SOUTH: return z == 15 ? -1 : i + 16;
			case SectionFaces.WEST: return x == 0 ? -1 : i - 1;
			default: return x == 15 ? -1 : i + 1;
		}
	}

	private static BitSet randomGrid(SplittableRandom random, double density, SectionFaces faces) {
		BitSet opaque = new BitSet(4096);
		faces.clear();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					if (random.nextDouble() < density) {
						opaque.set(SectionFaces.index(x, y, z));
						faces.setOpaque(x, y, z);
					}
				}
			}
		}
		return opaque;
	}

	/** Walls of opaque cells at random planes: large connected pockets with a few gaps, like caves and rooms. */
	private static BitSet wallGrid(SplittableRandom random, SectionFaces faces) {
		BitSet opaque = new BitSet(4096);
		faces.clear();
		int wallX = random.nextInt(16);
		int wallY = random.nextInt(16);
		int wallZ = random.nextInt(16);
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					boolean wall = x == wallX || y == wallY || z == wallZ;
					if (wall && random.nextInt(40) != 0) {
						opaque.set(SectionFaces.index(x, y, z));
						faces.setOpaque(x, y, z);
					}
				}
			}
		}
		return opaque;
	}

	@Test
	void indexMatchesVanillaLayout() {
		assertEquals(0, SectionFaces.index(0, 0, 0));
		assertEquals(1, SectionFaces.index(1, 0, 0));
		assertEquals(16, SectionFaces.index(0, 0, 1));
		assertEquals(256, SectionFaces.index(0, 1, 0));
		assertEquals(4095, SectionFaces.index(15, 15, 15));
	}

	@Test
	void emptySectionReachesEveryFace() {
		SectionFaces faces = new SectionFaces();
		faces.clear();
		assertEquals(SectionFaces.ALL_FACES, faces.fill(7, 7, 7));
		assertEquals(SectionFaces.ALL_FACES, faces.fill(0, 15, 0));
	}

	@Test
	void fullyOpaqueSectionReachesOnlyTheStartCellsFaces() {
		SectionFaces faces = new SectionFaces();
		faces.clear();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					faces.setOpaque(x, y, z);
				}
			}
		}
		assertEquals(0, faces.fill(5, 5, 5));
		assertEquals(1 << SectionFaces.WEST | 1 << SectionFaces.DOWN | 1 << SectionFaces.NORTH, faces.fill(0, 0, 0));
		assertEquals(1 << SectionFaces.EAST | 1 << SectionFaces.UP | 1 << SectionFaces.SOUTH, faces.fill(15, 15, 15));
		// An opaque start reuses only for itself.
		assertTrue(faces.reuses(15, 15, 15));
		assertFalse(faces.reuses(14, 15, 15));
	}

	@Test
	void opaqueStartJoinsItsNeighboursPockets() {
		// A wall at x = 8 splits the section; starting inside the wall reaches both halves.
		SectionFaces faces = new SectionFaces();
		faces.clear();
		BitSet opaque = new BitSet(4096);
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				faces.setOpaque(8, y, z);
				opaque.set(SectionFaces.index(8, y, z));
			}
		}
		int west = faces.fill(3, 8, 8);
		assertEquals(SectionFaces.ALL_FACES & ~(1 << SectionFaces.EAST), west);
		assertEquals(SectionFaces.ALL_FACES, faces.fill(8, 8, 8));
		assertEquals(reference(opaque, 8, 8, 8), faces.faces());
		// Not reusable from a non-opaque cell: from there only one half is reached.
		assertFalse(faces.reuses(3, 8, 8));
		assertEquals(reference(opaque, 3, 8, 8), faces.fill(3, 8, 8));
	}

	@Test
	void matchesReferenceOnRandomGrids() {
		SplittableRandom random = new SplittableRandom(0x5EC7105L);
		SectionFaces faces = new SectionFaces();
		double[] densities = {0.0, 0.05, 0.2, 0.3, 0.35, 0.4, 0.5, 0.7, 0.95, 1.0};
		int compared = 0;
		for (double density : densities) {
			for (int grid = 0; grid < 30; grid++) {
				BitSet opaque = randomGrid(random, density, faces);
				for (int s = 0; s < 40; s++) {
					int x = random.nextInt(16);
					int y = random.nextInt(16);
					int z = random.nextInt(16);
					assertEquals(reference(opaque, x, y, z), faces.fill(x, y, z), "density " + density + " start " + x + "," + y + "," + z);
					compared++;
				}
			}
		}
		for (int grid = 0; grid < 100; grid++) {
			BitSet opaque = wallGrid(random, faces);
			for (int s = 0; s < 40; s++) {
				int x = random.nextInt(16);
				int y = random.nextInt(16);
				int z = random.nextInt(16);
				assertEquals(reference(opaque, x, y, z), faces.fill(x, y, z));
				compared++;
			}
		}
		assertEquals(16_000, compared);
	}

	@Test
	void everyStartCellMatchesReferenceOnOneGrid() {
		SectionFaces faces = new SectionFaces();
		BitSet opaque = randomGrid(new SplittableRandom(31), 0.33, faces);
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					assertEquals(reference(opaque, x, y, z), faces.fill(x, y, z));
				}
			}
		}
	}

	@Test
	void reusedAnswersEqualAFreshFill() {
		SplittableRandom random = new SplittableRandom(77);
		SectionFaces faces = new SectionFaces();
		int reused = 0;
		int refused = 0;
		for (int grid = 0; grid < 200; grid++) {
			BitSet opaque = grid % 2 == 0 ? randomGrid(random, 0.1 + random.nextDouble() * 0.4, faces) : wallGrid(random, faces);
			int x = random.nextInt(16);
			int y = random.nextInt(16);
			int z = random.nextInt(16);
			int answer = faces.fill(x, y, z);
			// A sampled cell either reuses that answer, and then a fresh vanilla-structured fill
			// agrees, or does not, and the fill is repeated by the caller.
			for (int c = 0; c < 64; c++) {
				int cx = random.nextInt(16);
				int cy = random.nextInt(16);
				int cz = random.nextInt(16);
				if (faces.reuses(cx, cy, cz)) {
					assertEquals(reference(opaque, cx, cy, cz), answer);
					reused++;
				} else {
					refused++;
				}
			}
		}
		assertTrue(reused > 0 && refused > 0);
	}

	@Test
	void openAirReusesAcrossTheWholeSection() {
		SectionFaces faces = new SectionFaces();
		faces.clear();
		faces.fill(0, 0, 0);
		for (int i = 0; i < 4096; i++) {
			assertTrue(faces.reuses(i & 15, i >> 8 & 15, i >> 4 & 15));
		}
	}

	@Test
	void gridChangesDropTheLastAnswer() {
		SectionFaces faces = new SectionFaces();
		faces.clear();
		faces.fill(4, 4, 4);
		assertTrue(faces.reuses(4, 4, 4));
		assertTrue(faces.reuses(10, 10, 10));
		faces.setOpaque(1, 1, 1);
		assertFalse(faces.reuses(4, 4, 4));
		faces.fill(4, 4, 4);
		faces.clear();
		assertFalse(faces.reuses(4, 4, 4));
		assertFalse(faces.reuses(10, 10, 10));
		// A cleared grid floods from scratch: no opaque cells survive the clear.
		assertEquals(SectionFaces.ALL_FACES, faces.fill(1, 1, 1));
	}

	@Test
	void repeatedFillsDoNotAllocate() {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		SectionFaces faces = new SectionFaces();
		BitSet opaque = randomGrid(new SplittableRandom(5), 0.2, faces);
		long sink = 0;
		for (int i = 0; i < 20_000; i++) {
			sink += faces.fill(i & 15, i >> 4 & 15, i >> 8 & 15);
		}
		long thread = Thread.currentThread().threadId();
		long before = threads.getThreadAllocatedBytes(thread);
		for (int i = 0; i < 2_000; i++) {
			sink += faces.fill(i & 15, i >> 4 & 15, i >> 8 & 15);
		}
		long allocated = threads.getThreadAllocatedBytes(thread) - before;
		assertTrue(sink >= 0 && opaque.cardinality() > 0);
		// The counter itself and JIT transitions may allocate a little; vanilla allocates roughly
		// 160 KB per open-air fill, so 2,000 fills would be about 300 MB.
		assertTrue(allocated < 64 * 1024, "allocated " + allocated + " bytes in 2,000 fills");
	}
}
