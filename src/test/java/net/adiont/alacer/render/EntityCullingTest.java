package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.Culler;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;

class EntityCullingTest {

	private static class BaseRenderer extends net.minecraft.client.render.entity.EntityRenderer<Entity> {
		BaseRenderer() { super(null); }
		@Override protected net.minecraft.resource.Identifier getTextureLocation(Entity entity) { return null; }
	}
	private static class CustomRenderer extends BaseRenderer {
		final boolean decision;
		CustomRenderer(boolean decision) { this.decision = decision; }
		@Override public boolean shouldRender(Entity entity, Culler culler, double x, double y, double z) { return this.decision; }
	}
	@Test void customRendererPoliciesAreAlwaysPreserved() {
		DummyEntity entity = new DummyEntity();
		assertFalse(EntityCulling.shouldRenderWithRenderer(new CustomRenderer(false), entity, new DummyCuller(true), 0, 0, 0));
		entity.distanceResult = false;
		assertTrue(EntityCulling.shouldRenderWithRenderer(new CustomRenderer(true), entity, new DummyCuller(false), 0, 0, 0));
	}


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

		// The known base renderer can reject distance before looking up the shape.
		boolean visible = EntityCulling.shouldRenderWithRenderer(new BaseRenderer(), entity, null, 0.0, 0.0, 0.0);
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

		// Test the base predicate after resolving the renderer policy.
		boolean visible = EntityCulling.shouldRenderWithRenderer(new BaseRenderer(), entity, culler, 0.0, 0.0, 0.0);
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
		EntityCulling.shouldRenderWithRenderer(new BaseRenderer(), entity, null, 0.0, 0.0, 0.0);
		assertEquals(1, EntityCulling.tested());

		EntityCulling.clear();
		assertEquals(0, EntityCulling.tested());
		assertEquals(0, EntityCulling.culledDistance());
		assertEquals(0, EntityCulling.culledFrustum());
		assertEquals(0, EntityCulling.visible());
	}

	@Test
	void denseScene200EntitiesVerificationGate() {
		java.util.Random random = new java.util.Random(20261024);
		BaseRenderer baseRenderer = new BaseRenderer();
		CustomRenderer customAccept = new CustomRenderer(true);
		CustomRenderer customReject = new CustomRenderer(false);

		int expectedCulledDist = 0;
		int expectedCulledFrust = 0;
		int expectedVisible = 0;

		for (int i = 0; i < 200; i++) {
			DummyEntity entity = new DummyEntity();
			entity.x = random.nextDouble() * 100.0 - 50.0;
			entity.y = random.nextDouble() * 30.0;
			entity.z = random.nextDouble() * 100.0 - 50.0;

			int type = i % 5;
			boolean inDistance = (type != 0); // type 0: culled by distance
			boolean inFrustum = (type == 1 || type == 3); // type 1 & 3: in frustum
			boolean ignoreFrustum = (type == 3);
			boolean isCustom = (type == 4);

			entity.distanceResult = inDistance;
			entity.ignoreCameraFrustum = ignoreFrustum;
			DummyCuller culler = new DummyCuller(inFrustum);

			net.minecraft.client.render.entity.EntityRenderer<Entity> renderer;
			if (isCustom) {
				renderer = (i % 2 == 0) ? customAccept : customReject;
			} else {
				renderer = baseRenderer;
			}

			boolean actual = EntityCulling.shouldRenderWithRenderer(renderer, entity, culler, 0.0, 0.0, 0.0);

			boolean expected;
			if (isCustom) {
				expected = ((CustomRenderer) renderer).decision;
			} else if (!inDistance) {
				expected = false;
				expectedCulledDist++;
			} else if (ignoreFrustum) {
				expected = true;
				expectedVisible++;
			} else if (inFrustum) {
				expected = true;
				expectedVisible++;
			} else {
				expected = false;
				expectedCulledFrust++;
			}

			assertEquals(expected, actual, "Decision mismatch on entity " + i + " (type " + type + ")");
		}

		assertEquals(200, EntityCulling.tested());
		assertEquals(expectedCulledDist, EntityCulling.culledDistance());
		assertEquals(expectedCulledFrust, EntityCulling.culledFrustum());
		assertEquals(expectedVisible, EntityCulling.visible());
	}
}
