package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.IndependentQuality;
import net.adiont.alacer.render.Atmosphere;
import net.minecraft.client.render.GameRenderer;

/** Only precipitation geometry changes. The server weather, sounds and splash-particle logic are untouched. */
@Mixin(GameRenderer.class)
public class WeatherQualityMixin {
	@ModifyExpressionValue(method = "renderSnowAndRain(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getRain(F)F"))
	private float alacer$weatherOpacity(float original) {
		return Atmosphere.brightness(original, Alacer.get().config().weatherOpacity);
	}

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void alacer$hideWeather(float tickDelta, CallbackInfo ci) {
		if (Alacer.get().config().weatherQuality == 3) { ci.cancel(); }
	}
	@ModifyExpressionValue(method = "renderSnowAndRain", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fancyGraphics:Z"))
	private boolean alacer$weather(boolean original) {
		return IndependentQuality.fancy(Alacer.get().config().weatherQuality, original);
	}
}
