package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResidentMeshArenaTest {

	private ResidentMeshArena arena;

	@BeforeEach
	void setUp() {
		// Test arena with small page size (e.g. 2800 bytes = 100 vertices of 28 bytes)
		arena = new ResidentMeshArena(2800, false); // false = offline mode, no real OpenGL buffer creation
	}

	@Test
	void allocationAlignedToVertexStride() {
		ResidentMeshArena.Slice slice1 = arena.allocate(10); // 10 vertices = 280 bytes
		assertNotNull(slice1);
		assertEquals(0, slice1.byteOffset % 28, "Byte offset must align to vertex stride 28");
		assertEquals(0, slice1.firstVertex);
		assertEquals(10, slice1.vertexCount);
		assertEquals(280, slice1.byteLength);

		ResidentMeshArena.Slice slice2 = arena.allocate(25); // 25 vertices = 700 bytes
		assertNotNull(slice2);
		assertEquals(0, slice2.byteOffset % 28);
		assertEquals(10, slice2.firstVertex);
		assertEquals(25, slice2.vertexCount);
		assertEquals(700, slice2.byteLength);
		assertEquals(280, slice2.byteOffset);
	}

	@Test
	void freeCoalescesContiguousRanges() {
		ResidentMeshArena.Slice s1 = arena.allocate(10); // [0, 280)
		ResidentMeshArena.Slice s2 = arena.allocate(20); // [280, 840)
		ResidentMeshArena.Slice s3 = arena.allocate(30); // [840, 1680)

		// Free s2 first
		arena.free(s2);

		// Free s1 -> should coalesce with s2 into [0, 840)
		arena.free(s1);

		// Now allocate 30 vertices (840 bytes) -> should fit exactly into coalesced [0, 840)
		ResidentMeshArena.Slice s4 = arena.allocate(30);
		assertNotNull(s4);
		assertEquals(0, s4.byteOffset);
		assertEquals(0, s4.firstVertex);
		assertEquals(30, s4.vertexCount);

		arena.free(s3);
		arena.free(s4);
		// Entire page is free
		assertEquals(1, arena.totalFreeRanges());
	}

	@Test
	void allocatesNewPageWhenCapacityExceeded() {
		ResidentMeshArena.Slice s1 = arena.allocate(90); // 90 * 28 = 2520 bytes out of 2800
		assertNotNull(s1);
		assertEquals(0, s1.pageIndex);

		// Try to allocate 20 vertices (560 bytes) -> only 280 left in page 0, should allocate page 1
		ResidentMeshArena.Slice s2 = arena.allocate(20);
		assertNotNull(s2);
		assertEquals(1, s2.pageIndex);
		assertEquals(0, s2.byteOffset);
		assertEquals(0, s2.firstVertex);
		assertEquals(2, arena.pageCount());
	}

	@Test
	void zeroOrNegativeVerticesReturnNull() {
		assertNull(arena.allocate(0));
		assertNull(arena.allocate(-5));
	}

	@Test
	void clearReleasesAllPages() {
		arena.allocate(50);
		arena.allocate(80);
		assertTrue(arena.pageCount() >= 2);

		arena.clear();
		assertEquals(0, arena.pageCount());
	}

	@Test
	void stressRandomAllocationsAndFreesMaintainIntegrity() {
		List<ResidentMeshArena.Slice> active = new ArrayList<>();
		java.util.Random rand = new java.util.Random(12345);

		for (int i = 0; i < 500; i++) {
			if (active.isEmpty() || rand.nextBoolean()) {
				int count = 1 + rand.nextInt(20);
				ResidentMeshArena.Slice slice = arena.allocate(count);
				if (slice != null) {
					active.add(slice);
				}
			} else {
				int idx = rand.nextInt(active.size());
				ResidentMeshArena.Slice slice = active.remove(idx);
				arena.free(slice);
			}
		}

		// Free everything left
		for (ResidentMeshArena.Slice s : active) {
			arena.free(s);
		}

		// Verify arena can allocate a full page again
		ResidentMeshArena.Slice full = arena.allocate(100);
		assertNotNull(full);
		assertEquals(0, full.byteOffset);
		arena.free(full);
	}
}
