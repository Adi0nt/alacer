package net.adiont.alacer.mixin;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.ChunkSchedulingExperiments;
import net.adiont.alacer.render.ChunkVisibility;
import net.adiont.alacer.render.EntityShadows;
import net.adiont.alacer.render.ParticleLight;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

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

	// Vanilla cancels every queued rebuild that no worker has started yet.
	@Inject(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/ChunkRenderDispatcher;clear()V"))
	private void alacer$beforeQueueClear(CallbackInfo ci) {
		ChunkSchedulingExperiments.beforeClear(this.chunkRenderDispatcher);
	}

	@Inject(method = "setupRender", at = @At("RETURN"))
	private void alacer$endSetup(CallbackInfo ci) {
		Alacer.get().end(Section.TERRAIN_SETUP);
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
		ChunkSchedulingExperiments.reset();
		if (this.alacer$visibility != null) {
			this.alacer$visibility.reset();
		}
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
