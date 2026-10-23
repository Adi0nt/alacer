package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkTimeFloorTest {
	private static final long MS = 1_000_000L;

	@Test
	void theFirstFrameAndVanillaDeadlinesWithEnoughTimeAreUnchanged() {
		ChunkTimeFloor floor = new ChunkTimeFloor();
		assertEquals(5 * MS, floor.deadline(5 * MS, 10 * MS - 5 * MS), "no previous frame");
		// previous call at 5 ms; 64 ms later the floor is 2 ms and vanilla leaves 3 ms
		assertEquals(72 * MS, floor.deadline(72 * MS, 69 * MS));
	}

	@Test
	void anExpiredDeadlineGetsAThirtySecondOfThePreviousFrame() {
		ChunkTimeFloor floor = new ChunkTimeFloor();
		floor.deadline(0, 100 * MS);
		assertEquals(164 * MS + 2 * MS, floor.deadline(150 * MS, 164 * MS), "64 ms frame: 2 ms floor");
		assertEquals(168 * MS + MS / 8, floor.deadline(0, 168 * MS), "4 ms frame: 0.125 ms, about vanilla's single upload");
		assertEquals(200 * MS + 1 * MS, floor.deadline(200 * MS + MS / 2, 200 * MS), "partly used deadline raised to the floor");
	}

	@Test
	void theFloorNeverExceedsVanillasLargestBudgetAndIgnoresPauses() {
		ChunkTimeFloor floor = new ChunkTimeFloor();
		floor.deadline(0, 0 + 1);
		assertEquals(201 * MS + 1 + ChunkTimeFloor.MAX_NANOS, floor.deadline(0, 201 * MS + 1), "200 ms frame capped at 4.17 ms");
		assertEquals(0, floor.deadline(0, 2201 * MS + 1), "a 2 s gap is not a frame: vanilla's deadline is kept");
		floor.reset();
		assertEquals(7, floor.deadline(7, 3000 * MS), "reset forgets the previous frame");
	}
}
