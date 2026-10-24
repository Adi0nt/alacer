package net.adiont.alacer.mixin;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ChunkUpdateLatency;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;

/** No hooks installed without profiler.chunkLatency. */
@Mixin(WorldRenderer.class)
public class ChunkLatencyWorldRendererMixin {
	@Shadow private Set<RenderChunk> dirtyChunks;
	@Shadow private List<WorldRenderer.RenderChunkInfo> compiledChunks;

	@Inject(method = "setupRender", at = @At("RETURN"))
	private void alacer$eligible(CallbackInfo ci) {
		ChunkUpdateLatency.afterSetup(Alacer.get().profiler(), this.dirtyChunks, this.compiledChunks);
	}
}
