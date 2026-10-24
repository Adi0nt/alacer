package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;

import net.adiont.alacer.render.EntityChunkSlot;
import net.adiont.alacer.render.GlStateReads;
import net.adiont.alacer.render.WorkerBlockStateCache;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Adds the entity loop's cached world-chunk reference to each render chunk, and wraps the
 * modelview read in {@code glTransform}.
 */
@Mixin(RenderChunk.class)
public class RenderChunkMixin implements EntityChunkSlot {
	@WrapMethod(method = "compile")
	private void alacer$compileScope(float x, float y, float z, ChunkCompileTask task, Operation<Void> original) {
		int mark = WorkerBlockStateCache.beginCompile();
		try { original.call(x, y, z, task); }
		finally { WorkerBlockStateCache.endCompile(mark); }
	}

	@WrapOperation(method = "glTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;getFloat(ILjava/nio/FloatBuffer;)V"))
	private void alacer$chunkMatrix(int pname, FloatBuffer buffer, Operation<Void> original) {
		GlStateReads.chunkMatrix(pname, buffer, original);
	}

	@Unique
	private WorldChunk alacer$entityChunk;

	@Unique
	private long alacer$entityChunkColumn;

	@Unique
	private int alacer$entityChunkGeneration;

	@Override
	public WorldChunk alacer$getEntityChunk() {
		return this.alacer$entityChunk;
	}

	@Override
	public long alacer$getEntityChunkColumn() {
		return this.alacer$entityChunkColumn;
	}

	@Override
	public int alacer$getEntityChunkGeneration() {
		return this.alacer$entityChunkGeneration;
	}

	@Override
	public void alacer$setEntityChunk(WorldChunk chunk, long column, int generation) {
		this.alacer$entityChunk = chunk;
		this.alacer$entityChunkColumn = column;
		this.alacer$entityChunkGeneration = generation;
	}
}
