package net.adiont.alacer.mixin;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.FastCompiledChunk;
import net.adiont.alacer.render.FastRenderChunk;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.adiont.alacer.render.RenderPasses;
import net.adiont.alacer.render.TerrainLayerBatch;
import net.adiont.alacer.render.TerrainShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.client.render.world.GlListRenderChunk;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * High-performance terrain layer rendering override.
 * Replaces two-pass intermediate chunk list filtering with direct single-pass rendering,
 * eliminates thousands of getCompiledChunk calls per frame via cached RenderChunk bitmasks,
 * accelerates translucent sorting, and skips empty layer passes in zero iterations.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererPassesMixin {

	private static final String LAYER = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I";

	@Shadow private Minecraft minecraft;
	@Shadow private ChunkRenderDispatcher chunkRenderDispatcher;
	@Shadow private List<WorldRenderer.RenderChunkInfo> compiledChunks;
	@Shadow private RenderChunkList chunkList;
	@Shadow private double lastCameraX;
	@Shadow private double lastCameraY;
	@Shadow private double lastCameraZ;

	@Unique private int alacer$visibleLayerBits;
	@Unique private final TerrainLayerBatch alacer$batch = new TerrainLayerBatch();

	@Inject(method = LAYER, at = @At("HEAD"), cancellable = true)
	private void alacer$beginLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
		if (TerrainShader.isActive() && !ModernGl.foreignBuffersPresent()) {
			int count = alacer$renderShaderDirect(layer, camera, tickDelta);
			cir.setReturnValue(count);
			cir.cancel();
			return;
		}

		AlacerConfig config = Alacer.get() != null ? Alacer.get().config() : null;
		if (config != null && (config.fastTerrainPipeline || config.verifyTerrainPipeline)) {
			int count = alacer$renderStandardDirect(layer, camera, tickDelta);
			cir.setReturnValue(count);
			cir.cancel();
			return;
		}

		RenderPasses.beginLayer(layer);
	}

	@Unique
	private int alacer$renderShaderDirect(BlockLayer layer, Entity camera, double tickDelta) {
		RenderPasses.beginLayer(layer);

		int layerOrdinal = layer.ordinal();
		int layerBit = 1 << layerOrdinal;

		if (layer == BlockLayer.SOLID || !this.alacer$batch.isBuiltFor(this.compiledChunks)) {
			this.alacer$batch.build(this.compiledChunks);
			this.alacer$visibleLayerBits = this.alacer$batch.getVisibleLayerBits();
		}

		if ((this.alacer$visibleLayerBits & layerBit) == 0) {
			RenderPasses.chunks(layer, 0);
			RenderPasses.endLayer(layer);
			return 0;
		}

		TerrainLayerBatch.LayerQueue queue = this.alacer$batch.getQueue(layer);
		int queueSize = queue.size();
		if (queueSize == 0) {
			RenderPasses.chunks(layer, 0);
			RenderPasses.endLayer(layer);
			return 0;
		}

		Lighting.turnOff();

		double camX = camera.lastX + (camera.x - camera.lastX) * tickDelta;
		double camY = camera.lastY + (camera.y - camera.lastY) * tickDelta;
		double camZ = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;
		this.chunkList.setCameraPos(camX, camY, camZ);

		if (layer == BlockLayer.TRANSLUCENT) {
			double dx = camera.x - this.lastCameraX;
			double dy = camera.y - this.lastCameraY;
			double dz = camera.z - this.lastCameraZ;
			if (dx * dx + dy * dy + dz * dz > 1.0D) {
				this.lastCameraX = camera.x;
				this.lastCameraY = camera.y;
				this.lastCameraZ = camera.z;
				TerrainLayerBatch.LayerQueue resortQueue = this.alacer$batch.getTranslucentResortQueue();
				FastRenderChunk[] resortChunks = resortQueue.array();
				int resortLimit = Math.min(15, resortQueue.size());
				for (int i = 0; i < resortLimit; i++) {
					this.chunkRenderDispatcher.resortTransparency(resortChunks[i].alacer$renderChunk());
				}
			}
		}

		this.minecraft.gameRenderer.enableLightMap();

		boolean decoupled = TerrainShader.isAttribBindingActive();
		if (!decoupled) {
			GL11.glEnableClientState(32884);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
			GL11.glEnableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE1);
			GL11.glEnableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
			GL11.glEnableClientState(32886);
		}

		TerrainShader.begin(camX, camY, camZ);

		Alacer.get().begin(Section.TERRAIN_SUBMIT);
		int count = 0;
		boolean isTranslucent = (layer == BlockLayer.TRANSLUCENT);
		FastRenderChunk[] chunks = queue.array();

		try {
			if (decoupled) {
				if (isTranslucent) {
					for (int j = queueSize - 1; j >= 0; j--) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);
						ModernVertexBuffer mvb = (ModernVertexBuffer) buffer;
						int vCount = mvb.alacer$vertexCount();
						if (vCount > 0) {
							TerrainShader.setOrigin(chunk.alacer$originX(), chunk.alacer$originY(), chunk.alacer$originZ());
							ARBVertexAttribBinding.glBindVertexBuffer(0, mvb.alacer$bufferId(), 0L, 28);
							GL11.glDrawArrays(GL11.GL_QUADS, 0, vCount);
							count++;
						}
					}
				} else {
					for (int j = 0; j < queueSize; j++) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);
						ModernVertexBuffer mvb = (ModernVertexBuffer) buffer;
						int vCount = mvb.alacer$vertexCount();
						if (vCount > 0) {
							TerrainShader.setOrigin(chunk.alacer$originX(), chunk.alacer$originY(), chunk.alacer$originZ());
							ARBVertexAttribBinding.glBindVertexBuffer(0, mvb.alacer$bufferId(), 0L, 28);
							GL11.glDrawArrays(GL11.GL_QUADS, 0, vCount);
							count++;
						}
					}
				}
				ModernGl.recordDecoupledDraws(count);
			} else {
				if (isTranslucent) {
					for (int j = queueSize - 1; j >= 0; j--) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);
						TerrainShader.setOrigin(chunk.alacer$originX(), chunk.alacer$originY(), chunk.alacer$originZ());
						buffer.bind();
						GL11.glVertexPointer(3, 5126, 28, 0L);
						GL11.glColorPointer(4, 5121, 28, 12L);
						GL11.glTexCoordPointer(2, 5126, 28, 16L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE1);
						GL11.glTexCoordPointer(2, 5122, 28, 24L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE0);
						buffer.draw(7);
						count++;
					}
				} else {
					for (int j = 0; j < queueSize; j++) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);
						TerrainShader.setOrigin(chunk.alacer$originX(), chunk.alacer$originY(), chunk.alacer$originZ());
						buffer.bind();
						GL11.glVertexPointer(3, 5126, 28, 0L);
						GL11.glColorPointer(4, 5121, 28, 12L);
						GL11.glTexCoordPointer(2, 5126, 28, 16L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE1);
						GL11.glTexCoordPointer(2, 5122, 28, 24L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE0);
						buffer.draw(7);
						count++;
					}
				}
				ModernGl.recordFallbackDraws(count);
			}
		} finally {
			Alacer.get().end(Section.TERRAIN_SUBMIT);
			TerrainShader.end();
		}

		GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
		GlStateManager.clearColor();
		if (!decoupled) {
			GL11.glDisableClientState(32884);
			GL11.glDisableClientState(32886);
			GL11.glDisableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE1);
			GL11.glDisableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
		}

		this.minecraft.gameRenderer.disableLightMap();
		RenderPasses.chunks(layer, count);
		RenderPasses.endLayer(layer);
		return count;
	}

	@Unique
	private int alacer$renderStandardDirect(BlockLayer layer, Entity camera, double tickDelta) {
		RenderPasses.beginLayer(layer);

		int layerOrdinal = layer.ordinal();
		int layerBit = 1 << layerOrdinal;

		if (layer == BlockLayer.SOLID || !this.alacer$batch.isBuiltFor(this.compiledChunks)) {
			this.alacer$batch.build(this.compiledChunks);
			this.alacer$visibleLayerBits = this.alacer$batch.getVisibleLayerBits();
		}

		// Early skip: if solid pass saw zero chunks for this layer, bypass all work
		if ((this.alacer$visibleLayerBits & layerBit) == 0) {
			RenderPasses.chunks(layer, 0);
			RenderPasses.endLayer(layer);
			return 0;
		}

		TerrainLayerBatch.LayerQueue queue = this.alacer$batch.getQueue(layer);
		int queueSize = queue.size();
		if (queueSize == 0) {
			RenderPasses.chunks(layer, 0);
			RenderPasses.endLayer(layer);
			return 0;
		}

		Lighting.turnOff();

		double camX = camera.lastX + (camera.x - camera.lastX) * tickDelta;
		double camY = camera.lastY + (camera.y - camera.lastY) * tickDelta;
		double camZ = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;
		this.chunkList.setCameraPos(camX, camY, camZ);

		if (layer == BlockLayer.TRANSLUCENT) {
			double dx = camera.x - this.lastCameraX;
			double dy = camera.y - this.lastCameraY;
			double dz = camera.z - this.lastCameraZ;
			if (dx * dx + dy * dy + dz * dz > 1.0D) {
				this.lastCameraX = camera.x;
				this.lastCameraY = camera.y;
				this.lastCameraZ = camera.z;
				TerrainLayerBatch.LayerQueue resortQueue = this.alacer$batch.getTranslucentResortQueue();
				FastRenderChunk[] resortChunks = resortQueue.array();
				int resortLimit = Math.min(15, resortQueue.size());
				for (int i = 0; i < resortLimit; i++) {
					this.chunkRenderDispatcher.resortTransparency(resortChunks[i].alacer$renderChunk());
				}
			}
		}

		this.minecraft.gameRenderer.enableLightMap();
		boolean useVbo = GLX.useVbo();
		if (useVbo) {
			GL11.glEnableClientState(32884);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
			GL11.glEnableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE1);
			GL11.glEnableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
			GL11.glEnableClientState(32886);
		}

		Alacer.get().begin(Section.TERRAIN_SUBMIT);
		int count = 0;
		boolean isTranslucent = (layer == BlockLayer.TRANSLUCENT);
		FastRenderChunk[] chunks = queue.array();

		try {
			if (useVbo) {
				if (isTranslucent) {
					for (int j = queueSize - 1; j >= 0; j--) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);

						float ox = (float) (chunk.alacer$originX() - camX);
						float oy = (float) (chunk.alacer$originY() - camY);
						float oz = (float) (chunk.alacer$originZ() - camZ);

						GL11.glPushMatrix();
						GL11.glTranslatef(ox, oy, oz);
						chunk.alacer$glMultMatrix();
						buffer.bind();
						GL11.glVertexPointer(3, 5126, 28, 0L);
						GL11.glColorPointer(4, 5121, 28, 12L);
						GL11.glTexCoordPointer(2, 5126, 28, 16L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE1);
						GL11.glTexCoordPointer(2, 5122, 28, 24L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE0);
						buffer.draw(7);
						GL11.glPopMatrix();
						count++;
					}
				} else {
					for (int j = 0; j < queueSize; j++) {
						FastRenderChunk chunk = chunks[j];
						VertexBuffer buffer = chunk.alacer$getBuffer(layerOrdinal);

						float ox = (float) (chunk.alacer$originX() - camX);
						float oy = (float) (chunk.alacer$originY() - camY);
						float oz = (float) (chunk.alacer$originZ() - camZ);

						GL11.glPushMatrix();
						GL11.glTranslatef(ox, oy, oz);
						chunk.alacer$glMultMatrix();
						buffer.bind();
						GL11.glVertexPointer(3, 5126, 28, 0L);
						GL11.glColorPointer(4, 5121, 28, 12L);
						GL11.glTexCoordPointer(2, 5126, 28, 16L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE1);
						GL11.glTexCoordPointer(2, 5122, 28, 24L);
						GLX.clientActiveTexture(GLX.GL_TEXTURE0);
						buffer.draw(7);
						GL11.glPopMatrix();
						count++;
					}
				}
			} else {
				if (isTranslucent) {
					for (int j = queueSize - 1; j >= 0; j--) {
						FastRenderChunk chunk = chunks[j];
						int glList = chunk.alacer$glList(layerOrdinal);
						if (glList <= 0) {
							continue;
						}

						float ox = (float) (chunk.alacer$originX() - camX);
						float oy = (float) (chunk.alacer$originY() - camY);
						float oz = (float) (chunk.alacer$originZ() - camZ);

						GL11.glPushMatrix();
						GL11.glTranslatef(ox, oy, oz);
						GL11.glCallList(glList);
						GL11.glPopMatrix();
						count++;
					}
				} else {
					for (int j = 0; j < queueSize; j++) {
						FastRenderChunk chunk = chunks[j];
						int glList = chunk.alacer$glList(layerOrdinal);
						if (glList <= 0) {
							continue;
						}

						float ox = (float) (chunk.alacer$originX() - camX);
						float oy = (float) (chunk.alacer$originY() - camY);
						float oz = (float) (chunk.alacer$originZ() - camZ);

						GL11.glPushMatrix();
						GL11.glTranslatef(ox, oy, oz);
						GL11.glCallList(glList);
						GL11.glPopMatrix();
						count++;
					}
				}
			}
		} finally {
			Alacer.get().end(Section.TERRAIN_SUBMIT);
		}

		if (useVbo) {
			GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
		}
		GlStateManager.clearColor();
		if (useVbo) {
			GL11.glDisableClientState(32884);
			GL11.glDisableClientState(32886);
			GL11.glDisableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE1);
			GL11.glDisableClientState(32888);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
		}

		this.minecraft.gameRenderer.disableLightMap();
		RenderPasses.chunks(layer, count);
		RenderPasses.endLayer(layer);

		AlacerConfig config = Alacer.get() != null ? Alacer.get().config() : null;
		if (config != null && config.verifyTerrainPipeline) {
			int vanillaCount = 0;
			int total = this.compiledChunks.size();
			for (int i = 0; i < total; i++) {
				if (!this.compiledChunks.get(i).chunk.getCompiledChunk().hasBlock(layer)) {
					vanillaCount++;
				}
			}
			if (count != vanillaCount) {
				Alacer.LOGGER.warn("Terrain pipeline verification mismatch on {}: fast count {}, vanilla count {}",
					layer, count, vanillaCount);
			}
		}

		return count;
	}

	@Redirect(method = LAYER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
	private Iterator<?> alacer$fastTranslucentSort(List<WorldRenderer.RenderChunkInfo> list) {
		int size = list.size();
		int count = 0;
		int translucentMask = 1 << BlockLayer.TRANSLUCENT.ordinal();
		for (int i = 0; i < size; i++) {
			WorldRenderer.RenderChunkInfo info = list.get(i);
			RenderChunk chunk = info.chunk;
			boolean hasTranslucent = (chunk instanceof FastRenderChunk frc)
				? (frc.alacer$builtLayerMask() & translucentMask) != 0
				: (chunk.getCompiledChunk() instanceof FastCompiledChunk fcc)
					? (fcc.alacer$builtLayerMask() & translucentMask) != 0
					: chunk.getCompiledChunk().hasLayer(BlockLayer.TRANSLUCENT);
			if (hasTranslucent) {
				this.chunkRenderDispatcher.resortTransparency(info.chunk);
				if (++count == 15) {
					break;
				}
			}
		}
		return Collections.emptyIterator();
	}

	@Redirect(method = LAYER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/CompiledChunk;hasBlock(Lnet/minecraft/client/render/block/BlockLayer;)Z"))
	private boolean alacer$fastHasBlock(CompiledChunk compiled, BlockLayer layer) {
		if (compiled instanceof FastCompiledChunk fcc) {
			return (fcc.alacer$layerMask() & (1 << layer.ordinal())) == 0;
		}
		if (compiled.isEmpty()) {
			return true;
		}
		return compiled.hasBlock(layer);
	}

	@Inject(method = LAYER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderLastChunks(Lnet/minecraft/client/render/block/BlockLayer;)V", shift = At.Shift.AFTER))
	private void alacer$endLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
		RenderPasses.endLayer(layer);
	}

	@Redirect(method = "renderLastChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexFormat;getElements()Ljava/util/List;"))
	private List<VertexFormatElement> alacer$fastDisableClientStates(VertexFormat format) {
		GL11.glDisableClientState(32884);
		GL11.glDisableClientState(32886);
		GlStateManager.clearColor();
		GL11.glDisableClientState(32888);
		GLX.clientActiveTexture(GLX.GL_TEXTURE1);
		GL11.glDisableClientState(32888);
		GLX.clientActiveTexture(GLX.GL_TEXTURE0);
		return Collections.emptyList();
	}

	@Inject(method = "renderSky", at = @At("HEAD"))
	private void alacer$beginSky(CallbackInfo ci) {
		RenderPasses.begin(Section.SKY);
	}

	@Inject(method = "renderSky", at = @At("RETURN"))
	private void alacer$endSky(CallbackInfo ci) {
		RenderPasses.end(Section.SKY);
	}

	@Inject(method = "renderClouds", at = @At("HEAD"))
	private void alacer$beginClouds(CallbackInfo ci) {
		RenderPasses.begin(Section.CLOUDS);
	}

	@Inject(method = "renderClouds", at = @At("RETURN"))
	private void alacer$endClouds(CallbackInfo ci) {
		RenderPasses.end(Section.CLOUDS);
	}
}
