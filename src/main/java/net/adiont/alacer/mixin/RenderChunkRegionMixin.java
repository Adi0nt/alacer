package net.adiont.alacer.mixin;

import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.WorkerBlockStateCache;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.world.RenderChunkRegion;

/**
 * Replaces per-compile heap allocations of 8000-element block state and light arrays with
 * thread-local pooled arrays ({@code render.fastWorkerAo}).
 */
@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionMixin {

	@Redirect(method = "<init>", at = @At(value = "NEWARRAY", args = "primitiveValue=int"))
	private int[] alacer$redirectIntArray(int size) {
		AlacerConfig config = Alacer.get().config();
		if (config != null && config.fastWorkerAo && size == 8000) {
			WorkerBlockStateCache cache = WorkerBlockStateCache.get();
			Arrays.fill(cache.light, -1);
			return cache.light;
		}
		return new int[size];
	}

	@Redirect(method = "<init>", at = @At(value = "ANEWARRAY", target = "net/minecraft/block/state/BlockState"))
	private BlockState[] alacer$redirectBlockStateArray(int size) {
		AlacerConfig config = Alacer.get().config();
		if (config != null && config.fastWorkerAo && size == 8000) {
			WorkerBlockStateCache cache = WorkerBlockStateCache.get();
			Arrays.fill(cache.blocks, null);
			return cache.blocks;
		}
		return new BlockState[size];
	}
}
