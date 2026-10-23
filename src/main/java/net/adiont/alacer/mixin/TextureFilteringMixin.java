package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.TextureFiltering;
import net.minecraft.client.render.texture.TextureAtlas;

/** TextureUtil.prepare recreates and binds the atlas, so each resource reload sets the parameter on the new texture. */
@Mixin(TextureAtlas.class)
public class TextureFilteringMixin {
	@Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;prepare(IIII)V", shift = At.Shift.AFTER))
	private void alacer$anisotropy(CallbackInfo ci) {
		int choice = Alacer.get().config().anisotropicFiltering;
		if (choice != 0) { TextureFiltering.applyToBound(choice); }
	}
}
