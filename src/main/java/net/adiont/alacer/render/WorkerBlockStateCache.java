package net.adiont.alacer.render;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.atomic.LongAdder;
import net.adiont.alacer.Alacer;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.world.RenderChunkRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Thread-local pooled block-state and light caches for worker-thread chunk compilation and
 * ambient occlusion ({@code render.fastWorkerAo}).
 *
 * <p>Vanilla allocates a fresh {@code new BlockState[8000]} (32-64 KB) and {@code new int[8000]} (32 KB)
 * on every chunk section compilation. This class pools the 8000-element arrays per compilation thread,
 * avoiding the large arrays during flight and chunk updates. Leases end in compile's
 * finally block; multiple regions and nested compiles never share live arrays.
 */
public final class WorkerBlockStateCache {

	private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);
	private static final LongAdder blockComparisons = new LongAdder();
	private static final LongAdder lightComparisons = new LongAdder();
	private static final LongAdder mismatches = new LongAdder();

	private static final class Pool {
		final ArrayList<WorkerBlockStateCache> caches = new ArrayList<>();
		int[] marks = new int[4];
		int depth, used, references;
		WorkerBlockStateCache cache(int index) {
			while (this.caches.size() <= index) this.caches.add(new WorkerBlockStateCache());
			return this.caches.get(index);
		}
	}

	public static WorkerBlockStateCache get() {
		return POOL.get().cache(0);
	}

	/** One scope per synchronous compile invocation, including nested compiles and exceptions. */
	public static int beginCompile() {
		Pool pool = POOL.get();
		if (pool.depth == pool.marks.length) pool.marks = Arrays.copyOf(pool.marks, pool.depth * 2);
		pool.marks[pool.depth++] = pool.used;
		return pool.used;
	}

	public static void endCompile(int mark) {
		Pool pool = POOL.get();
		if (pool.depth == 0 || pool.marks[pool.depth - 1] != mark) throw new IllegalStateException("Unbalanced chunk cache scope");
		for (int i = mark; i < pool.used; i++) Arrays.fill(pool.caches.get(i).blocks, null);
		pool.used = mark;
		pool.depth--;
	}

	/** Never lend arrays to regions constructed outside a scoped chunk compile. */
	public static WorkerBlockStateCache acquireRegion() {
		Pool pool = POOL.get();
		if (pool.depth == 0 || pool.references != 0) return null;
		WorkerBlockStateCache cache = pool.cache(pool.used++);
		Arrays.fill(cache.blocks, null);
		Arrays.fill(cache.light, -1);
		return cache;
	}

	public static boolean constructingReference() { return POOL.get().references != 0; }

	public static RenderChunkRegion reference(World world, BlockPos min, BlockPos max, int padding) {
		Pool pool = POOL.get();
		pool.references++;
		try { return new RenderChunkRegion(world, min, max, padding); }
		finally { pool.references--; }
	}

	public final BlockState[] blocks = new BlockState[8000];
	public final int[] light = new int[8000];

	public WorkerBlockStateCache() {
		Arrays.fill(this.light, -1);
	}

	public static void verifyBlockState(BlockPos pos, BlockState cached, BlockState expected) {
		blockComparisons.increment();
		if (cached != expected) {
			mismatches.increment();
			if (mismatches.sum() <= 10) {
				Alacer.LOGGER.warn("render.fastWorkerAo verification mismatch at {}: cached={}, expected={}",
					pos, cached, expected);
			}
		}
	}

	public static void verifyLight(BlockPos pos, int cached, int expected) {
		lightComparisons.increment();
		if (cached != expected) {
			mismatches.increment();
			if (mismatches.sum() <= 10) Alacer.LOGGER.warn("render.fastWorkerAo light verification mismatch at {}: cached={}, expected={}", pos, cached, expected);
		}
	}

	public static String summary() {
		return blockComparisons.sum() + " worker block queries, " + lightComparisons.sum() + " light queries compared, " + mismatches.sum() + " mismatches";
	}
}
