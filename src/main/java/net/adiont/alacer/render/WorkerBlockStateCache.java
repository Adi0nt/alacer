package net.adiont.alacer.render;

import java.util.Arrays;
import net.adiont.alacer.Alacer;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.world.RenderChunkRegion;
import net.minecraft.util.math.BlockPos;

/**
 * Thread-local pooled block-state and light caches for worker-thread chunk compilation and
 * ambient occlusion ({@code render.fastWorkerAo}).
 *
 * <p>Vanilla allocates a fresh {@code new BlockState[8000]} (32-64 KB) and {@code new int[8000]} (32 KB)
 * on every chunk section compilation. This class pools the 8000-element arrays per compilation thread,
 * completely eliminating heap churn during flight and chunk updates.
 */
public final class WorkerBlockStateCache {

	private static final ThreadLocal<WorkerBlockStateCache> POOL = ThreadLocal.withInitial(WorkerBlockStateCache::new);

	private static long comparisons;
	private static long mismatches;

	public static WorkerBlockStateCache get() {
		return POOL.get();
	}

	public final BlockState[] blocks = new BlockState[8000];
	public final int[] light = new int[8000];

	public WorkerBlockStateCache() {
		Arrays.fill(this.light, -1);
	}

	public static void verifyBlockState(RenderChunkRegion region, BlockPos pos, BlockState cached) {
		comparisons++;
		BlockState expected = region.getBlockState(pos);
		if (cached != expected) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("render.fastWorkerAo verification mismatch at {}: cached={}, expected={}",
					pos, cached, expected);
			}
		}
	}

	public static String summary() {
		return comparisons + " worker block queries compared, " + mismatches + " mismatches";
	}
}
