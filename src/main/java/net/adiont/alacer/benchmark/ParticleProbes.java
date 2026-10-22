package net.adiont.alacer.benchmark;

import java.util.List;
import java.util.Random;

import net.adiont.alacer.mixin.ParticleManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.world.World;

/**
 * Verification-only particles for {@code render.fastParticleRemoval.verify}
 * ({@code benchmark.particleRemovalDiagnostic}): each exercises one case where dead-particle removal
 * could differ from vanilla's {@code removeAll}. Not a performance scenario.
 */
final class ParticleProbes {

	private static final int CAP = 4000;

	/** Layer changes made while ticking, by {@link LayerProbe}. */
	private static long layerMoves;
	/** Of those, moves made on the tick the probe died (it leaves the list whose dead list holds it). */
	private static long deathMoves;
	/** Particles added to a full list while that list was ticking (each evicts index 0). */
	private static long cappedAdds;
	/** Probes that died while their equal-id twin was still alive (removeAll takes both). */
	private static long liveTwins;
	private static long duplicates;
	private static long equalsProbes;

	private ParticleProbes() {
	}

	static String summary() {
		return layerMoves + " layer moves while ticking (" + deathMoves + " on the dying tick), " + cappedAdds + " additions to a full list while it ticked, "
			+ liveTwins + " deaths with a live equal-id twin, " + duplicates + " duplicated probes, " + equalsProbes
			+ " equals-overriding probes";
	}

	/** Adds one tick's probes near the given point; returns how many particles were added. */
	static int emit(ParticleManager manager, World world, Random random, double x, double y, double z, long tick) {
		int added = 0;
		for (int i = 0; i < 4; i++) {
			manager.add(new LayerProbe(world, x + jitter(random), y + jitter(random), z + jitter(random)));
			added++;
		}
		manager.add(new SpawnProbe(world, x, y + 1.0, z));
		added++;
		// Same id as a live smoke particle: when the probe dies, removeAll also removes its twin.
		Particle twin = manager.addParticle(ParticleType.SMOKE_NORMAL.getId(), x + jitter(random), y, z + jitter(random), 0.0, 0.0, 0.0);
		if (twin != null) {
			added++;
			Probe probe = new Probe(world, x, y, z, 1 + random.nextInt(3));
			probe.twin = twin;
			probe.setNetworkId(twin.getNetworkId());
			manager.add(probe);
			added++;
		}
		// The same object twice in one list.
		Probe duplicate = new Probe(world, x, y + 0.5, z, 2 + random.nextInt(4));
		manager.add(duplicate);
		manager.add(duplicate);
		duplicates++;
		added += 2;
		if (tick % 10 == 0) {
			// Overrides equals, so its list (atlas 2, otherwise unused) takes vanilla's removeAll.
			manager.add(new EqualsProbe(world, x, y + 1.5, z));
			equalsProbes++;
			added++;
		}
		return added;
	}

	private static double jitter(Random random) {
		return (random.nextDouble() - 0.5) * 2.0;
	}

	/** A short-lived misc-atlas particle with no other behavior. */
	static class Probe extends Particle {
		Particle twin;

		Probe(World world, double x, double y, double z, int lifetime) {
			super(world, x, y, z);
			this.lifetime = lifetime;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed && this.twin != null && !this.twin.removed) {
				liveTwins++;
			}
		}
	}

	/**
	 * Switches between the opaque and translucent lists every tick ({@code setAlpha}, during
	 * ticking), including on the tick it dies: it is then in one list's dead list but already in
	 * the other list. A probe that was already dead stays put, so it is removed there (one that
	 * kept switching would never be removed, in vanilla too, and probes would pile up).
	 */
	static final class LayerProbe extends Probe {
		LayerProbe(World world, double x, double y, double z) {
			super(world, x, y, z, 4 + (int) (Math.random() * 12));
		}

		@Override
		public void tick() {
			boolean wasRemoved = this.removed;
			super.tick();
			if (!wasRemoved) {
				this.setAlpha(this.getAlpha() == 1.0F ? 0.5F : 1.0F);
				layerMoves++;
				if (this.removed) {
					deathMoves++;
				}
			}
		}
	}

	/** Adds smoke to its own list while that list is ticking (at the cap, vanilla evicts index 0). */
	static final class SpawnProbe extends Probe {
		SpawnProbe(World world, double x, double y, double z) {
			super(world, x, y, z, 6);
		}

		@Override
		public void tick() {
			super.tick();
			ParticleManager manager = Minecraft.getInstance().particleManager;
			List<Particle> own = ((ParticleManagerAccessor) manager).alacer$particles()[0][1];
			for (int i = 0; i < 8; i++) {
				if (own.size() >= CAP) {
					cappedAdds++;
				}
				manager.addParticle(ParticleType.SMOKE_NORMAL.getId(), this.x, this.y, this.z, 0.0, 0.02, 0.0);
			}
		}
	}

	/** Overrides {@code equals} (with the same answer), which the fast path must not assume. */
	static final class EqualsProbe extends Probe {
		EqualsProbe(World world, double x, double y, double z) {
			super(world, x, y, z, 3);
		}

		@Override
		public int getAtlasType() {
			return 2;
		}

		@Override
		public boolean equals(Object object) {
			return super.equals(object);
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}
	}
}
