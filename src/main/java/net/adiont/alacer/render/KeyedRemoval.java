package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * {@code list.removeAll(dead)} for two {@link ArrayList}s in one pass, for elements whose
 * {@code equals} is equality of an {@code int} key (used by {@link ParticleRemoval}).
 *
 * <p>{@code ArrayList.removeAll} removes every element {@code e} for which {@code dead.contains(e)},
 * that is {@code e.equals(d)} for some {@code d} in {@code dead}: one {@code equals} call per pair,
 * O(list x dead). When {@link Keys#exact} holds for every element of both lists, {@code e.equals(d)}
 * is {@code key(e) == key(d)}, so the same elements are removed by testing each element's key
 * against a set of the dead keys. Survivors keep their order and the list object is the same.
 * Survivors are moved down with {@code set} and the tail is removed from the end one element at
 * a time, so the backing array, size and modification count end as {@code removeAll} leaves them
 * (it adds the number removed to the count; {@code set} adds nothing, each {@code remove} one).
 *
 * <p>Nothing is changed when an element is null or not {@link Keys#exact}: {@link #removeAll}
 * returns -1 and the caller runs {@code removeAll} itself. The key set is reused (it grows to the
 * largest dead list seen and is never cleared, only re-stamped), so a call allocates nothing.
 * One thread at a time.
 */
public final class KeyedRemoval<E> {

	/** How elements compare; must match their {@code equals} exactly. */
	public interface Keys<E> {
		/**
		 * True when {@code element.equals(other)} is exactly {@code key(element) == key(other)} for
		 * every {@code other} for which {@code exact} holds, and {@code key} reads the value
		 * {@code equals} compares, without side effects.
		 */
		boolean exact(E element);

		int key(E element);
	}

	private static final int MIN_CAPACITY = 64;

	private final Keys<? super E> keys;
	private int[] table = new int[MIN_CAPACITY];
	private int[] stamps = new int[MIN_CAPACITY];
	private int stamp;
	private int mask;

	public KeyedRemoval(Keys<? super E> keys) {
		this.keys = keys;
	}

	/**
	 * Removes from {@code list} every element equal to an element of {@code dead}, as
	 * {@code list.removeAll(dead)} would.
	 *
	 * @return the number of elements removed, or -1 (list unchanged) when the fast path does not
	 *     apply and the caller must use {@code removeAll}
	 */
	public int removeAll(ArrayList<E> list, ArrayList<E> dead) {
		int deadCount = dead.size();
		if (deadCount == 0) {
			// removeAll would call no equals and change nothing.
			return 0;
		}
		Keys<? super E> keys = this.keys;
		prepare(deadCount);
		for (int i = 0; i < deadCount; i++) {
			E element = dead.get(i);
			if (element == null || !keys.exact(element)) {
				return -1;
			}
			add(keys.key(element));
		}
		int size = list.size();
		int first = -1;
		for (int r = 0; r < size; r++) {
			E element = list.get(r);
			if (element == null || !keys.exact(element)) {
				return -1;
			}
			if (first < 0 && contains(keys.key(element))) {
				first = r;
			}
		}
		if (first < 0) {
			return 0;
		}
		int w = first;
		for (int r = first + 1; r < size; r++) {
			E element = list.get(r);
			if (!contains(keys.key(element))) {
				list.set(w++, element);
			}
		}
		for (int i = size - 1; i >= w; i--) {
			list.remove(i);
		}
		return size - w;
	}

	/** Empties the key set, sized for at least twice the dead keys (load at most one half). */
	private void prepare(int count) {
		int capacity = MIN_CAPACITY;
		while (capacity < count * 2 && capacity < 1 << 30) {
			capacity <<= 1;
		}
		if (capacity > this.table.length) {
			this.table = new int[capacity];
			this.stamps = new int[capacity];
			this.stamp = 0;
		}
		if (++this.stamp == 0) {
			Arrays.fill(this.stamps, 0);
			this.stamp = 1;
		}
		this.mask = capacity - 1;
	}

	private int slot(int key) {
		int h = key * 0x9E3779B9;
		return (h ^ h >>> 16) & this.mask;
	}

	private void add(int key) {
		int i = slot(key);
		while (this.stamps[i] == this.stamp) {
			if (this.table[i] == key) {
				return;
			}
			i = i + 1 & this.mask;
		}
		this.stamps[i] = this.stamp;
		this.table[i] = key;
	}

	private boolean contains(int key) {
		int i = slot(key);
		while (this.stamps[i] == this.stamp) {
			if (this.table[i] == key) {
				return true;
			}
			i = i + 1 & this.mask;
		}
		return false;
	}
}
