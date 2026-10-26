package net.adiont.alacer.mixin;

import java.util.List;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.RenderChunkOrigin;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.GlListRenderChunk;
import net.minecraft.client.render.world.GlListRenderChunkList;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;

/**
 * Eliminates per-layer {@link java.util.Iterator} allocations by indexing {@code chunks} directly,
 * short-circuits empty chunk lists, and accelerates chunk origin translations in the display list
 * terrain rendering loop.
 */
@Mixin(value = GlListRenderChunkList.class, priority = 1100)
public abstract class GlListRenderChunkListMixin extends RenderChunkList {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void alacer$fastRender(BlockLayer layer, CallbackInfo ci) {
		if (this.ready) {
			List<RenderChunk> list = this.chunks;
			int size = list.size();
			if (size == 0) {
				GlStateManager.clearColor();
				ci.cancel();
				return;
			}
			RenderChunkListAccessor acc = (RenderChunkListAccessor) this;
			double camX = acc.alacer$getX();
			double camY = acc.alacer$getY();
			double camZ = acc.alacer$getZ();

			for (int i = 0; i < size; i++) {
				RenderChunk renderChunk = list.get(i);
				GlListRenderChunk glListRenderChunk = (GlListRenderChunk) renderChunk;
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
				GL11.glCallList(glListRenderChunk.getGlList(layer, glListRenderChunk.getCompiledChunk()));
				GlStateManager.popMatrix();
			}

			GlStateManager.clearColor();
			list.clear();
			ci.cancel();
		}
	}
}
