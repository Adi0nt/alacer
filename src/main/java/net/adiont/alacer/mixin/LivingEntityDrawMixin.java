package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.EntityDrawDetails;
import net.adiont.alacer.render.EntityDrawDetails.Stage;
import net.minecraft.client.render.entity.LivingEntityRenderer;

/** Optional timing hooks without primitive boxing; applied only with profiler.entityDetail. */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityDrawMixin {

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"))
	private void alacer$beginSetup(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SETUP);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;prepare(Lnet/minecraft/entity/living/LivingEntity;FFF)V"))
	private void alacer$prepare(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SETUP);
		EntityDrawDetails.begin(Stage.PREPARE);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;prepare(Lnet/minecraft/entity/living/LivingEntity;FFF)V", shift = At.Shift.AFTER))
	private void alacer$endPrepare(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.PREPARE);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V"))
	private void alacer$beginAnimation(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.ANIMATION);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V", shift = At.Shift.AFTER))
	private void alacer$endAnimation(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.ANIMATION);
	}

	@Inject(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V", at = @At("HEAD"))
	private void alacer$beginModel(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.MODEL);
	}

	@Inject(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V", at = @At("RETURN"))
	private void alacer$endModel(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.MODEL);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At("HEAD"))
	private void alacer$beginLayers(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.LAYERS);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At("RETURN"))
	private void alacer$endLayers(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.LAYERS);
	}

	@Inject(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
	private void alacer$beginModelDraw(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.MODEL_DRAW);
	}

	@Inject(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V", shift = At.Shift.AFTER))
	private void alacer$endModelDraw(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.MODEL_DRAW);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;F)Z"))
	private void alacer$beginOverlay(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.OVERLAY);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;F)Z", shift = At.Shift.AFTER))
	private void alacer$endOverlay(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.OVERLAY);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z"))
	private void alacer$beginLayerOverlay(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.OVERLAY);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", shift = At.Shift.AFTER))
	private void alacer$endLayerOverlay(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.OVERLAY);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;tearDownOverlayColor()V"))
	private void alacer$beginOverlayCleanup(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.OVERLAY);
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;tearDownOverlayColor()V", shift = At.Shift.AFTER))
	private void alacer$endOverlayCleanup(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.OVERLAY);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;tearDownOverlayColor()V"))
	private void alacer$beginLayerOverlayCleanup(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.OVERLAY);
	}

	@Inject(method = "renderLayers(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;tearDownOverlayColor()V", shift = At.Shift.AFTER))
	private void alacer$endLayerOverlayCleanup(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.OVERLAY);
	}

}
