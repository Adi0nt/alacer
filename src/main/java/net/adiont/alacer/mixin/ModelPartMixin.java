package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.adiont.alacer.render.ZeroTranslates;
import net.minecraft.client.render.model.ModelPart;

/**
 * Lets translations by zero in model parts' render methods be skipped. A condition rather than
 * a full wrapper, so the arguments are not boxed on every call.
 */
@Mixin(ModelPart.class)
public class ModelPartMixin {

	@WrapWithCondition(method = {"render", "renderForceTransform", "transform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V"))
	private boolean alacer$translate(float x, float y, float z) {
		return ZeroTranslates.shouldTranslate(x, y, z);
	}
}
