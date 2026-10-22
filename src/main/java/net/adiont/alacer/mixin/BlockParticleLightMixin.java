package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleLight;
import net.minecraft.client.entity.particle.BlockParticle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Block-break particles' second light query, for their source block; see {@link ParticleLight}. */
@Mixin(BlockParticle.class)
public class BlockParticleLightMixin {

	@Redirect(method = "getLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLightColor(Lnet/minecraft/util/math/BlockPos;I)I"))
	private int alacer$lightColor(World world, BlockPos pos, int blockLight) {
		return ParticleLight.lightColor(this, world, pos, blockLight);
	}
}
