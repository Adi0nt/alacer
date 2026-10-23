package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Session 28 candidate ({@code render.reuseShadowPositions}, off by default): the block-below position
 * of entity shadows.
 *
 * <p>For every block position under a nearby entity's shadow, vanilla's {@code renderShadow} calls
 * {@code world.getBlockState(pos.down())}. {@code down()} allocates a new {@code BlockPos} that only that
 * call reads (bytecode 305-308: never stored); it was 16-23% of client-thread frame-path allocation with 200
 * mobs in the session 28 attribution. That call is optimization 6's redirect, which reads the coordinates
 * (or passes the position to vanilla's {@code World.getBlockState}, which reads them too). For exactly
 * {@link ClientWorld}, the position is instead one reused {@code BlockPos.Mutable} set to the same
 * coordinates; any other world keeps vanilla's new position. The query, its order and its answer are unchanged.
 *
 * <p>The position is scratch state, overwritten before every use and meaningless between calls, so no world,
 * reload or backend lifecycle applies. Client thread only (entity rendering).
 */
public final class ShadowPositions {

	private static final long SUMMARY_EVERY = 1_000_000L;

	private static final BlockPos.Mutable BELOW = new BlockPos.Mutable();

	private static long verified;
	private static long mismatches;

	private ShadowPositions() {
	}

	/** Replaces {@code pos.down()} in {@code renderShadow}. */
	public static BlockPos below(World world, BlockPos pos) {
		AlacerConfig config = Alacer.get().config();
		if (!config.reuseShadowPositions || world == null || world.getClass() != ClientWorld.class) {
			return pos.down();
		}
		BlockPos reused = BELOW.set(pos.getX(), pos.getY() - 1, pos.getZ());
		if (config.verifyReuseShadowPositions) {
			BlockPos expected = pos.down();
			if (reused.getX() != expected.getX() || reused.getY() != expected.getY() || reused.getZ() != expected.getZ()) {
				mismatch("position", expected, reused.toString(), expected.toString());
			} else {
				BlockState fromReused = world.getBlockState(reused);
				BlockState fromExpected = world.getBlockState(expected);
				if (fromReused != fromExpected) {
					mismatch("block state", expected, String.valueOf(fromReused), String.valueOf(fromExpected));
				}
			}
			compared();
			return expected;
		}
		return reused;
	}

	private static void compared() {
		verified++;
		if (verified % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Shadow position reuse verification: {}", verificationSummary());
		}
	}

	private static void mismatch(String what, BlockPos pos, String reused, String expected) {
		mismatches++;
		if (mismatches <= 10) {
			Alacer.LOGGER.warn("Shadow position reuse {} mismatch at {}: reused {} vs vanilla {}", what, pos, reused, expected);
		}
	}

	public static String verificationSummary() {
		return verified + " positions compared, " + mismatches + " mismatched";
	}
}
