package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ChunkScheduling;
import net.minecraft.client.render.world.ChunkRenderDispatcher;

/**
 * The dispatcher is created once with the world renderer. Its constructor's only 2 is the worker count; both 5s size
 * the buffer queue and fill it, so they change together and keep vanilla's three spare buffer sets.
 */
@Mixin(ChunkRenderDispatcher.class)
public class ChunkWorkersMixin {
	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 2))
	private int alacer$workers(int vanilla) {
		return Alacer.get() == null ? vanilla : ChunkScheduling.threads(Alacer.get().config().chunkThreads);
	}
	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 5))
	private int alacer$buffers(int vanilla) {
		return Alacer.get() == null ? vanilla : ChunkScheduling.buffers(Alacer.get().config().chunkThreads);
	}
}
