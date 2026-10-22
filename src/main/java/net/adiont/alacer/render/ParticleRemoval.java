package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.List;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.Entity;

/**
 * Dead-particle removal (optimization 8).
 *
 * <p>Every client tick, {@code ParticleManager.tickParticles(List)} ticks each particle of a list,
 * collects the ones that are now removed in a new {@code ArrayList} and calls
 * {@code particles.removeAll(dead)}. {@code ArrayList.removeAll} asks the dead list whether it
 * contains each live particle, which calls {@code live.equals(dead)} for every pair: about 3,500 x
 * 210 {@code Entity.equals} calls per tick in the particle scenario, all in the frame that ticks.
 *
 * <p>{@code Entity.equals} is true exactly when the other object is an entity with the same
 * {@code networkId}, and {@code Entity.hashCode} returns that {@code networkId}. For particle
 * classes that inherit both from {@code Entity} (every vanilla particle), removal is therefore
 * "remove each particle whose network id is a dead particle's id", which {@link KeyedRemoval}
 * does in one pass with a reused id set. Particles with equal ids (possible after
 * {@code setNetworkId} or id wrap-around) and duplicates are removed exactly as {@code removeAll}
 * removes them. Only the {@code removeAll} call changes: the ticking loop, its handling of
 * particles added during the tick (including the 4,000-per-list cap's {@code remove(0)}), layer
 * changes through {@code setAlpha}, the removed particles, survivor order and the list object are
 * vanilla's.
 *
 * <p>Vanilla's call is used when the option is off, off the client thread, when either list is
 * not exactly {@code java.util.ArrayList}, or when any particle in either list is null or of a
 * class that overrides {@code equals} or {@code hashCode} (checked once per class). Verification
 * ({@code render.fastParticleRemoval.verify}) runs the fast path on a copy, vanilla's
 * {@code removeAll} on the real list, and compares the survivors element by element.
 */
public final class ParticleRemoval {

	private static final long SUMMARY_EVERY = 100_000L;

	/** True when the class's {@code equals} and {@code hashCode} are {@code Entity}'s own. */
	private static final ClassValue<Boolean> ENTITY_IDENTITY = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return Entity.class.isAssignableFrom(type)
					&& type.getMethod("equals", Object.class).getDeclaringClass() == Entity.class
					&& type.getMethod("hashCode").getDeclaringClass() == Entity.class;
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
	};

	/** Network ids as keys; one class remembered, since a list's particles share a few classes. */
	static final KeyedRemoval.Keys<Particle> KEYS = new KeyedRemoval.Keys<>() {
		private Class<?> lastClass;
		private boolean lastExact;

		@Override
		public boolean exact(Particle particle) {
			Class<?> type = particle.getClass();
			if (type != this.lastClass) {
				this.lastExact = ENTITY_IDENTITY.get(type);
				this.lastClass = type;
			}
			return this.lastExact;
		}

		@Override
		public int key(Particle particle) {
			return particle.hashCode();
		}
	};

	private static final KeyedRemoval<Particle> REMOVAL = new KeyedRemoval<>(KEYS);
	private static final ArrayList<Particle> COPY = new ArrayList<>();

	private static Thread owner;
	private static long compared;
	private static long removedTotal;
	private static long fallbacks;
	private static long mismatches;

	private ParticleRemoval() {
	}

	/** Called on the client thread at startup; only that thread uses the reused key set. */
	public static void init() {
		owner = Thread.currentThread();
	}

	/** Replaces {@code particles.removeAll(dead)} in {@code ParticleManager.tickParticles(List)}. */
	public static boolean removeAll(List<Particle> particles, List<Particle> dead) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastParticleRemoval || particles.getClass() != ArrayList.class || dead.getClass() != ArrayList.class
			|| Thread.currentThread() != owner) {
			return particles.removeAll(dead);
		}
		ArrayList<Particle> list = (ArrayList<Particle>) particles;
		ArrayList<Particle> deadList = (ArrayList<Particle>) dead;
		if (config.verifyFastParticleRemoval) {
			return verify(list, deadList);
		}
		int removed = REMOVAL.removeAll(list, deadList);
		if (removed < 0) {
			fallbacks++;
			return list.removeAll(deadList);
		}
		return removed > 0;
	}

	// --- Verification ---

	private static boolean verify(ArrayList<Particle> list, ArrayList<Particle> dead) {
		COPY.clear();
		COPY.addAll(list);
		int removed = REMOVAL.removeAll(COPY, dead);
		boolean expected = list.removeAll(dead);
		if (removed < 0) {
			fallbacks++;
		} else {
			removedTotal += removed;
			boolean same = (removed > 0) == expected && COPY.size() == list.size();
			for (int i = 0; same && i < list.size(); i++) {
				same = COPY.get(i) == list.get(i);
			}
			if (!same) {
				mismatch(list.size(), COPY.size(), dead.size());
			}
		}
		COPY.clear();
		if (++compared % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Particle removal verification: {}", verificationSummary());
		}
		return expected;
	}

	private static void mismatch(int expectedSize, int fastSize, int deadCount) {
		mismatches++;
		if (mismatches <= 10) {
			Alacer.LOGGER.warn("Particle removal mismatch: {} survivors vs vanilla {} ({} dead)", fastSize, expectedSize, deadCount);
		}
	}

	public static String verificationSummary() {
		return compared + " removals compared (" + removedTotal + " particles removed, " + fallbacks + " vanilla fallbacks), "
			+ mismatches + " mismatched";
	}
}
