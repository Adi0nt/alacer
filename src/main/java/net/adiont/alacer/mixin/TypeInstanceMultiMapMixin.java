package net.adiont.alacer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.adiont.alacer.render.FastTypeMultiMap;
import net.minecraft.util.TypeInstanceMultiMap;

/**
 * Exposes the internal entity instance list of {@link TypeInstanceMultiMap} directly,
 * allowing iteration with zero iterator allocation.
 */
@Mixin(TypeInstanceMultiMap.class)
public abstract class TypeInstanceMultiMapMixin<T> implements FastTypeMultiMap<T> {

	@Shadow
	private List<T> instances;

	@Override
	public List<T> alacer$getInstances() {
		return this.instances;
	}
}
