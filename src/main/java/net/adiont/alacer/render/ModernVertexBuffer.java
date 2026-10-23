package net.adiont.alacer.render;

/** Client-thread state owned by the vanilla VertexBuffer; disposed with that buffer. */
public interface ModernVertexBuffer {
	int alacer$bufferId();
	int alacer$vertexCount();
	TerrainVertexArray alacer$vertexArray();
}
