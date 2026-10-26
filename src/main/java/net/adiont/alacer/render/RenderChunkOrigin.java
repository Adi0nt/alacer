package net.adiont.alacer.render;

/**
 * Direct primitive integer coordinates for chunk origin, eliminating repeated BlockPos
 * object lookups and method dispatch during terrain visibility traversal and draw loops.
 */
public interface RenderChunkOrigin {
	int alacer$originX();
	int alacer$originY();
	int alacer$originZ();
}
