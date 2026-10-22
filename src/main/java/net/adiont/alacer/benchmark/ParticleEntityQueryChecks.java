package net.adiont.alacer.benchmark;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.ParticleBoxes;
import net.adiont.alacer.render.ParticleEntityQuery;
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
final class ParticleEntityQueryChecks {
	private ParticleEntityQueryChecks() { }

	static void run(World world, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		boolean original = config.scalarParticleEntityQuery, verify = config.verifyScalarParticleEntityQuery;
		WorldBorder border = world.getWorldBorder();
		double bx = border.getCenterX(), bz = border.getCenterZ(), size = border.getLerpSize();
		if (border.getLerpTime() != 0) { throw new IllegalStateException("Box probes require a stationary initial border"); }
		int direct = 0, pairs = 0, boatHits = 0;
		List<BlockPos> cells = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BoatEntity boat = null;
		try {
			config.scalarParticleEntityQuery = true;
			Probe probe = new Probe(world, x, y, z);
			double[] values = {0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE,
				-Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.longBitsToDouble(0x7ff8000000000019L), -30000000, -16.25, -1, 1, 16.25, 30000000};
			for (double a : values) {
				for (double b : values) {
					Box box = new Box(a, b, -2, b, a, 2);
					Box grown = ParticleEntityQuery.grow(world, probe, box, 0.25, 0.25, 0.25);
					boolean eligible = ParticleEntityQuery.eligible(probe, box);
					check((grown == box) == eligible, "growth decision");
					if (!eligible) { check(ParticleBoxes.same(grown, box.grown(0.25, 0.25, 0.25)), "unsafe growth fallback"); }
					direct++;
				}
			}
			Box inverted = new Box(new BlockPos(2, 3, 4), new BlockPos(-2, -3, -4));
			check(ParticleEntityQuery.grow(world, probe, inverted, .25, .25, .25) != inverted, "inverted fallback");
			Box subclass = new Box(1, 2, 3, 4, 5, 6) {
				@Override public Box grown(double dx, double dy, double dz) { return new Box(10, 20, 30, 40, 50, 60); }
			};
			check(ParticleBoxes.same(ParticleEntityQuery.grow(world, probe, subclass, .25, .25, .25), subclass.grown(.25, .25, .25)), "subclass dispatch");
			Box safe = new Box(1, 2, 3, 4, 5, 6);
			check(ParticleEntityQuery.grow(world, probe, safe, .25, .25, .25) == safe, "safe scalar identity");
			check(ParticleEntityQuery.grow(world, probe, safe, .125, .25, .25) != safe, "other growth fallback");
			Box[] otherThread = new Box[1];
			Thread thread = new Thread(() -> otherThread[0] = ParticleEntityQuery.grow(world, probe, safe, .25, .25, .25));
			thread.start(); thread.join();
			check(otherThread[0] != null && otherThread[0] != safe && ParticleBoxes.same(otherThread[0], safe.grown(.25, .25, .25)), "thread fallback");
			BoatEntity nonParticle = new BoatEntity(world);
			check(ParticleEntityQuery.grow(world, nonParticle, safe, .25, .25, .25) != safe, "non-particle fallback");
			direct += 6;
			java.lang.reflect.Method predicate = ParticleEntityQuery.class.getDeclaredMethod("intersects", Box.class,
				double.class, double.class, double.class, double.class, double.class, double.class, Box.class);
			predicate.setAccessible(true);
			Random predicateRandom = new Random(190019);
			for (int trial = 0; trial < 10000; trial++) {
				double a = trial < values.length ? values[trial] : Double.longBitsToDouble(predicateRandom.nextLong());
				double b = trial < values.length ? values[values.length - 1 - trial] : Double.longBitsToDouble(predicateRandom.nextLong());
				Box entityBounds = trial % 3 == 0 ? new Box(new BlockPos(2, 3, 4), new BlockPos(-2, -3, -4)) : new Box(a, b, -2, b, a, 2);
				Box query = new Box(-.25, -.25, -.25, .25, .25, .25);
				boolean answer = (Boolean) predicate.invoke(null, entityBounds, query.minX, query.minY, query.minZ, query.maxX, query.maxY, query.maxZ, query);
				check(answer == entityBounds.intersects(query), "direct scalar predicate"); direct++;
			}
			Box virtualShape = new Box(1, 2, 3, 4, 5, 6) {
				@Override public boolean intersects(Box other) { return other.minX == -0.25 && other.maxX == 0.25; }
			};
			check((Boolean) predicate.invoke(null, virtualShape, -.25, -.25, -.25, .25, .25, .25, new Box(-.25, -.25, -.25, .25, .25, .25)), "shape virtual dispatch"); direct++;
			// Ordered queries at loaded/unloaded, chunk, height and hard-coordinate edges.
			for (double px : new double[]{x, -16.25, -16, -0.0, 0.0, 15.75, 16, 4096.5, -30000000, 30000000}) {
				for (double py : new double[]{-16.25, -0.0, 0.0, y, 255.75, 256, 1024}) {
					Box box = new Box(px, py, px, px + .2, py + .2, px + .2);
					Box grown = ParticleEntityQuery.grow(world, probe, box, .25, .25, .25);
					List<Entity> actual = ParticleEntityQuery.entities(world, probe, grown, box);
					List<Entity> expected = world.getEntities(probe, box.grown(.25, .25, .25));
					check(actual.size() == expected.size(), "direct list size");
					for (int i = 0; i < actual.size(); i++) { check(actual.get(i) == expected.get(i), "direct list order"); }
					direct++;
				}
			}

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
			direct += entityFixtures(world, sx + .5, sy + 2, sz + .5);
			Random random = new Random(190019);
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
						config.scalarParticleEntityQuery = false; a.move(dx, dy, dz);
						config.scalarParticleEntityQuery = true; b.move(dx, dy, dz);
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
			throw new IllegalStateException("Particle entity query diagnostic failed", ex);
		} finally {
			if (boat != null) { world.removeEntityNow(boat); }
			for (int i = cells.size() - 1; i >= 0; i--) { world.setBlockState(cells.get(i), states.get(i), 2); }
			border.setCenter(bx, bz); border.setSize(size);
			config.scalarParticleEntityQuery = original; config.verifyScalarParticleEntityQuery = verify;
		}
		Alacer.LOGGER.info("Particle entity query probes: {} direct checks, {} movement pairs matched, {} boat collision checks", direct, pairs, boatHits);
	}

