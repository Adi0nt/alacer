package net.adiont.alacer.mixin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import com.google.common.collect.Sets;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.ChunkSchedulingExperiments;
import net.adiont.alacer.render.ChunkVisibility;
import net.adiont.alacer.render.EntityCulling;
import net.adiont.alacer.render.EntityLighting;
import net.adiont.alacer.render.EntityRendererCache;
import net.adiont.alacer.render.EntityShadows;
import net.adiont.alacer.render.FastDirtyChunk;
import net.adiont.alacer.render.ParticleLight;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/** Terrain setup timing and the chunk visibility hooks. Logic lives in {@link ChunkVisibility}. */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Shadow private ChunkRenderDispatcher chunkRenderDispatcher;
	@Shadow private Set<RenderChunk> dirtyChunks;

	@Unique
	private ChunkVisibility alacer$visibility;

	@Unique
	private ChunkVisibility alacer$visibility() {
		if (this.alacer$visibility == null) {
			this.alacer$visibility = new ChunkVisibility((WorldRenderer) (Object) this);
		}
		return this.alacer$visibility;
	}

	@Inject(method = "setupRender", at = @At("HEAD"))
	private void alacer$beginSetup(CallbackInfo ci) {
		Alacer.get().begin(Section.TERRAIN_SETUP);
	}

	// Right after "viewChanged = viewChanged || <camera moved>", before vanilla's search.
	@Inject(method = "setupRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;viewChanged:Z", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
	private void alacer$visibilitySearch(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks, CallbackInfo ci) {
		alacer$visibility().beforeVanillaSearch(camera, tickDelta, culler, frame, loadChunks);
	}

	// "if (this.captureFrustum)" directly follows vanilla's search block.
	@Inject(method = "setupRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;captureFrustum:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
	private void alacer$afterVisibilitySearch(CallbackInfo ci) {
		alacer$visibility().afterVanillaSearch();
	}

	@Unique
	private Set<RenderChunk> alacer$swapSetA;
	@Unique
	private Set<RenderChunk> alacer$swapSetB;
	@Unique
	private int alacer$currentDirtyEpoch = 1;
	@Unique
	private int alacer$previousDirtyEpoch = 0;

	@Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Sets;newLinkedHashSet()Ljava/util/LinkedHashSet;"))
	private LinkedHashSet<RenderChunk> alacer$reusedDirtySet() {
		if (this.alacer$swapSetA == null) {
			this.alacer$swapSetA = Sets.newLinkedHashSet();
			this.alacer$swapSetB = Sets.newLinkedHashSet();
		}
		this.alacer$previousDirtyEpoch = this.alacer$currentDirtyEpoch;
		this.alacer$currentDirtyEpoch++;
		if (this.alacer$currentDirtyEpoch == Integer.MAX_VALUE) {
			this.alacer$currentDirtyEpoch = 1;
			this.alacer$previousDirtyEpoch = 0;
		}
		Set<RenderChunk> target = (this.dirtyChunks == this.alacer$swapSetA) ? this.alacer$swapSetB : this.alacer$swapSetA;
		target.clear();
		return (LinkedHashSet<RenderChunk>) target;
	}

	@Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
	private boolean alacer$fastDirtyContains(Set<RenderChunk> set, Object obj) {
		if (set.isEmpty()) {
			return false;
		}
		if (obj instanceof FastDirtyChunk fc) {
			return fc.alacer$dirtyEpoch() == this.alacer$previousDirtyEpoch;
		}
		return set.contains(obj);
	}

	@Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
	private boolean alacer$fastDirtyAdd(Set<RenderChunk> set, Object obj) {
		if (obj instanceof FastDirtyChunk fc) {
			fc.alacer$setDirtyEpoch(this.alacer$currentDirtyEpoch);
		}
		return set.add((RenderChunk) obj);
	}

	@Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Ljava/util/Set;addAll(Ljava/util/Collection;)Z"))
	private boolean alacer$fastDirtyAddAll(Set<RenderChunk> dest, Collection<RenderChunk> src) {
		if (src.isEmpty()) {
			return false;
		}
		int cur = this.alacer$currentDirtyEpoch;
		int prev = this.alacer$previousDirtyEpoch;
		for (RenderChunk chunk : src) {
			if (chunk instanceof FastDirtyChunk fc) {
				if (fc.alacer$dirtyEpoch() == prev) {
					fc.alacer$setDirtyEpoch(cur);
					dest.add(chunk);
				}
			} else {
				dest.add(chunk);
			}
		}
		return true;
	}

	// Vanilla cancels every queued rebuild that no worker has started yet.
	@Inject(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/ChunkRenderDispatcher;clear()V"))
	private void alacer$beforeQueueClear(CallbackInfo ci) {
		ChunkSchedulingExperiments.beforeClear(this.chunkRenderDispatcher);
	}

	@Inject(method = "setupRender", at = @At("RETURN"))
	private void alacer$endSetup(CallbackInfo ci) {
		Alacer.get().end(Section.TERRAIN_SETUP);
	}

	@Inject(method = "isNear", at = @At("HEAD"), cancellable = true)
	private void alacer$fastIsNear(BlockPos pos, RenderChunk chunk, CallbackInfoReturnable<Boolean> cir) {
		if (chunk instanceof RenderChunkOrigin ro) {
			int dx = pos.getX() - ro.alacer$originX();
			if (dx < -16 || dx > 16) {
				cir.setReturnValue(false);
				return;
			}
			int dy = pos.getY() - ro.alacer$originY();
			if (dy < -16 || dy > 16) {
				cir.setReturnValue(false);
				return;
			}
			int dz = pos.getZ() - ro.alacer$originZ();
			cir.setReturnValue(dz >= -16 && dz <= 16);
		}
	}

	@Inject(method = "markDirty", at = @At("HEAD"))
	private void alacer$regionDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
		if (this.alacer$visibility != null) {
			this.alacer$visibility.onRegionDirty(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}

	@Inject(method = "setWorld", at = @At("HEAD"))
	private void alacer$worldChanged(ClientWorld world, CallbackInfo ci) {
		EntityShadows.reset();
		ParticleLight.reset();
		EntityRendererCache.clear();
		EntityCulling.clear();
		ChunkSchedulingExperiments.reset();
		this.alacer$currentDirtyEpoch = 1;
		this.alacer$previousDirtyEpoch = 0;
		if (this.alacer$visibility != null) {
			this.alacer$visibility.reset();
		}
		if (this.alacer$swapSetA != null) {
			this.alacer$swapSetA.clear();
			this.alacer$swapSetB.clear();
		}
	}

	@Inject(method = "reload()V", at = @At("HEAD"))
	private void alacer$reloadReset(CallbackInfo ci) {
		ChunkSchedulingExperiments.reset();
		EntityRendererCache.clear();
		EntityCulling.clear();
		this.alacer$currentDirtyEpoch = 1;
		this.alacer$previousDirtyEpoch = 0;
		if (this.alacer$visibility != null) {
			this.alacer$visibility.reset();
		}
		if (this.alacer$swapSetA != null) {
			this.alacer$swapSetA.clear();
			this.alacer$swapSetB.clear();
		}
	}

	@Redirect(method = "renderEntities", at = @At(value = "NEW", target = "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/math/BlockPos;"))
	private BlockPos alacer$pooledEntityPos(Entity entity) {
		if (EntityLighting.isEnabled(entity)) {
			return EntityLighting.pooledEntityPos(entity);
		}
		return new BlockPos(entity);
	}

	// Its only caller passes the frame's chunk deadline; uploads and rebuild queueing both stop at it.
	@ModifyVariable(method = "compileChunksUntil", at = @At("HEAD"), argsOnly = true)
	private long alacer$chunkTimeFloor(long deadline) {
		return ChunkSchedulingExperiments.deadline(deadline);
	}

	@Inject(method = "compileChunksUntil", at = @At("HEAD"))
	private void alacer$beginUpload(CallbackInfo ci) {
		Alacer.get().begin(Section.CHUNK_UPLOAD);
	}

	@Inject(method = "compileChunksUntil", at = @At("RETURN"))
	private void alacer$endUpload(CallbackInfo ci) {
		ChunkSchedulingExperiments.afterCompile(this.chunkRenderDispatcher, this.dirtyChunks);
		Alacer.get().end(Section.CHUNK_UPLOAD);
	}
}
