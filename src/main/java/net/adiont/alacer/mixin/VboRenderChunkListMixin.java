package net.adiont.alacer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.RenderChunkOrigin;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.VboRenderChunkList;

/**
 * Eliminates per-layer {@link java.util.Iterator} allocations by indexing {@code chunks} directly,
 * short-circuits empty vertex buffers and chunk lists, and accelerates chunk origin translations in
 * the fallback terrain rendering loop.
 */
@Mixin(value = VboRenderChunkList.class, priority = 1100)
public abstract class VboRenderChunkListMixin extends RenderChunkList {

	@Shadow
	private void prepareDrawState() {
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void alacer$fastRender(BlockLayer layer, CallbackInfo ci) {
		if (this.ready) {
			List<RenderChunk> list = this.chunks;
			int size = list.size();
			if (size == 0) {
				GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
				GlStateManager.clearColor();
				ci.cancel();
				return;
			}
			int layerOrdinal = layer.ordinal();
			RenderChunkListAccessor acc = (RenderChunkListAccessor) this;
			double camX = acc.alacer$getX();
			double camY = acc.alacer$getY();
			double camZ = acc.alacer$getZ();

			for (int i = 0; i < size; i++) {
				RenderChunk renderChunk = list.get(i);
				VertexBuffer vertexBuffer = renderChunk.getBuffer(layerOrdinal);
				if (vertexBuffer instanceof ModernVertexBuffer mvb && mvb.alacer$vertexCount() <= 0) {
					continue;
				}
				GlStateManager.pushMatrix();
				if (renderChunk instanceof RenderChunkOrigin ro) {
					GlStateManager.translatef(
						(float) (ro.alacer$originX() - camX),
						(float) (ro.alacer$originY() - camY),
						(float) (ro.alacer$originZ() - camZ)
					);
				} else {
					this.glTranslatefOrigin(renderChunk);
				}
				renderChunk.glMultMatrix();
				vertexBuffer.bind();
				this.prepareDrawState();
				vertexBuffer.draw(7);
				GlStateManager.popMatrix();
			}

			GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
			GlStateManager.clearColor();
			list.clear();
			ci.cancel();
		}
	}
}
