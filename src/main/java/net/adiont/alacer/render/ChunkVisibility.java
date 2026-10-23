package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.lwjgl.util.vector.Vector3f;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.RenderChunkStorageAccessor;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.mixin.WorldRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.CompiledChunk;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkStorage;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Allocation-free replacement for the render-chunk visibility search in
 * {@code WorldRenderer.setupRender}.
 *
 * <p>The search is a breadth-first flood fill from the camera's render chunk through chunk
 * faces that the chunk's occlusion data says are connected, limited to the view distance and
 * the frustum. Vanilla runs it every frame the camera moves or turns and, per visited chunk,
 * allocates a queue node, a {@code RenderChunkInfo}, an {@code EnumSet} and a copy of
 * {@code Direction.values()}. It also recomputes a 4096-block flood fill of the camera's own
 * chunk section every time. Profiling showed this to be about half of client-thread CPU time
 * and most client-thread allocation while the camera moves.
 *
 * <p>This implementation produces the same list, in the same order, but keeps its state in
 * flat arrays indexed by render-chunk storage slot:
 * <ul>
 * <li>the FIFO queue is an {@code int[]} (every slot is enqueued at most once per search);</li>
 * <li>"visited this search" is a per-slot stamp instead of a per-search clear;</li>
 * <li>the set of directions travelled so far is a 6-bit mask per slot;</li>
 * <li>one {@code RenderChunkInfo} per slot is created once and reused, since everything
 * outside the search only reads its {@code chunk} field;</li>
 * <li>the camera section's open faces are cached until a block in that section changes, the
 * camera enters another block, or the chunk object is replaced; chunks that have not had their
 * first client tick are never cached, because their block changes are not reported.</li>
 * </ul>
 *
 * <p>Traversal order, direction order, range checks and the point at which a chunk is marked
 * visited (before the frustum test) all mirror vanilla so the output is identical; the
 * {@code render.fastChunkVisibility.verify} option checks that at runtime. Chunk boxes are tested
 * with {@link ChunkFrustum} ({@code render.fastChunkFrustum}), which makes the culler's decisions
 * with fewer evaluations, or with the culler itself.
 *
 * <p>Only used on the client thread.
 */
public final class ChunkVisibility {

	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int[] OFFSET_X = new int[6];
	private static final int[] OFFSET_Y = new int[6];
	private static final int[] OFFSET_Z = new int[6];
	private static final int[] OPPOSITE = new int[6];
	private static final int NO_DIRECTION = -1;
	private static final int FRUSTUM_CULLER = 0;
	private static final int FRUSTUM_PLANES = 1;
	private static final int FRUSTUM_VERIFY = 2;

	static {
		for (int d = 0; d < DIRECTIONS.length; d++) {
			OFFSET_X[d] = DIRECTIONS[d].getOffsetX() * 16;
			OFFSET_Y[d] = DIRECTIONS[d].getOffsetY() * 16;
			OFFSET_Z[d] = DIRECTIONS[d].getOffsetZ() * 16;
			OPPOSITE[d] = DIRECTIONS[d].getOpposite().ordinal();
		}
	}

	private final WorldRenderer renderer;
	private final WorldRendererAccessor access;
	private final List<WorldRenderer.RenderChunkInfo> result = new ArrayList<>();

	// Per-slot state, sized to the current RenderChunkStorage.
	private RenderChunk[] chunks;
	private WorldRenderer.RenderChunkInfo[] infos;
	private int[] visited;
	private byte[] travelled;
	private byte[] sourceDir;
	private int[] queue;
	private int stamp;
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	// Frustum test for the current search (render.fastChunkFrustum).
	private final ChunkFrustum frustum = new ChunkFrustum();
	private int frustumMode;
	private int frustumTests;

	// profiler.frustumTiming: the slots tested by the last search, repeated after it.
	private final boolean frustumTiming;
	private int[] tested = new int[0];
	private int testedCount;
	private Culler testedCuller;
	private long replaySink;

	// Open faces of the camera's chunk section, as a direction bit mask.
	private boolean facesValid;
	private int facesX;
	private int facesY;
	private int facesZ;
	private WorldChunk facesChunk;
	private int faces;

