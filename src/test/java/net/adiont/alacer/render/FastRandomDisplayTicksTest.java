package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.Bootstrap;
import net.minecraft.block.Blocks;

class FastRandomDisplayTicksTest {

	@BeforeAll
	static void initBlocks() {
		try {
			Bootstrap.init();
		} catch (Throwable ignored) {
		}
	}

	@BeforeEach
	void setUp() {
		FastRandomDisplayTicks.clear();
	}

	@Test
	void airBlockDoesNotHaveDisplayTick() {
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.AIR));
	}

	@Test
	void stoneDoesNotHaveDisplayTick() {
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.STONE));
	}

	@Test
	void torchHasDisplayTick() {
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.TORCH));
	}

	@Test
	void redstoneOreHasDisplayTick() {
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.REDSTONE_ORE));
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.LIT_REDSTONE_ORE));
	}

	@Test
	void barrierDoesNotHaveDisplayTickDirectly() {
		// Barrier particles are handled by holdingBarrier condition in doRandomDisplayTicks,
		// the block itself does not override randomDisplayTick.
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.BARRIER));
	}

	@Test
	void clearResetsCounters() {
		FastRandomDisplayTicks.clear();
		assertEquals(0, FastRandomDisplayTicks.ticks());
		assertEquals(0, FastRandomDisplayTicks.blocksChecked());
		assertEquals(0, FastRandomDisplayTicks.blocksTicked());
		assertEquals(0, FastRandomDisplayTicks.emptySectionsSkipped());
		assertEquals(0, FastRandomDisplayTicks.airSkipped());
		assertEquals(0, FastRandomDisplayTicks.mismatches());
	}

	@Test
	void standardBlocksDisplayTickClassification() {
		// Verify known blocks with display ticks
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.TORCH));
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.REDSTONE_ORE));
		assertTrue(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.LIT_REDSTONE_ORE));

		// Verify known blocks without display ticks
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.AIR));
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.STONE));
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.DIRT));
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.GRASS));
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.BEDROCK));
		assertFalse(FastRandomDisplayTicks.hasRandomDisplayTick(Blocks.BARRIER));

		assertEquals(0, FastRandomDisplayTicks.mismatches());
	}
}
