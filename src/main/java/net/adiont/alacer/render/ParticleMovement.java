package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Opt-in substitution of block-state reads in vanilla particle movement and its collision
 * gathering. Every collision loop, callback, shape, border check and movement calculation
 * stays in vanilla. No block-state or geometry cache: a state is fetched from the current
 * chunk on every read. The shared ParticleLight cache retains only chunk references and
 * checks world identity, column and load/unload generation, and resets on world replacement.
 * Client thread only; unknown world implementations and other entities take vanilla's call.
 */
public final class ParticleMovement {

	private static Thread owner;
	private static long compared;
	private static long mismatches;

	private ParticleMovement() {
	}

	public static void init() {
		owner = Thread.currentThread();
	}

	public static BlockState blockState(World world, BlockPos pos, Entity entity) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastParticleMovement || !(entity instanceof Particle)
			|| world.getClass() != ClientWorld.class || Thread.currentThread() != owner) {
			return world.getBlockState(pos);
		}
		// Match World.contains and World.getBlockState, including out-of-height air. Keep
		// vanilla's original mutable position at the chunk call rather than retaining it.
		int x = pos.getX(), y = pos.getY(), z = pos.getZ();
		BlockState state = x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000 || y < 0 || y >= 256
			? Blocks.AIR.defaultState() : ParticleLight.chunk(world, x >> 4, z >> 4).getBlockState(pos);
		if (config.verifyFastParticleMovement) {
			BlockState expected = world.getBlockState(pos);
			compared++;
			if (state != expected) {
				mismatches++;
				if (mismatches <= 10) {
					Alacer.LOGGER.error("Particle movement mismatch at {}: {} vs {}", pos, state, expected);
				}
			}
			return expected;
		}
		return state;
	}

	public static String verificationSummary() {
		return compared + " block reads compared, " + mismatches + " mismatched";
	}
}
