package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.render.GlStateReads;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.player.PlayerEntity;

/**
 * The OpenGL state reads at the start of the world render, and the camera setup whose matrices
 * they read. Logic lives in {@link GlStateReads}.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

	private static final String WORLD_RENDER = "render(IFJ)V";

	@Inject(method = "setupCamera", at = @At("HEAD"))
	private void alacer$beginCameraSetup(CallbackInfo ci) {
		GlStateReads.beginCameraSetup();
	}

	@WrapOperation(method = "setupCamera", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
	private void alacer$perspective(float fovy, float aspect, float zNear, float zFar, Operation<Void> original) {
		GlStateReads.perspective(fovy, aspect, zNear, zFar, original);
	}

	@Inject(method = WORLD_RENDER, at = @At("RETURN"))
	private void alacer$endWorldRender(CallbackInfo ci) {
		GlStateReads.endWorldRender();
	}

	@WrapOperation(method = WORLD_RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setup(Lnet/minecraft/entity/living/player/PlayerEntity;Z)V"))
	private void alacer$cameraSetup(PlayerEntity player, boolean thirdPerson, Operation<Void> original) {
		GlStateReads.cameraSetup(player, thirdPerson, original);
	}

	@WrapOperation(method = WORLD_RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;"))
	private FrustumData alacer$frustum(Operation<FrustumData> original) {
		return GlStateReads.frustum(original);
	}

	@WrapOperation(method = WORLD_RENDER, at = @At(value = "NEW", target = "()Lnet/minecraft/client/render/FrustumCuller;"))
	private FrustumCuller alacer$frustumCuller(Operation<FrustumCuller> original) {
		return GlStateReads.frustumCuller(original);
	}
}
