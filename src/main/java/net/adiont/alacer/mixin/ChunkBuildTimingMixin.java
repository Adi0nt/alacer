package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.adiont.alacer.profiler.DirtyQueueAge;
import net.adiont.alacer.render.ChunkDirtyAge;

import net.adiont.alacer.render.ChunkPipelineTimings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.RenderChunk;

/** No hooks installed without profiler.chunkDetail. Per-call locals remain thread-confined. */
@Mixin(RenderChunk.class)
public class ChunkBuildTimingMixin implements ChunkDirtyAge {
	@Unique private final DirtyQueueAge alacer$age = new DirtyQueueAge();

	@Override
	public DirtyQueueAge alacer$dirtyAge() { return this.alacer$age; }
	@WrapMethod(method = "compile")
	private void alacer$build(float x, float y, float z, ChunkCompileTask task, Operation<Void> original) {
		boolean client = Minecraft.getInstance().isOnSameThread();
		long start = System.nanoTime();
		try { original.call(x, y, z, task); }
		finally { ChunkPipelineTimings.build(client, false, System.nanoTime() - start); }
	}

	@WrapMethod(method = "rebuildTranslucent")
	private void alacer$resort(float x, float y, float z, ChunkCompileTask task, Operation<Void> original) {
		boolean client = Minecraft.getInstance().isOnSameThread();
		long start = System.nanoTime();
		try { original.call(x, y, z, task); }
		finally { ChunkPipelineTimings.build(client, true, System.nanoTime() - start); }
	}
}
