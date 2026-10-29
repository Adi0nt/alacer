package net.adiont.alacer.render;

import java.util.function.Supplier;

import net.adiont.alacer.Alacer;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Conservative startup check for experiments that depend on unchanged vanilla method bodies. */
final class RenderCompatibility {
	private static int buffers, neighbours, displayTicks;
	private RenderCompatibility() { }
	static boolean displayTicks() {
		if (displayTicks == 0) displayTicks = unchanged(() -> new Class<?>[] { net.minecraft.block.Block.class, net.minecraft.block.Blocks.AIR.getClass(), net.minecraft.client.world.ClientWorld.class }) ? 1 : -1;
		return displayTicks > 0;
	}
	static boolean buffers() {
		if (buffers == 0) { buffers = unchanged(() -> new Class<?>[] { net.minecraft.client.render.vertex.VertexBuffer.class, net.minecraft.client.render.world.VboRenderChunkList.class }) ? 1 : -1; }
		return buffers > 0;
	}
	static boolean neighbours() {
		if (neighbours == 0) { neighbours = unchanged(() -> new Class<?>[] { net.minecraft.client.render.world.RenderChunkStorage.class, net.minecraft.client.render.world.RenderChunk.class }) ? 1 : -1; }
		return neighbours > 0;
	}
	private static boolean unchanged(Supplier<Class<?>[]> types) {
		try {
			for (Class<?> target : types.get()) {
				// The public Mixin query reports applied mixins only. Class literals load every target first,
				// so an early buffer constructor cannot cache success for an as-yet-unloaded draw list. They are
				// also remapped with the JAR: hard-coded named-mapping strings fail on intermediary runtimes.
				String type = target.getName();
				for (IMixinInfo mixin : Mixins.getMixinsForClass(type)) {
					if (!mixin.getClassName().startsWith("net.adiont.alacer.")) {
						Alacer.LOGGER.info("Modern rendering fallback: {} is modified by {}", type, mixin.getClassName()); return false;
					}
				}
			}
			return true;
		} catch (RuntimeException | LinkageError e) {
			Alacer.LOGGER.warn("Modern rendering fallback: could not inspect mixin compatibility", e); return false;
		}
	}
}
