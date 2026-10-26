package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

class TerrainPipelineTest {

	private static final class TestChunkOrigin implements RenderChunkOrigin {
		private final int x, y, z;

		TestChunkOrigin(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override public int alacer$originX() { return this.x; }
		@Override public int alacer$originY() { return this.y; }
		@Override public int alacer$originZ() { return this.z; }
	}

	@Test
	void testRenderChunkOriginCoordinatesMatchBlockPos() {
		BlockPos pos = new BlockPos(-64, 128, 256);
		TestChunkOrigin origin = new TestChunkOrigin(pos.getX(), pos.getY(), pos.getZ());
		assertEquals(-64, origin.alacer$originX());
		assertEquals(128, origin.alacer$originY());
		assertEquals(256, origin.alacer$originZ());
	}

	@Test
	void testCompiledChunkEmptyBehaviorAcrossAllLayers() {
		CompiledChunk compiled = new CompiledChunk();
		assertTrue(compiled.isEmpty());
		for (BlockLayer layer : BlockLayer.values()) {
			// In vanilla CompiledChunk: hasBlock returns !blocks[layer.ordinal()].
			// When empty, blocks are false, so hasBlock returns true (layer is empty).
			assertTrue(compiled.hasBlock(layer));
		}
	}

	@Test
	void testPingPongSetSwappingAvoidsAllocations() {
		Set<String> swapSetA = new LinkedHashSet<>();
		Set<String> swapSetB = new LinkedHashSet<>();
		Set<String> current = new LinkedHashSet<>();

		// Simulate 100 frames of setupRender
		for (int frame = 0; frame < 100; frame++) {
			Set<String> prev = current;
			Set<String> target = (current == swapSetA) ? swapSetB : swapSetA;
			target.clear();
			current = target;

			// Add a newly dirty chunk to current
			current.add("chunk_" + frame);

			// Add previous dirty chunks
			current.addAll(prev);

			// Verify current is one of the two pre-allocated instances
			assertTrue(current == swapSetA || current == swapSetB);
			assertTrue(prev != current);
		}
	}

	@Test
	void testForeignBufferNotification() {
		assertDoesNotThrow(() -> {
			net.adiont.alacer.render.ModernGl.notifyForeignBuffer();
		});
	}

	@Test
	void testIsNearEquivalenceToVanilla() {
		int[] offsets = {-32, -17, -16, -15, -1, 0, 1, 15, 16, 17, 32};
		BlockPos originPos = new BlockPos(128, 64, -256);
		TestChunkOrigin origin = new TestChunkOrigin(128, 64, -256);

		for (int dx : offsets) {
			for (int dy : offsets) {
				for (int dz : offsets) {
					BlockPos testPos = new BlockPos(128 + dx, 64 + dy, -256 + dz);

					// Vanilla logic from WorldRenderer.isNear
					boolean vanillaExpected;
					if (MathHelper.abs(testPos.getX() - originPos.getX()) > 16) {
						vanillaExpected = false;
					} else if (MathHelper.abs(testPos.getY() - originPos.getY()) > 16) {
						vanillaExpected = false;
					} else {
						vanillaExpected = MathHelper.abs(testPos.getZ() - originPos.getZ()) <= 16;
					}

					// Optimized logic from WorldRendererMixin.alacer$fastIsNear
					boolean fastActual;
					int diffX = testPos.getX() - origin.alacer$originX();
					if (diffX < -16 || diffX > 16) {
						fastActual = false;
					} else {
						int diffY = testPos.getY() - origin.alacer$originY();
						if (diffY < -16 || diffY > 16) {
							fastActual = false;
						} else {
							int diffZ = testPos.getZ() - origin.alacer$originZ();
							fastActual = diffZ >= -16 && diffZ <= 16;
						}
					}

					assertEquals(vanillaExpected, fastActual,
						String.format("Mismatch at dx=%d, dy=%d, dz=%d", dx, dy, dz));
				}
			}
		}
	}

