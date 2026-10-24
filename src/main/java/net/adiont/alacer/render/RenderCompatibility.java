package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Conservative startup check for experiments that depend on unchanged vanilla method bodies. */
final class RenderCompatibility {
	private static int buffers, neighbours, displayTicks;
	private RenderCompatibility() { }
	static boolean displayTicks() {
		if (displayTicks == 0) displayTicks = unchanged("net.minecraft.block.Block", net.minecraft.block.Blocks.AIR.getClass().getName(), "net.minecraft.client.world.ClientWorld") ? 1 : -1;
		return displayTicks > 0;
	}
	static boolean buffers() {
		if (buffers == 0) { buffers = unchanged("net.minecraft.client.render.vertex.VertexBuffer", "net.minecraft.client.render.world.VboRenderChunkList") ? 1 : -1; }
		return buffers > 0;
	}
	static boolean neighbours() {
		if (neighbours == 0) { neighbours = unchanged("net.minecraft.client.render.world.RenderChunkStorage", "net.minecraft.client.render.world.RenderChunk") ? 1 : -1; }
		return neighbours > 0;
	}
	private static boolean unchanged(String... types) {
		try {
			for (String type : types) {
				// The public Mixin query reports applied mixins only. Resolve every target first:
				// an early buffer constructor must not cache success for an as-yet-unloaded draw list.
				Class.forName(type, false, RenderCompatibility.class.getClassLoader());
				for (IMixinInfo mixin : Mixins.getMixinsForClass(type)) {
					if (!mixin.getClassName().startsWith("net.adiont.alacer.")) {
						Alacer.LOGGER.info("Modern rendering fallback: {} is modified by {}", type, mixin.getClassName()); return false;
					}
				}
			}
			return true;
		} catch (ClassNotFoundException | RuntimeException | LinkageError e) {
			Alacer.LOGGER.warn("Modern rendering fallback: could not inspect mixin compatibility", e); return false;
		}
	}
}
