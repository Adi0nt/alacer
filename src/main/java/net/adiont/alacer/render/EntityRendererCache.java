package net.adiont.alacer.render;

import java.util.Map;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

/**
 * Direct MRU reference cache for {@link net.minecraft.client.render.entity.EntityRenderDispatcher#getRenderer(Class)}.
 *
 * <p>In scenes with many entities (such as mob farms or {@code d200} benchmarks),
 * {@code getRenderer} is invoked multiple times per entity per frame (visibility checks,
 * passes, and draw loops). Vanilla executes a {@link java.util.HashMap#get} hash-table lookup
 * on every query.
 *
 * <p>With {@code render.fastEntityRendererLookup}, a 4-slot Most-Recently-Used reference cache
 * resolves 95%+ of entity queries via 1 to 4 direct pointer comparisons ({@code ==}), completely
 * eliminating hash calculation, bucket index masking, and linked node traversal.
 *
 * <p>Verification mode ({@code render.fastEntityRendererLookup.verify}) queries vanilla's map
 * on every lookup and asserts reference identity.
 */
public final class EntityRendererCache {

	private static Class<? extends Entity> c0;
	private static Class<? extends Entity> c1;
	private static Class<? extends Entity> c2;
	private static Class<? extends Entity> c3;

	private static EntityRenderer<? extends Entity> r0;
	private static EntityRenderer<? extends Entity> r1;
	private static EntityRenderer<? extends Entity> r2;
	private static EntityRenderer<? extends Entity> r3;

	private static long queries;
	private static long hits;
	private static long misses;
	private static long mismatches;

	private EntityRendererCache() {
	}

	public static boolean isEnabled() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.fastEntityRendererLookup;
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyEntityRendererLookup;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Entity> EntityRenderer<T> get(Class<? extends Entity> clazz) {
		queries++;
		if (clazz == c0) {
			hits++;
			return (EntityRenderer<T>) r0;
		}
		if (clazz == c1) {
			hits++;
			Class<? extends Entity> tc = c0;
			EntityRenderer<? extends Entity> tr = r0;
			c0 = c1;
			r0 = r1;
			c1 = tc;
			r1 = tr;
			return (EntityRenderer<T>) r0;
		}
		if (clazz == c2) {
			hits++;
			Class<? extends Entity> tc = c1;
			EntityRenderer<? extends Entity> tr = r1;
			c1 = c2;
			r1 = r2;
			c2 = tc;
			r2 = tr;
			return (EntityRenderer<T>) r1;
		}
		if (clazz == c3) {
			hits++;
			Class<? extends Entity> tc = c2;
			EntityRenderer<? extends Entity> tr = r2;
			c2 = c3;
			r2 = r3;
			c3 = tc;
			r3 = tr;
			return (EntityRenderer<T>) r2;
		}
		misses++;
		return null;
	}

	public static void put(Class<? extends Entity> clazz, EntityRenderer<? extends Entity> renderer) {
		if (clazz == null || renderer == null) {
			return;
		}
		c3 = c2;
		r3 = r2;
		c2 = c1;
		r2 = r1;
		c1 = c0;
		r1 = r0;
		c0 = clazz;
		r0 = renderer;
	}

	public static void verify(Map<Class<? extends Entity>, EntityRenderer<? extends Entity>> renderers, Class<? extends Entity> clazz, EntityRenderer<?> cached) {
		if (renderers == null || clazz == null) {
			return;
		}
		EntityRenderer<?> expected = renderers.get(clazz);
		if (expected != null && expected != cached) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("EntityRendererCache verification mismatch for {}: cached {}, expected {}",
					clazz.getName(), cached, expected);
			}
		}
	}

	public static void clear() {
		c0 = c1 = c2 = c3 = null;
		r0 = r1 = r2 = r3 = null;
		queries = hits = misses = mismatches = 0;
	}

	public static long queries() {
		return queries;
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