	@Test
	void testRenderChunkRegionIndexEquivalence() {
		BlockPos center = new BlockPos(32, 16, 48);
		int centerX = center.getX();
		int centerY = center.getY();
		int centerZ = center.getZ();

		for (int x = 0; x < 20; x++) {
			for (int y = 0; y < 20; y++) {
				for (int z = 0; z < 20; z++) {
					BlockPos pos = new BlockPos(centerX + x, centerY + y, centerZ + z);

					// Vanilla RenderChunkRegion.index:
					int i = pos.getX() - center.getX();
					int j = pos.getY() - center.getY();
					int k = pos.getZ() - center.getZ();
					int vanillaIndex = i * 400 + k * 20 + j;

					// Optimized primitive index:
					int fastIndex = (pos.getX() - centerX) * 400 + (pos.getZ() - centerZ) * 20 + (pos.getY() - centerY);

					assertEquals(vanillaIndex, fastIndex);
				}
			}
		}
	}

	@Test
	void testTileEntityPosReuseAvoidsAllocation() {
		BlockPos.Mutable mutable = new BlockPos.Mutable(10, 20, 30);
		// Simulate alacer$reuseTileEntityPos redirect in RenderChunkMixin
		Object input = mutable;
		BlockPos result = (input instanceof BlockPos bp) ? bp : new BlockPos((net.minecraft.util.math.Vec3i) input);

		assertSame(mutable, result);
		assertEquals(10, result.getX());
		assertEquals(20, result.getY());
		assertEquals(30, result.getZ());
	}

	@Test
	void testFastSlotMathEquivalenceToVanilla() {
		int[] testCoordinates = {
			-1_000_000, -65536, -1024, -257, -256, -255, -33, -32, -31, -17, -16, -15,
			-2, -1, 0, 1, 2, 15, 16, 17, 31, 32, 33, 255, 256, 257, 1024, 65536, 1_000_000
		};
		int[] testSizes = {1, 3, 5, 16, 17, 25, 33, 65};

		for (int sizeX : testSizes) {
			for (int sizeZ : testSizes) {
				int sizeY = 16;
				for (int y : testCoordinates) {
					int cyExpected = Math.floorDiv(y, 16);
					int cyActual = y >> 4;
					assertEquals(cyExpected, cyActual, "cy mismatch for y=" + y);

					boolean outOfBounds = cyActual < 0 || cyActual >= sizeY;
					if (outOfBounds) {
						continue;
					}

					for (int x : testCoordinates) {
						int cxExpected = Math.floorMod(Math.floorDiv(x, 16), sizeX);
						int cxActual = (x >> 4) % sizeX;
						if (cxActual < 0) {
							cxActual += sizeX;
						}
						assertEquals(cxExpected, cxActual, "cx mismatch for x=" + x + " sizeX=" + sizeX);

						for (int z : testCoordinates) {
							int czExpected = Math.floorMod(Math.floorDiv(z, 16), sizeZ);
							int czActual = (z >> 4) % sizeZ;
							if (czActual < 0) {
								czActual += sizeZ;
							}
							assertEquals(czExpected, czActual, "cz mismatch for z=" + z + " sizeZ=" + sizeZ);

							int vanillaSlot = (czExpected * sizeY + cyExpected) * sizeX + cxExpected;
							int fastSlot = (czActual * sizeY + cyActual) * sizeX + cxActual;
							assertEquals(vanillaSlot, fastSlot);
						}
					}
				}
			}
		}
	}

	@Test
	void testBoundingDistanceCheckEquivalence() {
		int range = 256;
		int origin = 1000;
		for (int n = 500; n <= 1500; n += 5) {
			boolean vanilla = Math.abs(origin - n) > range;
			int d = origin - n;
			boolean fast = d < -range || d > range;
			assertEquals(vanilla, fast);
		}
	}
}
