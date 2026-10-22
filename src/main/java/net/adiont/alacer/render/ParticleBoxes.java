package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/**
 * Stateless immutable value reuse at Entity.move's own call sites only. No box is mutated,
 * pooled or retained here. Vanilla still executes collision queries, axis clipping, shape
 * assignments and callbacks, even when all movement offsets are zero.
 */
public final class ParticleBoxes {

	private static Thread owner;
	private static long compared, reused, mismatches;

	private ParticleBoxes() { }

	public static void init() { owner = Thread.currentThread(); }

	private static boolean eligible(Entity entity, Box box, AlacerConfig config) {
		return config.reuseParticleBoxes && entity instanceof Particle
			&& entity.world.getClass() == ClientWorld.class && Thread.currentThread() == owner
			&& box.getClass() == Box.class;
	}

	public static Box moved(Entity entity, Box box, double dx, double dy, double dz) {
		AlacerConfig config = Alacer.get().config();
		if (!eligible(entity, box, config)) { return box.moved(dx, dy, dz); }
		boolean reuse = ZeroOffsetBox.canReuse(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, dx, dy, dz);
		Box result = reuse ? box : box.moved(dx, dy, dz);
		if (config.verifyReuseParticleBoxes) { verify(result, box.moved(dx, dy, dz), reuse); }
		return result;
	}

	public static Box expanded(Entity entity, Box box, double dx, double dy, double dz) {
		AlacerConfig config = Alacer.get().config();
		if (!eligible(entity, box, config)) { return box.expanded(dx, dy, dz); }
		boolean reuse = ZeroOffsetBox.canReuse(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, dx, dy, dz);
		Box result = reuse ? box : box.expanded(dx, dy, dz);
		if (config.verifyReuseParticleBoxes) { verify(result, box.expanded(dx, dy, dz), reuse); }
		return result;
	}

	private static void verify(Box actual, Box expected, boolean reuse) {
		compared++;
		if (reuse) { reused++; }
		if (!same(actual, expected)) {
			mismatches++;
			throw new IllegalStateException("Particle box mismatch: " + actual + " vs " + expected);
		}
	}

	public static boolean same(Box a, Box b) {
		return bits(a.minX, b.minX) && bits(a.minY, b.minY) && bits(a.minZ, b.minZ)
			&& bits(a.maxX, b.maxX) && bits(a.maxY, b.maxY) && bits(a.maxZ, b.maxZ);
	}

	private static boolean bits(double a, double b) {
		return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
	}

	public static String verificationSummary() {
		return compared + " compared, " + reused + " reused, " + mismatches + " mismatched";
	}
}
