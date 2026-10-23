package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.adiont.alacer.render.AtlasPadding;
import net.minecraft.client.render.texture.Stitcher;

/** Places each sprite inside its border, and records the border on the sprite for its uploads. */
@Mixin(Stitcher.class)
public class AtlasPaddingStitcherMixin {
	private static final String INIT = "Lnet/minecraft/client/render/texture/TextureAtlasSprite;init(IIIIZ)V";

	@ModifyArg(method = "collectSprites", at = @At(value = "INVOKE", target = INIT), index = 2)
	private int alacer$x(int x, @Local Stitcher.Holder holder) {
		int border = ((AtlasPadding.Bordered) holder).alacer$border();
		((AtlasPadding.Bordered) holder.getSprite()).alacer$setBorder(border);
		return x + border;
	}

	@ModifyArg(method = "collectSprites", at = @At(value = "INVOKE", target = INIT), index = 3)
	private int alacer$y(int y, @Local Stitcher.Holder holder) {
		return y + ((AtlasPadding.Bordered) holder).alacer$border();
	}
}
