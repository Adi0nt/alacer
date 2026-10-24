package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.EntityLighting;
import net.minecraft.entity.Entity;

/**
 * Intercepts Entity.getLightLevel and Entity.getBrightness to eliminate per-call BlockPos allocations.
 * Logic lives in {@link EntityLighting}.
 */
@Mixin(Entity.class)
public abstract class EntityLightingMixin {

	@Inject(method = "getLightLevel", at = @At("HEAD"), cancellable = true)
	private void alacer$getLightLevel(float tickDelta, CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		if (EntityLighting.isEnabled(entity)) {
			cir.setReturnValue(EntityLighting.getLightLevel(entity, tickDelta));
		}
	}

	@Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
	private void alacer$getBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
		Entity entity = (Entity) (Object) this;
		if (EntityLighting.isEnabled(entity)) {
			cir.setReturnValue(EntityLighting.getBrightness(entity, tickDelta));
		}
	}
}