	// Verification against vanilla (render.fastChunkVisibility.verify).
	//
	// Chunk builder threads publish compiled occlusion data concurrently (for chunks with no
	// geometry the publish runs on the builder thread), so a chunk can become visible between
	// our search and vanilla's. Every slot's compiled-data reference is snapshotted before our
	// search and compared after vanilla's: if any reference changed, the two searches may have
	// seen different input and the comparison is "inconclusive". With all references unchanged
	// both searches saw identical input, so any difference is a real bug.
	private RenderChunk[] expected = new RenderChunk[0];
	private CompiledChunk[] compiledSnapshot = new CompiledChunk[0];
	private int expectedCount;
	private boolean verifyPending;
	private long verifiedSearches;
	private long stableMismatches;
	private long inconclusiveSearches;
	private long inconclusiveMismatches;

	public ChunkVisibility(WorldRenderer renderer) {
		this.renderer = renderer;
		this.access = (WorldRendererAccessor) renderer;
		this.frustumTiming = Alacer.get().config().profilerFrustumTiming;
	}

	/**
	 * Hook placed in {@code setupRender} right after vanilla decides whether the view changed.
	 * When enabled, runs this search, installs its result and clears {@code viewChanged} so
	 * vanilla's own search is skipped; the rest of {@code setupRender} runs unchanged.
	 */
	public void beforeVanillaSearch(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks) {
		Alacer alacer = Alacer.get();
		AlacerConfig config = alacer.config();
		FrameProfiler profiler = alacer.profiler();
		if (profiler != null) {
			// Ends in afterVanillaSearch, so vanilla's search, ours, or both (verify) are timed alike.
			profiler.begin(Section.VISIBILITY);
		}
		// Same guard as vanilla: no search while a frustum is captured (debug) or nothing moved.
		if (this.access.alacer$getCapturedFrustum() != null || !this.access.alacer$isViewChanged()) {
			return;
		}
		if (profiler != null) {
			profiler.count(Counter.VISIBILITY_SEARCHES);
		}
		if (config.verifyChunkVisibility) {
			prepare(this.access.alacer$getChunkStorage());
			if (this.compiledSnapshot.length != this.chunks.length) {
				this.compiledSnapshot = new CompiledChunk[this.chunks.length];
			}
			for (int i = 0; i < this.chunks.length; i++) {
				this.compiledSnapshot[i] = this.chunks[i].getCompiledChunk();
			}
			List<WorldRenderer.RenderChunkInfo> mine = search(camera, tickDelta, culler, frame, loadChunks, false);
			if (this.expected.length < mine.size()) {
				this.expected = new RenderChunk[Math.max(mine.size(), this.expected.length * 2)];
			}
			for (int i = 0; i < mine.size(); i++) {
				this.expected[i] = mine.get(i).chunk;
			}
			this.expectedCount = mine.size();
			this.verifyPending = true;
		} else if (config.fastChunkVisibility) {
			this.access.alacer$setVisibleChunks(search(camera, tickDelta, culler, frame, loadChunks, true));
			this.access.alacer$setViewChanged(false);
		}
	}

	/**
	 * Hook placed right after vanilla's search block (before frustum capture and chunk rebuild
	 * scheduling, which may compile chunks synchronously): compares the two results when
	 * verifying, and closes the visibility timing section.
	 */
	public void afterVanillaSearch() {
		if (this.verifyPending) {
			this.verifyPending = false;
			compareWithVanilla();
		}
		FrameProfiler profiler = Alacer.get().profiler();
		if (profiler != null) {
			profiler.end(Section.VISIBILITY);
			if (this.testedCuller != null) {
				replayFrustumTests(profiler);
			}
		}
		this.testedCuller = null;
	}

