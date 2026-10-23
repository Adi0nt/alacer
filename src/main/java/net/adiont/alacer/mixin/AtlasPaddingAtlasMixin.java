package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.AtlasPadding;
import net.minecraft.client.render.texture.Stitcher;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

/** Chooses the border before sprites are registered, keeps stitching from failing on it, and uploads each border. */
@Mixin(TextureAtlas.class)
public class AtlasPaddingAtlasMixin {
	@Shadow private int maxMipLevel;

	@Inject(method = "loadAndStitch", at = @At("HEAD"))
	private void alacer$chooseBorder(CallbackInfo ci) {
		AtlasPadding.stitching = AtlasPadding.border(Alacer.get().config().anisotropicFiltering, this.maxMipLevel);
	}

	@WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Stitcher;stitch()V"))
	private void alacer$stitch(Stitcher stitcher, Operation<Void> original) {
		AtlasPadding.stitch(stitcher, original);
	}

	@WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;upload([[IIIIIZZ)V"))
	private void alacer$upload(int[][] data, int width, int height, int x, int y, boolean blur, boolean clamp, Operation<Void> original, @Local TextureAtlasSprite sprite) {
		AtlasPadding.upload(sprite, data, width, height, x, y, blur, clamp, original);
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"))
	private void alacer$stitched(CallbackInfo ci) {
		AtlasPadding.stitched(AtlasPadding.stitching);
		AtlasPadding.stitching = 0;
	}
}
