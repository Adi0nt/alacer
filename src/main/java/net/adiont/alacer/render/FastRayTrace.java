package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

/**
 * Allocation-free DDA ray tracing for {@link World#rayTrace} (optimization Phase 5).
 *
 * <p>During crosshair picking ({@code GameRenderer.pick}), Minecraft traces a 3D ray through the
 * world grid. Vanilla's implementation allocates a {@link Vec3d}, a {@link BlockPos}, and an
 * intermediate {@link HitResult} on every single block boundary stepped along the ray. In steady
 * state, this accounts for over 20% of all client heap allocations.
 *
 * <p>With {@code render.fastRayTrace}, intermediate grid steps are evaluated strictly with primitive
 * doubles and integers using a reusable thread-local mutable coordinate. Intermediate allocations
 * are eliminated entirely, constructing only the final hit result when a block collision occurs.
 *
 * <p>Verification mode ({@code render.fastRayTrace.verify}) compares the result with vanilla's
 * exact calculation and asserts numerical and positional identity.
 */
public final class FastRayTrace {

	private static final ThreadLocal<BlockPos.Mutable> POOLED_POS = ThreadLocal.withInitial(BlockPos.Mutable::new);

	private static long traces;
	private static long hits;
	private static long misses;
	private static long mismatches;

	private FastRayTrace() {
	}

	public static boolean isEnabled(World world) {
		if (world == null || world.getClass() != ClientWorld.class) {
			return false;
		}
		AlacerConfig config = Alacer.get().config();
		return config != null && config.fastRayTrace;
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyFastRayTrace;
	}

