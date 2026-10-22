package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.ParticleTimings;
import net.minecraft.entity.Entity;

/** Particle movement and light-query timers; applied only with profiler.particleDetail at startup. */
@Mixin(Entity.class)
public class EntityParticleDetailMixin {

	@Inject(method = "move", at = @At("HEAD"))
	private void alacer$beginMove(double dx, double dy, double dz, CallbackInfo ci) {
		ParticleTimings.beginMove(this);
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void alacer$endMove(double dx, double dy, double dz, CallbackInfo ci) {
		ParticleTimings.endMove(this);
	}

	@Inject(method = "getLightLevel", at = @At("HEAD"))
	private void alacer$beginLight(float tickDelta, CallbackInfoReturnable<Integer> cir) {
		ParticleTimings.beginLight(this);
	}

	@Inject(method = "getLightLevel", at = @At("RETURN"))
	private void alacer$endLight(float tickDelta, CallbackInfoReturnable<Integer> cir) {
		ParticleTimings.endLight(this);
	}
}
