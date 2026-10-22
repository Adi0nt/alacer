package net.adiont.alacer.mixin;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.world.ChunkRenderDispatcher;

@Mixin(ChunkRenderDispatcher.class)
public interface ChunkRenderDispatcherAccessor {

	@Accessor("pendingTasks")
	BlockingQueue<?> alacer$getPendingTasks();

	@Accessor("pendingUploads")
	Queue<?> alacer$getPendingUploads();

	@Accessor("availableBuffers")
	BlockingQueue<?> alacer$getAvailableBuffers();
}
