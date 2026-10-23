package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;

/** Worn armor skips only its glint pass, following the same Enchantment glint setting as items. */
@Mixin(AbstractArmorLayer.class)
public class ArmorGlintQualityMixin {
	@ModifyExpressionValue(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEnchantments()Z"))
	private boolean alacer$glint(boolean original) {
		return original && Alacer.get().config().enchantGlintQuality != 0;
	}
}
