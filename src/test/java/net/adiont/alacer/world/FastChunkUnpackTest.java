package net.adiont.alacer.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.adiont.alacer.mixin.WorldChunkSectionAccessor;
import net.minecraft.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.WorldChunkSection;

class FastChunkUnpackTest {

	@BeforeAll
	static void initBlocks() {
		try {
			Bootstrap.init();
		} catch (Throwable ignored) {
			// In test classpath, Stats.init throws IllegalAccessError on BannerRecipes,
			// but Bootstrap.init() has already set initialized=true and completed Block.init().
		}
	}

	@Test
	void unpackBlockStatesMatchesScalarUnpack() {
		byte[] data = new byte[8192];
		char[] blockStates = new char[4096];
		Random rand = new Random(42L);
		rand.nextBytes(data);

		FastChunkUnpack.unpackBlockStates(data, 0, blockStates);

		for (int i = 0; i < 4096; i++) {
			char expected = (char) (((data[i * 2 + 1] & 0xFF) << 8) | (data[i * 2] & 0xFF));
			assertEquals(expected, blockStates[i], "Mismatch at index " + i);
		}
	}

	@Test
	void unpackBlockStatesWithOffsetMatchesScalarUnpack() {
		int offset = 128;
		byte[] data = new byte[offset + 8192 + 64];
		char[] blockStates = new char[4096];
		Random rand = new Random(1337L);
		rand.nextBytes(data);

		FastChunkUnpack.unpackBlockStates(data, offset, blockStates);

		for (int i = 0; i < 4096; i++) {
			int pos = offset + i * 2;
			char expected = (char) (((data[pos + 1] & 0xFF) << 8) | (data[pos] & 0xFF));
			assertEquals(expected, blockStates[i], "Mismatch at offset index " + i);
		}
	}

	@Test
	void validateBlockCountersMatchesVanilla() {
		WorldChunkSection section = new WorldChunkSection(0, true);

		// Fill section with a mix of Air, Stone, Dirt, Grass, and random ticking blocks (Wheat, Sapling)
		int expectedNonAir = 0;
		int expectedTicking = 0;

		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					if ((x + y + z) % 5 == 0) {
						section.setBlockState(x, y, z, Blocks.STONE.defaultState());
						expectedNonAir++;
					} else if ((x + y + z) % 5 == 1) {
						section.setBlockState(x, y, z, Blocks.GRASS.defaultState());
						expectedNonAir++;
						if (Blocks.GRASS.ticksRandomly()) {
							expectedTicking++;
						}
					} else if ((x + y + z) % 5 == 2) {
						section.setBlockState(x, y, z, Blocks.WHEAT.defaultState());
						expectedNonAir++;
						if (Blocks.WHEAT.ticksRandomly()) {
							expectedTicking++;
						}
					}
					// others are air
				}
			}
		}

		FastChunkUnpack.validateBlockCounters(section);

		int actualNonAir;
		int actualTicking;
		if (section instanceof WorldChunkSectionAccessor acc) {
			actualNonAir = acc.alacer$getNonAirBlockCount();
			actualTicking = acc.alacer$getRandomTickingBlockCount();
		} else {
			try {
				java.lang.reflect.Field f1 = WorldChunkSection.class.getDeclaredField("nonAirBlockCount");
				f1.setAccessible(true);
				actualNonAir = f1.getInt(section);
				java.lang.reflect.Field f2 = WorldChunkSection.class.getDeclaredField("randomTickingBlockCount");
				f2.setAccessible(true);
				actualTicking = f2.getInt(section);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		assertEquals(expectedNonAir, actualNonAir);
		assertEquals(expectedTicking, actualTicking);
	}
}
