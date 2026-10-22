package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ChunkPipelineTimings;
import net.minecraft.client.render.world.ChunkCompileTask;

@Mixin(ChunkCompileTask.class)
public class ChunkTaskTimingMixin {
	@Unique private long alacer$created;
	@Unique private long alacer$uploadStart;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void alacer$created(CallbackInfo ci) { this.alacer$created = System.nanoTime(); }

	@Inject(method = "setStatus", at = @At("HEAD"))
	private void alacer$status(ChunkCompileTask.Status status, CallbackInfo ci) {
		this.alacer$uploadStart = ChunkPipelineTimings.status(status, this.alacer$created, this.alacer$uploadStart);
	}
}
