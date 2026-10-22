package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.FrameProfiler.Counter;

/**
 * Attribution counter for roadmap item 0 (per-pass particle light reuse): how many particle
 * light queries one unlit {@code ParticleManager.render} pass makes, and how many distinct
 * (x, y, z, blockLight) arguments and (x, y, z) positions they hit, plus how many repeat the
 * previous query's arguments exactly.
 *
 * <p>{@code profiler.particleLightRepeats} is read once, when this class is first used (during
 * rendering, after the mod has initialized), into a constant, so the check in
 * {@link ParticleLight#lightColor} folds away when it is off and the tables are never allocated.
 * When on, it costs two open-addressing set inserts per query: attribution only, not performance
 * selection. Queries are those of particles reaching optimization 7's redirect, whether or not
 * optimization 7 answers them. Client thread only.
 */
public final class ParticleLightRepeats {

	/** Fixed at first use; see the class comment. */
	public static final boolean ENABLED;

	private static final FrameProfiler PROFILER;
	private static final StampSet KEYS;
	private static final StampSet POSITIONS;

	static {
		Alacer alacer = Alacer.get();
		boolean enabled = alacer != null && alacer.config() != null && alacer.config().profilerParticleLightRepeats
			&& alacer.profiler() != null;
		ENABLED = enabled;
		PROFILER = enabled ? alacer.profiler() : null;
		KEYS = enabled ? new StampSet(1 << 16) : null;
		POSITIONS = enabled ? new StampSet(1 << 16) : null;
	}

	private static boolean active;
	private static int queries;
	private static int keys;
	private static int positions;
	private static int samePrevious;
	private static boolean hasPrevious;
	private static int previousX;
	private static int previousY;
	private static int previousZ;
	private static int previousLight;

	private static long passes;
	private static long totalQueries;
	private static long totalKeys;
	private static long totalPositions;
	private static long totalSamePrevious;
	private static long outside;
	private static long overflowedPasses;
	private static int maxQueries;
	private static int maxKeys;

	private ParticleLightRepeats() {
	}

	/** {@code ParticleManager.render} head. */
	public static void beginPass() {
		if (!ENABLED) {
			return;
		}
		active = true;
		queries = 0;
		keys = 0;
		positions = 0;
		samePrevious = 0;
		hasPrevious = false;
		KEYS.clear();
		POSITIONS.clear();
	}

	/** {@code ParticleManager.render} return. */
	public static void endPass() {
		if (!ENABLED || !active) {
			return;
		}
		active = false;
		passes++;
		totalQueries += queries;
		totalKeys += keys;
		totalPositions += positions;
		totalSamePrevious += samePrevious;
		maxQueries = Math.max(maxQueries, queries);
		maxKeys = Math.max(maxKeys, keys);
		if (KEYS.overflowed || POSITIONS.overflowed) {
			overflowedPasses++;
		}
		PROFILER.count(Counter.PARTICLE_LIGHT_QUERIES, queries);
		PROFILER.count(Counter.PARTICLE_LIGHT_KEYS, keys);
		PROFILER.count(Counter.PARTICLE_LIGHT_POSITIONS, positions);
		PROFILER.count(Counter.PARTICLE_LIGHT_SAME_AS_PREVIOUS, samePrevious);
	}

	/** One particle light query's arguments ({@code World.getLightColor(pos, blockLight)}). */
	public static void record(int x, int y, int z, int blockLight) {
		if (!active) {
			outside++;
			return;
		}
		queries++;
		if (hasPrevious && x == previousX && y == previousY && z == previousZ && blockLight == previousLight) {
			samePrevious++;
		}
		hasPrevious = true;
		previousX = x;
		previousY = y;
		previousZ = z;
		previousLight = blockLight;
		if (KEYS.add(x, y, z, blockLight)) {
			keys++;
		}
		if (POSITIONS.add(x, y, z, 0)) {
			positions++;
		}
	}

	public static String summary() {
		if (!ENABLED) {
			return "off";
		}
		return passes + " passes, " + totalQueries + " queries, " + totalKeys + " distinct (x, y, z, blockLight), "
			+ totalPositions + " distinct positions, " + totalSamePrevious + " same as previous, max " + maxQueries
			+ " queries / " + maxKeys + " keys per pass, " + outside + " outside the pass, " + overflowedPasses
			+ " passes overflowed";
	}

	/** Open-addressing set of int quadruples, cleared in O(1) by a generation stamp. */
	static final class StampSet {

		private final int mask;
		private final int limit;
		private final int[] stamps;
		private final int[] xs;
		private final int[] ys;
		private final int[] zs;
		private final int[] ws;
		private int stamp = 1;
		private int size;
		boolean overflowed;

		StampSet(int capacity) {
			this.mask = capacity - 1;
			this.limit = capacity / 4 * 3;
			this.stamps = new int[capacity];
			this.xs = new int[capacity];
			this.ys = new int[capacity];
			this.zs = new int[capacity];
			this.ws = new int[capacity];
		}

		void clear() {
			this.size = 0;
			this.overflowed = false;
			if (++this.stamp == 0) {
				java.util.Arrays.fill(this.stamps, 0);
				this.stamp = 1;
			}
		}

		/** True if the quadruple was not in the set; past three quarters full, nothing is added. */
		boolean add(int x, int y, int z, int w) {
			int h = x * 0x9E3779B1 ^ y * 0x85EBCA77 ^ z * 0xC2B2AE3D ^ w * 0x27D4EB2F;
			h ^= h >>> 15;
			h *= 0x2C1B3C6D;
			h ^= h >>> 13;
			for (int i = h & this.mask;; i = i + 1 & this.mask) {
				if (this.stamps[i] != this.stamp) {
					if (this.size >= this.limit) {
						this.overflowed = true;
						return false;
					}
					this.stamps[i] = this.stamp;
					this.xs[i] = x;
					this.ys[i] = y;
					this.zs[i] = z;
					this.ws[i] = w;
					this.size++;
					return true;
				}
				if (this.xs[i] == x && this.ys[i] == y && this.zs[i] == z && this.ws[i] == w) {
					return false;
				}
			}
		}
	}
}
