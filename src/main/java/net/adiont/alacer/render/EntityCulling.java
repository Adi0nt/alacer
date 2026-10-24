package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/**
 * Distance-first evaluation for renderers that inherit the unchanged base predicate.
 * Renderer lookup happens first so leashes, guardian beams and custom policies keep
 * their virtual dispatch. Verification evaluates that reference policy and uses it.
 */
public final class EntityCulling {

	private static long tested;
	private static long culledDistance;
	private static long culledFrustum;
	private static long visible;
	private static long mismatches;

	private EntityCulling() {
	}

	public static boolean isEnabled() {
		AlacerConfig config = Alacer.get().config();
		return config != null && (config.fastEntityCulling || config.verifyEntityCulling);
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyEntityCulling;
	}

	private static final ClassValue<Boolean> BASE_POLICY = new ClassValue<>() {
		@Override protected Boolean computeValue(Class<?> type) {
			try {
				Class<?>[] params = {Entity.class, Culler.class, double.class, double.class, double.class};
				for (java.lang.reflect.Method method : EntityRenderer.class.getDeclaredMethods()) {
					if (method.getReturnType() == boolean.class && java.util.Arrays.equals(method.getParameterTypes(), params))
						return type.getMethod(method.getName(), params).getDeclaringClass() == EntityRenderer.class;
				}
			} catch (ReflectiveOperationException | LinkageError unavailable) { }
			return false;
		}
	};
	private static Boolean baseUnchanged;

	private static boolean compatibleBase() {
		if (baseUnchanged == null) {
			baseUnchanged = true;
			try {
				for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mixin : org.spongepowered.asm.mixin.Mixins.getMixinsForClass(EntityRenderer.class.getName())) {
					if (!mixin.getClassName().startsWith("net.adiont.alacer.")) baseUnchanged = false;
				}
			} catch (RuntimeException | LinkageError unavailable) { baseUnchanged = false; }
		}
		return baseUnchanged;
	}

	public static boolean shouldRender(EntityRenderDispatcher dispatcher, Entity entity, Culler culler, double x, double y, double z) {
		EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
		if (!compatibleBase()) { tested++; return renderer != null && invokeShouldRender(renderer, entity, culler, x, y, z); }
		return shouldRenderWithRenderer(renderer, entity, culler, x, y, z);
	}

	static boolean shouldRenderWithRenderer(EntityRenderer<?> renderer, Entity entity, Culler culler, double x, double y, double z) {
		tested++;
		if (renderer == null) return false;
		// Leashes, guardian beams and all foreign/subclass policies retain virtual dispatch.
		if (!BASE_POLICY.get(renderer.getClass())) return invokeShouldRender(renderer, entity, culler, x, y, z);
		if (!entity.shouldRender(x, y, z)) { culledDistance++; return false; }
		if (entity.ignoreCameraFrustum) { visible++; return true; }
		Box box = entity.getShape();
		if (box.isInvalid() || box.getAverageSideLength() == 0.0)
			box = new Box(entity.x - 2.0, entity.y - 2.0, entity.z - 2.0, entity.x + 2.0, entity.y + 2.0, entity.z + 2.0);
		if (culler.isVisible(box)) { visible++; return true; }
		culledFrustum++;
		return false;
	}

	@SuppressWarnings("unchecked")
	private static boolean invokeShouldRender(EntityRenderer<?> renderer, Entity entity, Culler culler, double camX, double camY, double camZ) {
		return ((EntityRenderer<Entity>) renderer).shouldRender(entity, culler, camX, camY, camZ);
	}

	public static boolean verify(EntityRenderDispatcher dispatcher, Entity entity, Culler culler, double camX, double camY, double camZ, boolean fastResult) {
		EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
		boolean vanilla = renderer != null && invokeShouldRender(renderer, entity, culler, camX, camY, camZ);
		if (vanilla != fastResult) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("EntityCulling verification mismatch for {}: fast {}, vanilla {}",
					entity, fastResult, vanilla);
			}
		}
		return vanilla;
	}

	public static void clear() {
		tested = culledDistance = culledFrustum = visible = mismatches = 0;
	}

	public static long tested() {
		return tested;
	}

	public static long culledDistance() {
		return culledDistance;
	}

	public static long culledFrustum() {
		return culledFrustum;
	}

	public static long visible() {
		return visible;
	}

	public static long mismatches() {
		return mismatches;
	}
}
