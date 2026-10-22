package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.ParticleLightReuse;
import net.minecraft.client.ParticleManager;
import net.minecraft.world.World;

/**
 * Pass boundaries for {@link ParticleLightReuse}: the unlit pass opens a table for the manager's
 * world and closes it on return; the lit pass, which starts each frame's particle drawing, closes
 * it too. Installed only when {@code render.reuseParticleLight}, its verification or a benchmark
 * toggle of it is on at startup; three calls per frame.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerLightReuseMixin {

	@Shadow
	protected World world;

	@Inject(method = "render", at = @At("HEAD"))
	private void alacer$beginLightReuse(CallbackInfo ci) {
		ParticleLightReuse.beginPass(this.world);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void alacer$endLightReuse(CallbackInfo ci) {
		ParticleLightReuse.endPass();
	}

	@Inject(method = "renderLit", at = @At("HEAD"))
	private void alacer$closeLightReuse(CallbackInfo ci) {
		ParticleLightReuse.endPass();
	}
}
