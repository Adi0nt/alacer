package net.adiont.alacer.mixin;

import java.util.List;
import java.util.Set;

import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkStorage;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {

	@Accessor("dirtyChunks")
	Set<RenderChunk> alacer$getDirtyChunks();

	/** The chunks selected for rendering by the last visibility pass. */
	@Accessor("compiledChunks")
	List<WorldRenderer.RenderChunkInfo> alacer$getVisibleChunks();

	@Accessor("compiledChunks")
	void alacer$setVisibleChunks(List<WorldRenderer.RenderChunkInfo> chunks);

	@Accessor("chunkRenderDispatcher")
	ChunkRenderDispatcher alacer$getChunkRenderDispatcher();

	@Accessor("chunkStorage")
	RenderChunkStorage alacer$getChunkStorage();

	@Accessor("world")
	ClientWorld alacer$getWorld();

	@Accessor("ticks")
	int alacer$getTicks();

	@Accessor("lastViewDistance")
	int alacer$getLastViewDistance();

	@Accessor("viewChanged")
	boolean alacer$isViewChanged();

	@Accessor("viewChanged")
	void alacer$setViewChanged(boolean viewChanged);

	@Accessor("capturedFrustum")
	FrustumData alacer$getCapturedFrustum();

	@Invoker("getOpenChunkFaces")
	Set<Direction> alacer$getOpenChunkFaces(BlockPos pos);

	@Invoker("getLookVector")
	Vector3f alacer$getLookVector(Entity camera, double tickDelta);
}
