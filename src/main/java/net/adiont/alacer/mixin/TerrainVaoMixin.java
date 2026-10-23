package net.adiont.alacer.mixin;

import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.VboRenderChunkList;

/**
 * Startup-gated terrain rendering pipeline replacement.
 * Eliminates per-chunk mixin callbacks and iterator allocations while preserving
 * exact vertex formats, draw order, draw calls, matrices, buffer bytes and state cleanup.
 */
@Mixin(VboRenderChunkList.class)
public abstract class TerrainVaoMixin extends RenderChunkList {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void alacer$render(BlockLayer layer, CallbackInfo ci) {
		if (ModernGl.pipelineActive()) {
			alacer$renderPipeline(layer);
			ci.cancel();
		}
	}

	@Unique
	private void alacer$renderPipeline(BlockLayer layer) {
		if (!this.ready) {
			return;
		}
		List<RenderChunk> list = this.chunks;
		int count = list.size();
		if (count == 0) {
			return;
		}
		int layerOrdinal = layer.ordinal();

		for (int i = 0; i < count; i++) {
			RenderChunk chunk = list.get(i);
			VertexBuffer buffer = chunk.getBuffer(layerOrdinal);

			GlStateManager.pushMatrix();
			this.glTranslatefOrigin(chunk);
			chunk.glMultMatrix();

			if (buffer.getClass() != VertexBuffer.class) {
				buffer.bind();
				alacer$prepareVanilla();
				buffer.draw(7);
			} else {
				ModernVertexBuffer access = (ModernVertexBuffer) buffer;
				int bufferId = access.alacer$bufferId();
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);

				GL11.glVertexPointer(3, GL11.GL_FLOAT, 28, 0L);
				GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12L);
				GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16L);
				GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
				GL11.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24L);
				GL13.glClientActiveTexture(GL13.GL_TEXTURE0);

				GL11.glDrawArrays(GL11.GL_QUADS, 0, access.alacer$vertexCount());
			}

			GlStateManager.popMatrix();
		}

		GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
		GlStateManager.clearColor();
		list.clear();
	}

	@Unique
	private static void alacer$prepareVanilla() {
		GL11.glVertexPointer(3, 5126, 28, 0L);
		GL11.glColorPointer(4, 5121, 28, 12L);
		GL11.glTexCoordPointer(2, 5126, 28, 16L);
		GLX.clientActiveTexture(GLX.GL_TEXTURE1);
		GL11.glTexCoordPointer(2, 5122, 28, 24L);
		GLX.clientActiveTexture(GLX.GL_TEXTURE0);
	}
}
