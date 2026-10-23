package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.CameraEffects;
import net.minecraft.client.render.GameRenderer;

/** The configured field of view, underwater narrowing and the death rotation are unchanged. */
@Mixin(GameRenderer.class)
public class CameraEffectsMixin {
	@ModifyExpressionValue(method = "tickFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getFovModifier()F"))
	private float alacer$fovEffects(float vanilla) {
		return CameraEffects.fovModifier(vanilla, Alacer.get().config().fovEffects);
	}
	@ModifyConstant(method = "applyHurtCam", constant = @Constant(floatValue = 14.0F))
	private float alacer$damageTilt(float vanilla) {
		return CameraEffects.hurtTilt(vanilla, Alacer.get().config().damageTilt);
	}
}
