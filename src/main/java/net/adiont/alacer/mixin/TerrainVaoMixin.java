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
import net.adiont.alacer.render.TerrainShader;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
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
			alacer$renderShaderPipeline(layer);
			ci.cancel();
		}
	}

	@Unique
	private void alacer$renderShaderPipeline(BlockLayer layer) {
		if (!this.ready) {
			return;
		}
		List<RenderChunk> list = this.chunks;
		int count = list.size();
		if (count == 0) {
			return;
		}
		int layerOrdinal = layer.ordinal();

		RenderChunkListAccessor acc = (RenderChunkListAccessor) this;
		TerrainShader.begin(acc.alacer$getX(), acc.alacer$getY(), acc.alacer$getZ());

		if (TerrainShader.isAttribBindingActive()) {
			int fast = 0;
			for (int i = 0; i < count; i++) {
				RenderChunk chunk = list.get(i);
				VertexBuffer buffer = chunk.getBuffer(layerOrdinal);

				BlockPos pos = chunk.getOrigin();
				TerrainShader.setOrigin(pos.getX(), pos.getY(), pos.getZ());

				if (((Object) buffer).getClass() == VertexBuffer.class) {
					ModernVertexBuffer mvb = (ModernVertexBuffer) buffer;
					ARBVertexAttribBinding.glBindVertexBuffer(0, mvb.alacer$bufferId(), 0L, 28);
					GL11.glDrawArrays(GL11.GL_QUADS, 0, mvb.alacer$vertexCount());
					fast++;
				} else {
					buffer.bind();
					this.prepareDrawState();
					buffer.draw(7);
				}
			}
			if (fast > 0) {
				ModernGl.recordDecoupledDraws(fast);
			}
			if (count > fast) {
				ModernGl.recordFallbackDraws(count - fast);
			}
		} else {
			for (int i = 0; i < count; i++) {
				RenderChunk chunk = list.get(i);
				VertexBuffer buffer = chunk.getBuffer(layerOrdinal);

				BlockPos pos = chunk.getOrigin();
				TerrainShader.setOrigin(pos.getX(), pos.getY(), pos.getZ());

				buffer.bind();
				this.prepareDrawState();
				buffer.draw(7);
			}
			ModernGl.recordFallbackDraws(count);
		}

		TerrainShader.end();

		GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
		GlStateManager.clearColor();
		list.clear();
	}
}
