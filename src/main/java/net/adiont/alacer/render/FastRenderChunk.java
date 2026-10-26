package net.adiont.alacer.render;

import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunk;

/**
 * Accessor for cached layer occupancy, built layer status bitmasks, chunk origin, buffer access,
 * and display list indexing on {@link RenderChunk}, avoiding indirect
 * {@link net.minecraft.client.render.world.CompiledChunk} dereferences and virtual dispatch
 * during terrain rendering passes.
 */
public interface FastRenderChunk extends RenderChunkOrigin {

	int alacer$layerMask();

	int alacer$builtLayerMask();

	void alacer$setLayerMasks(int layerMask, int builtLayerMask);

	VertexBuffer alacer$getBuffer(int layerOrdinal);

	void alacer$glMultMatrix();

	RenderChunk alacer$renderChunk();

	default int alacer$glList(int layerOrdinal) {
		return -1;
	}
}
