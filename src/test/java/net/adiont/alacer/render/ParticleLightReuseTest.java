package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The reuse table against a HashMap across passes, its O(1) emptying and its full limit. */
class ParticleLightReuseTest {

	@Test
	void hooksAreNotRequestedWithoutTheMod() {
		assertFalse(ParticleLightReuse.HOOKED);
	}

	@Test
	void tableMatchesHashMapAndForgetsEveryPass() {
		ParticleLightReuse.Table table = new ParticleLightReuse.Table(1 << 12);
		Random random = new Random(7);
		for (int pass = 0; pass < 200; pass++) {
			table.clear();
			Map<List<Integer>, int[]> expected = new HashMap<>();
			int offset = pass % 3 == 0 ? 0 : random.nextInt(2_000_000) - 1_000_000;
			for (int i = 0; i < 4000; i++) {
				int x = offset + random.nextInt(24) - 12;
				int y = random.nextInt(300) - 20;
				int z = offset + random.nextInt(24) - 12;
				List<Integer> key = List.of(x, y, z);
				int slot = table.find(x, y, z);
				int[] stored = expected.get(key);
				if (stored != null) {
					assertTrue(slot >= 0);
					assertEquals(stored[0], table.sky[slot]);
					assertEquals(stored[1], table.block[slot]);
				} else if (slot != ParticleLightReuse.Table.FULL) {
					assertTrue(slot < 0);
					int sky = random.nextInt(16);
					int block = random.nextInt(16);
					table.put(~slot, x, y, z, sky, block);
					expected.put(key, new int[] {sky, block});
				} else {
					assertEquals(3072, expected.size());
				}
			}
		}
	}

	@Test
	void fullTableStoresNothingMoreUntilTheNextPass() {
		ParticleLightReuse.Table table = new ParticleLightReuse.Table(16);
		for (int i = 0; i < 12; i++) {
			int slot = table.find(i, 64, -i);
			assertTrue(slot < 0 && slot != ParticleLightReuse.Table.FULL);
			table.put(~slot, i, 64, -i, 15, i);
		}
		assertEquals(ParticleLightReuse.Table.FULL, table.find(12, 64, -12));
		assertEquals(5, table.block[table.find(5, 64, -5)]);
		table.clear();
		assertTrue(table.find(5, 64, -5) < 0);
	}
}