	/**
	 * Attribution only ({@code profiler.frustumTiming}): repeats the last search's frustum tests,
	 * same boxes in the same order, once through the culler and once through {@link ChunkFrustum},
	 * each timed as a whole. The order alternates per search so neither always runs on warmer
	 * caches. The answers are summed into a field so the JIT cannot drop either loop.
	 */
	private void replayFrustumTests(FrameProfiler profiler) {
		Culler culler = this.testedCuller;
		boolean planes = this.frustum.prepare(culler);
		boolean vanillaFirst = (this.stamp & 1) == 0;
		int vanillaVisible = 0;
		int planesVisible = 0;
		for (int pass = 0; pass < 2; pass++) {
			boolean vanilla = vanillaFirst == (pass == 0);
			if (!vanilla && !planes) {
				continue;
			}
			long start = System.nanoTime();
			int visible = 0;
			for (int i = 0; i < this.testedCount; i++) {
				Box box = this.chunks[this.tested[i]].bounds;
				if (vanilla ? culler.isVisible(box) : this.frustum.isVisible(box)) {
					visible++;
				}
			}
			profiler.add(vanilla ? Section.FRUSTUM_REPLAY_VANILLA : Section.FRUSTUM_REPLAY_PLANES, System.nanoTime() - start);
			if (vanilla) {
				vanillaVisible = visible;
			} else {
				planesVisible = visible;
			}
		}
		if (planes && planesVisible != vanillaVisible) {
			Alacer.LOGGER.warn("Chunk frustum replay: vanilla {} of {} boxes visible, plane test {}", vanillaVisible, this.testedCount, planesVisible);
		}
		this.replaySink += vanillaVisible + planesVisible;
	}

	private void compareWithVanilla() {
		boolean dirty = false;
		for (int i = 0; i < this.chunks.length; i++) {
			if (this.chunks[i].getCompiledChunk() != this.compiledSnapshot[i]) {
				dirty = true;
				break;
			}
		}

		List<WorldRenderer.RenderChunkInfo> vanilla = this.access.alacer$getVisibleChunks();
		int firstDifference = -1;
		int n = Math.min(vanilla.size(), this.expectedCount);
		for (int i = 0; i < n; i++) {
			if (vanilla.get(i).chunk != this.expected[i]) {
				firstDifference = i;
				break;
			}
		}
		if (firstDifference < 0 && vanilla.size() != this.expectedCount) {
			firstDifference = n;
		}

		this.verifiedSearches++;
		if (dirty) {
			this.inconclusiveSearches++;
			if (firstDifference >= 0) {
				this.inconclusiveMismatches++;
			}
		} else if (firstDifference >= 0) {
			this.stableMismatches++;
			if (this.stableMismatches <= 10) {
				Alacer.LOGGER.warn("Chunk visibility mismatch with unchanged chunk data: vanilla {} chunks, Alacer {} chunks, first difference at index {}",
					vanilla.size(), this.expectedCount, firstDifference);
			}
		}
		if (this.verifiedSearches % 1000 == 0) {
			logVerification();
		}
	}

	public void logVerification() {
		if (this.verifiedSearches == 0) {
			return;
		}
		Alacer.LOGGER.info("Chunk visibility verification: {} searches compared; {} mismatched with stable input; {} had compiled chunk data change mid-comparison ({} of those differed)",
			this.verifiedSearches, this.stableMismatches, this.inconclusiveSearches, this.inconclusiveMismatches);
	}

