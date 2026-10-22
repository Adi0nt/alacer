package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class KeyedRemovalTest {

	/** Equal when the other is an Item with the same id, like Entity.equals and networkId. */
	static class Item {
		final int id;

		Item(int id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Item && ((Item) other).id == this.id;
		}

		@Override
		public int hashCode() {
			return this.id;
		}
	}

	/** Overrides equals, so the fast path must refuse it. */
	static final class Custom extends Item {
		Custom(int id) {
			super(id);
		}

		@Override
		public boolean equals(Object other) {
			return other == this;
		}
	}

	static final KeyedRemoval.Keys<Item> KEYS = new KeyedRemoval.Keys<>() {
		@Override
		public boolean exact(Item item) {
			return item.getClass() == Item.class;
		}

		@Override
		public int key(Item item) {
			return item.id;
		}
	};

	/** Exposes the modification count that removeAll and remove(int) change. */
	static final class CountingList<E> extends ArrayList<E> {
		CountingList(List<E> source) {
			super(source);
		}

		int modifications() {
			return this.modCount;
		}
	}

	private static void assertSameElements(List<?> expected, List<?> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertSame(expected.get(i), actual.get(i), "index " + i);
		}
	}

	/** Runs vanilla removeAll and the fast path on copies of the same list and compares everything. */
	private static int compare(KeyedRemoval<Item> removal, List<Item> list, ArrayList<Item> dead) {
		CountingList<Item> vanilla = new CountingList<>(list);
		CountingList<Item> fast = new CountingList<>(list);
		int vanillaCount = vanilla.modifications();
		int fastCount = fast.modifications();
		boolean changed = vanilla.removeAll(dead);
		int removed = removal.removeAll(fast, dead);
		if (removed < 0) {
			assertSameElements(list, fast);
			assertEquals(fastCount, fast.modifications());
			return removed;
		}
		assertEquals(changed, removed > 0);
		assertEquals(list.size() - vanilla.size(), removed);
		assertSameElements(vanilla, fast);
		assertEquals(vanilla.modifications() - vanillaCount, fast.modifications() - fastCount);
		return removed;
	}

	@Test
	void matchesRemoveAllOnRandomListsWithDuplicatesAndSharedIds() {
		Random random = new Random(1789);
		KeyedRemoval<Item> removal = new KeyedRemoval<>(KEYS);
		int removedTotal = 0;
		for (int round = 0; round < 3000; round++) {
			int size = random.nextInt(round % 50 == 0 ? 5000 : 300);
			int ids = 1 + random.nextInt(Math.max(1, size * 2));
			List<Item> list = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				// Repeated objects, distinct objects with a shared id and negative ids all occur.
				if (!list.isEmpty() && random.nextInt(20) == 0) {
					list.add(list.get(random.nextInt(list.size())));
				} else {
					list.add(new Item(random.nextInt(ids) - ids / 2 + (random.nextInt(50) == 0 ? Integer.MIN_VALUE : 0)));
				}
			}
			ArrayList<Item> dead = new ArrayList<>();
			int deadCount = random.nextInt(Math.max(1, size / (1 + random.nextInt(8))) + 1);
			for (int i = 0; i < deadCount; i++) {
				// Mostly list members; some equal-id strangers and ids not in the list at all.
				int kind = random.nextInt(10);
				if (kind < 7 && !list.isEmpty()) {
					dead.add(list.get(random.nextInt(list.size())));
				} else if (kind < 9 && !list.isEmpty()) {
					dead.add(new Item(list.get(random.nextInt(list.size())).id));
				} else {
					dead.add(new Item(random.nextInt()));
				}
			}
			int removed = compare(removal, list, dead);
			assertTrue(removed >= 0);
			removedTotal += removed;
		}
		assertTrue(removedTotal > 100_000, "removed " + removedTotal);
	}

	@Test
	void emptyDeadListAndNoMatchesChangeNothing() {
		KeyedRemoval<Item> removal = new KeyedRemoval<>(KEYS);
		List<Item> list = List.of(new Item(1), new Item(2), new Item(3));
		assertEquals(0, compare(removal, list, new ArrayList<>()));
		assertEquals(0, compare(removal, list, new ArrayList<>(List.of(new Item(4)))));
		assertEquals(0, compare(removal, new ArrayList<>(), new ArrayList<>(List.of(new Item(4)))));
		ArrayList<Item> all = new ArrayList<>(list);
		assertEquals(3, compare(removal, list, all));
	}

	@Test
	void refusesNullsAndOverriddenEqualsWithoutChangingTheList() {
		KeyedRemoval<Item> removal = new KeyedRemoval<>(KEYS);
		Item a = new Item(1);
		Item b = new Item(2);
		List<Item> withNull = new ArrayList<>();
		withNull.add(a);
		withNull.add(null);
		withNull.add(b);
		assertEquals(-1, compare(removal, withNull, new ArrayList<>(List.of(a))));
		ArrayList<Item> deadNull = new ArrayList<>();
		deadNull.add(null);
		assertEquals(-1, compare(removal, List.of(a, b), deadNull));
		Custom custom = new Custom(2);
		assertEquals(-1, compare(removal, List.of(a, custom, b), new ArrayList<>(List.of(a))));
		assertEquals(-1, compare(removal, List.of(a, b), new ArrayList<>(List.of(custom))));
		// The refused calls left no state behind.
		assertEquals(1, compare(removal, List.of(a, b), new ArrayList<>(List.of(new Item(2)))));
	}

	@Test
	void keySetGrowsAndIsReusedAcrossCalls() {
		KeyedRemoval<Item> removal = new KeyedRemoval<>(KEYS);
		for (int size : new int[]{10, 20_000, 3, 30_000, 5}) {
			List<Item> list = new ArrayList<>();
			ArrayList<Item> dead = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				Item item = new Item(i * 64);
				list.add(item);
				if (i % 3 == 0) {
					dead.add(item);
				}
			}
			assertEquals(dead.size(), compare(removal, list, dead));
		}
	}
}
