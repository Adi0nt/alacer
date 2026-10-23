package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.FogDistance;
import net.minecraft.client.render.GameRenderer;

/**
 * Only the overworld air branch's terrain fog (mode 0) changes: its 0.75 start factor and its fogEnd call, which is the
 * fourth fogEnd in setupFog after blindness (two) and the sky pass. The Nether's thick-fog override still follows it.
 */
@Mixin(GameRenderer.class)
public class FogDistanceMixin {
	@ModifyConstant(method = "setupFog", constant = @Constant(floatValue = 0.75F))
	private float alacer$fogStart(float vanilla) {
		return FogDistance.startFraction(vanilla, Alacer.get().config().fogStart);
	}
	@ModifyArg(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;fogEnd(F)V", ordinal = 3))
	private float alacer$fogEnd(float vanilla) {
		return FogDistance.end(vanilla, Alacer.get().config().fogStart);
	}
}
