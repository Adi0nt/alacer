package net.adiont.alacer.benchmark;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.ParticleBoxes;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

/** Intrusive synchronous diagnostics in the disposable benchmark world, before emission. */
final class ParticleBoxChecks {
	private ParticleBoxChecks() { }

	static void run(World world, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		boolean original = config.reuseParticleBoxes, verify = config.verifyReuseParticleBoxes;
		WorldBorder border = world.getWorldBorder();
		double bx = border.getCenterX(), bz = border.getCenterZ(), size = border.getLerpSize();
		if (border.getLerpTime() != 0) { throw new IllegalStateException("Box probes require a stationary initial border"); }
		int direct = 0, pairs = 0, boatHits = 0;
		List<BlockPos> cells = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BoatEntity boat = null;
		try {
			config.reuseParticleBoxes = true;
			Probe probe = new Probe(world, x, y, z);
			double[] values = {0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE,
				-Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.longBitsToDouble(0x7ff8000000000018L), -30000000, -16, -1, 1, 16, 30000000};
			for (double a : values) {
				for (double b : values) {
					Box box = new Box(a, b, -2, b, a, 2);
					for (double dx : new double[]{0.0, -0.0, Double.MIN_VALUE, -0.125}) {
						for (double dy : new double[]{0.0, -0.0, 0.125}) {
							Box actual = ParticleBoxes.moved(probe, box, dx, dy, -0.0);
							check(ParticleBoxes.same(actual, box.moved(dx, dy, -0.0)), "direct moved");
							actual = ParticleBoxes.expanded(probe, box, dx, dy, -0.0);
							check(ParticleBoxes.same(actual, box.expanded(dx, dy, -0.0)), "direct expanded");
							direct += 2;
						}
					}
				}
			}
			Box inverted = new Box(new BlockPos(2, 3, 4), new BlockPos(-2, -3, -4));
			check(ParticleBoxes.moved(probe, inverted, 0, 0, 0) != inverted, "inverted fallback");
			check(ParticleBoxes.same(ParticleBoxes.moved(probe, inverted, 0, 0, 0), inverted.moved(0, 0, 0)), "inverted coordinates");
			Box subclass = new Box(1, 2, 3, 4, 5, 6) {
				@Override public Box moved(double dx, double dy, double dz) { return new Box(10, 20, 30, 40, 50, 60); }
			};
			check(ParticleBoxes.same(ParticleBoxes.moved(probe, subclass, 0, 0, 0), subclass.moved(0, 0, 0)), "subclass dispatch");
			Box safe = new Box(1, 2, 3, 4, 5, 6);
			check(ParticleBoxes.moved(probe, safe, -0.0, 0, -0.0) == safe, "safe identity reuse");
			Box[] otherThread = new Box[1];
			Thread thread = new Thread(() -> otherThread[0] = ParticleBoxes.moved(probe, safe, 0, 0, 0));
			thread.start(); thread.join();
			check(otherThread[0] != null && otherThread[0] != safe && ParticleBoxes.same(otherThread[0], safe), "thread fallback");
			BoatEntity nonParticle = new BoatEntity(world);
			check(ParticleBoxes.moved(nonParticle, safe, 0, 0, 0) != safe, "non-particle fallback");
			direct += 6;

			// Install a tiny isolated floor, slab, wall and cobweb above terrain in loaded chunks.
			int sx = (int) Math.floor(x) + 3, sy = 120, sz = (int) Math.floor(z) + 3;
			for (int ox = -2; ox <= 2; ox++) {
				for (int oz = -2; oz <= 2; oz++) {
					for (int oy = 0; oy <= 3; oy++) {
						BlockPos pos = new BlockPos(sx + ox, sy + oy, sz + oz);
						cells.add(pos); states.add(world.getBlockState(pos));
						Block block = oy == 0 ? Blocks.STONE : Blocks.AIR;
						if (oy == 1 && ox == 1 && oz == 0) { block = Blocks.STONE_SLAB; }
						if (oy <= 2 && ox == -1 && oz == 0) { block = Blocks.STONE; }
						if (oy == 1 && ox == 0 && oz == 1) { block = Blocks.WEB; }
						world.setBlockState(pos, block.defaultState(), 2);
					}
				}
			}
			// Copy and compare every nonstatic primitive Entity/Particle field, including private
			// fire/fall/cobweb/border state. Trace intermediate shape assignments and callback order.
			List<Field> fields = new ArrayList<>();
			for (Class<?> type = Particle.class; type != Object.class; type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					if (field.getType().isPrimitive() && !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
						field.setAccessible(true); fields.add(field);
					}
				}
			}
			Random random = new Random(180018);
			for (int scenario = 0; scenario < 6; scenario++) {
				if (scenario == 1) { border.setCenter(sx + 0.5, sz + 0.5); border.setSize(4); }
				if (scenario == 2) { border.setCenter(sx + 1.5, sz - 0.5); border.setSize(8); }
				if (scenario == 3) {
					border.setCenter(bx, bz); border.setSize(size);
					boat = new BoatEntity(world, sx + 0.5, sy + 1, sz + 0.5);
					check(world.addEntity(boat), "boat insertion");
				}
				for (int trial = 0; trial < 120; trial++) {
					double px = scenario == 4 ? -16.05 : scenario == 5 ? 4096.5 : sx + 0.5 + (trial % 5 - 2) * 0.25;
					double py = scenario >= 4 ? (trial % 2 == 0 ? -1 : 256) : sy + 1 + (trial % 4) * 0.2;
					double pz = scenario == 4 ? -16.05 : scenario == 5 ? 4096.5 : sz + 0.5;
					Probe a = new Probe(world, px, py, pz), b = new Probe(world, px, py, pz);
					a.noClip = trial % 11 == 0; a.stepHeight = trial % 3 == 0 ? 0.6F : 0;
					a.onGround = trial % 2 == 0; a.cobweb(trial % 7 == 0);
					a.setOutsideWorldBorder(trial % 2 == 0);
					a.velocityX = 0.3; a.velocityY = -0.2; a.velocityZ = -0.3;
					for (Field field : fields) { field.set(b, field.get(a)); }
					b.setShape(a.getShape());
					a.resetTrace(); b.resetTrace();
					if (scenario == 3 && trial == 0) {
						Box boatBounds = boat.getShape();
						Probe collisionProbe = new Probe(world, px, py, pz);
						check(world.getCollisions(collisionProbe, collisionProbe.getShape()).stream().anyMatch(box -> box == boatBounds), "boat collision present");
						boatHits++;
					}
					for (int step = 0; step < 8; step++) {
						double dx = step % 4 == 0 ? 0.0 : step % 4 == 1 ? -0.0 : (random.nextDouble() - 0.5) * 0.8;
						double dy = step % 3 == 0 ? -0.0 : step % 3 == 1 ? -0.4 : 0.25;
						double dz = step % 2 == 0 ? 0.0 : (random.nextDouble() - 0.5) * 0.8;
						config.reuseParticleBoxes = false; a.move(dx, dy, dz);
						config.reuseParticleBoxes = true; b.move(dx, dy, dz);
						check(ParticleBoxes.same(a.getShape(), b.getShape()), "movement shape");
						for (Field field : fields) {
							Object av = field.get(a), bv = field.get(b);
							boolean equal = av instanceof Double ad ? Double.doubleToRawLongBits(ad) == Double.doubleToRawLongBits((Double) bv)
								: av instanceof Float af ? Float.floatToRawIntBits(af) == Float.floatToRawIntBits((Float) bv) : av.equals(bv);
							check(equal, "movement field " + field.getName() + " scenario=" + scenario + " trial=" + trial + " step=" + step + ": " + av + " vs " + bv);
						}
						check(a.trace.equals(b.trace), "callback/intermediate shape order");
						pairs++;
					}
				}
				if (scenario == 3) { world.removeEntityNow(boat); boat = null; }
			}
		} catch (ReflectiveOperationException | InterruptedException ex) {
			throw new IllegalStateException("Particle Box diagnostic failed", ex);
		} finally {
			if (boat != null) { world.removeEntityNow(boat); }
			for (int i = cells.size() - 1; i >= 0; i--) { world.setBlockState(cells.get(i), states.get(i), 2); }
			border.setCenter(bx, bz); border.setSize(size);
			config.reuseParticleBoxes = original; config.verifyReuseParticleBoxes = verify;
		}
		Alacer.LOGGER.info("Particle box probes: {} direct checks, {} movement pairs matched, {} boat collision checks", direct, pairs, boatHits);
	}

	private static void check(boolean passed, String what) {
		if (!passed) { throw new IllegalStateException("Particle box mismatch: " + what); }
	}

	private static final class Probe extends Particle {
		private final List<String> trace = new ArrayList<>();
		Probe(World world, double x, double y, double z) { super(world, x, y, z); }
		void cobweb(boolean value) { this.inCobweb = value; }
		void resetTrace() { this.random.setSeed(18); this.trace.clear(); }
		@Override public void setShape(Box box) {
			if (this.trace != null) {
				this.trace.add("shape:" + Long.toHexString(Double.doubleToRawLongBits(box.minX)) + ":"
					+ Long.toHexString(Double.doubleToRawLongBits(box.minY)) + ":" + Long.toHexString(Double.doubleToRawLongBits(box.minZ)) + ":"
					+ Long.toHexString(Double.doubleToRawLongBits(box.maxX)) + ":" + Long.toHexString(Double.doubleToRawLongBits(box.maxY)) + ":"
					+ Long.toHexString(Double.doubleToRawLongBits(box.maxZ)));
			}
			super.setShape(box);
		}
		@Override protected void checkFallDamage(double dy, boolean grounded, Block block, BlockPos pos) {
			this.trace.add("fall:" + Long.toHexString(Double.doubleToRawLongBits(dy)) + ":" + grounded + ":" + block + ":" + pos);
			super.checkFallDamage(dy, grounded, block, pos);
		}
		@Override protected void checkBlockCollisions() {
			this.trace.add("blocks:begin"); super.checkBlockCollisions(); this.trace.add("blocks:end");
		}
	}
}
