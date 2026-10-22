package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleVertices;
import net.minecraft.client.entity.particle.BlockParticle;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;

/**
 * The four element calls of each vertex that {@code Particle.render} (also reached through every
 * {@code super.render} call) and {@code BlockParticle.render} make. Logic lives in
 * {@link ParticleVertices}; the particle's own render method, size, texture coordinates and light
 * query still run. Plain redirects: about 16 calls per particle per frame. Another mod's
 * {@code @Redirect} of the same calls would conflict; injections around them still compose.
 * Applied only when the option, its verification or a benchmark toggle of it is on at startup.
 */
@Mixin({Particle.class, BlockParticle.class})
public class ParticleVertexMixin {

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;vertex(DDD)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
	private BufferBuilder alacer$position(BufferBuilder buffer, double x, double y, double z) {
		return ParticleVertices.position(buffer, x, y, z);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;texture(DD)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
	private BufferBuilder alacer$texture(BufferBuilder buffer, double u, double v) {
		return ParticleVertices.texture(buffer, u, v);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
	private BufferBuilder alacer$color(BufferBuilder buffer, float red, float green, float blue, float alpha) {
		return ParticleVertices.color(buffer, red, green, blue, alpha);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;texture(II)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
	private BufferBuilder alacer$light(BufferBuilder buffer, int u, int v) {
		return ParticleVertices.light(buffer, u, v);
	}
}
