package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.util.math.Box;

/**
 * Fast entity distance and frustum culling (optimization Phase 2).
 *
 * <p>In vanilla Minecraft, {@link EntityRenderDispatcher#shouldRender} evaluates the renderer
 * map lookup and bounding-box validation/allocation before checking whether the entity is within
 * render distance. In scenes with 200 mobs, hundreds of off-screen or out-of-range entities waste
 * CPU time calculating shapes and looking up renderers.
 *
 * <p>With {@code render.fastEntityCulling}:
 * <ol>
 *   <li>{@link Entity#shouldRender(double, double, double)} is evaluated first, immediately rejecting
 *       out-of-range entities without shape calculations or renderer lookups (except leashed mobs or
 *       attacking guardians whose attachment may remain visible).</li>
 *   <li>If {@link Entity#ignoreCameraFrustum} is true, the entity is accepted immediately.</li>
 *   <li>Frustum visibility is tested on the entity's bounding box.</li>
 *   <li>Only visible entities query {@link EntityRenderDispatcher#getRenderer}.</li>
 * </ol>
 *
 * <p>Verification mode ({@code render.fastEntityCulling.verify}) executes vanilla's exact
 * decision and confirms identical visibility results.
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
		return config != null && config.fastEntityCulling;
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyEntityCulling;
	}

	public static boolean shouldRender(EntityRenderDispatcher dispatcher, Entity entity, Culler culler, double camX, double camY, double camZ) {
		tested++;
		boolean inDistance = entity.shouldRender(camX, camY, camZ);
		if (!inDistance) {
			if (entity instanceof MobEntity) {
				MobEntity mob = (MobEntity) entity;
				if (mob.isLeashed() && mob.getLeashHolder() != null) {
					return culler.isVisible(mob.getLeashHolder().getShape());
				}
			}
			if (entity instanceof GuardianEntity) {
				GuardianEntity guardian = (GuardianEntity) entity;
				if (guardian.hasBeamTarget() && guardian.getLaserTarget() != null) {
					EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
					return renderer != null && invokeShouldRender(renderer, entity, culler, camX, camY, camZ);
				}
			}
			culledDistance++;
			return false;
		}

		if (entity.ignoreCameraFrustum) {
			visible++;
			return dispatcher.getRenderer(entity) != null;
		}

		Box box = entity.getShape();
		if (box == null || box.isInvalid() || box.getAverageSideLength() == 0.0) {
			box = new Box(entity.x - 2.0, entity.y - 2.0, entity.z - 2.0, entity.x + 2.0, entity.y + 2.0, entity.z + 2.0);
		}

		if (culler.isVisible(box)) {
			visible++;
			return dispatcher.getRenderer(entity) != null;
		}

		if (entity instanceof MobEntity) {
			MobEntity mob = (MobEntity) entity;
			if (mob.isLeashed() && mob.getLeashHolder() != null) {
				return culler.isVisible(mob.getLeashHolder().getShape());
			}
		}
		if (entity instanceof GuardianEntity) {
			GuardianEntity guardian = (GuardianEntity) entity;
			if (guardian.hasBeamTarget() && guardian.getLaserTarget() != null) {
				EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
				return renderer != null && invokeShouldRender(renderer, entity, culler, camX, camY, camZ);
			}
		}

		culledFrustum++;
		return false;
	}

	@SuppressWarnings("unchecked")
	private static boolean invokeShouldRender(EntityRenderer<?> renderer, Entity entity, Culler culler, double camX, double camY, double camZ) {
		return ((EntityRenderer<Entity>) renderer).shouldRender(entity, culler, camX, camY, camZ);
	}

	public static void verify(EntityRenderDispatcher dispatcher, Entity entity, Culler culler, double camX, double camY, double camZ, boolean fastResult) {
		EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
		boolean vanilla = renderer != null && invokeShouldRender(renderer, entity, culler, camX, camY, camZ);
		if (vanilla != fastResult) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("EntityCulling verification mismatch for {}: fast {}, vanilla {}",
					entity, fastResult, vanilla);
			}
		}
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
