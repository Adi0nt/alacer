package net.adiont.alacer.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.AtlasMipmapAccess;
import net.adiont.alacer.render.FastMipmaps;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

@Mixin(TextureAtlas.class)
public class FastMipmapsAtlasMixin implements AtlasMipmapAccess {
	@Shadow private int maxMipLevel;
	@Shadow private Map<String, TextureAtlasSprite> sourcedSprites;
	@Shadow private TextureAtlasSprite missingSprite;

	@Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
	private void alacer$parallelMipmaps(CallbackInfo ci) {
		if (Alacer.get().config().fastMipmaps) {
			FastMipmaps.parallelApplyMipmaps(this.sourcedSprites.values(), this.missingSprite, this.maxMipLevel);
		}
	}

	@Override
	public int alacer$getStitchedMipLevel() {
		return this.maxMipLevel;
	}
}
