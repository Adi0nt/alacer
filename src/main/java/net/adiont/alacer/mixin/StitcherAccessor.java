package net.adiont.alacer.mixin;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.render.texture.Stitcher;

@Mixin(Stitcher.class)
public interface StitcherAccessor {
	@Accessor("sprites")
	Set<Stitcher.Holder> alacer$getHolders();

	@Accessor("regions")
	List<Stitcher.Region> alacer$getRegions();

	@Accessor("width")
	void alacer$setWidth(int width);

	@Accessor("height")
	void alacer$setHeight(int height);
}
