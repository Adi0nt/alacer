package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.TextureFiltering;
import net.minecraft.client.render.texture.TextureAtlas;

/** TextureUtil.prepare recreates and binds the atlas, so each resource reload sets filtering parameters on the new texture. */
@Mixin(TextureAtlas.class)
public class TextureFilteringMixin {
	@Shadow private int maxMipLevel;

	@Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;prepare(IIII)V", shift = At.Shift.AFTER))
	private void alacer$applyTextureFiltering(CallbackInfo ci) {
		AlacerConfig c = Alacer.get().config();
		TextureFiltering.applyToBound(c.anisotropicFiltering, c.mipmapFiltering, c.lodBias, this.maxMipLevel > 0);
	}
}
