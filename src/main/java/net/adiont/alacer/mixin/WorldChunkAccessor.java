package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(WorldChunk.class)
public interface WorldChunkAccessor {
	@Invoker("populateHeightMapOnly")
	void alacer$populateHeightMapOnly();
}