	/**
	 * Runs the search and returns the visible chunks (a list owned and reused by this object).
	 *
	 * @param markFrames whether to stamp visited chunks with {@code RenderChunk.setFrame} as
	 *                   vanilla does; false when only verifying against vanilla, which stamps
	 *                   them itself afterwards
	 */
	public List<WorldRenderer.RenderChunkInfo> search(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks, boolean markFrames) {
		RenderChunkStorage storage = this.access.alacer$getChunkStorage();
		prepare(storage);
		List<WorldRenderer.RenderChunkInfo> out = this.result;
		out.clear();
		nextStamp();
		AlacerConfig config = Alacer.get().config();
		if ((config.fastChunkFrustum || config.verifyChunkFrustum) && this.frustum.prepare(culler)) {
			this.frustumMode = config.verifyChunkFrustum ? FRUSTUM_VERIFY : FRUSTUM_PLANES;
		} else {
			this.frustumMode = FRUSTUM_CULLER;
		}
		this.frustumTests = 0;
		if (this.frustumTiming) {
			this.testedCount = 0;
			this.testedCuller = culler;
		}

		double camX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
		double camY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
		double camZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
		BlockPos eye = new BlockPos(camX, camY + camera.getEyeHeight(), camZ);
		// Range checks are relative to the chunk containing the camera's feet, as in vanilla.
		int originX = floor16(camX);
		int originZ = floor16(camZ);
		int range = this.access.alacer$getLastViewDistance() * 16;
		boolean smartCull = Minecraft.getInstance().smartCull;

		int head = 0;
		int tail = 0;
		int cameraSlot = slot(eye.getX(), eye.getY(), eye.getZ());
		if (cameraSlot >= 0) {
			int open = openFaces(eye);
			if (Integer.bitCount(open) == 1) {
				Vector3f look = this.access.alacer$getLookVector(camera, tickDelta);
				open &= ~(1 << Direction.getNearest(look.x, look.y, look.z).getOpposite().ordinal());
			}

			if (open == 0 && !loadChunks) {
				// Camera is sealed inside its own chunk: only that chunk can be seen.
				out.add(info(cameraSlot));
				return out;
			}
			if (loadChunks && this.access.alacer$getWorld().getBlockState(eye).getBlock().isSolidRender()) {
				smartCull = false;
			}
			visit(cameraSlot, frame, markFrames);
			this.sourceDir[cameraSlot] = NO_DIRECTION;
			this.travelled[cameraSlot] = 0;
			this.queue[tail++] = cameraSlot;
		} else {
			// Camera above or below the world: seed with the top or bottom layer. Like vanilla,
			// this layer is centred on the world origin rather than on the camera.
			int y = eye.getY() > 0 ? 248 : 8;
			int distance = this.access.alacer$getLastViewDistance();
			for (int cx = -distance; cx <= distance; cx++) {
				for (int cz = -distance; cz <= distance; cz++) {
					int s = slot((cx << 4) + 8, y, (cz << 4) + 8);
					if (s >= 0 && inFrustum(culler, s)) {
						visit(s, frame, markFrames);
						this.sourceDir[s] = NO_DIRECTION;
						this.travelled[s] = 0;
						this.queue[tail++] = s;
					}
				}
			}
		}

		while (head < tail) {
			int current = this.queue[head++];
			RenderChunk chunk = this.chunks[current];
			out.add(info(current));

			int from = this.sourceDir[current];
			int mask = this.travelled[current];
			BlockPos origin = chunk.getOrigin();
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			for (int d = 0; d < 6; d++) {
				// Never step back against a direction already travelled on this path.
				if (smartCull && (mask & (1 << OPPOSITE[d])) != 0) {
					continue;
				}
				// Only continue through faces connected to the face this chunk was entered by.
				if (smartCull && from != NO_DIRECTION && !chunk.getCompiledChunk().isVisible(DIRECTIONS[OPPOSITE[from]], DIRECTIONS[d])) {
					continue;
				}
				int nx = x + OFFSET_X[d];
				int ny = y + OFFSET_Y[d];
				int nz = z + OFFSET_Z[d];
				if (Math.abs(originX - nx) > range || ny < 0 || ny >= 256 || Math.abs(originZ - nz) > range) {
					continue;
				}
				int next = slot(nx, ny, nz);
				// Marked before the frustum test: a chunk reached first from outside the frustum
				// is not revisited from another side, exactly as in vanilla.
				if (next < 0 || !visit(next, frame, markFrames) || !inFrustum(culler, next)) {
					continue;
				}
				this.sourceDir[next] = (byte) d;
				this.travelled[next] = (byte) (mask | 1 << d);
				this.queue[tail++] = next;
			}
		}
		FrameProfiler profiler = Alacer.get().profiler();
		if (profiler != null) {
			profiler.count(Counter.FRUSTUM_TESTS, this.frustumTests);
		}
		return out;
	}

	/** {@code culler.isVisible(bounds)} of the chunk in this slot, answered as configured for the search. */
	private boolean inFrustum(Culler culler, int slot) {
		Box bounds = this.chunks[slot].bounds;
		this.frustumTests++;
		if (this.frustumTiming) {
			this.tested[this.testedCount++] = slot;
		}
		switch (this.frustumMode) {
			case FRUSTUM_PLANES:
				return this.frustum.isVisible(bounds);
			case FRUSTUM_VERIFY:
				return this.frustum.verify(bounds);
			default:
				return culler.isVisible(bounds);
		}
	}

