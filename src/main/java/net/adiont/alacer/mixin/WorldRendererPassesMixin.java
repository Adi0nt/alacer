package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.RenderPasses;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;

/** Sky, cloud and terrain layer timing. Logic lives in {@link RenderPasses}. */
@Mixin(WorldRenderer.class)
public class WorldRendererPassesMixin {

	private static final String LAYER = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I";

	@Inject(method = LAYER, at = @At("HEAD"))
	private void alacer$beginLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
		RenderPasses.beginLayer(layer);
	}

	// After the draw rather than at RETURN, whose callback would box the returned chunk count.
	@Inject(method = LAYER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderLastChunks(Lnet/minecraft/client/render/block/BlockLayer;)V", shift = At.Shift.AFTER))
	private void alacer$endLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
		RenderPasses.endLayer(layer);
	}

	@Inject(method = "renderSky", at = @At("HEAD"))
	private void alacer$beginSky(CallbackInfo ci) {
		RenderPasses.begin(Section.SKY);
	}

	@Inject(method = "renderSky", at = @At("RETURN"))
	private void alacer$endSky(CallbackInfo ci) {
		RenderPasses.end(Section.SKY);
	}

	@Inject(method = "renderClouds", at = @At("HEAD"))
	private void alacer$beginClouds(CallbackInfo ci) {
		RenderPasses.begin(Section.CLOUDS);
	}

	@Inject(method = "renderClouds", at = @At("RETURN"))
	private void alacer$endClouds(CallbackInfo ci) {
		RenderPasses.end(Section.CLOUDS);
	}
}
