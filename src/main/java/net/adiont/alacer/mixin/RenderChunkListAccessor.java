package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.render.world.RenderChunkList;

@Mixin(RenderChunkList.class)
public interface RenderChunkListAccessor {
	@Accessor("x")
	double alacer$getX();

	@Accessor("y")
	double alacer$getY();

	@Accessor("z")
	double alacer$getZ();
}
