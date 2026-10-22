package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.item.ItemStack;

/** Fast draws a dropped stack as vanilla draws a single item: one centered copy. */
@Mixin(ItemEntityRenderer.class)
public class DroppedItemsQualityMixin {
	@Inject(method = "scaledStackSize", at = @At("RETURN"), cancellable = true)
	private void alacer$copies(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (Alacer.get().config().droppedItemsQuality == 0) { cir.setReturnValue(1); }
	}
}
