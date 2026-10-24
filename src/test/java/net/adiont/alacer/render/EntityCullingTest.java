package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.Culler;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;

class EntityCullingTest {

	private static class DummyEntity extends Entity {
		boolean distanceResult = true;
		Box shapeBox = new Box(0, 0, 0, 1, 1, 1);

		DummyEntity() {
			super(null);
		}

		@Override
		public boolean shouldRender(double x, double y, double z) {
			return distanceResult;
		}

		@Override
		public Box getShape() {
			return shapeBox;
		}

		@Override
		protected void registerSyncedData() {
		}

		@Override
		protected void readCustomNbt(NbtCompound nbt) {
		}

		@Override
		protected void writeCustomNbt(NbtCompound nbt) {
		}
	}

	private static class DummyCuller implements Culler {
		boolean visible;

		DummyCuller(boolean visible) {
			this.visible = visible;
		}

		@Override
		public boolean isVisible(Box box) {
			return visible;
		}

		@Override
		public void prepare(double x, double y, double z) {
		}
	}

	@BeforeEach
	void setUp() {
		EntityCulling.clear();
	}

	@Test
	void outOfDistanceEntityCulledImmediatelyWithoutDispatcherOrCuller() {
		DummyEntity entity = new DummyEntity();
		entity.distanceResult = false;

		// dispatcher is null; if out of distance, dispatcher must NOT be touched
		boolean visible = EntityCulling.shouldRender(null, entity, null, 0.0, 0.0, 0.0);
		assertFalse(visible);
		assertEquals(1, EntityCulling.tested());
		assertEquals(1, EntityCulling.culledDistance());
		assertEquals(0, EntityCulling.culledFrustum());
		assertEquals(0, EntityCulling.visible());
	}

	@Test
	void inDistanceFrustumCulledWithoutCallingDispatcher() {
		DummyEntity entity = new DummyEntity();
		entity.distanceResult = true;
		DummyCuller culler = new DummyCuller(false);

		// dispatcher is null; since culler returns false, dispatcher.getRenderer must NOT be called
		boolean visible = EntityCulling.shouldRender(null, entity, culler, 0.0, 0.0, 0.0);
		assertFalse(visible);
		assertEquals(1, EntityCulling.tested());
		assertEquals(0, EntityCulling.culledDistance());
		assertEquals(1, EntityCulling.culledFrustum());
		assertEquals(0, EntityCulling.visible());
	}

	@Test
	void clearResetsCounters() {
		DummyEntity entity = new DummyEntity();
		entity.distanceResult = false;
		EntityCulling.shouldRender(null, entity, null, 0.0, 0.0, 0.0);
		assertEquals(1, EntityCulling.tested());

		EntityCulling.clear();
		assertEquals(0, EntityCulling.tested());
		assertEquals(0, EntityCulling.culledDistance());
		assertEquals(0, EntityCulling.culledFrustum());
		assertEquals(0, EntityCulling.visible());
	}
}