	/** Positive multi-entity results, duplicate parts, exclude ordering and virtual shape calls. */
	private static int entityFixtures(World world, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		boolean verify = config.verifyScalarParticleEntityQuery;
		List<String> calls = new ArrayList<>();
		RecordingBoat first = new RecordingBoat(world, x, y, z, "first", calls);
		RecordingBoat second = new RecordingBoat(world, x + .1, y, z, "second", calls);
		Probe exclude = new Probe(world, x, y, z), part = new Probe(world, x, y, z);
		first.parts = new Entity[]{part, exclude, part};
		Box[] seenQuery = new Box[1];
		first.setShape(recordingShape(first.getShape(), calls, seenQuery));
		second.setShape(recordingShape(second.getShape(), calls, seenQuery));

		try {
			check(world.addEntity(first), "parts fixture insertion");
			check(world.addEntity(second), "shape fixture insertion");
			check(world.addEntity(exclude), "exclude fixture insertion");
			config.verifyScalarParticleEntityQuery = false;
			for (int i = 0; i < 4; i++) {
				Box query = new Box(x - .2 + i * .05, y - .2, z - .2, x + .2, y + .2, z + .2);
				config.scalarParticleEntityQuery = true;
				Box grown = ParticleEntityQuery.grow(world, exclude, query, .25, .25, .25);
				check(grown == query, "fixture scalar enabled");
				// The identity carries the decision across even a toggle at the adjacent boundary.
				config.scalarParticleEntityQuery = false;
				calls.clear(); seenQuery[0] = null;
				List<Entity> actual = ParticleEntityQuery.entities(world, exclude, grown, query);
				List<String> actualCalls = new ArrayList<>(calls);
				calls.clear(); seenQuery[0] = null;
				List<Entity> expected = world.getEntities(exclude, query.grown(.25, .25, .25));
				check(actualCalls.equals(calls), "entity/parts/virtual shape callback order");
				check(actual.size() == expected.size() && actual.size() == 4, "positive duplicate-part list size");
				for (int j = 0; j < actual.size(); j++) { check(actual.get(j) == expected.get(j), "positive identity/order"); }
				check(actual.get(0) == first && actual.get(1) == part && actual.get(2) == part && actual.get(3) == second,
					"expected insertion/parts order");
			}
		} finally {
			world.removeEntityNow(exclude); world.removeEntityNow(second); world.removeEntityNow(first);
			config.verifyScalarParticleEntityQuery = verify; config.scalarParticleEntityQuery = true;
		}
		return 4;
	}

	private static Box recordingShape(Box base, List<String> calls, Box[] seen) {
		return new Box(base.minX, base.minY, base.minZ, base.maxX, base.maxY, base.maxZ) {
			@Override public boolean intersects(Box other) {
				if (seen[0] == null) { seen[0] = other; }
				else { check(seen[0] == other, "shared query Box identity across virtual fallbacks"); }
				calls.add("virtual:" + Double.doubleToRawLongBits(other.minX) + ":" + Double.doubleToRawLongBits(other.maxY));
				return super.intersects(other);
			}
		};
	}

	private static final class RecordingBoat extends BoatEntity {
		private final String name;
		private final List<String> calls;
		private Entity[] parts;
		RecordingBoat(World world, double x, double y, double z, String name, List<String> calls) {
			super(world, x, y, z); this.name = name; this.calls = calls;
		}
		@Override public Box getShape() {
			if (this.calls != null) { this.calls.add("shape:" + this.name); }
			return super.getShape();
		}
		@Override public Entity[] getParts() {
			if (this.calls != null) { this.calls.add("parts:" + this.name); }
			return this.parts;
		}
	}

	private static void check(boolean passed, String what) {
		if (!passed) { throw new IllegalStateException("Particle entity query mismatch: " + what); }
	}

	private static final class Probe extends Particle {
		private final List<String> trace = new ArrayList<>();
		Probe(World world, double x, double y, double z) { super(world, x, y, z); }
		void cobweb(boolean value) { this.inCobweb = value; }
		void resetTrace() { this.random.setSeed(19); this.trace.clear(); }
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
