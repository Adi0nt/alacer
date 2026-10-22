package net.adiont.alacer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.ParticleLightRepeats;
import net.adiont.alacer.render.ParticleTimings;
import net.adiont.alacer.render.ParticleVertices;
import net.adiont.alacer.render.RenderPasses;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;

/** Particle drawing time (lit and unlit passes, summed), draw calls, tick time and particle counts. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

	@Shadow
	private List<Particle>[][] particles;

	@Inject(method = {"render", "renderLit"}, at = @At("HEAD"))
	private void alacer$beginParticles(CallbackInfo ci) {
		RenderPasses.begin(Section.PARTICLES);
	}

	@Inject(method = {"render", "renderLit"}, at = @At("RETURN"))
	private void alacer$endParticles(CallbackInfo ci) {
		RenderPasses.end(Section.PARTICLES);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void alacer$countRendered(CallbackInfo ci) {
		ParticleTimings.countRendered(this.particles);
		ParticleVertices.beginPass();
		ParticleLightRepeats.beginPass();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void alacer$endRendered(CallbackInfo ci) {
		ParticleLightRepeats.endPass();
	}

	@Inject(method = "renderLit", at = @At("HEAD"))
	private void alacer$countRenderedLit(CallbackInfo ci) {
		ParticleTimings.countRenderedLit(this.particles);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
	private void alacer$beginDraw(CallbackInfo ci) {
		ParticleTimings.beginDraw();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", shift = At.Shift.AFTER))
	private void alacer$endDraw(CallbackInfo ci) {
		ParticleTimings.endDraw();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void alacer$beginTick(CallbackInfo ci) {
		ParticleTimings.beginTick(this.particles);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void alacer$endTick(CallbackInfo ci) {
		ParticleTimings.endTick();
	}
}
