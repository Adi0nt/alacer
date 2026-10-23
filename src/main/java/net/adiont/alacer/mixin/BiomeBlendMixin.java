package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.BiomeBlend;
import net.minecraft.client.world.color.BiomeColors;
import net.minecraft.util.math.BlockPos;

/**
 * Widens or narrows vanilla's averaging square and its divisor together; the loop, color provider and integer
 * averaging are vanilla's. Runs on chunk builder threads. A change applied mid-build can mix one block's square and
 * divisor once; the menu rebuilds terrain after every change, which replaces that block.
 */
@Mixin(BiomeColors.class)
public class BiomeBlendMixin {
	@WrapOperation(method = "getColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;iterateRegionMutable(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Ljava/lang/Iterable;"))
	private static Iterable<BlockPos.Mutable> alacer$square(BlockPos from, BlockPos to, Operation<Iterable<BlockPos.Mutable>> original) {
		int extra = BiomeBlend.radius(Alacer.get().config().biomeBlend) - 1;
		if (extra == 0) { return original.call(from, to); }
		return original.call(from.add(-extra, 0, -extra), to.add(extra, 0, extra));
	}
	@ModifyConstant(method = "getColor", constant = @Constant(intValue = 9))
	private static int alacer$samples(int vanilla) {
		return BiomeBlend.samples(Alacer.get().config().biomeBlend);
	}
}
