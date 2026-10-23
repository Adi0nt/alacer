package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.profiler.UpdateStamp;
import net.adiont.alacer.render.ChunkUpdateLatency;
import net.adiont.alacer.render.ChunkUpdateStamp;
import net.minecraft.client.render.world.RenderChunk;

/** No hooks installed without profiler.chunkLatency. */
@Mixin(RenderChunk.class)
public class ChunkLatencyRenderChunkMixin implements ChunkUpdateStamp {
	@Unique private final UpdateStamp alacer$stamp = new UpdateStamp();

	@Override
	public UpdateStamp alacer$updateStamp() { return this.alacer$stamp; }

	@Inject(method = "setOrigin", at = @At("HEAD"))
	private void alacer$moved(CallbackInfo ci) { ChunkUpdateLatency.moved((RenderChunk) (Object) this); }

	@Inject(method = "delete", at = @At("HEAD"))
	private void alacer$deleted(CallbackInfo ci) { ChunkUpdateLatency.moved((RenderChunk) (Object) this); }
}
