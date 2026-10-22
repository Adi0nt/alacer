package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The counter's stamp set against a HashSet, its O(1) clear and its overflow limit. */
class ParticleLightRepeatsTest {

	@Test
	void disabledWithoutTheMod() {
		assertFalse(ParticleLightRepeats.ENABLED);
		assertEquals("off", ParticleLightRepeats.summary());
	}

	@Test
	void stampSetMatchesHashSetAcrossClears() {
		ParticleLightRepeats.StampSet set = new ParticleLightRepeats.StampSet(1 << 12);
		Random random = new Random(1);
		for (int pass = 0; pass < 50; pass++) {
			set.clear();
			Set<List<Integer>> expected = new HashSet<>();
			for (int i = 0; i < 2500; i++) {
				int x = random.nextInt(40) - 20;
				int y = random.nextInt(6) + (pass % 2 == 0 ? 60 : -3);
				int z = random.nextInt(40) - 20;
				int w = random.nextInt(2);
				assertEquals(expected.add(List.of(x, y, z, w)), set.add(x, y, z, w));
			}
			assertFalse(set.overflowed);
		}
	}

	@Test
	void stampSetStopsAddingPastThreeQuarters() {
		ParticleLightRepeats.StampSet set = new ParticleLightRepeats.StampSet(16);
		for (int i = 0; i < 12; i++) {
			assertTrue(set.add(i, 0, 0, 0));
		}
		assertFalse(set.add(12, 0, 0, 0));
		assertTrue(set.overflowed);
		assertFalse(set.add(3, 0, 0, 0));
		set.clear();
		assertFalse(set.overflowed);
		assertTrue(set.add(12, 0, 0, 0));
	}
}
