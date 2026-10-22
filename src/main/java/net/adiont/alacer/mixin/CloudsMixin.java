package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.adiont.alacer.render.CloudCache;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.Vec3d;

/** Keep vanilla's state setup and both passes; replace only each pass's tile loop. */
@Mixin(WorldRenderer.class)
public class CloudsMixin {

	@Inject(method = "renderClouds", at = @At("HEAD"))
	private void alacer$cloudMode(float tickDelta, int pass, CallbackInfo ci) {
		CloudCache.onClouds(pass);
	}

	@Inject(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;scalef(FFF)V", shift = At.Shift.AFTER))
	private void alacer$prepare(float tickDelta, int pass, CallbackInfo ci, @Local Vec3d color) {
		CloudCache.prepare((WorldRenderer) (Object) this, tickDelta, pass, color);
	}

	// The first -3 initializes the outer tile loop, once per pass, after its color mask is set.
	@ModifyConstant(method = "renderFancyClouds", constant = @Constant(intValue = -3, ordinal = 0))
	private int alacer$tiles(int start) {
		return CloudCache.tileLoopStart(start);
	}

	@Inject(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
	private void alacer$verify(CallbackInfo ci, @Local BufferBuilder buffer) {
		CloudCache.verifyTile(buffer);
	}

	@Inject(method = "renderFancyClouds", at = @At("RETURN"))
	private void alacer$finish(CallbackInfo ci) {
		CloudCache.finish();
	}

	@Inject(method = "setWorld", at = @At("HEAD"))
	private void alacer$reset(CallbackInfo ci) {
		CloudCache.reset();
	}
}
