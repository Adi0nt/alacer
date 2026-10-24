package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.FastMipmaps;
import net.minecraft.client.render.texture.TextureAtlasSprite;

@Mixin(TextureAtlasSprite.class)
public class FastMipmapsSpriteMixin {
	@Unique private int alacer$mipmappedLevel = -1;

	@Inject(method = "applyMipmaps", at = @At("HEAD"), cancellable = true)
	private void alacer$checkAlreadyApplied(int mipLevels, CallbackInfo ci) {
		if (this.alacer$mipmappedLevel == mipLevels) {
			ci.cancel();
		}
	}

	@Inject(method = "applyMipmaps", at = @At("RETURN"))
	private void alacer$markApplied(int mipLevels, CallbackInfo ci) {
		this.alacer$mipmappedLevel = mipLevels;
	}

	@Inject(method = "clearFrames", at = @At("HEAD"))
	private void alacer$onClear(CallbackInfo ci) {
		this.alacer$mipmappedLevel = -1;
	}

	@WrapOperation(method = "applyMipmaps", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;generateMipmaps(II[[I)[[I"))
	private int[][] alacer$generateMipmaps(int mipLevels, int width, int[][] data, Operation<int[][]> original) {
		if (!Alacer.get().config().fastMipmaps) {
			return original.call(mipLevels, width, data);
		}
		return FastMipmaps.generateMipmaps(mipLevels, width, data, Alacer.get().config().verifyFastMipmaps, Alacer.get().config().mipmapCutouts);
	}
}
