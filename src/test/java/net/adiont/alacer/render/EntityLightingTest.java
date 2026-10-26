package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EntityLightingTest {

	@Test
	void pooledCoordinatesMatchVanillaAtNegativeAndWorldBoundaries() {
		TestEntity entity = new TestEntity();
		for (double coordinate : new double[] {0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE,
			-1, Math.nextDown(-1.0), Math.nextUp(-1.0), -30_000_001, -30_000_000,
			29_999_999, 30_000_000, -1, 255, 256}) {
			entity.x = coordinate; entity.y = coordinate; entity.z = coordinate;
			net.minecraft.util.math.BlockPos expected = new net.minecraft.util.math.BlockPos(entity);
			net.minecraft.util.math.BlockPos actual = EntityLighting.pooledEntityPos(entity);
			assertEquals(expected.getX(), actual.getX());
			assertEquals(expected.getY(), actual.getY());
			assertEquals(expected.getZ(), actual.getZ());
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
	void pooledEntityPositionsMatchVanillaAcross200Coordinates() {
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

}
