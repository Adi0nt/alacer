package net.adiont.alacer.render;

/** Client-thread state owned by the vanilla VertexBuffer; disposed with that buffer. */
public interface ModernVertexBuffer {
	int alacer$bufferId();
	int alacer$vertexCount();
	default ResidentMeshArena.Slice alacer$slice() { return null; }
	default void alacer$setSlice(ResidentMeshArena.Slice slice) {}
}
