package net.adiont.alacer.render;

import java.util.List;

/**
 * Accessor for the internal instance list of {@link net.minecraft.util.TypeInstanceMultiMap},
 * avoiding iterator allocations and virtual method calls during entity traversal.
 */
public interface FastTypeMultiMap<T> {
	List<T> alacer$getInstances();
}
