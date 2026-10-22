package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleLight;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The light query that every particle makes through {@code Entity.getLightLevel} while it is
 * drawn. Logic lives in {@link ParticleLight}; other entities keep vanilla's call.
 *
 * <p>This runs once per particle per frame (thousands in dense scenes), so a plain redirect
 * keeps the disabled path free of {@code Operation.call} argument arrays and boxing. Another
 * mod's {@code @Redirect} of the same call would conflict; injections around it still compose.
 */
@Mixin(Entity.class)
public class EntityParticleLightMixin {

	@Redirect(method = "getLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLightColor(Lnet/minecraft/util/math/BlockPos;I)I"))
	private int alacer$lightColor(World world, BlockPos pos, int blockLight) {
		return ParticleLight.lightColor(this, world, pos, blockLight);
	}
}
