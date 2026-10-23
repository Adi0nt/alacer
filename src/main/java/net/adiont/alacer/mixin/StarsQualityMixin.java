package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.world.WorldRenderer;

/** Vanilla skips the star list when star brightness is zero; the rest of the sky is unchanged. */
@Mixin(WorldRenderer.class)
public class StarsQualityMixin {
	@ModifyExpressionValue(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getStarBrightness(F)F"))
	private float alacer$stars(float original) {
		return Alacer.get().config().starsQuality == 0 ? 0 : original;
	}
}
