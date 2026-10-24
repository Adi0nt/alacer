package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.resource.Identifier;

class EntityRendererCacheTest {

	private static class DummyRenderer extends EntityRenderer<Entity> {
		DummyRenderer() {
			super(null);
		}

		@Override
		protected Identifier getTextureLocation(Entity entity) {
			return null;
		}
	}

	@BeforeEach
	void setUp() {
		EntityRendererCache.clear();
	}

	@Test
	void emptyCacheReturnsNullAndCountsMiss() {
		assertNull(EntityRendererCache.get(ZombieEntity.class));
		assertEquals(1, EntityRendererCache.queries());
		assertEquals(0, EntityRendererCache.hits());
		assertEquals(1, EntityRendererCache.misses());
	}

	@Test
	void putAndGetHits() {
		EntityRenderer<?> zombieRenderer = new DummyRenderer();
		EntityRendererCache.put(ZombieEntity.class, zombieRenderer);

		assertSame(zombieRenderer, EntityRendererCache.get(ZombieEntity.class));
		assertEquals(1, EntityRendererCache.queries());
		assertEquals(1, EntityRendererCache.hits());
		assertEquals(0, EntityRendererCache.misses());

		// Second query hits slot 0
		assertSame(zombieRenderer, EntityRendererCache.get(ZombieEntity.class));
		assertEquals(2, EntityRendererCache.queries());
		assertEquals(2, EntityRendererCache.hits());
	}

	@Test
	void mruPromotionMaintainsFourSlots() {
		EntityRenderer<?> rZ = new DummyRenderer();
		EntityRenderer<?> rS = new DummyRenderer();
		EntityRenderer<?> rC = new DummyRenderer();
		EntityRenderer<?> rB = new DummyRenderer();
		EntityRenderer<?> rA = new DummyRenderer();

		EntityRendererCache.put(ZombieEntity.class, rZ);
		EntityRendererCache.put(SkeletonEntity.class, rS);
		EntityRendererCache.put(CreeperEntity.class, rC);
		EntityRendererCache.put(BatEntity.class, rB);

		// All 4 present
		assertSame(rZ, EntityRendererCache.get(ZombieEntity.class));
		assertSame(rS, EntityRendererCache.get(SkeletonEntity.class));
		assertSame(rC, EntityRendererCache.get(CreeperEntity.class));
		assertSame(rB, EntityRendererCache.get(BatEntity.class));
		assertEquals(4, EntityRendererCache.hits());

		// Adding 5th pushes out the least recently used
		EntityRendererCache.put(ArmorStandEntity.class, rA);
		assertSame(rA, EntityRendererCache.get(ArmorStandEntity.class));

		// Zombie was accessed least recently before the ArmorStand put
		// (Bat, Creeper, Skeleton, Zombie was the order, so ArmorStand evicts Zombie)
		assertNull(EntityRendererCache.get(ZombieEntity.class));
	}

	@Test
	void nullPutIsIgnored() {
		EntityRendererCache.put(null, null);
		assertEquals(0, EntityRendererCache.queries());
		assertNull(EntityRendererCache.get(ZombieEntity.class));
	}

	@Test
	void verifyMatchesAndMismatches() {
		EntityRenderer<?> r1 = new DummyRenderer();
		EntityRenderer<?> r2 = new DummyRenderer();
		Map<Class<? extends Entity>, EntityRenderer<? extends Entity>> map = new HashMap<>();
		map.put(ZombieEntity.class, r1);

		// Match
		EntityRendererCache.verify(map, ZombieEntity.class, r1);
		assertEquals(0, EntityRendererCache.mismatches());

		// Mismatch
		EntityRendererCache.verify(map, ZombieEntity.class, r2);
		assertEquals(1, EntityRendererCache.mismatches());
	}

	@Test
	void clearResetsSlotsAndCounters() {
		EntityRenderer<?> r = new DummyRenderer();
		EntityRendererCache.put(ZombieEntity.class, r);
		assertSame(r, EntityRendererCache.get(ZombieEntity.class));
		assertEquals(1, EntityRendererCache.hits());

		EntityRendererCache.clear();
		assertEquals(0, EntityRendererCache.queries());
		assertEquals(0, EntityRendererCache.hits());
		assertNull(EntityRendererCache.get(ZombieEntity.class));
	}
}
