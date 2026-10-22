package net.adiont.alacer.mixin;

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleRemoval;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;

/**
 * The dead-particle {@code removeAll} at the end of each particle list's tick. Logic lives in
 * {@link ParticleRemoval}. A plain redirect, like the light queries; it runs eight times per
 * tick. Another mod's {@code @Redirect} of the same call would conflict; injections around it
 * (such as {@code profiler.particleDetail}'s removal timer) still compose.
 */
@Mixin(ParticleManager.class)
public class ParticleRemovalMixin {

	@SuppressWarnings("unchecked")
	@Redirect(method = "tickParticles(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z"))
	private boolean alacer$removeDead(List<Particle> particles, Collection<?> dead) {
		return ParticleRemoval.removeAll(particles, (List<Particle>) dead);
	}
}
