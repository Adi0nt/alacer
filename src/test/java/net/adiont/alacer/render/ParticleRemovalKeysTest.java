package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.entity.particle.SmokeParticle;

/** Real (unticked, worldless) particles through ParticleRemoval's keys: Entity.equals by network id. */
class ParticleRemovalKeysTest {

	static class Plain extends Particle {
		Plain() {
			super(null, 0.0, 0.0, 0.0);
		}
	}

	static final class Overriding extends Plain {
		@Override
		public boolean equals(Object other) {
			return other == this;
		}
	}

	static final class OwnHash extends Plain {
		@Override
		public int hashCode() {
			return 7;
		}
	}

	@Test
	void keysAreEntityEqualityByNetworkId() {
		assertTrue(ParticleRemoval.KEYS.exact(new Plain()));
		assertFalse(ParticleRemoval.KEYS.exact(new Overriding()));
		assertFalse(ParticleRemoval.KEYS.exact(new OwnHash()));
		assertTrue(ParticleRemoval.KEYS.exact(new Plain()));

		Plain a = new Plain();
		Plain b = new Plain();
		Plain twin = new Plain();
		twin.setNetworkId(a.getNetworkId());
		Plain c = new Plain();
		assertEquals(a.getNetworkId(), ParticleRemoval.KEYS.key(twin));
		assertTrue(a.equals(twin));

		List<Particle> source = List.of(a, b, twin, c, b);
		ArrayList<Particle> vanilla = new ArrayList<>(source);
		ArrayList<Particle> fast = new ArrayList<>(source);
		ArrayList<Particle> dead = new ArrayList<>(List.of(twin, b));
		assertTrue(vanilla.removeAll(dead));
		assertEquals(4, new KeyedRemoval<>(ParticleRemoval.KEYS).removeAll(fast, dead));
		assertEquals(1, fast.size());
		assertSame(vanilla.get(0), fast.get(0));
		assertSame(c, fast.get(0));
	}

	@Test
	void vanillaParticleClassesUseEntityEquality() {
		assertTrue(ParticleRemoval.KEYS.exact(new SmokeParticle.Factory().create(0, null, 0, 0, 0, 0, 0, 0)));
	}
}
