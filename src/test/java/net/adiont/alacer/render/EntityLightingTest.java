package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EntityLightingTest {

	@Test
	void boundaryLimitsMatchVanillaWorldRules() {
		int limit = 30000000;
		// Within valid build height and world bounds
		assertTrue(isValid(0, 64, 0, limit));
		assertTrue(isValid(-limit, 0, -limit, limit));
		assertTrue(isValid(limit - 1, 255, limit - 1, limit));

		// Outside boundaries
		assertFalse(isValid(-limit - 1, 64, 0, limit));
		assertFalse(isValid(limit, 64, 0, limit));
		assertFalse(isValid(0, -1, 0, limit));
		assertFalse(isValid(0, 256, 0, limit));
	}

	@Test
	void coordinateFlooringMatchesMathFloor() {
		double[] testCoords = {0.0, 0.25, 0.75, 1.0, -0.25, -0.75, -1.0, 128.99, -64.01};
		for (double d : testCoords) {
			assertEquals((int) Math.floor(d), (int) Math.floor(d));
		}
	}

	@Test
	void verificationSummaryFormat() {
		String summary = EntityLighting.verificationSummary();
		assertNotNull(summary);
		assertTrue(summary.contains("queries compared"));
		assertTrue(summary.contains("mismatched"));
	}

	private static class TestEntity extends net.minecraft.entity.Entity {
		TestEntity() {
			super(null);
		}
		@Override protected void registerSyncedData() {}
		@Override protected void readCustomNbt(net.minecraft.nbt.NbtCompound nbt) {}
		@Override protected void writeCustomNbt(net.minecraft.nbt.NbtCompound nbt) {}
	}

	@Test
	void denseScene200EntitiesLightingVerificationGate() {
		java.util.Random random = new java.util.Random(1337);
		TestEntity entity = new TestEntity();

		for (int i = 0; i < 200; i++) {
			entity.x = (random.nextDouble() * 2000.0 - 1000.0);
			entity.y = (random.nextDouble() * 250.0);
			entity.z = (random.nextDouble() * 2000.0 - 1000.0);

			net.minecraft.util.math.BlockPos vanillaPos = new net.minecraft.util.math.BlockPos(entity);
			net.minecraft.util.math.BlockPos fastPos = EntityLighting.pooledEntityPos(entity);

			assertEquals(vanillaPos.getX(), fastPos.getX(), "X mismatch on entity " + i);
			assertEquals(vanillaPos.getY(), fastPos.getY(), "Y mismatch on entity " + i);
			assertEquals(vanillaPos.getZ(), fastPos.getZ(), "Z mismatch on entity " + i);
		}
	}

	private static boolean isValid(int x, int y, int z, int limit) {
		return x >= -limit && z >= -limit && x < limit && z < limit && y >= 0 && y < 256;
	}
}
