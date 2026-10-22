package net.adiont.alacer.benchmark;

import java.util.List;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.EntityRendering;
import net.adiont.alacer.render.ParticleLight;
import net.adiont.alacer.render.ParticleMovement;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

/** Synchronous client-thread diagnostics only; no probes are added to the manager or world. */
final class ParticleMovementChecks {

	private ParticleMovementChecks() {
	}

	static void run(World world, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		boolean original = config.fastParticleMovement;
		boolean originalVerify = config.verifyFastParticleMovement;
		WorldBorder border = world.getWorldBorder();
		double centerX = border.getCenterX(), centerZ = border.getCenterZ(), size = border.getLerpSize();
		if (border.getLerpTime() != 0) {
			throw new IllegalStateException("movement probes need a stationary initial world border");
		}
		Probe probe = new Probe(world, x, y, z);
		int reads = 0, collisions = 0;
		try {
			// Compare the candidate itself, not verification mode's deliberately returned vanilla state.
			config.verifyFastParticleMovement = false;
			// Read current states at negative chunk edges, unloaded columns, y limits and hard limits.
			for (int bx : new int[]{(int) x, -17, -16, -1, 0, 15, 16, 4096, -30000001, -30000000, 29999999, 30000000}) {
				for (int by : new int[]{-1, 0, 63, 64, 255, 256}) {
					for (int bz : new int[]{(int) z, -17, 16, 30000000}) {
						BlockPos pos = new BlockPos(bx, by, bz);
						config.fastParticleMovement = true;
						if (ParticleMovement.blockState(world, pos, probe) != world.getBlockState(pos)) {
							throw new IllegalStateException("movement probe block read differs at " + pos);
						}
						reads++;
					}
				}
			}
			// Empty, solid/terrain, partial blocks as supplied by the changing particle diagnostic,
			// wide boxes, both initial border flags, resized/recentered borders, cache invalidation.
			for (int scenario = 0; scenario < 4; scenario++) {
				if (scenario == 1) { border.setCenter(x, z); border.setSize(8); }
				if (scenario == 2) { border.setCenter(x + 4, z - 4); border.setSize(16); }
				if (scenario == 3) { ParticleLight.reset(); EntityRendering.onChunkMapChanged(((int) x) >> 4, ((int) z) >> 4); }
				for (double dx : new double[]{-16, -0.25, 0, 0.25, 16}) {
					for (double dy : new double[]{-66, -1, 0, 1, 191}) {
						probe.setPosition(x + dx, y + dy, z);
						Box box = probe.getShape().expanded(0.3, -0.8, -0.4);
						for (boolean outside : new boolean[]{false, true}) {
							probe.setOutsideWorldBorder(outside);
							config.fastParticleMovement = false;
							List<Box> expected = world.getCollisions(probe, box);
							boolean expectedBorder = probe.isOutsideWorldBorder();
							probe.setOutsideWorldBorder(outside);
							config.fastParticleMovement = true;
							List<Box> actual = world.getCollisions(probe, box);
							if (expectedBorder != probe.isOutsideWorldBorder() || expected.size() != actual.size()) {
								throw new IllegalStateException("movement probe collision count/border differs");
							}
							for (int i = 0; i < expected.size(); i++) {
								if (!same(expected.get(i), actual.get(i))) {
									throw new IllegalStateException("movement probe collision order/box differs");
								}
							}
							collisions++;
						}
					}
				}
			}
		} finally {
			config.fastParticleMovement = original;
			config.verifyFastParticleMovement = originalVerify;
			border.setCenter(centerX, centerZ);
			border.setSize(size);
			ParticleLight.reset();
		}
		Alacer.LOGGER.info("Particle movement probes: {} block states, {} ordered collision lists and border flags matched", reads, collisions);
	}

	private static boolean same(Box a, Box b) {
		return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ
			&& a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
	}

	private static final class Probe extends Particle {
		Probe(World world, double x, double y, double z) { super(world, x, y, z); }
	}
}
