package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleMovement;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Preserve collision collection order, border effects, loaded checks and entity collisions. */
@Mixin(World.class)
public class ParticleCollisionMixin {

	@Redirect(method = "getCollisions", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"))
	private BlockState alacer$collisionBlockState(World world, BlockPos pos, Entity entity, Box shape) {
		return ParticleMovement.blockState(world, pos, entity);
	}
}
