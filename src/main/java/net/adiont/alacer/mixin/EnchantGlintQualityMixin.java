package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.entity.ItemRenderer;

/** Item models (world, hand and GUI) skip only their glint pass; the item itself is drawn unchanged. */
@Mixin(ItemRenderer.class)
public class EnchantGlintQualityMixin {
	@ModifyExpressionValue(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEnchantmentGlint()Z"))
	private boolean alacer$glint(boolean original) {
		return original && Alacer.get().config().enchantGlintQuality != 0;
	}
}
