package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.RenderDistances;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityDistanceMixin {
	@ModifyExpressionValue(method = "render(Lnet/minecraft/block/entity/BlockEntity;FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/BlockEntity;getSquaredViewDistance()D"))
	private double alacer$blockEntityDistance(double squaredLimit) {
		return RenderDistances.blockEntitySquaredLimit(squaredLimit, Alacer.get().config().blockEntityDistance);
	}
}