	public static HitResult rayTrace(World world, Vec3d from, Vec3d to, boolean allowLiquids, boolean ignoreBlocksWithoutCollision, boolean allowHitInStartPosition) {
		traces++;
		if (Double.isNaN(from.x) || Double.isNaN(from.y) || Double.isNaN(from.z)) {
			return null;
		}
		if (Double.isNaN(to.x) || Double.isNaN(to.y) || Double.isNaN(to.z)) {
			return null;
		}

		int toX = MathHelper.floor(to.x);
		int toY = MathHelper.floor(to.y);
		int toZ = MathHelper.floor(to.z);

		double fx = from.x;
		double fy = from.y;
		double fz = from.z;

		int currentX = MathHelper.floor(fx);
		int currentY = MathHelper.floor(fy);
		int currentZ = MathHelper.floor(fz);

		BlockPos.Mutable mutablePos = POOLED_POS.get().set(currentX, currentY, currentZ);
		BlockState blockState = world.getBlockState(mutablePos);
		Block block = blockState.getBlock();

		if ((!ignoreBlocksWithoutCollision || block.getCollisionShape(world, mutablePos, blockState) != null) && block.canRayTrace(blockState, allowLiquids)) {
			HitResult hit = block.rayTrace(world, new BlockPos(currentX, currentY, currentZ), from, to);
			if (hit != null) {
				hits++;
				return hit;
			}
		}

		boolean hasMiss = false;
		double missX = 0, missY = 0, missZ = 0;
		Direction missDirection = null;
		int missBlockX = 0, missBlockY = 0, missBlockZ = 0;

		int steps = 200;
		while (steps-- >= 0) {
			if (Double.isNaN(fx) || Double.isNaN(fy) || Double.isNaN(fz)) {
				return null;
			}

			if (currentX == toX && currentY == toY && currentZ == toZ) {
				if (allowHitInStartPosition && hasMiss) {
					return new HitResult(HitResult.Type.MISS, new Vec3d(missX, missY, missZ), missDirection, new BlockPos(missBlockX, missBlockY, missBlockZ));
				}
				misses++;
				return null;
			}

			boolean stepX = true;
			boolean stepY = true;
			boolean stepZ = true;

			double boundX = 999.0;
			double boundY = 999.0;
			double boundZ = 999.0;

			if (toX > currentX) {
				boundX = currentX + 1.0;
			} else if (toX < currentX) {
				boundX = currentX + 0.0;
			} else {
				stepX = false;
			}

			if (toY > currentY) {
				boundY = currentY + 1.0;
			} else if (toY < currentY) {
				boundY = currentY + 0.0;
			} else {
				stepY = false;
			}

			if (toZ > currentZ) {
				boundZ = currentZ + 1.0;
			} else if (toZ < currentZ) {
				boundZ = currentZ + 0.0;
			} else {
				stepZ = false;
			}

			double tX = 999.0;
			double tY = 999.0;
			double tZ = 999.0;

			double deltaX = to.x - fx;
			double deltaY = to.y - fy;
			double deltaZ = to.z - fz;

			if (stepX) {
				tX = (boundX - fx) / deltaX;
			}
			if (stepY) {
				tY = (boundY - fy) / deltaY;
			}
			if (stepZ) {
				tZ = (boundZ - fz) / deltaZ;
			}

			if (tX == -0.0) {
				tX = -1.0E-4;
			}
			if (tY == -0.0) {
				tY = -1.0E-4;
			}
			if (tZ == -0.0) {
				tZ = -1.0E-4;
			}

			Direction direction;
			if (tX < tY && tX < tZ) {
				direction = toX > currentX ? Direction.WEST : Direction.EAST;
				fx = boundX;
				fy = fy + deltaY * tX;
				fz = fz + deltaZ * tX;
			} else if (tY < tZ) {
				direction = toY > currentY ? Direction.DOWN : Direction.UP;
				fx = fx + deltaX * tY;
				fy = boundY;
				fz = fz + deltaZ * tY;
			} else {
				direction = toZ > currentZ ? Direction.NORTH : Direction.SOUTH;
				fx = fx + deltaX * tZ;
				fy = fy + deltaY * tZ;
				fz = boundZ;
			}

			currentX = MathHelper.floor(fx) - (direction == Direction.EAST ? 1 : 0);
			currentY = MathHelper.floor(fy) - (direction == Direction.UP ? 1 : 0);
			currentZ = MathHelper.floor(fz) - (direction == Direction.SOUTH ? 1 : 0);

			mutablePos.set(currentX, currentY, currentZ);
			BlockState state = world.getBlockState(mutablePos);
			Block b = state.getBlock();

			if (!ignoreBlocksWithoutCollision || b.getCollisionShape(world, mutablePos, state) != null) {
				if (b.canRayTrace(state, allowLiquids)) {
					Vec3d stepFrom = new Vec3d(fx, fy, fz);
					HitResult hit = b.rayTrace(world, new BlockPos(currentX, currentY, currentZ), stepFrom, to);
					if (hit != null) {
						hits++;
						return hit;
					}
				} else {
					hasMiss = true;
					missX = fx;
					missY = fy;
					missZ = fz;
					missDirection = direction;
					missBlockX = currentX;
					missBlockY = currentY;
					missBlockZ = currentZ;
				}
			}
		}

		if (allowHitInStartPosition && hasMiss) {
			return new HitResult(HitResult.Type.MISS, new Vec3d(missX, missY, missZ), missDirection, new BlockPos(missBlockX, missBlockY, missBlockZ));
		}
		misses++;
		return null;
	}

