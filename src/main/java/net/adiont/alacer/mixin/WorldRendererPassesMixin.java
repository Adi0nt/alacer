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
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.FastCompiledChunk;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.adiont.alacer.render.RenderPasses;
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
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/** Sky, cloud and terrain layer timing and fast layer filtering. Logic lives in {@link RenderPasses}. */
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

	@Inject(method = LAYER, at = @At("HEAD"), cancellable = true)
	private void alacer$beginLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
		if (TerrainShader.isActive() && !ModernGl.foreignBuffersPresent()) {
			int count = alacer$renderShaderDirect(layer, camera);
			cir.setReturnValue(count);
			cir.cancel();
			return;
		}
		RenderPasses.beginLayer(layer);
	}

	@Unique
	private int alacer$renderShaderDirect(BlockLayer layer, Entity camera) {
		RenderPasses.beginLayer(layer);

		Lighting.turnOff();

		if (layer == BlockLayer.TRANSLUCENT) {
			double dx = camera.x - this.lastCameraX;
			double dy = camera.y - this.lastCameraY;
			double dz = camera.z - this.lastCameraZ;
			if (dx * dx + dy * dy + dz * dz > 1.0D) {
				this.lastCameraX = camera.x;
				this.lastCameraY = camera.y;
				this.lastCameraZ = camera.z;
				int resortCount = 0;
				int size = this.compiledChunks.size();
				for (int i = 0; i < size; i++) {
					WorldRenderer.RenderChunkInfo info = this.compiledChunks.get(i);
					CompiledChunk cc = info.chunk.getCompiledChunk();
					boolean hasTranslucent = (cc instanceof FastCompiledChunk fcc)
						? (fcc.alacer$builtLayerMask() & (1 << BlockLayer.TRANSLUCENT.ordinal())) != 0
						: cc.hasLayer(BlockLayer.TRANSLUCENT);
					if (hasTranslucent) {
						this.chunkRenderDispatcher.resortTransparency(info.chunk);
						if (++resortCount == 15) {
							break;
						}
					}
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

		RenderChunkListAccessor acc = (RenderChunkListAccessor) this.chunkList;
		TerrainShader.begin(acc.alacer$getX(), acc.alacer$getY(), acc.alacer$getZ());

		Alacer.get().begin(Section.TERRAIN_SUBMIT);
		int count = 0;
		int layerOrdinal = layer.ordinal();
		int layerBit = 1 << layerOrdinal;
		boolean isTranslucent = (layer == BlockLayer.TRANSLUCENT);
		int size = this.compiledChunks.size();
		int startIndex = isTranslucent ? size - 1 : 0;
		int endIndex = isTranslucent ? -1 : size;
		int step = isTranslucent ? -1 : 1;

		try {
			if (decoupled) {
				for (int j = startIndex; j != endIndex; j += step) {
					WorldRenderer.RenderChunkInfo info = this.compiledChunks.get(j);
					RenderChunk chunk = info.chunk;
					CompiledChunk cc = chunk.getCompiledChunk();
					if (cc instanceof FastCompiledChunk fcc) {
						if ((fcc.alacer$layerMask() & layerBit) == 0) continue;
					} else if (cc.hasBlock(layer)) {
						continue;
					}
					VertexBuffer buffer = chunk.getBuffer(layerOrdinal);
					ModernVertexBuffer mvb = (ModernVertexBuffer) buffer;
					int vCount = mvb.alacer$vertexCount();
					if (vCount > 0) {
						int ox, oy, oz;
						if (chunk instanceof RenderChunkOrigin ro) {
							ox = ro.alacer$originX();
							oy = ro.alacer$originY();
							oz = ro.alacer$originZ();
						} else {
							BlockPos pos = chunk.getOrigin();
							ox = pos.getX();
							oy = pos.getY();
							oz = pos.getZ();
						}
						TerrainShader.setOrigin(ox, oy, oz);
						ARBVertexAttribBinding.glBindVertexBuffer(0, mvb.alacer$bufferId(), 0L, 28);
						GL11.glDrawArrays(GL11.GL_QUADS, 0, vCount);
						count++;
					}
				}
				ModernGl.recordDecoupledDraws(count);
			} else {
				for (int j = startIndex; j != endIndex; j += step) {
					WorldRenderer.RenderChunkInfo info = this.compiledChunks.get(j);
					RenderChunk chunk = info.chunk;
					CompiledChunk cc = chunk.getCompiledChunk();
					if (cc instanceof FastCompiledChunk fcc) {
						if ((fcc.alacer$layerMask() & layerBit) == 0) continue;
					} else if (cc.hasBlock(layer)) {
						continue;
					}
					VertexBuffer buffer = chunk.getBuffer(layerOrdinal);
					if (buffer instanceof ModernVertexBuffer mvb && mvb.alacer$vertexCount() <= 0) {
						continue;
					}
					int ox, oy, oz;
					if (chunk instanceof RenderChunkOrigin ro) {
						ox = ro.alacer$originX();
						oy = ro.alacer$originY();
						oz = ro.alacer$originZ();
					} else {
						BlockPos pos = chunk.getOrigin();
						ox = pos.getX();
						oy = pos.getY();
						oz = pos.getZ();
					}
					TerrainShader.setOrigin(ox, oy, oz);
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

	@Redirect(method = LAYER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
	private Iterator<?> alacer$fastTranslucentSort(List<WorldRenderer.RenderChunkInfo> list) {
		int size = list.size();
		int count = 0;
		for (int i = 0; i < size; i++) {
			WorldRenderer.RenderChunkInfo info = list.get(i);
			CompiledChunk cc = info.chunk.getCompiledChunk();
			boolean hasTranslucent = (cc instanceof FastCompiledChunk fcc)
				? (fcc.alacer$builtLayerMask() & (1 << BlockLayer.TRANSLUCENT.ordinal())) != 0
				: cc.hasLayer(BlockLayer.TRANSLUCENT);
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

	// After the draw rather than at RETURN, whose callback would box the returned chunk count.
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
