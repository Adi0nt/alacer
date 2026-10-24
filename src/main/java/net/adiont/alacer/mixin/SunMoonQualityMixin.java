package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.world.WorldRenderer;

/**
 * The sun and moon share one color call before their quads, drawn with additive blending (source alpha, one).
 * A zero alpha adds nothing to the sky; the rain factor used for stars is a separate local and is unchanged.
 */
@Mixin(WorldRenderer.class)
public class SunMoonQualityMixin {
	@ModifyArg(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V", ordinal = 0), index = 3)
	private float alacer$sunMoon(float alpha) {
		return Alacer.get().config().sunMoonQuality == 0 ? 0f : alpha;
	}
}
