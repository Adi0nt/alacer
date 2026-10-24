package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.Atmosphere;
import net.minecraft.client.render.world.WorldRenderer;

@Mixin(WorldRenderer.class)
public class AtmosphereQualityMixin {
	@ModifyExpressionValue(method = {"renderClouds(FI)V", "renderFancyClouds(FI)V"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;getCloudHeight()F"))
	private float alacer$cloudHeight(float original) {
		return Atmosphere.cloudHeight(original, Alacer.get().config().cloudHeightOffset);
	}
}
