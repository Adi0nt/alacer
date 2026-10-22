package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.EntityShadows;
import net.minecraft.client.render.entity.EntityRenderer;

/** Coarse shadow timer; applied only with profiler.shadowTiming at startup. */
@Mixin(EntityRenderer.class)
public class EntityShadowTimingMixin {

	@Inject(method = "renderShadow", at = @At("HEAD"))
	private void alacer$beginShadow(CallbackInfo ci) {
		EntityShadows.beginShadow();
	}

	@Inject(method = "renderShadow", at = @At("RETURN"))
	private void alacer$endShadow(CallbackInfo ci) {
		EntityShadows.endShadow();
	}
}
