package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.Atmosphere;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.world.WorldRenderer;

@Mixin(GameRenderer.class)
public class FogQualityMixin {
	/** Skip the entire pass, including its paired profiler hooks and GL state changes. */
	@WrapWithCondition(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V"))
	private boolean alacer$sky(WorldRenderer renderer, float tickDelta, int pass) {
		return Alacer.get().config().skyQuality != 0;
	}

	// This constant belongs only to ordinary terrain distance fog. Blindness, sky, cloud,
	// water and lava branches do not use it; dimension-specific thick fog overrides it afterward.
	@ModifyConstant(method = "setupFog(IF)V", constant = @Constant(floatValue = 0.75F))
	private float alacer$fogStart(float original) {
		return Atmosphere.fogStart(original, Alacer.get().config().fogStartPercent);
	}
}
