package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ChunkUpdateLatency;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.RenderChunk;

/**
 * UPLOADING to DONE through setStatus happens only in a successful rebuild's completion callback, immediately before
 * it publishes the compiled chunk; cancellation writes DONE directly. No hooks installed without profiler.chunkLatency.
 */
@Mixin(ChunkCompileTask.class)
public class ChunkLatencyTaskMixin {
	@Shadow @Final private RenderChunk chunk;
	@Shadow @Final private ChunkCompileTask.Type type;
	@Shadow private ChunkCompileTask.Status status;

	@Inject(method = "setStatus", at = @At("HEAD"))
	private void alacer$published(ChunkCompileTask.Status next, CallbackInfo ci) {
		if (next == ChunkCompileTask.Status.DONE && this.status == ChunkCompileTask.Status.UPLOADING
			&& this.type == ChunkCompileTask.Type.REBUILD_CHUNK) {
			ChunkUpdateLatency.published(this.chunk);
		}
	}
}
