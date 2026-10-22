package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.Polygon;

@Mixin(Box.class)
public interface BoxAccessor {
	@Accessor("faces")
	Polygon[] alacer$faces();
}
