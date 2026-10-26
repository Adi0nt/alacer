package net.adiont.alacer.render;

/**
 * Accessor for precomputed bitmasks of layer block occupancy, built layer status, and
 * 36-bit section-to-section occlusion connectivity on {@link net.minecraft.client.render.world.CompiledChunk},
 * avoiding boolean array lookups and graph searches during terrain pass filtering and BFS visibility.
 */
public interface FastCompiledChunk {
	int alacer$layerMask();
	int alacer$builtLayerMask();
	long alacer$occlusionMask();
	boolean alacer$hasOcclusionMask();
	boolean alacer$hasBlockEntities();
}
