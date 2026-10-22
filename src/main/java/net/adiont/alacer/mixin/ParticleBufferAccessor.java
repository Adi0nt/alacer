package net.adiont.alacer.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;

/** The {@code BufferBuilder} state that the particle vertex writer reads (render.fastParticleVertices). */
@Mixin(BufferBuilder.class)
public interface ParticleBufferAccessor {
	@Accessor("byteBuffer")
	ByteBuffer alacer$particleBytes();

	@Accessor("vertexCount")
	int alacer$particleVertexCount();

	@Accessor("elementIndex")
	int alacer$particleElementIndex();

	@Accessor("format")
	VertexFormat alacer$particleFormat();

	@Accessor("uncolored")
	boolean alacer$particleUncolored();

	@Accessor("offsetX")
	double alacer$particleOffsetX();

	@Accessor("offsetY")
	double alacer$particleOffsetY();

	@Accessor("offsetZ")
	double alacer$particleOffsetZ();
}
