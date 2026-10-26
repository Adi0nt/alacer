package net.adiont.alacer.mixin;

import java.util.List;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.adiont.alacer.render.TerrainShader;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.VboRenderChunkList;
import net.minecraft.util.math.BlockPos;

/**
 * Startup-gated terrain rendering pipeline replacement.
 * Evaluates chunk translation and boundary expansion on the GPU via TerrainShader,
 * eliminating 4,500+ glPushMatrix, glPopMatrix, and glMultMatrix calls per frame.
 * When decoupled attribute binding is active, replaces per-chunk pointer calls with
 * a single glBindVertexBuffer call per chunk.
 */
@Mixin(VboRenderChunkList.class)
public abstract class TerrainVaoMixin extends RenderChunkList {

	@Shadow
	private void prepareDrawState() {
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void alacer$render(BlockLayer layer, CallbackInfo ci) {
		if (TerrainShader.isActive()) {
			if (alacer$renderShaderPipeline(layer)) ci.cancel();
		}
	}

	@Unique
	private boolean alacer$renderShaderPipeline(BlockLayer layer) {
		if (!this.ready) {
			return true;
		}
		List<RenderChunk> list = this.chunks;
		int count = list.size();
		if (count == 0) {
			return true;
		}
		int layerOrdinal = layer.ordinal();
		// Conventional pointers cannot feed the generic shader. Retain the entire vanilla
		// layer for foreign buffers, before submitting any geometry or changing bindings.
		if (ModernGl.foreignBuffersPresent()) {
			for (int i = 0; i < count; i++) {
				if (list.get(i).getBuffer(layerOrdinal).getClass() != VertexBuffer.class) return false;
			}
		}

		RenderChunkListAccessor acc = (RenderChunkListAccessor) this;
		TerrainShader.begin(acc.alacer$getX(), acc.alacer$getY(), acc.alacer$getZ());

		try {
			if (TerrainShader.isAttribBindingActive()) {
				for (int i = 0; i < count; i++) {
					RenderChunk chunk = list.get(i);
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
							BlockPos origin = chunk.getOrigin();
							ox = origin.getX();
							oy = origin.getY();
							oz = origin.getZ();
						}
						TerrainShader.setOrigin(ox, oy, oz);
						ARBVertexAttribBinding.glBindVertexBuffer(0, mvb.alacer$bufferId(), 0L, 28);
						GL11.glDrawArrays(GL11.GL_QUADS, 0, vCount);
					}
				}
				ModernGl.recordDecoupledDraws(count);
			} else {
				for (int i = 0; i < count; i++) {
					RenderChunk chunk = list.get(i);
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
						BlockPos origin = chunk.getOrigin();
						ox = origin.getX();
						oy = origin.getY();
						oz = origin.getZ();
					}
					TerrainShader.setOrigin(ox, oy, oz);

					buffer.bind();
					this.prepareDrawState();
					buffer.draw(7);
				}
				ModernGl.recordFallbackDraws(count);
			}
		} finally {
			TerrainShader.end();
		}

		GlStateManager.clearColor();
		list.clear();
		return true;
	}
}
