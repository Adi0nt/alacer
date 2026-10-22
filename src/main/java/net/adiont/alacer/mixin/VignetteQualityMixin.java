package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.IndependentQuality;
import net.minecraft.client.gui.GameGui;

/** The vignette is the HUD's only Graphics-dependent pass; vanilla's Fast branch keeps its blend state. */
@Mixin(GameGui.class)
public class VignetteQualityMixin {
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isFancyGraphicsEnabled()Z"))
	private boolean alacer$vignette(boolean original) {
		return IndependentQuality.fancy(Alacer.get().config().vignetteQuality, original);
	}
}
