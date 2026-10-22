package net.adiont.alacer.mixin;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.RenderPasses;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Startup-gated by profiler.chunkDetail. Submission wall time includes driver waits. */
@Mixin(WorldRenderer.class)
public class WorldRendererBackendTimingMixin {
	@Inject(method = "renderLastChunks", at = @At("HEAD"))
	private void alacer$submitStart(CallbackInfo ci) { Alacer.get().begin(Section.TERRAIN_SUBMIT); }
	@Inject(method = "renderLastChunks", at = @At("RETURN"))
	private void alacer$submitEnd(CallbackInfo ci) { Alacer.get().end(Section.TERRAIN_SUBMIT); }
	@Inject(method = "render", at = @At("RETURN"))
	private void alacer$chunks(BlockLayer layer, double delta, int pass, Entity camera, CallbackInfoReturnable<Integer> ci) {
		RenderPasses.chunks(layer, ci.getReturnValue());
	}
}
