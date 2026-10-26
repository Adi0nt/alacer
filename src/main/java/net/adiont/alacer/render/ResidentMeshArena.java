package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15;
import net.minecraft.client.render.platform.GLX;

/**
 * Shared resident vertex buffer arena for terrain rendering (Phase 6.1 / Phase 3.3).
 *
 * <p>Consolidates individual per-chunk VBO allocations into large shared resident mesh pages.
 * Chunks allocate slices aligned to the 28-byte vertex stride, enabling multi-draw indirect (MDI)
 * submission and eliminating thousands of per-chunk buffer binding and descriptor invalidations.
 */
public final class ResidentMeshArena {

	public static final int VERTEX_STRIDE = 28; // Position(12) + Color(4) + UV0(8) + UV1(4)
	public static final int DEFAULT_PAGE_SIZE = 32 * 1024 * 1024; // 32 MiB (~1.2 million block vertices)

	private static final Logger LOGGER = LogManager.getLogger("Alacer/ResidentMeshArena");

	public static final class Slice {
		public final int pageIndex;
		public final int glBufferId;
		public final int byteOffset;
		public final int firstVertex;
		public final int vertexCount;
		public final int byteLength;

		Slice(int pageIndex, int glBufferId, int byteOffset, int vertexCount) {
			this.pageIndex = pageIndex;
			this.glBufferId = glBufferId;
			this.byteOffset = byteOffset;
			this.firstVertex = byteOffset / VERTEX_STRIDE;
			this.vertexCount = vertexCount;
			this.byteLength = vertexCount * VERTEX_STRIDE;
		}
	}

	static final class Range {
		int offset;
		int length;

		Range(int offset, int length) {
			this.offset = offset;
			this.length = length;
		}
	}

	static final class Page {
		final int index;
		final int glBufferId;
		final int capacity;
		final List<Range> freeRanges = new ArrayList<>();

		Page(int index, int glBufferId, int capacity) {
			this.index = index;
			this.glBufferId = glBufferId;
			this.capacity = capacity;
			this.freeRanges.add(new Range(0, capacity));
		}

		Slice allocate(int vertexCount) {
			int bytes = vertexCount * VERTEX_STRIDE;
			for (int i = 0; i < this.freeRanges.size(); i++) {
				Range range = this.freeRanges.get(i);
				if (range.length >= bytes) {
					int allocOffset = range.offset;
					if (range.length == bytes) {
						this.freeRanges.remove(i);
					} else {
						range.offset += bytes;
						range.length -= bytes;
					}
					return new Slice(this.index, this.glBufferId, allocOffset, vertexCount);
				}
			}
			return null;
		}

		void free(int offset, int bytes) {
			this.freeRanges.add(new Range(offset, bytes));
			coalesce();
		}

		void coalesce() {
			if (this.freeRanges.size() <= 1) {
				return;
			}
			this.freeRanges.sort(Comparator.comparingInt(r -> r.offset));
			List<Range> coalesced = new ArrayList<>();
			Range current = this.freeRanges.get(0);

			for (int i = 1; i < this.freeRanges.size(); i++) {
				Range next = this.freeRanges.get(i);
				if (current.offset + current.length == next.offset) {
					current.length += next.length;
				} else {
					coalesced.add(current);
					current = next;
				}
			}
			coalesced.add(current);
			this.freeRanges.clear();
			this.freeRanges.addAll(coalesced);
		}
	}

	private final int pageSize;
	private final boolean glEnabled;
	private final List<Page> pages = new ArrayList<>();

	private static final ResidentMeshArena INSTANCE = new ResidentMeshArena();

	public static ResidentMeshArena get() {
		return INSTANCE;
	}

	public ResidentMeshArena() {
		this(DEFAULT_PAGE_SIZE, true);
	}

	public ResidentMeshArena(int pageSize, boolean glEnabled) {
		this.pageSize = (pageSize / VERTEX_STRIDE) * VERTEX_STRIDE;
		this.glEnabled = glEnabled;
	}

	public synchronized Slice allocate(int vertexCount) {
		if (vertexCount <= 0) {
			return null;
		}
		int bytes = vertexCount * VERTEX_STRIDE;
		if (bytes > this.pageSize) {
			LOGGER.warn("Allocation of {} vertices ({} bytes) exceeds page size {}", vertexCount, bytes, this.pageSize);
			return null;
		}

		// Try existing pages
		for (Page page : this.pages) {
			Slice slice = page.allocate(vertexCount);
			if (slice != null) {
				return slice;
			}
		}

		// Allocate new page
		Page newPage = createPage(this.pages.size(), this.pageSize);
		this.pages.add(newPage);
		return newPage.allocate(vertexCount);
	}

	public synchronized void free(Slice slice) {
		if (slice == null || slice.pageIndex < 0 || slice.pageIndex >= this.pages.size()) {
			return;
		}
		Page page = this.pages.get(slice.pageIndex);
		page.free(slice.byteOffset, slice.byteLength);
	}

	public synchronized void upload(Slice slice, ByteBuffer data) {
		if (!this.glEnabled || slice == null || data == null || slice.glBufferId == 0) {
			return;
		}
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, slice.glBufferId);
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, slice.byteOffset, data);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

	public synchronized void clear() {
		if (this.glEnabled) {
			for (Page page : this.pages) {
				if (page.glBufferId != 0) {
					GL15.glDeleteBuffers(page.glBufferId);
				}
			}
		}
		this.pages.clear();
	}

	public synchronized int pageCount() {
		return this.pages.size();
	}

	public synchronized int totalFreeRanges() {
		int sum = 0;
		for (Page p : this.pages) {
			sum += p.freeRanges.size();
		}
		return sum;
	}

	private Page createPage(int index, int capacity) {
		int bufferId = 0;
		if (this.glEnabled) {
			bufferId = GL15.glGenBuffers();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STATIC_DRAW);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			LOGGER.info("Created resident mesh arena page {} (glBufferId={}, capacity={} bytes)", index, bufferId, capacity);
		}
		return new Page(index, bufferId, capacity);
	}
}
