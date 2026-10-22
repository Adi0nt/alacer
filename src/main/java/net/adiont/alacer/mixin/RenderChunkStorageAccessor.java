package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.world.RenderChunkStorage;

@Mixin(RenderChunkStorage.class)
public interface RenderChunkStorageAccessor {

	@Accessor("sizeX")
	int alacer$getSizeX();

	@Accessor("sizeY")
	int alacer$getSizeY();

	@Accessor("sizeZ")
	int alacer$getSizeZ();
}
