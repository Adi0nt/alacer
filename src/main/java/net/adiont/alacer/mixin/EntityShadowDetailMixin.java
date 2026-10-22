package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.EntityDrawDetails;
import net.adiont.alacer.render.EntityDrawDetails.Stage;
import net.minecraft.client.render.entity.EntityRenderer;

/** Optional timing hooks without primitive boxing; applied only with profiler.entityDetail. */
@Mixin(EntityRenderer.class)
public class EntityShadowDetailMixin {

	@Inject(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"))
	private void alacer$beginBlockState(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;", shift = At.Shift.AFTER))
	private void alacer$endBlockState(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getRawBrightness(Lnet/minecraft/util/math/BlockPos;)I"))
	private void alacer$beginRawBrightness(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getRawBrightness(Lnet/minecraft/util/math/BlockPos;)I", shift = At.Shift.AFTER))
	private void alacer$endRawBrightness(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadowOnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBrightness(Lnet/minecraft/util/math/BlockPos;)F"))
	private void alacer$beginBrightness(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadowOnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBrightness(Lnet/minecraft/util/math/BlockPos;)F", shift = At.Shift.AFTER))
	private void alacer$endBrightness(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SHADOW_QUERY);
	}

	@Inject(method = "renderShadow", at = @At("HEAD"))
	private void alacer$beginShadow(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SHADOW);
	}

	@Inject(method = "renderShadow", at = @At("RETURN"))
	private void alacer$endShadow(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SHADOW);
	}

	@Inject(method = "renderShadowOnBlock", at = @At("HEAD"))
	private void alacer$beginShadowBlock(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.SHADOW_BLOCK);
	}

	@Inject(method = "renderShadowOnBlock", at = @At("RETURN"))
	private void alacer$endShadowBlock(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.SHADOW_BLOCK);
	}

	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderNameTag(Lnet/minecraft/entity/Entity;DDD)V"))
	private void alacer$beginNameTag(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.NAME_TAG);
	}

	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderNameTag(Lnet/minecraft/entity/Entity;DDD)V", shift = At.Shift.AFTER))
	private void alacer$endNameTag(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.NAME_TAG);
	}

}
