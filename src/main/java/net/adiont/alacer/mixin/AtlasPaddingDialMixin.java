package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.render.AtlasPadding;
import net.minecraft.client.render.texture.ClockSprite;
import net.minecraft.client.render.texture.CompassSprite;
import net.minecraft.client.render.texture.TextureAtlasSprite;

/** Clocks and compasses choose their own frames; their uploads update the border too. */
@Mixin({ClockSprite.class, CompassSprite.class})
public class AtlasPaddingDialMixin {
	@WrapOperation(method = "tick*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;upload([[IIIIIZZ)V"))
	private void alacer$upload(int[][] data, int width, int height, int x, int y, boolean blur, boolean clamp, Operation<Void> original) {
		AtlasPadding.upload((TextureAtlasSprite) (Object) this, data, width, height, x, y, blur, clamp, original);
	}
}
