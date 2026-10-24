package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.chunk.WorldChunkSection;

@Mixin(WorldChunkSection.class)
public interface WorldChunkSectionAccessor {
	@Accessor("nonAirBlockCount")
	int alacer$getNonAirBlockCount();

	@Accessor("nonAirBlockCount")
	void alacer$setNonAirBlockCount(int count);

	@Accessor("randomTickingBlockCount")
	int alacer$getRandomTickingBlockCount();

	@Accessor("randomTickingBlockCount")
	void alacer$setRandomTickingBlockCount(int count);
}
