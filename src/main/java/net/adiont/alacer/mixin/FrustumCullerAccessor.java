package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;

/** The planes and camera offsets that {@code FrustumCuller.isVisible} tests boxes against. */
@Mixin(FrustumCuller.class)
public interface FrustumCullerAccessor {
	@Accessor("frustum")
	FrustumData alacer$getFrustum();

	@Accessor("offsetX")
	double alacer$getOffsetX();

	@Accessor("offsetY")
	double alacer$getOffsetY();

	@Accessor("offsetZ")
	double alacer$getOffsetZ();
}
