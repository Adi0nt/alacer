package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.ParticleTimings;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;

/** Per-particle update and removal timers; applied only with profiler.particleDetail at startup. */
@Mixin(ParticleManager.class)
public class ParticleDetailMixin {

	@Inject(method = "tickParticle", at = @At("HEAD"))
	private void alacer$beginUpdate(Particle particle, CallbackInfo ci) {
		ParticleTimings.beginUpdate();
	}

	@Inject(method = "tickParticle", at = @At("RETURN"))
	private void alacer$endUpdate(Particle particle, CallbackInfo ci) {
		ParticleTimings.endUpdate(particle);
	}

	@Inject(method = "tickParticles(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z"))
	private void alacer$beginRemove(CallbackInfo ci) {
		ParticleTimings.beginRemove();
	}

	@Inject(method = "tickParticles(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z", shift = At.Shift.AFTER))
	private void alacer$endRemove(CallbackInfo ci) {
		ParticleTimings.endRemove();
	}

	@Inject(method = {"render", "renderLit"}, at = @At("HEAD"))
	private void alacer$beginRendering(CallbackInfo ci) {
		ParticleTimings.setRendering(true);
	}

	@Inject(method = {"render", "renderLit"}, at = @At("RETURN"))
	private void alacer$endRendering(CallbackInfo ci) {
		ParticleTimings.setRendering(false);
	}
}
