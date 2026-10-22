package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.MathHelper;

/** Structural scan sizes against the loop bounds copied from vanilla's methods, and default-off gating. */
class ParticleMovementCensusTest {

	@Test
	void censusAndRegionsAreOffWithoutTheirStartupGates() {
		assertFalse(ParticleMovementCensus.ENABLED);
		assertFalse(ParticleMovementCensus.REGIONS);
	}

	@Test
	void structuralCountsMatchVanillaLoopBounds() {
		SplittableRandom random = new SplittableRandom(25);
		double[] edges = {0.0, -0.0, 1.0, -1.0, 0.999, 1.001, 15.999, 16.0, -16.0, 255.0, 255.999, 256.0, -0.001,
			29999999.5, -30000000.5, 30000000.0, -29999999.0, 64.0, 2.0, -2.0};
		for (int c = 0; c < 200_000; c++) {
			double[] v = new double[6];
			for (int i = 0; i < 6; i++) {
				double pick = random.nextInt(4) == 0 ? edges[random.nextInt(edges.length)] : 0;
				v[i] = pick + (random.nextDouble() - 0.5) * (random.nextBoolean() ? 3.0 : 40.0)
					+ (random.nextInt(8) == 0 ? random.nextInt(300) : 0);
			}
			// Ordered bounds as vanilla Boxes keep them, plus some reversed (empty) ones.
			double minX = v[0], minY = v[1], minZ = v[2];
			double span = random.nextInt(10) == 0 ? -6.0 : 0.0;
			double maxX = minX + span + random.nextDouble() * 4.0;
			double maxY = minY + span + random.nextDouble() * 4.0;
			double maxZ = minZ + span + random.nextDouble() * 4.0;
			check(minX, minY, minZ, maxX, maxY, maxZ);
		}
		check(0.4, 70.2, 0.6, 0.6, 70.4, 0.8);
		check(15.95, -1.0, 15.95, 16.05, 0.05, 16.05);
		check(29999999.9, 255.9, -30000000.1, 30000000.1, 256.1, -29999999.9);
	}

	private static void check(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		String at = minX + "," + minY + "," + minZ + " .. " + maxX + "," + maxY + "," + maxZ;
		long columns = 0, cells = 0, inHeight = 0;
		// World.getCollisions: columns pass isChunkLoaded(set(o, 64, p)) through World.contains; y from floor(minY) - 1.
		int i = MathHelper.floor(minX), j = MathHelper.floor(maxX + 1.0), k = MathHelper.floor(minY);
		int l = MathHelper.floor(maxY + 1.0), m = MathHelper.floor(minZ), n = MathHelper.floor(maxZ + 1.0);
		if (Math.abs((long) j - i) < 64 && Math.abs((long) n - m) < 64 && Math.abs((long) l - k) < 64) {
			for (int o = i; o < j; o++) {
				for (int p = m; p < n; p++) {
					if (o >= -30000000 && p >= -30000000 && o < 30000000 && p < 30000000) {
						columns++;
						for (int q = k - 1; q < l; q++) {
							cells++;
							if (q >= 0 && q < 256) { inHeight++; }
						}
					}
				}
			}
			assertEquals(columns, ParticleMovementCensus.gatherColumns(minX, maxX, minZ, maxZ), at);
			assertEquals(cells, columns * ParticleMovementCensus.gatherLayers(minY, maxY, false), at);
			assertEquals(inHeight, columns * ParticleMovementCensus.gatherLayers(minY, maxY, true), at);
		}
		// World.getEntities over grown bounds, then WorldChunk.getEntities' clamped sections.
		long chunks = 0;
		// Bounds here stay within a few hundred blocks, so these loops are short.
		for (int x = MathHelper.floor((minX - 2.0) / 16.0); x <= MathHelper.floor((maxX + 2.0) / 16.0); x++) {
			for (int z = MathHelper.floor((minZ - 2.0) / 16.0); z <= MathHelper.floor((maxZ + 2.0) / 16.0); z++) { chunks++; }
		}
		assertEquals(chunks, ParticleMovementCensus.entityQueryChunks(minX, maxX, minZ, maxZ), at);
		long sections = 0;
		int s0 = MathHelper.clamp(MathHelper.floor((minY - 2.0) / 16.0), 0, 15);
		int s1 = MathHelper.clamp(MathHelper.floor((maxY + 2.0) / 16.0), 0, 15);
		for (int s = s0; s <= s1; s++) { sections++; }
		assertEquals(sections, ParticleMovementCensus.entityQuerySections(minY, maxY), at);
		// Entity.checkBlockCollisions: BlockPos(double) floors the 0.001 inset bounds; isAreaLoaded's height test.
		int x0 = MathHelper.floor(minX + 0.001), y0 = MathHelper.floor(minY + 0.001), z0 = MathHelper.floor(minZ + 0.001);
		int x1 = MathHelper.floor(maxX - 0.001), y1 = MathHelper.floor(maxY - 0.001), z1 = MathHelper.floor(maxZ - 0.001);
		if (Math.abs((long) x1 - x0) < 64 && Math.abs((long) y1 - y0) < 64 && Math.abs((long) z1 - z0) < 64) {
			long collisions = 0;
			if (y1 >= 0 && y0 < 256) {
				for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { collisions++; } } }
			}
			assertEquals(collisions, ParticleMovementCensus.blockCollisionCells(minX, minY, minZ, maxX, maxY, maxZ), at);
		}
		// World.containsFireSource: isAreaLoaded(i, k, m, j, l, n) tests maxY = l before the half-open loops.
		if (Math.abs((long) j - i) < 64 && Math.abs((long) n - m) < 64 && Math.abs((long) l - k) < 64) {
			long fire = 0;
			if (l >= 0 && k < 256) {
				for (int o = i; o < j; o++) { for (int p = k; p < l; p++) { for (int q = m; q < n; q++) { fire++; } } }
			}
			assertEquals(fire, ParticleMovementCensus.fireCells(minX, minY, minZ, maxX, maxY, maxZ), at);
		}
	}
}
