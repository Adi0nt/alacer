package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.render.AtlasPadding;
import net.minecraft.client.render.texture.TextureAtlasSprite;

/** Animation frames, including prismarine's interpolated frames, update the sprite's border with its interior. */
@Mixin(TextureAtlasSprite.class)
public class AtlasPaddingSpriteMixin implements AtlasPadding.BufferedSprite {
	@Unique private int alacer$border;
	@Unique private int[][] alacer$borderBuffer;

	@WrapOperation(method = {"tick", "animatePrismarine"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;upload([[IIIIIZZ)V"))
	private void alacer$upload(int[][] data, int width, int height, int x, int y, boolean blur, boolean clamp, Operation<Void> original) {
		AtlasPadding.upload((TextureAtlasSprite) (Object) this, data, width, height, x, y, blur, clamp, original);
	}

	@Override public int alacer$border() { return this.alacer$border; }
	@Override public void alacer$setBorder(int border) { this.alacer$border = border; if (border == 0) { this.alacer$borderBuffer = null; } }
	@Override public int[][] alacer$borderBuffer() { return this.alacer$borderBuffer; }
	@Override public void alacer$setBorderBuffer(int[][] buffer) { this.alacer$borderBuffer = buffer; }
}
