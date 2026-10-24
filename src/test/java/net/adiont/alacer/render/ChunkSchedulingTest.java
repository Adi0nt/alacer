package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ChunkSchedulingTest {

	@Test
	void budgetPercentClampsAndMapsChoices() {
		assertEquals(25, ChunkScheduling.budgetPercent(-1));
		assertEquals(25, ChunkScheduling.budgetPercent(0));
		assertEquals(50, ChunkScheduling.budgetPercent(1));
		assertEquals(100, ChunkScheduling.budgetPercent(2));
		assertEquals(200, ChunkScheduling.budgetPercent(3));
		assertEquals(400, ChunkScheduling.budgetPercent(4));
		assertEquals(400, ChunkScheduling.budgetPercent(5));
	}

	@Test
	void vanillaBudgetLeavesDeadlineUnchanged() {
		long vanilla = 1_000_000_000L;
		long now = 500_000_000L;
		assertEquals(vanilla, ChunkScheduling.deadline(vanilla, now, ChunkScheduling.VANILLA_BUDGET));
	}

	@Test
	void expiredDeadlineLeavesVanillaUnchanged() {
		long vanilla = 500_000_000L;
		long now = 600_000_000L;
		assertEquals(vanilla, ChunkScheduling.deadline(vanilla, now, 4));
	}

	@Test
	void scaledBudgetsRescaleRemainingTime() {
		long now = 100_000_000L;
		long vanilla = 200_000_000L; // remaining = 100_000_000

		// choice 0: 25% -> now + 25_000_000
		assertEquals(now + 25_000_000L, ChunkScheduling.deadline(vanilla, now, 0));

		// choice 1: 50% -> now + 50_000_000
		assertEquals(now + 50_000_000L, ChunkScheduling.deadline(vanilla, now, 1));

		// choice 3: 200% -> now + 200_000_000
		assertEquals(now + 200_000_000L, ChunkScheduling.deadline(vanilla, now, 3));

		// choice 4: 400% -> now + 400_000_000
		assertEquals(now + 400_000_000L, ChunkScheduling.deadline(vanilla, now, 4));
	}

	@Test
	void threadClampingAndBufferCalculation() {
		assertEquals(1, ChunkScheduling.threads(0));
		assertEquals(1, ChunkScheduling.threads(1));
		assertEquals(4, ChunkScheduling.threads(4));
		assertEquals(6, ChunkScheduling.threads(6));
		assertEquals(6, ChunkScheduling.threads(10));

		assertEquals(4, ChunkScheduling.buffers(1));
		assertEquals(5, ChunkScheduling.buffers(2));
		assertEquals(7, ChunkScheduling.buffers(4));
		assertEquals(9, ChunkScheduling.buffers(6));
	}
}
