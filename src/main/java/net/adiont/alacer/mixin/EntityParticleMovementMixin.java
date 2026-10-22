package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleMovement;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Leave vanilla movement and callbacks intact, changing only the chunk lookup for block reads. */
@Mixin(Entity.class)
public class EntityParticleMovementMixin {

	@Redirect(method = {"move", "checkBlockCollisions"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"))
	private BlockState alacer$movementBlockState(World world, BlockPos pos) {
		return ParticleMovement.blockState(world, pos, (Entity) (Object) this);
	}
}
