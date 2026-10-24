package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.FastTextureUpload;
import net.adiont.alacer.render.TextureFiltering;
import net.minecraft.client.render.texture.TextureUtil;

@Mixin(TextureUtil.class)
public class FastTextureUploadMixin {
	@Inject(method = {"prepare(IIII)V", "bind(I)V", "deleteTextures(I)V"}, at = @At("HEAD"))
	private static void alacer$onTextureStateChange(CallbackInfo ci) {
		FastTextureUpload.invalidate();
	}

	@WrapWithCondition(method = "upload(I[IIIIIZZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;setTextureFilter(ZZ)V"))
	private static boolean alacer$filterCheck(boolean blur, boolean mipmap, @Local(argsOnly = true, ordinal = 0) int level) {
		if (!Alacer.get().config().fastMipmaps) {
			return true;
		}
		return level == 0 && FastTextureUpload.shouldSetFilter(blur, mipmap);
	}

	@WrapWithCondition(method = "upload(I[IIIIIZZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;setTextureClamp(Z)V"))
	private static boolean alacer$clampCheck(boolean clamp, @Local(argsOnly = true, ordinal = 0) int level) {
		if (!Alacer.get().config().fastMipmaps) {
			return true;
		}
		return level == 0 && FastTextureUpload.shouldSetClamp(clamp);
	}

	@WrapOperation(method = "prepare(IIII)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameterf(IIF)V", ordinal = 2))
	private static void alacer$setLodBias(int target, int pname, float param, Operation<Void> original) {
		float bias = TextureFiltering.lodBiasFraction(Alacer.get().config().lodBias);
		original.call(target, pname, bias);
	}

	@WrapOperation(method = "setTextureFilter", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V", ordinal = 2))
	private static void alacer$adjustMinFilter(int target, int pname, int param, Operation<Void> original) {
		if (pname == GL11.GL_TEXTURE_MIN_FILTER && Alacer.get().config().mipmapFiltering == TextureFiltering.TRILINEAR) {
			if (param == 9986) {
				param = 9987;
			}
		}
		original.call(target, pname, param);
	}
}
