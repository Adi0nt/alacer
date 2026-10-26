package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.RenderPasses;
import net.adiont.alacer.render.SwapOptimizer;
import net.minecraft.client.render.GameRenderer;

/**
 * World render, lightmap, crosshair pick, weather and hand timing. The lightmap and pick are
 * timed at their calls in {@code renderWorld}, because ticks call {@code pick} too.
 */
@Mixin(GameRenderer.class)
public class GameRendererPassesMixin {

	private static final String LIGHTMAP = "Lnet/minecraft/client/render/GameRenderer;updateLightMap(F)V";
	private static final String PICK = "Lnet/minecraft/client/render/GameRenderer;pick(F)V";

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void alacer$beginWorld(CallbackInfo ci) {
		RenderPasses.begin(Section.WORLD);
	}

	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void alacer$endWorld(CallbackInfo ci) {
		RenderPasses.end(Section.WORLD);
		SwapOptimizer.onPostRenderWorld();
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = LIGHTMAP))
	private void alacer$beginLightmap(CallbackInfo ci) {
		RenderPasses.begin(Section.LIGHTMAP);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = LIGHTMAP, shift = At.Shift.AFTER))
	private void alacer$endLightmap(CallbackInfo ci) {
		RenderPasses.end(Section.LIGHTMAP);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = PICK))
	private void alacer$beginPick(CallbackInfo ci) {
		RenderPasses.begin(Section.PICK);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = PICK, shift = At.Shift.AFTER))
	private void alacer$endPick(CallbackInfo ci) {
		RenderPasses.end(Section.PICK);
	}

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"))
	private void alacer$beginWeather(CallbackInfo ci) {
		RenderPasses.begin(Section.WEATHER);
	}

	@Inject(method = "renderSnowAndRain", at = @At("RETURN"))
	private void alacer$endWeather(CallbackInfo ci) {
		RenderPasses.end(Section.WEATHER);
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void alacer$beginHand(CallbackInfo ci) {
		RenderPasses.begin(Section.HAND);
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	private void alacer$endHand(CallbackInfo ci) {
		RenderPasses.end(Section.HAND);
	}
}
