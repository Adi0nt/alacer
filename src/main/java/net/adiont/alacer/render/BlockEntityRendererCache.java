package net.adiont.alacer.render;

import java.util.Map;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Direct MRU reference cache for {@link net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher#getRenderer(Class)}.
 *
 * <p>During block entity rendering across chests, signs, banners, and skulls, vanilla calls
 * {@code HashMap.get} on every block entity every frame. This MRU cache resolves 95%+ of queries
 * via 1-4 direct pointer comparisons ({@code ==}), eliminating hashing and map bucket lookups.
 */
public final class BlockEntityRendererCache {

	private static Class<? extends BlockEntity> c0;
	private static Class<? extends BlockEntity> c1;
	private static Class<? extends BlockEntity> c2;
	private static Class<? extends BlockEntity> c3;

	private static BlockEntityRenderer<? extends BlockEntity> r0;
	private static BlockEntityRenderer<? extends BlockEntity> r1;
	private static BlockEntityRenderer<? extends BlockEntity> r2;
	private static BlockEntityRenderer<? extends BlockEntity> r3;

	private static long queries;
	private static long hits;
	private static long misses;
	private static long mismatches;

	private BlockEntityRendererCache() {
	}

	public static boolean isEnabled() {
		AlacerConfig config = Alacer.get().config();
		return config != null && (config.fastEntityPipeline || config.verifyEntityPipeline);
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyEntityPipeline;
	}

	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity> BlockEntityRenderer<T> get(Class<? extends BlockEntity> clazz) {
		queries++;
		if (clazz == c0) {
			hits++;
			return (BlockEntityRenderer<T>) r0;
		}
		if (clazz == c1) {
			hits++;
			Class<? extends BlockEntity> tc = c0;
			BlockEntityRenderer<? extends BlockEntity> tr = r0;
			c0 = c1;
			r0 = r1;
			c1 = tc;
			r1 = tr;
			return (BlockEntityRenderer<T>) r0;
		}
		if (clazz == c2) {
			hits++;
			Class<? extends BlockEntity> tc = c0;
			BlockEntityRenderer<? extends BlockEntity> tr = r0;
			c0 = c2;
			r0 = r2;
			c2 = c1;
			r2 = r1;
			c1 = tc;
			r1 = tr;
			return (BlockEntityRenderer<T>) r0;
		}
		if (clazz == c3) {
			hits++;
			Class<? extends BlockEntity> tc = c0;
			BlockEntityRenderer<? extends BlockEntity> tr = r0;
			c0 = c3;
			r0 = r3;
			c3 = c2;
			r3 = r2;
			c2 = c1;
			r2 = r1;
			c1 = tc;
			r1 = tr;
			return (BlockEntityRenderer<T>) r0;
		}
		misses++;
		return null;
	}

	public static void put(Class<? extends BlockEntity> clazz, BlockEntityRenderer<? extends BlockEntity> renderer) {
		if (clazz == null || renderer == null) return;
		if (clazz == c0) { r0 = renderer; return; }
		c3 = c2; r3 = r2;
		c2 = c1; r2 = r1;
		c1 = c0; r1 = r0;
		c0 = clazz; r0 = renderer;
	}

	public static void verify(Map<Class<? extends BlockEntity>, BlockEntityRenderer<? extends BlockEntity>> map,
		Class<? extends BlockEntity> clazz, BlockEntityRenderer<?> cached) {
		BlockEntityRenderer<?> vanilla = map.get(clazz);
		if (vanilla != cached) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("BlockEntityRendererCache mismatch for {}: cached {}, vanilla {}", clazz, cached, vanilla);
			}
		}
	}

	public static void clear() {
		c0 = c1 = c2 = c3 = null;
		r0 = r1 = r2 = r3 = null;
		queries = hits = misses = mismatches = 0;
	}

	public static long queries() { return queries; }
	public static long hits() { return hits; }
	public static long misses() { return misses; }
	public static long mismatches() { return mismatches; }
}