	/** Marks a slot visited for this search; returns false if it already was. */
	private boolean visit(int slot, int frame, boolean markFrames) {
		if (this.visited[slot] == this.stamp) {
			return false;
		}
		this.visited[slot] = this.stamp;
		if (markFrames) {
			this.chunks[slot].setFrame(frame);
		}
		return true;
	}

	private void nextStamp() {
		if (++this.stamp == Integer.MAX_VALUE) {
			Arrays.fill(this.visited, 0);
			this.stamp = 1;
		}
	}

	/** Same slot mapping as {@code RenderChunkStorage.getChunk}; -1 outside the vertical range. */
	private int slot(int x, int y, int z) {
		int cy = Math.floorDiv(y, 16);
		if (cy < 0 || cy >= this.sizeY) {
			return -1;
		}
		int cx = Math.floorMod(Math.floorDiv(x, 16), this.sizeX);
		int cz = Math.floorMod(Math.floorDiv(z, 16), this.sizeZ);
		return (cz * this.sizeY + cy) * this.sizeX + cx;
	}

	private static int floor16(double coordinate) {
		return (int) Math.floor(coordinate / 16.0) * 16;
	}

	private WorldRenderer.RenderChunkInfo info(int slot) {
		WorldRenderer.RenderChunkInfo info = this.infos[slot];
		if (info == null) {
			// Source direction and step are only read by the search itself, so pooled infos
			// carry placeholder values.
			info = this.renderer.new RenderChunkInfo(this.chunks[slot], null, 0);
			this.infos[slot] = info;
		}
		return info;
	}

	/** (Re)allocates per-slot state when the renderer has replaced its chunk storage. */
	private void prepare(RenderChunkStorage storage) {
		if (storage.chunks == this.chunks) {
			return;
		}
		RenderChunkStorageAccessor dims = (RenderChunkStorageAccessor) storage;
		this.chunks = storage.chunks;
		this.sizeX = dims.alacer$getSizeX();
		this.sizeY = dims.alacer$getSizeY();
		this.sizeZ = dims.alacer$getSizeZ();
		int n = this.chunks.length;
		this.infos = new WorldRenderer.RenderChunkInfo[n];
		this.visited = new int[n];
		this.travelled = new byte[n];
		this.sourceDir = new byte[n];
		this.queue = new int[n];
		if (this.frustumTiming) {
			this.tested = new int[n];
		}
		this.stamp = 0;
		this.facesValid = false;
	}

	private int openFaces(BlockPos eye) {
		ClientWorld world = this.access.alacer$getWorld();
		WorldChunk chunk = world.getChunk(eye);
		// Block changes in a chunk that is not yet "populated" (it has not had its first client
		// tick) do not notify render listeners, so markDirty would not invalidate the cache.
		// Recompute every time for such chunks, exactly like vanilla, and keep the result only if
		// the chunk was populated when it was computed: a change made before the first tick would
		// otherwise survive in the cache once the chunk becomes populated.
		if (!this.facesValid || !chunk.isPopulated() || this.facesChunk != chunk || this.facesX != eye.getX() || this.facesY != eye.getY() || this.facesZ != eye.getZ()) {
			Set<Direction> open = this.access.alacer$getOpenChunkFaces(eye);
			int mask = 0;
			for (Direction direction : open) {
				mask |= 1 << direction.ordinal();
			}
			this.faces = mask;
			this.facesChunk = chunk;
			this.facesX = eye.getX();
			this.facesY = eye.getY();
			this.facesZ = eye.getZ();
			this.facesValid = chunk.isPopulated();
		}
		return this.faces;
	}

	/** Called for every render-dirty region; drops the cached faces if they could be affected. */
	public void onRegionDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		if (!this.facesValid) {
			return;
		}
		int sx = this.facesX & ~15;
		int sy = this.facesY & ~15;
		int sz = this.facesZ & ~15;
		if (maxX >= sx && minX <= sx + 15 && maxY >= sy && minY <= sy + 15 && maxZ >= sz && minZ <= sz + 15) {
			this.facesValid = false;
		}
	}

	/** Drops all cached state, e.g. when the renderer switches worlds. */
	public void reset() {
		this.chunks = null;
		this.infos = null;
		this.facesValid = false;
		this.facesChunk = null;
		this.testedCuller = null;
		this.result.clear();
	}
}
