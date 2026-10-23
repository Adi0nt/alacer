package net.adiont.alacer.render;

import net.adiont.alacer.profiler.UpdateStamp;

/** Added to RenderChunk only when {@code profiler.chunkLatency} applies its mixins. */
public interface ChunkUpdateStamp {
	UpdateStamp alacer$updateStamp();
}
