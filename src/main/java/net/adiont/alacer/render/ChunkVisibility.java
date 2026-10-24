package net.adiont.alacer.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.lwjgl.util.vector.Vector3f;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.RenderChunkStorageAccessor;
import net.adiont.alacer.mixin.FrustumCullerAccessor;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.Frustum;
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
 * <p>With {@code render.fastCameraSectionFaces} those open faces come from {@link SectionFaces}
 * instead of vanilla's flood fill: the section's opaque blocks are read with vanilla's own
 * {@code getBlock(pos).isSolidRender()} calls and flooded without allocation, and the answer is
 * reused while the camera stays inside the same connected pocket of air, under the same
 * invalidation as above (any reuse needs a chunk that was populated when its blocks were read).
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
	private static final int FRUSTUM_VECTOR = 3;
	private static final int FRUSTUM_VECTOR_VERIFY = 4;

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
	private ChunkNeighbours neighbours;
	private static long neighbourChecks, neighbourFailures, neighbourLookups;
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	// Frustum test for the current search (render.fastChunkFrustum and render.vectorFrustum).
	private final ChunkFrustum frustum = new ChunkFrustum();
	private VectorFrustumAccess.Predicate vectorFrustum;
	private boolean vectorAttempted;

	// render.flatOcclusion: dense 36-bit packed occlusion bitmask per chunk slot.
	private long[] flatOcclusion = new long[0];
	private CompiledChunk[] compiledOcclusion = new CompiledChunk[0];
	private static long flatOcclusionHits;
	private static long flatOcclusionUpdates;
	private static long flatOcclusionMismatches;
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
	private final BlockPos.Mutable searchEye = new BlockPos.Mutable();

	// render.fastCameraSectionFaces: the camera section's opaque blocks and last flood fill.
	private final SectionFaces section = new SectionFaces();
	private final BlockPos.Mutable sectionCell = new BlockPos.Mutable();
	private boolean sectionValid;
	private int sectionX;
	private int sectionY;
	private int sectionZ;
	private WorldChunk sectionChunk;
	private static long sectionReads;
	private static long sectionFills;
	private static long sectionReuses;
	private static long sectionCompared;
	private static long sectionMismatches;

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
	private final VisibilityReuse visibilityReuse = new VisibilityReuse();
	private final double[] reuseInputs = new double[64];
	private CompiledChunk[] reuseCompiled = new CompiledChunk[0];
	private List<WorldRenderer.RenderChunkInfo> reuseVisible;
	private Entity searchCamera;
	private boolean captureReuse;
	private long reuseEpoch, capturedReuseEpoch;
	private long hysteresisSkips;

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

		this.captureReuse = false;
		this.searchCamera = camera;
		boolean verifyingVisibility = config.verifyChunkVisibility || config.verifyCameraHysteresis;
		this.capturedReuseEpoch = this.reuseEpoch;
		if ((config.cameraHysteresis || config.verifyCameraHysteresis) && (config.fastChunkVisibility || verifyingVisibility) && reuseInputs(camera, tickDelta, culler, loadChunks)) {
			prepare(this.access.alacer$getChunkStorage());
			List<WorldRenderer.RenderChunkInfo> current = this.access.alacer$getVisibleChunks();
			if (current == this.reuseVisible && current != null && !current.isEmpty()
				&& this.access.alacer$getDirtyChunks().isEmpty()
				&& this.visibilityReuse.matches(camera, this.access.alacer$getChunkStorage(), this.reuseInputs)
				&& compiledUnchanged()) {
				if (verifyingVisibility) {
					// Check the retained candidate list against an actual vanilla search.
					snapshotExpected(current);
				} else {
					this.hysteresisSkips++;
					this.access.alacer$setViewChanged(false);
				}
				return;
			}
			if (this.reuseCompiled.length != this.chunks.length) this.reuseCompiled = new CompiledChunk[this.chunks.length];
			for (int i = 0; i < this.chunks.length; i++) this.reuseCompiled[i] = this.chunks[i].getCompiledChunk();
			this.captureReuse = true;
		} else {
			this.visibilityReuse.invalidate();
		}
		if (profiler != null) {
			profiler.count(Counter.VISIBILITY_SEARCHES);
		}
		if (verifyingVisibility) {
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
		if (this.captureReuse) {
			if (this.capturedReuseEpoch == this.reuseEpoch && compiledUnchanged()) {
				this.visibilityReuse.remember(this.searchCamera, this.access.alacer$getChunkStorage(), this.reuseInputs);
				this.reuseVisible = this.access.alacer$getVisibleChunks();
			} else this.visibilityReuse.invalidate();
		}
		this.captureReuse = false;
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

	public static String neighbourSummary() { return neighbourChecks + " compared, " + neighbourFailures + " mismatches, " + neighbourLookups + " indexed lookups"; }

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
		if ((config.vectorFrustum || config.verifyVectorFrustum) && !this.vectorAttempted) {
			this.vectorAttempted = true;
			this.vectorFrustum = VectorFrustumAccess.create();
		}
		if ((config.vectorFrustum || config.verifyVectorFrustum) && this.vectorFrustum != null && this.vectorFrustum.prepare(culler)) {
			this.frustumMode = config.verifyVectorFrustum ? FRUSTUM_VECTOR_VERIFY : FRUSTUM_VECTOR;
		} else if ((config.fastChunkFrustum || config.verifyChunkFrustum) && this.frustum.prepare(culler)) {
			this.frustumMode = config.verifyChunkFrustum ? FRUSTUM_VERIFY : FRUSTUM_PLANES;
		} else {
			this.frustumMode = FRUSTUM_CULLER;
		}
		boolean adjacent = (config.fastChunkNeighbours || config.verifyChunkNeighbours)
			&& tickDelta >= 0 && tickDelta <= 1 && Math.abs(camera.x) <= 30_000_000 && Math.abs(camera.z) <= 30_000_000
			&& Math.abs(camera.prevX) <= 30_000_000 && Math.abs(camera.prevZ) <= 30_000_000
			&& storage.getClass() == RenderChunkStorage.class && RenderCompatibility.neighbours();
		if (adjacent && this.neighbours == null) {
			this.neighbours = new ChunkNeighbours(this.sizeX, this.sizeY, this.sizeZ, OFFSET_X, OFFSET_Y, OFFSET_Z);
		}
		this.frustumTests = 0;
		if (this.frustumTiming) {
			this.testedCount = 0;
			this.testedCuller = culler;
		}

		double camX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
		double camY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
		double camZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
		int eyeX = (int) Math.floor(camX);
		int eyeY = (int) Math.floor(camY + camera.getEyeHeight());
		int eyeZ = (int) Math.floor(camZ);
		this.searchEye.set(eyeX, eyeY, eyeZ);
		// Range checks are relative to the chunk containing the camera's feet, as in vanilla.
		int originX = floor16(camX);
		int originZ = floor16(camZ);
		int range = this.access.alacer$getLastViewDistance() * 16;
		boolean smartCull = Minecraft.getInstance().smartCull;

		int head = 0;
		int tail = 0;
		int cameraSlot = slot(eyeX, eyeY, eyeZ);
		if (cameraSlot >= 0) {
			int open = openFaces(this.searchEye);
			if (Integer.bitCount(open) == 1) {
				Vector3f look = this.access.alacer$getLookVector(camera, tickDelta);
				open &= ~(1 << Direction.getNearest(look.x, look.y, look.z).getOpposite().ordinal());
			}

			if (open == 0 && !loadChunks) {
				// Camera is sealed inside its own chunk: only that chunk can be seen.
				out.add(info(cameraSlot));
				return out;
			}
			if (loadChunks && this.access.alacer$getWorld().getBlockState(this.searchEye).getBlock().isSolidRender()) {
				smartCull = false;
			}
			visit(cameraSlot, frame, markFrames);
			this.sourceDir[cameraSlot] = NO_DIRECTION;
			this.travelled[cameraSlot] = 0;
			this.queue[tail++] = cameraSlot;
		} else {
			// Camera above or below the world: seed with the top or bottom layer. Like vanilla,
			// this layer is centred on the world origin rather than on the camera.
			int y = eyeY > 0 ? 248 : 8;
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

			long occlusionMask = 0L;
			int allowedFaces = 0x3F;
			if (smartCull && from != NO_DIRECTION && (config.flatOcclusion || config.verifyFlatOcclusion)) {
				occlusionMask = getOcclusionMask(current, chunk);
				allowedFaces = (int) ((occlusionMask >>> (OPPOSITE[from] * 6)) & 0x3F);
			}

			for (int d = 0; d < 6; d++) {
				// Never step back against a direction already travelled on this path.
				if (smartCull && (mask & (1 << OPPOSITE[d])) != 0) {
					continue;
				}
				// Only continue through faces connected to the face this chunk was entered by.
				if (smartCull && from != NO_DIRECTION) {
					if (config.flatOcclusion || config.verifyFlatOcclusion) {
						boolean visible = (allowedFaces & (1 << d)) != 0;
						if (config.verifyFlatOcclusion) {
							boolean vanillaVis = chunk.getCompiledChunk().isVisible(DIRECTIONS[OPPOSITE[from]], DIRECTIONS[d]);
							if (visible != vanillaVis) {
								flatOcclusionMismatches++;
								if (flatOcclusionMismatches <= 10) {
									Alacer.LOGGER.warn("render.flatOcclusion mismatch for face {} -> {}: flat {}, vanilla {}",
										OPPOSITE[from], d, visible, vanillaVis);
								}
							}
							visible = vanillaVis;
						}
						if (!visible) {
							continue;
						}
					} else if (!chunk.getCompiledChunk().isVisible(DIRECTIONS[OPPOSITE[from]], DIRECTIONS[d])) {
						continue;
					}
				}
				int nx = x + OFFSET_X[d];
				int ny = y + OFFSET_Y[d];
				int nz = z + OFFSET_Z[d];
				if (Math.abs(originX - nx) > range || ny < 0 || ny >= 256 || Math.abs(originZ - nz) > range) {
					continue;
				}
				int next = adjacent ? this.neighbours.next(current, d) : slot(nx, ny, nz);
				if (adjacent) { neighbourLookups++; }
				if (adjacent && config.verifyChunkNeighbours) {
					int expected = slot(nx, ny, nz); neighbourChecks++;
					if (next != expected) { neighbourFailures++; throw new IllegalStateException("Chunk adjacency differs from original slot math"); }
					next = expected;
				}
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
			case FRUSTUM_VECTOR:
				return this.vectorFrustum.isVisible(bounds);
			case FRUSTUM_VECTOR_VERIFY:
				return this.vectorFrustum.verify(bounds, culler);
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
		this.neighbours = null;
		this.sizeX = dims.alacer$getSizeX();
		this.sizeY = dims.alacer$getSizeY();
		this.sizeZ = dims.alacer$getSizeZ();
		int n = this.chunks.length;
		this.infos = new WorldRenderer.RenderChunkInfo[n];
		for (int i = 0; i < n; i++) {
			this.infos[i] = this.renderer.new RenderChunkInfo(this.chunks[i], null, 0);
		}
		this.visited = new int[n];
		this.travelled = new byte[n];
		this.sourceDir = new byte[n];
		this.queue = new int[n];
		this.flatOcclusion = new long[n];
		this.compiledOcclusion = new CompiledChunk[n];
		if (this.frustumTiming) {
			this.tested = new int[n];
		}
		if (this.expected.length < n) {
			this.expected = new RenderChunk[n];
		}
		if (this.compiledSnapshot.length < n) {
			this.compiledSnapshot = new CompiledChunk[n];
		}
		if (this.reuseCompiled.length < n) {
			this.reuseCompiled = new CompiledChunk[n];
		}
		((ArrayList<?>) this.result).ensureCapacity(n);
		this.stamp = 0;
		this.facesValid = false;
		this.sectionValid = false;
	}

	private int openFaces(BlockPos eye) {
		ClientWorld world = this.access.alacer$getWorld();
		WorldChunk chunk = world.getChunk(eye);
		AlacerConfig config = Alacer.get().config();
		if (config.fastCameraSectionFaces || config.verifyCameraSectionFaces) {
			// The per-block cache below is not maintained meanwhile; never resume from it.
			this.facesValid = false;
			return sectionFaces(chunk, eye, config.verifyCameraSectionFaces);
		}
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

	/**
	 * {@code getOpenChunkFaces(eye)} as a direction mask, from {@link SectionFaces}. The section's
	 * blocks are read again unless they are known unchanged since the last read (same chunk object
	 * and section, populated when read, no render-dirty region touching it since), and the last
	 * flood fill's answer is reused when the eye is in the pocket it reached.
	 */
	private int sectionFaces(WorldChunk chunk, BlockPos eye, boolean verify) {
		int x = eye.getX();
		int y = eye.getY();
		int z = eye.getZ();
		int sx = x & ~15;
		int sy = y & ~15;
		int sz = z & ~15;
		boolean unchanged = this.sectionValid && this.sectionChunk == chunk && this.sectionX == sx && this.sectionY == sy && this.sectionZ == sz;
		int mask;
		if (unchanged && this.section.reuses(x & 15, y & 15, z & 15)) {
			mask = this.section.faces();
			sectionReuses++;
		} else {
			if (!unchanged) {
				// The same reads as vanilla's getOpenChunkFaces, through one reused position.
				SectionFaces section = this.section;
				BlockPos.Mutable cell = this.sectionCell;
				section.clear();
				for (int cy = 0; cy < 16; cy++) {
					for (int cz = 0; cz < 16; cz++) {
						for (int cx = 0; cx < 16; cx++) {
							if (chunk.getBlock(cell.set(sx + cx, sy + cy, sz + cz)).isSolidRender()) {
								section.setOpaque(cx, cy, cz);
							}
						}
					}
				}
				this.sectionChunk = chunk;
				this.sectionX = sx;
				this.sectionY = sy;
				this.sectionZ = sz;
				// Block changes in a chunk without its first client tick are not reported to
				// markDirty, so such a chunk's blocks are read again every time.
				this.sectionValid = chunk.isPopulated();
				sectionReads++;
			}
			mask = this.section.fill(x & 15, y & 15, z & 15);
			sectionFills++;
		}
		if (verify) {
			int expected = mask(this.access.alacer$getOpenChunkFaces(eye));
			sectionCompared++;
			if (expected != mask) {
				sectionMismatches++;
				if (sectionMismatches <= 10) {
					Alacer.LOGGER.warn("Camera section faces mismatch at {}: Alacer {} vs vanilla {} (section {})",
						eye, Integer.toBinaryString(mask), Integer.toBinaryString(expected), unchanged ? "reused" : "read");
				}
			}
			if (sectionCompared % 1000 == 0) {
				Alacer.LOGGER.info("Camera section faces verification: {}", sectionFacesSummary());
			}
			return expected;
		}
		return mask;
	}

	private static int mask(Set<Direction> directions) {
		int mask = 0;
		for (Direction direction : directions) {
			mask |= 1 << direction.ordinal();
		}
		return mask;
	}

	/** Counts for {@code render.fastCameraSectionFaces} and its verification, over all renderers. */
	public static String sectionFacesSummary() {
		return sectionReads + " section reads, " + sectionFills + " flood fills, " + sectionReuses + " reused answers; "
			+ sectionCompared + " compared with vanilla, " + sectionMismatches + " mismatched";
	}

	/** Called for every render-dirty region; drops the cached faces if they could be affected. */
	public void onRegionDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.reuseEpoch++;
		this.visibilityReuse.invalidate();
		if (this.sectionValid) {
			int sx = this.sectionX;
			int sy = this.sectionY;
			int sz = this.sectionZ;
			if (maxX >= sx && minX <= sx + 15 && maxY >= sy && minY <= sy + 15 && maxZ >= sz && minZ <= sz + 15) {
				this.sectionValid = false;
			}
		}
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
		this.neighbours = null;
		this.infos = null;
		this.facesValid = false;
		this.facesChunk = null;
		this.sectionValid = false;
		this.sectionChunk = null;
		this.testedCuller = null;
		this.searchCamera = null;
		this.reuseVisible = null;
		this.captureReuse = false;
		this.visibilityReuse.invalidate();
		this.result.clear();
	}

	private boolean compiledUnchanged() {
		if (this.reuseCompiled.length != this.chunks.length) return false;
		for (int i = 0; i < this.chunks.length; i++) if (this.reuseCompiled[i] != this.chunks[i].getCompiledChunk()) return false;
		return true;
	}

	private void snapshotExpected(List<WorldRenderer.RenderChunkInfo> list) {
		if (this.compiledSnapshot.length != this.chunks.length) this.compiledSnapshot = new CompiledChunk[this.chunks.length];
		for (int i = 0; i < this.chunks.length; i++) this.compiledSnapshot[i] = this.chunks[i].getCompiledChunk();
		if (this.expected.length < list.size()) this.expected = new RenderChunk[list.size()];
		for (int i = 0; i < list.size(); i++) this.expected[i] = list.get(i).chunk;
		this.expectedCount = list.size();
		this.verifyPending = true;
	}

	/** All vanilla traversal inputs, not just the camera pose. No approximate boundary reuse. */
	private boolean reuseInputs(Entity camera, double delta, Culler culler, boolean loadChunks) {
		if (culler == null || culler.getClass() != FrustumCuller.class || !ChunkFrustum.compatible()) return false;
		FrustumCullerAccessor access = (FrustumCullerAccessor) culler;
		FrustumData data = access.alacer$getFrustum();
		if (data == null || (data.getClass() != FrustumData.class && data.getClass() != Frustum.class)) return false;
		if (data.frustum == null || data.frustum.length != 6) return false;
		for (float[] plane : data.frustum) {
			if (plane == null || plane.length < 4) return false;
			for (int i = 0; i < 4; i++) if (!Float.isFinite(plane[i])) return false;
		}
		int n = 0;
		for (float[] plane : data.frustum) for (int i = 0; i < 4; i++) this.reuseInputs[n++] = plane[i];
		this.reuseInputs[n++] = access.alacer$getOffsetX();
		this.reuseInputs[n++] = access.alacer$getOffsetY();
		this.reuseInputs[n++] = access.alacer$getOffsetZ();
		this.reuseInputs[n++] = camera.x; this.reuseInputs[n++] = camera.y; this.reuseInputs[n++] = camera.z;
		this.reuseInputs[n++] = camera.prevX; this.reuseInputs[n++] = camera.prevY; this.reuseInputs[n++] = camera.prevZ;
		this.reuseInputs[n++] = camera.pitch; this.reuseInputs[n++] = camera.yaw;
		this.reuseInputs[n++] = camera.lastPitch; this.reuseInputs[n++] = camera.lastYaw;
		this.reuseInputs[n++] = camera.getEyeHeight(); this.reuseInputs[n++] = delta;
		this.reuseInputs[n++] = loadChunks ? 1 : 0;
		this.reuseInputs[n++] = Minecraft.getInstance().smartCull ? 1 : 0;
		this.reuseInputs[n++] = Minecraft.getInstance().options.perspective;
		this.reuseInputs[n++] = this.access.alacer$getLastViewDistance();
		AlacerConfig config = Alacer.get().config();
		this.reuseInputs[n++] = config.flatOcclusion ? 1 : 0;
		this.reuseInputs[n++] = config.vectorFrustum ? 1 : 0;
		this.reuseInputs[n++] = config.fastChunkFrustum ? 1 : 0;
		this.reuseInputs[n++] = config.fastCameraSectionFaces ? 1 : 0;
		this.reuseInputs[n++] = config.fastChunkNeighbours ? 1 : 0;
		this.reuseInputs[n++] = config.verifyFlatOcclusion ? 1 : 0;
		this.reuseInputs[n++] = config.verifyVectorFrustum ? 1 : 0;
		this.reuseInputs[n++] = config.verifyChunkFrustum ? 1 : 0;
		return true;
	}

	public long hysteresisSkips() {
		return this.hysteresisSkips;
	}
	public static String occlusionSummary() {
		return flatOcclusionHits + " mask hits, " + flatOcclusionUpdates + " updates, " + flatOcclusionMismatches + " mismatches";
	}

	private long getOcclusionMask(int slot, RenderChunk chunk) {
		CompiledChunk compiled = chunk.getCompiledChunk();
		if (this.compiledOcclusion[slot] == compiled) {
			flatOcclusionHits++;
			return this.flatOcclusion[slot];
		}
		flatOcclusionUpdates++;
		long mask = packOcclusion(compiled);
		this.compiledOcclusion[slot] = compiled;
		this.flatOcclusion[slot] = mask;
		return mask;
	}

	public static long packOcclusion(CompiledChunk compiled) {
		if (compiled == null || compiled == CompiledChunk.UNCOMPILED) {
			return 0L;
		}
		long mask = 0L;
		for (int f = 0; f < 6; f++) {
			for (int t = 0; t < 6; t++) {
				if (compiled.isVisible(DIRECTIONS[f], DIRECTIONS[t])) {
					mask |= (1L << (f * 6 + t));
				}
			}
		}
		return mask;
	}

}