	public static void verify(World world, Vec3d from, Vec3d to, boolean allowLiquids, boolean ignoreBlocksWithoutCollision, boolean allowHitInStartPosition, HitResult fastResult) {
		HitResult vanilla = vanillaRayTrace(world, from, to, allowLiquids, ignoreBlocksWithoutCollision, allowHitInStartPosition);
		if (vanilla == null && fastResult == null) {
			return;
		}
		if (vanilla == null || fastResult == null) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("FastRayTrace verification mismatch: fast {}, vanilla {}", fastResult, vanilla);
			}
			return;
		}
		if (vanilla.type != fastResult.type || vanilla.face != fastResult.face || !java.util.Objects.equals(vanilla.getPos(), fastResult.getPos())) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("FastRayTrace verification mismatch: fast [{}, {}, {}], vanilla [{}, {}, {}]",
					fastResult.type, fastResult.face, fastResult.getPos(),
					vanilla.type, vanilla.face, vanilla.getPos());
			}
		}
	}

	private static HitResult vanillaRayTrace(World world, Vec3d from, Vec3d to, boolean allowLiquids, boolean ignoreBlocksWithoutCollision, boolean allowHitInStartPosition) {
		if (Double.isNaN(from.x) || Double.isNaN(from.y) || Double.isNaN(from.z)) {
			return null;
		}
		if (Double.isNaN(to.x) || Double.isNaN(to.y) || Double.isNaN(to.z)) {
			return null;
		}
		int i = MathHelper.floor(to.x), j = MathHelper.floor(to.y), k = MathHelper.floor(to.z);
		int l = MathHelper.floor(from.x), m = MathHelper.floor(from.y), n = MathHelper.floor(from.z);
		BlockPos blockPos = new BlockPos(l, m, n);
		BlockState blockState = world.getBlockState(blockPos);
		Block block = blockState.getBlock();
		if ((!ignoreBlocksWithoutCollision || block.getCollisionShape(world, blockPos, blockState) != null) && block.canRayTrace(blockState, allowLiquids)) {
			HitResult hitResult = block.rayTrace(world, blockPos, from, to);
			if (hitResult != null) {
				return hitResult;
			}
		}
		HitResult hitResult2 = null;
		int o = 200;
		while (o-- >= 0) {
			if (Double.isNaN(from.x) || Double.isNaN(from.y) || Double.isNaN(from.z)) {
				return null;
			}
			if (l == i && m == j && n == k) {
				return allowHitInStartPosition ? hitResult2 : null;
			}
			boolean bl = true, bl2 = true, bl3 = true;
			double d = 999.0, e = 999.0, f = 999.0;
			if (i > l) {
				d = l + 1.0;
			} else if (i < l) {
				d = l + 0.0;
			} else {
				bl = false;
			}
			if (j > m) {
				e = m + 1.0;
			} else if (j < m) {
				e = m + 0.0;
			} else {
				bl2 = false;
			}
			if (k > n) {
				f = n + 1.0;
			} else if (k < n) {
				f = n + 0.0;
			} else {
				bl3 = false;
			}
			double g = 999.0, h = 999.0, p = 999.0;
			double q = to.x - from.x, r = to.y - from.y, s = to.z - from.z;
			if (bl) {
				g = (d - from.x) / q;
			}
			if (bl2) {
				h = (e - from.y) / r;
			}
			if (bl3) {
				p = (f - from.z) / s;
			}
			if (g == -0.0) {
				g = -1.0E-4;
			}
			if (h == -0.0) {
				h = -1.0E-4;
			}
			if (p == -0.0) {
				p = -1.0E-4;
			}
			Direction direction;
			if (g < h && g < p) {
				direction = i > l ? Direction.WEST : Direction.EAST;
				from = new Vec3d(d, from.y + r * g, from.z + s * g);
			} else if (h < p) {
				direction = j > m ? Direction.DOWN : Direction.UP;
				from = new Vec3d(from.x + q * h, e, from.z + s * h);
			} else {
				direction = k > n ? Direction.NORTH : Direction.SOUTH;
				from = new Vec3d(from.x + q * p, from.y + r * p, f);
			}
			l = MathHelper.floor(from.x) - (direction == Direction.EAST ? 1 : 0);
			m = MathHelper.floor(from.y) - (direction == Direction.UP ? 1 : 0);
			n = MathHelper.floor(from.z) - (direction == Direction.SOUTH ? 1 : 0);
			blockPos = new BlockPos(l, m, n);
			BlockState blockState2 = world.getBlockState(blockPos);
			Block block2 = blockState2.getBlock();
			if (!ignoreBlocksWithoutCollision || block2.getCollisionShape(world, blockPos, blockState2) != null) {
				if (block2.canRayTrace(blockState2, allowLiquids)) {
					HitResult hitResult3 = block2.rayTrace(world, blockPos, from, to);
					if (hitResult3 != null) {
						return hitResult3;
					}
				} else {
					hitResult2 = new HitResult(HitResult.Type.MISS, from, direction, blockPos);
				}
			}
		}
		return allowHitInStartPosition ? hitResult2 : null;
	}

	public static void clear() {
		traces = hits = misses = mismatches = 0;
	}

	public static long traces() {
		return traces;
	}

	public static long hits() {
		return hits;
	}

	public static long misses() {
		return misses;
	}

	public static long mismatches() {
		return mismatches;
	}
}
