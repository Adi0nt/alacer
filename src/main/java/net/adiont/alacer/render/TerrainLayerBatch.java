package net.adiont.alacer.render;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;

/**
 * High-performance, zero-allocation layer queues for terrain rendering passes.
 * Segregates visible chunks during a single ultra-tight linear scan so that subsequent
 * layer passes (Solid, Cutout Mipped, Cutout, Translucent) and transparency resorting
 * only visit chunks containing geometry for that specific layer, eliminating 5,000+
 * redundant iterations per frame.
 */
public final class TerrainLayerBatch {
	private static long verificationComparisons;
	private static long verificationMismatches;

	/** Client-thread verification only; does not execute in performance runs. */
	public static boolean verifyCount(int actual, int expected) {
		verificationComparisons++;
		if (actual == expected) return true;
		verificationMismatches++;
		return false;
	}

	public static String verificationSummary() {
		return verificationComparisons + " layer counts compared, " + verificationMismatches + " mismatches";
	}

	private static final int SOLID_BIT = 1 << BlockLayer.SOLID.ordinal();
	private static final int CUTOUT_MIPPED_BIT = 1 << BlockLayer.CUTOUT_MIPPED.ordinal();
	private static final int CUTOUT_BIT = 1 << BlockLayer.CUTOUT.ordinal();
	private static final int TRANSLUCENT_BIT = 1 << BlockLayer.TRANSLUCENT.ordinal();

	public static final class LayerQueue {
		private FastRenderChunk[] chunks;
		private int size = 0;

		public LayerQueue() {
			this(128);
		}

		public LayerQueue(int initialCapacity) {
			this.chunks = new FastRenderChunk[initialCapacity];
		}

		public void clear() {
			Arrays.fill(this.chunks, 0, this.size, null);
			this.size = 0;
		}

		public void add(FastRenderChunk chunk) {
			if (this.size == this.chunks.length) {
				this.chunks = Arrays.copyOf(this.chunks, this.chunks.length << 1);
			}
			this.chunks[this.size++] = chunk;
		}

		public FastRenderChunk[] array() {
			return this.chunks;
		}

		public FastRenderChunk get(int index) {
			return this.chunks[index];
		}

		public int size() {
			return this.size;
		}

		public boolean isEmpty() {
			return this.size == 0;
		}
	}

	private final LayerQueue solid = new LayerQueue(2048);
	private final LayerQueue cutoutMipped = new LayerQueue(1024);
	private final LayerQueue cutout = new LayerQueue(1024);
	private final LayerQueue translucent = new LayerQueue(512);
	private final LayerQueue translucentResort = new LayerQueue(512);
	private int visibleLayerBits;
	private Object lastListIdentity;
	private int lastListSize;

	public void clear() {
		this.solid.clear();
		this.cutoutMipped.clear();
		this.cutout.clear();
		this.translucent.clear();
		this.translucentResort.clear();
		this.visibleLayerBits = 0;
		this.lastListIdentity = null;
		this.lastListSize = -1;
	}

	public boolean isBuiltFor(List<WorldRenderer.RenderChunkInfo> compiledChunks) {
		return this.lastListIdentity == compiledChunks && this.lastListSize == compiledChunks.size();
	}

	public void build(List<WorldRenderer.RenderChunkInfo> compiledChunks) {
		this.solid.clear();
		this.cutoutMipped.clear();
		this.cutout.clear();
		this.translucent.clear();
		this.translucentResort.clear();
		this.visibleLayerBits = 0;

		int total = compiledChunks.size();

		for (int i = 0; i < total; i++) {
			WorldRenderer.RenderChunkInfo info = compiledChunks.get(i);
			RenderChunk chunk = info.chunk;
			if (!(chunk instanceof FastRenderChunk frc)) {
				CompiledChunk cc = chunk.getCompiledChunk();
				int mask = (cc instanceof FastCompiledChunk fcc) ? fcc.alacer$layerMask() : -1;
				if (mask == -1) {
					this.visibleLayerBits = -1;
				} else {
					this.visibleLayerBits |= mask;
				}
				continue;
			}

			int mask = frc.alacer$layerMask();
			int builtMask = frc.alacer$builtLayerMask();

			this.visibleLayerBits |= mask;

			if ((mask & SOLID_BIT) != 0) this.solid.add(frc);
			if ((mask & CUTOUT_MIPPED_BIT) != 0) this.cutoutMipped.add(frc);
			if ((mask & CUTOUT_BIT) != 0) this.cutout.add(frc);
			if ((mask & TRANSLUCENT_BIT) != 0) this.translucent.add(frc);

			if ((builtMask & TRANSLUCENT_BIT) != 0) {
				this.translucentResort.add(frc);
			}
		}

		this.lastListIdentity = compiledChunks;
		this.lastListSize = total;
	}

	public LayerQueue getQueue(BlockLayer layer) {
		return switch (layer) {
			case SOLID -> this.solid;
			case CUTOUT_MIPPED -> this.cutoutMipped;
			case CUTOUT -> this.cutout;
			case TRANSLUCENT -> this.translucent;
		};
	}

	public LayerQueue getTranslucentResortQueue() {
		return this.translucentResort;
	}

	public int getVisibleLayerBits() {
		return this.visibleLayerBits;
	}
}
