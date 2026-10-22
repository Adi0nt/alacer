package net.adiont.alacer.render;

import java.util.List;

import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.entity.particle.Particle;

/**
 * Particle attribution. The coarse part is always on: the manager's tick and its draws as
 * sections, plus how many particles each tick and render pass handled (a few list sizes per
 * frame and tick). With {@code profiler.particleDetail} at startup, separate mixins also time
 * each particle's update, its {@code Entity.move}, the dead-particle {@code removeAll} and the
 * light query each particle makes while drawing: two {@code nanoTime} calls per particle and
 * operation, so detail runs are for attribution, not frame-time comparisons.
 *
 * <p>Client thread only, like the particle manager itself.
 */
public final class ParticleTimings {

	private static FrameProfiler profiler;
	private static boolean updating;
	private static Object moving;
	private static boolean rendering;
	private static Object lighting;
	private static long updateStart;
	private static long moveStart;
	private static long lightStart;
	private static long removeStart;

	private ParticleTimings() {
	}

	public static void init(FrameProfiler frameProfiler) {
		profiler = frameProfiler;
	}

	private static int size(List<?>[] lists) {
		int total = 0;
		for (List<?> list : lists) {
			total += list.size();
		}
		return total;
	}

	public static void beginTick(List<?>[][] particles) {
		if (profiler != null) {
			profiler.begin(Section.PARTICLE_TICK);
			int total = 0;
			for (List<?>[] layer : particles) {
				total += size(layer);
			}
			profiler.count(Counter.PARTICLES_TICKED, total);
		}
	}

	public static void endTick() {
		if (profiler != null) {
			profiler.end(Section.PARTICLE_TICK);
		}
	}

	/** Unlit pass: vanilla draws atlas layers 0 to 2. */
	public static void countRendered(List<?>[][] particles) {
		if (profiler != null) {
			profiler.count(Counter.PARTICLES_RENDERED, size(particles[0]) + size(particles[1]) + size(particles[2]));
		}
	}

	/** Lit pass: vanilla draws atlas layer 3 (entity-textured particles such as large explosions). */
	public static void countRenderedLit(List<?>[][] particles) {
		if (profiler != null) {
			profiler.count(Counter.PARTICLES_RENDERED, size(particles[3]));
		}
	}

	public static void beginDraw() {
		if (profiler != null) {
			profiler.begin(Section.PARTICLE_DRAW);
		}
	}

	public static void endDraw() {
		if (profiler != null) {
			profiler.end(Section.PARTICLE_DRAW);
		}
	}

	// Detail hooks (profiler.particleDetail only).

	public static void beginUpdate() {
		updating = true;
		updateStart = System.nanoTime();
	}

	public static void endUpdate(Particle particle) {
		long now = System.nanoTime();
		updating = false;
		if (profiler != null) {
			profiler.add(Section.PARTICLE_UPDATE, now - updateStart);
			if (particle.removed) {
				profiler.count(Counter.PARTICLES_REMOVED);
			}
		}
	}

	public static void beginMove(Object entity) {
		if (updating && moving == null && entity instanceof Particle) {
			moving = entity;
			moveStart = System.nanoTime();
		}
	}

	public static void endMove(Object entity) {
		if (moving == entity && entity != null) {
			long now = System.nanoTime();
			moving = null;
			if (profiler != null) {
				profiler.add(Section.PARTICLE_MOVE, now - moveStart);
			}
		}
	}

	public static void beginRemove() {
		removeStart = System.nanoTime();
	}

	public static void endRemove() {
		if (profiler != null) {
			profiler.add(Section.PARTICLE_REMOVE, System.nanoTime() - removeStart);
		}
	}

	public static void setRendering(boolean value) {
		rendering = value;
		lighting = null;
	}

	public static void beginLight(Object entity) {
		if (rendering && lighting == null && entity instanceof Particle) {
			lighting = entity;
			lightStart = System.nanoTime();
		}
	}

	public static void endLight(Object entity) {
		if (lighting == entity && entity != null) {
			long now = System.nanoTime();
			lighting = null;
			if (profiler != null) {
				profiler.add(Section.PARTICLE_LIGHT, now - lightStart);
			}
		}
	}
}
