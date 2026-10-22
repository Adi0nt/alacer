package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ShadowPositions;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Session 28 candidate ({@code render.reuseShadowPositions}): the block-below position passed to
 * {@code renderShadow}'s block-state query (see {@link ShadowPositions}). Applied only when the option, its
 * verification or its benchmark toggle is set at startup, so the hook is absent otherwise.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityShadowPositionMixin {

	@Shadow
	private World getWorld() {
		throw new AssertionError();
	}

	@Redirect(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;down()Lnet/minecraft/util/math/BlockPos;"), allow = 1)
	private BlockPos alacer$below(BlockPos pos) {
		return ShadowPositions.below(this.getWorld(), pos);
	}
}
