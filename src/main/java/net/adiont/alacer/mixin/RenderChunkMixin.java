package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;

import net.adiont.alacer.render.EntityChunkSlot;
import net.adiont.alacer.render.FastCompiledChunk;
import net.adiont.alacer.render.FastDirtyChunk;
import net.adiont.alacer.render.FastRenderChunk;
import net.adiont.alacer.render.GlStateReads;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.adiont.alacer.render.WorkerBlockStateCache;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Adds the entity loop's cached world-chunk reference to each render chunk, and wraps the
 * modelview read in {@code glTransform}. Exposes primitive chunk origin coordinates and tracks
 * dirty queue epoch. Implements {@link FastRenderChunk} for single-cycle layer mask evaluation.
 */
@Mixin(RenderChunk.class)
public abstract class RenderChunkMixin implements EntityChunkSlot, RenderChunkOrigin, FastDirtyChunk, FastRenderChunk {

	@Unique
	private static final BlockLayer[] ALACER$LAYERS = BlockLayer.values();

	@Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockLayer;values()[Lnet/minecraft/client/render/block/BlockLayer;"))
	private BlockLayer[] alacer$cachedBlockLayers() {
		return ALACER$LAYERS;
	}

	@WrapMethod(method = "compile")
	private void alacer$compileScope(float x, float y, float z, ChunkCompileTask task, Operation<Void> original) {
		int mark = WorkerBlockStateCache.beginCompile();
		try { original.call(x, y, z, task); }
		finally { WorkerBlockStateCache.endCompile(mark); }
	}

	@Redirect(method = "compile", at = @At(value = "NEW", target = "(Lnet/minecraft/util/math/Vec3i;)Lnet/minecraft/util/math/BlockPos;"))
	private BlockPos alacer$reuseTileEntityPos(Vec3i vec) {
		if (vec instanceof BlockPos bp) {
			return bp;
		}
		return new BlockPos(vec);
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

	@Unique
	private int alacer$entityStamp;

	@Override
	public int alacer$getEntityStamp() {
		return this.alacer$entityStamp;
	}

	@Override
	public void alacer$setEntityStamp(int stamp) {
		this.alacer$entityStamp = stamp;
	}

	@Unique
	private int alacer$originX;

	@Unique
	private int alacer$originY;

	@Unique
	private int alacer$originZ;

	@Inject(method = "setOrigin", at = @At("HEAD"))
	private void alacer$captureOrigin(BlockPos origin, CallbackInfo ci) {
		this.alacer$originX = origin.getX();
		this.alacer$originY = origin.getY();
		this.alacer$originZ = origin.getZ();
	}

	@Override
	public int alacer$originX() {
		return this.alacer$originX;
	}

	@Override
	public int alacer$originY() {
		return this.alacer$originY;
	}

	@Override
	public int alacer$originZ() {
		return this.alacer$originZ;
	}

	@Unique
	private int alacer$dirtyEpoch;

	@Inject(method = "setDirty", at = @At("HEAD"))
	private void alacer$onSetDirty(boolean dirty, CallbackInfo ci) {
		if (!dirty) {
			this.alacer$dirtyEpoch = 0;
		}
	}

	@Unique
	private boolean alacer$hasBlockEntities;

	@Override
	public boolean alacer$hasBlockEntities() {
		return this.alacer$hasBlockEntities;
	}

	@Override
	public void alacer$setHasBlockEntities(boolean has) {
		this.alacer$hasBlockEntities = has;
	}

	@Unique
	private int alacer$layerMask;

	@Unique
	private int alacer$builtLayerMask;

	@Override
	public int alacer$layerMask() {
		return this.alacer$layerMask;
	}

	@Override
	public int alacer$builtLayerMask() {
		return this.alacer$builtLayerMask;
	}

	@Shadow private VertexBuffer[] buffers;
	@Shadow public abstract void glMultMatrix();

	@Override
	public void alacer$setLayerMasks(int layerMask, int builtLayerMask) {
		this.alacer$layerMask = layerMask;
		this.alacer$builtLayerMask = builtLayerMask;
	}

	@Override
	public VertexBuffer alacer$getBuffer(int layerOrdinal) {
		return this.buffers[layerOrdinal];
	}

	@Override
	public void alacer$glMultMatrix() {
		this.glMultMatrix();
	}

	@Override
	public RenderChunk alacer$renderChunk() {
		return (RenderChunk) (Object) this;
	}

	@Inject(method = "setCompiledChunk", at = @At("RETURN"))
	private void alacer$onSetCompiledChunk(CompiledChunk cc, CallbackInfo ci) {
		if (cc instanceof FastCompiledChunk fcc) {
			this.alacer$hasBlockEntities = fcc.alacer$hasBlockEntities();
			this.alacer$layerMask = fcc.alacer$layerMask();
			this.alacer$builtLayerMask = fcc.alacer$builtLayerMask();
		} else {
			this.alacer$hasBlockEntities = false;
			this.alacer$layerMask = 0;
			this.alacer$builtLayerMask = 0;
		}
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void alacer$onReset(CallbackInfo ci) {
		this.alacer$dirtyEpoch = 0;
		this.alacer$hasBlockEntities = false;
		this.alacer$layerMask = 0;
		this.alacer$builtLayerMask = 0;
	}

	@Override
	public int alacer$dirtyEpoch() {
		return this.alacer$dirtyEpoch;
	}

	@Override
	public void alacer$setDirtyEpoch(int epoch) {
		this.alacer$dirtyEpoch = epoch;
	}
}
