package net.adiont.alacer.render;

/**
 * Tracks dirty queue epoch on {@link net.minecraft.client.render.world.RenderChunk} to avoid
 * hash lookups and duplicate traversals during terrain setup.
 */
public interface FastDirtyChunk {
	int alacer$dirtyEpoch();
	void alacer$setDirtyEpoch(int epoch);
}
