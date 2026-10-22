package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.List;

import net.adiont.alacer.Alacer;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Disabled experiment: scalar bounds for just getCollisions' nearby-entity query. */
public final class ParticleEntityQuery {

	/** Implemented by the startup-gated World mixin; calls the original virtual loaded check. */
	public interface LoadedCheck { boolean alacer$isQueryChunkLoaded(int x, int z, boolean allowEmpty); }

	private static Thread owner;
	private static long compared, mismatches, scalarQueries, chunkFallbacks, shapeFallbacks;
	private ParticleEntityQuery() { }
	public static void init() { owner = Thread.currentThread(); }

	public static boolean eligible(Entity entity, Box box) {
		return Alacer.get().config().scalarParticleEntityQuery && entity instanceof Particle
			&& entity.world.getClass() == ClientWorld.class && Thread.currentThread() == owner
			&& box.getClass() == Box.class && safe(box);
	}

	private static boolean safe(Box box) {
		return bounded(box.minX, box.maxX) && bounded(box.minY, box.maxY) && bounded(box.minZ, box.maxZ);
	}

	private static boolean bounded(double min, double max) {
		// Also excludes NaN/infinity; growing by .25 cannot overflow inside these bounds.
		return min >= -30000000 && max <= 30000000 && min <= max;
	}

	/** Only the following redirect consumes the returned identity; no Box is mutated. */
	public static Box grow(World world, Entity entity, Box box, double dx, double dy, double dz) {
		return dx == 0.25 && dy == 0.25 && dz == 0.25 && eligible(entity, box) && world == entity.world ? box : box.grown(dx, dy, dz);
	}

	/** Identity records the preceding pure grown call's decision, even if the toggle changes. */
	public static List<Entity> entities(World world, Entity exclude, Box bounds, Box original) {
		if (bounds != original || bounds.getClass() != Box.class) { return world.getEntities(exclude, bounds); }
		return scalar(world, exclude, original);
	}

	private static List<Entity> scalar(World world, Entity exclude, Box box) {
		double x1 = box.minX - 0.25, y1 = box.minY - 0.25, z1 = box.minZ - 0.25;
		double x2 = box.maxX + 0.25, y2 = box.maxY + 0.25, z2 = box.maxZ + 0.25;
		double minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
		double maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
		List<Entity> result = new ArrayList<>();
		// Vanilla shares one immutable grown Box across every virtual query call. Materialize
		// it only on demand, but preserve that same identity for all fallbacks in this query.
		Box virtualBounds = null;
		int firstX = MathHelper.floor((minX - 2.0) / 16.0), lastX = MathHelper.floor((maxX + 2.0) / 16.0);
		int firstZ = MathHelper.floor((minZ - 2.0) / 16.0), lastZ = MathHelper.floor((maxZ + 2.0) / 16.0);
		for (int x = firstX; x <= lastX; x++) {
			for (int z = firstZ; z <= lastZ; z++) {
				if (!((LoadedCheck) world).alacer$isQueryChunkLoaded(x, z, true)) { continue; }
				WorldChunk chunk = world.getChunkAt(x, z);
				if (chunk.getClass() != WorldChunk.class) {
					if (virtualBounds == null) { virtualBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ); }
					chunk.getEntities(exclude, virtualBounds, result, EntityFilter.NOT_SPECTATOR);
					if (Alacer.get().config().verifyScalarParticleEntityQuery) { chunkFallbacks++; }
					continue;
				}
				TypeInstanceMultiMap<Entity>[] sections = chunk.getEntities();
				int firstY = MathHelper.clamp(MathHelper.floor((minY - 2.0) / 16.0), 0, sections.length - 1);
				int lastY = MathHelper.clamp(MathHelper.floor((maxY + 2.0) / 16.0), 0, sections.length - 1);
				for (int y = firstY; y <= lastY; y++) {
					if (sections[y].isEmpty()) { continue; }
					for (Entity entity : sections[y]) {
						// Keep getShape/intersects before exclude, and getParts inside the outer hit.
						Box entityShape = entity.getShape();
						if (entityShape.getClass() != Box.class && virtualBounds == null) {
							virtualBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
						}
						if (intersects(entityShape, minX, minY, minZ, maxX, maxY, maxZ, virtualBounds) && entity != exclude) {
							if (EntityFilter.NOT_SPECTATOR.apply(entity)) { result.add(entity); }
							Entity[] parts = entity.getParts();
							if (parts != null) {
								for (Entity part : parts) {
									if (part == exclude) { continue; }
									Box partShape = part.getShape();
									if (partShape.getClass() != Box.class && virtualBounds == null) {
										virtualBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
									}
									if (intersects(partShape, minX, minY, minZ, maxX, maxY, maxZ, virtualBounds)
										&& EntityFilter.NOT_SPECTATOR.apply(part)) { result.add(part); }
								}
							}
						}
					}
				}
			}
		}
		if (Alacer.get().config().verifyScalarParticleEntityQuery) {
			scalarQueries++;
			Box expectedBounds = box.grown(0.25, 0.25, 0.25);
			check(ParticleBoxes.same(new Box(minX, minY, minZ, maxX, maxY, maxZ), expectedBounds), "bounds");
			List<Entity> expected = world.getEntities(exclude, expectedBounds);
			check(result.size() == expected.size(), "list size");
			for (int i = 0; i < result.size(); i++) { check(result.get(i) == expected.get(i), "list identity/order"); }
			compared++;
		}
		return result;
	}

	private static boolean intersects(Box box, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Box virtualBounds) {
		if (box.getClass() != Box.class) {
			if (Alacer.get().config().verifyScalarParticleEntityQuery) { shapeFallbacks++; }
			return box.intersects(virtualBounds);
		}
		// Receiver/argument orientation and short-circuit order match Box.intersects exactly.
		if (maxX <= box.minX || minX >= box.maxX) { return false; }
		return maxY <= box.minY || minY >= box.maxY ? false : !(maxZ <= box.minZ) && !(minZ >= box.maxZ);
	}

	private static void check(boolean matches, String what) {
		if (!matches) { mismatches++; throw new IllegalStateException("Particle entity query mismatch: " + what); }
	}

	public static String verificationSummary() {
		return compared + " compared, " + mismatches + " mismatched, " + scalarQueries + " scalar queries, "
			+ chunkFallbacks + " chunk fallbacks, " + shapeFallbacks + " shape fallbacks";
	}
}
