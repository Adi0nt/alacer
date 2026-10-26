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

	public static final class LayerQueue {
		private FastRenderChunk[] chunks = new FastRenderChunk[512];
		private int size = 0;

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

	private final LayerQueue[] queues = new LayerQueue[BlockLayer.values().length];
	private final LayerQueue translucentResort = new LayerQueue();
	private int visibleLayerBits;
	private Object lastListIdentity;
	private int lastListSize;

	public TerrainLayerBatch() {
		for (int i = 0; i < this.queues.length; i++) {
			this.queues[i] = new LayerQueue();
		}
	}

	public void clear() {
		for (LayerQueue queue : this.queues) {
			queue.clear();
		}
		this.translucentResort.clear();
		this.visibleLayerBits = 0;
		this.lastListIdentity = null;
		this.lastListSize = -1;
	}

	public boolean isBuiltFor(List<WorldRenderer.RenderChunkInfo> compiledChunks) {
		return this.lastListIdentity == compiledChunks && this.lastListSize == compiledChunks.size();
	}

	public void build(List<WorldRenderer.RenderChunkInfo> compiledChunks) {
		for (LayerQueue queue : this.queues) {
			queue.clear();
		}
		this.translucentResort.clear();
		this.visibleLayerBits = 0;

		int total = compiledChunks.size();
		int translucentMask = 1 << BlockLayer.TRANSLUCENT.ordinal();

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

			for (int layerIdx = 0; layerIdx < this.queues.length; layerIdx++) {
				if ((mask & (1 << layerIdx)) != 0) {
					this.queues[layerIdx].add(frc);
				}
			}

			if ((builtMask & translucentMask) != 0) {
				this.translucentResort.add(frc);
			}
		}

		this.lastListIdentity = compiledChunks;
		this.lastListSize = total;
	}

	public LayerQueue getQueue(BlockLayer layer) {
		return this.queues[layer.ordinal()];
	}

	public LayerQueue getTranslucentResortQueue() {
		return this.translucentResort;
	}

	public int getVisibleLayerBits() {
		return this.visibleLayerBits;
	}
}
