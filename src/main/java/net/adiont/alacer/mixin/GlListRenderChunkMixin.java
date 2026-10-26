package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.adiont.alacer.render.FastRenderChunk;
import net.minecraft.client.render.world.GlListRenderChunk;

@Mixin(GlListRenderChunk.class)
public abstract class GlListRenderChunkMixin implements FastRenderChunk {

	@Shadow
	private int list;

	@Override
	public int alacer$glList(int layerOrdinal) {
		return this.list + layerOrdinal;
	}
}
