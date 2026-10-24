package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.TextureAnimations;
import net.minecraft.client.render.texture.ClockSprite;
import net.minecraft.client.render.texture.CompassSprite;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

/** A skipped sprite keeps its last uploaded frame. Clocks and compasses show game state, so they always tick. */
@Mixin(TextureAtlas.class)
public class TextureAnimationMixin {
	@WrapWithCondition(method = "bindAndTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureAtlasSprite;tick()V"))
	private boolean alacer$animate(TextureAtlasSprite sprite) {
		if (sprite instanceof ClockSprite || sprite instanceof CompassSprite) { return true; }
		AlacerConfig c = Alacer.get().config();
		return TextureAnimations.animate(sprite.getName(), c.waterAnimation, c.lavaAnimation, c.fireAnimation, c.portalAnimation, c.otherAnimations);
	}
}
