package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.EntityShadows;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The three world queries of entity shadows. Logic lives in {@link EntityShadows}.
 *
 * <p>These run about 2,000 times per frame with 200 mobs nearby. {@code @WrapOperation} would
 * pass vanilla's path through {@code Operation.call(Object...)}, whose argument arrays (and
 * {@code Float} boxing) the JIT removed in some launches but not others (19-38 KB per frame
 * measured), so plain redirects keep the disabled path allocation-free. Another mod's
 * {@code @Redirect} of the same three calls would conflict; wrappers and injections still compose.
 */
@Mixin(EntityRenderer.class)
public class EntityShadowMixin {

	@Redirect(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"))
	private BlockState alacer$blockState(World world, BlockPos pos) {
		return EntityShadows.blockState(world, pos);
	}

	@Redirect(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getRawBrightness(Lnet/minecraft/util/math/BlockPos;)I"))
	private int alacer$rawBrightness(World world, BlockPos pos) {
		return EntityShadows.rawBrightness(world, pos);
	}

	@Redirect(method = "renderShadowOnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBrightness(Lnet/minecraft/util/math/BlockPos;)F"))
	private float alacer$brightness(World world, BlockPos pos) {
		return EntityShadows.brightness(world, pos);
	}
}
