package net.adiont.alacer.render;

import net.minecraft.world.chunk.WorldChunk;

/** Per-render-chunk storage for the entity loop's world-chunk cache, added to RenderChunk by a mixin. */
public interface EntityChunkSlot {

	WorldChunk alacer$getEntityChunk();

	/** Chunk column the cached reference was looked up for. */
	long alacer$getEntityChunkColumn();

	int alacer$getEntityChunkGeneration();

	void alacer$setEntityChunk(WorldChunk chunk, long column, int generation);
}
