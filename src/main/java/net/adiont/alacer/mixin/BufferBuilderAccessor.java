package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.render.vertex.BufferBuilder;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
	@Accessor("vertexCount")
	void alacer$vertexCount(int count);
}
