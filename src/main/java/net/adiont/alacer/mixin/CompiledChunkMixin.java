package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.FastCompiledChunk;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.util.math.Direction;

/**
 * Tracks layer occupancy, built layer status, and 36-bit section-to-section occlusion connectivity
 * as precomputed bitmasks on compiled chunks.
 */
@Mixin(CompiledChunk.class)
public abstract class CompiledChunkMixin implements FastCompiledChunk {

	@Unique
	private static final Direction[] ALACER$DIRECTIONS = Direction.values();

	@Unique
	private int alacer$layerMask;

	@Unique
	private int alacer$builtLayerMask;

	@Unique
	private long alacer$occlusionMask;

	@Unique
	private boolean alacer$occlusionReady;

	@Unique
	private boolean alacer$hasBlockEntities;

	@Inject(method = "addRenderableBlockEntity", at = @At("HEAD"))
	private void alacer$recordBlockEntity(net.minecraft.block.entity.BlockEntity blockEntity, CallbackInfo ci) {
		this.alacer$hasBlockEntities = true;
	}

	@Inject(method = "setBlock", at = @At("HEAD"))
	private void alacer$recordBlock(BlockLayer layer, CallbackInfo ci) {
		this.alacer$layerMask |= (1 << layer.ordinal());
	}

	@Inject(method = "setLayer", at = @At("HEAD"))
	private void alacer$recordLayer(BlockLayer layer, CallbackInfo ci) {
		this.alacer$builtLayerMask |= (1 << layer.ordinal());
	}

	@Inject(method = "setOcclusionData", at = @At("RETURN"))
	private void alacer$computeOcclusion(CallbackInfo ci) {
		CompiledChunk self = (CompiledChunk) (Object) this;
		long mask = 0L;
		for (int f = 0; f < 6; f++) {
			for (int t = 0; t < 6; t++) {
				if (self.isVisible(ALACER$DIRECTIONS[f], ALACER$DIRECTIONS[t])) {
					mask |= (1L << (f * 6 + t));
				}
			}
		}
		this.alacer$occlusionMask = mask;
		this.alacer$occlusionReady = true;
	}

	@Inject(method = "hasBlock", at = @At("HEAD"), cancellable = true)
	private void alacer$fastHasBlock(BlockLayer layer, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue((this.alacer$layerMask & (1 << layer.ordinal())) == 0);
	}

	@Inject(method = "hasLayer", at = @At("HEAD"), cancellable = true)
	private void alacer$fastHasLayer(BlockLayer layer, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue((this.alacer$builtLayerMask & (1 << layer.ordinal())) != 0);
	}

	@Override
	public int alacer$layerMask() {
		return this.alacer$layerMask;
	}

	@Override
	public int alacer$builtLayerMask() {
		return this.alacer$builtLayerMask;
	}

	@Override
	public long alacer$occlusionMask() {
		return this.alacer$occlusionMask;
	}

	@Override
	public boolean alacer$hasOcclusionMask() {
		return this.alacer$occlusionReady || ((CompiledChunk) (Object) this) == CompiledChunk.UNCOMPILED;
	}

	@Override
	public boolean alacer$hasBlockEntities() {
		return this.alacer$hasBlockEntities;
	}
}
