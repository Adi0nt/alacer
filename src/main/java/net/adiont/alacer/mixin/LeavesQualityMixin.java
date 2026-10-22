package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.IndependentQuality;
import net.minecraft.client.render.world.WorldRenderer;

@Mixin(WorldRenderer.class)
public class LeavesQualityMixin {
	@ModifyExpressionValue(method = "reload()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fancyGraphics:Z"), require = 2)
	private boolean alacer$leaves(boolean original) {
		return IndependentQuality.fancy(Alacer.get().config().leavesQuality, original);
	}
}
