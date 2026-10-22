package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.ChunkPipelineTimings;
import net.adiont.alacer.render.ChunkPipelineTimings.Work;
import net.minecraft.client.render.world.ChunkBufferBuilders;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkDispatcherTimingMixin {
	@Inject(method = "rebuildSync", at = @At("HEAD"))
	private void alacer$syncStart(CallbackInfoReturnable<Boolean> ci) { Alacer.get().begin(Section.CHUNK_SYNC); }
	@Inject(method = "rebuildSync", at = @At("RETURN"))
	private void alacer$syncEnd(CallbackInfoReturnable<Boolean> ci) { ChunkPipelineTimings.end(Section.CHUNK_SYNC, Counter.CHUNK_SYNC_CALLS); }
	@Inject(method = "rebuildAsync", at = @At("HEAD"))
	private void alacer$enqueueStart(CallbackInfoReturnable<Boolean> ci) { Alacer.get().begin(Section.CHUNK_ENQUEUE); }
	@Inject(method = "rebuildAsync", at = @At("RETURN"))
	private void alacer$enqueueEnd(RenderChunk chunk, CallbackInfoReturnable<Boolean> ci) { ChunkPipelineTimings.enqueued(chunk, ci.getReturnValue()); }
	@Inject(method = "runTasksUntil", at = @At("HEAD"))
	private void alacer$drainStart(long deadline, CallbackInfoReturnable<Boolean> ci) {
		Alacer.get().begin(Section.CHUNK_DRAIN);
		ChunkPipelineTimings.deadline(deadline);
		if (net.adiont.alacer.benchmark.ChunkUploadGpu.ENABLED) { net.adiont.alacer.benchmark.ChunkUploadGpu.begin(); }
	}
	@Inject(method = "runTasksUntil", at = @At("RETURN"))
	private void alacer$drainEnd(CallbackInfoReturnable<Boolean> ci) {
		if (net.adiont.alacer.benchmark.ChunkUploadGpu.ENABLED) { net.adiont.alacer.benchmark.ChunkUploadGpu.end(ci.getReturnValue()); }
		ChunkPipelineTimings.end(Section.CHUNK_DRAIN, Counter.CHUNK_DRAIN_CALLS);
	}
	@Inject(method = "uploadGlList", at = @At("HEAD"))
	private void alacer$listStart(CallbackInfo ci) { Alacer.get().begin(Section.CHUNK_GL_LIST); }
	@Inject(method = "uploadGlList", at = @At("RETURN"))
	private void alacer$listEnd(CallbackInfo ci) { ChunkPipelineTimings.end(Section.CHUNK_GL_LIST, Counter.CHUNK_GL_LIST_CALLS); }
	@Inject(method = "uploadBuffer", at = @At("HEAD"))
	private void alacer$vboStart(CallbackInfo ci) { Alacer.get().begin(Section.CHUNK_VBO); }
	@Inject(method = "uploadBuffer", at = @At("RETURN"))
	private void alacer$vboEnd(CallbackInfo ci) { ChunkPipelineTimings.end(Section.CHUNK_VBO, Counter.CHUNK_VBO_CALLS); }

	@WrapMethod(method = "takeBuffer")
	private ChunkBufferBuilders alacer$bufferWait(Operation<ChunkBufferBuilders> original) throws InterruptedException {
		boolean client = net.minecraft.client.Minecraft.getInstance().isOnSameThread();
		long start = System.nanoTime();
		try { return original.call(); }
		finally { if (!client) { ChunkPipelineTimings.record(Work.BUFFER, System.nanoTime() - start); } }
	}
}
