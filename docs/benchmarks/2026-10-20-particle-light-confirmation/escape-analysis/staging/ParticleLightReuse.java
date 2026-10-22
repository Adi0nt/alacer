package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Per-pass particle light reuse (session 22, {@code render.reuseParticleLight}).
 *
 * <p>In the particle scenario one unlit {@code ParticleManager.render} pass makes about 3,900
 * (scale 1) or 6,400 (scale 2) light queries but hits only about 400 distinct block positions:
 * 90-94% of the queries repeat a position already answered in the same pass. Nothing can change
 * the client world while the pass runs: it runs on the client thread, which applies block, light
 * and chunk changes only in its tick and in the packet tasks it drains at the start of each
 * frame, never from particle render code, and other threads never write the client world.
 *
 * <p>With the option on, while the startup-gated pass hooks ({@code ParticleManagerLightReuseMixin})
 * mark an unlit pass, queries that {@link ParticleLight} answers (optimization 7 on and not
 * verifying) and whose world is the manager's world are answered from a table of the sky and
 * block brightness already computed in that pass for the same (x, y, z); a position seen for the
 * first time is computed by optimization 7's code (sky, then block) and stored. The requested
 * minimum block light is applied after the lookup, as in {@code World.getLightColor}, so the key
 * is only the position. The table is emptied at every pass start (a generation stamp), at the
 * lit pass that starts each frame's particle drawing, and when the renderer's world changes;
 * nothing survives from one pass to the next. A full table (3,072 positions) answers further new
 * positions without storing them. Particle order, the particles drawn, vertex data and draw calls
 * are untouched. Client thread only (the owner captured at startup); no GL objects; no allocation.
 *
 * <p>Verification ({@code render.reuseParticleLight.verify}, with the option on) runs the table as
 * above, also runs vanilla's {@code World.getLightColor} for every query the table answers, uses
 * vanilla's result and logs differences.
 */
public final class ParticleLightReuse {

	private static final long SUMMARY_EVERY = 10_000_000L;
	private static final Table TABLE = new Table(1 << 12);

	/** Whether the pass hooks were requested at startup (the mixin plugin's rule); folds the check away otherwise. */
	public static final boolean HOOKED;

	static {
		Alacer alacer = Alacer.get();
		AlacerConfig config = alacer != null ? alacer.config() : null;
		HOOKED = config != null && (config.reuseParticleLight || config.verifyReuseParticleLight
			|| "render.reuseParticleLight".equals(System.getProperty("alacer.benchmark.toggle", "").trim()));
	}

	private static Thread owner;
	private static World world;
	private static boolean verifying;

	private static long passes;
	private static long hits;
	private static long misses;
	private static long unstored;
	private static long verified;
	private static long verifiedHits;
	private static long mismatches;

	private ParticleLightReuse() {
	}

	/** Called on the client thread at startup, after the config is loaded (fixes {@link #HOOKED}). */
	public static void init() {
		owner = Thread.currentThread();
	}

	/** {@code ParticleManager.render} head: opens a pass for the manager's world if the option answers. */
	public static void beginPass(World managerWorld) {
		AlacerConfig config = Alacer.get().config();
		world = null;
		if (config.reuseParticleLight && config.fastParticleLight && !config.verifyFastParticleLight
			&& managerWorld != null && managerWorld.getClass() == ClientWorld.class && Thread.currentThread() == owner) {
			TABLE.clear();
			verifying = config.verifyReuseParticleLight;
			world = managerWorld;
			passes++;
		}
	}

	/** {@code ParticleManager.render} return, the lit pass's head and world changes: no reuse outside a pass. */
	public static void endPass() {
		world = null;
	}

	/** True inside a pass for this world; {@link ParticleLight} has already checked the particle, world class and thread. */
	static boolean answers(World queryWorld) {
		return queryWorld == world && queryWorld != null;
	}

	/** Verification state saved at pass start; callers must first check {@link #answers}. */
	static boolean verifying() {
		return verifying;
	}

	/** Scalar-only reuse boundary: table lookup and minimum-light composition, without verification. */
	static int lightColor(World world, int x, int y, int z, int blockLight) {
		int slot = TABLE.find(x, y, z);
		int sky;
		int block;
		boolean hit = slot >= 0;
		if (hit) {
			sky = TABLE.sky[slot];
			block = TABLE.block[slot];
			hits++;
		} else {
			sky = ParticleLight.brightness(world, net.minecraft.world.LightType.SKY, x, y, z);
			block = ParticleLight.brightness(world, net.minecraft.world.LightType.BLOCK, x, y, z);
			misses++;
			if (slot == Table.FULL) {
				unstored++;
			} else {
				TABLE.put(~slot, x, y, z, sky, block);
			}
		}
		if (block < blockLight) {
			block = blockLight;
		}
		int color = sky << 20 | block << 4;
		return color;
	}

	/** Separate object boundary; vanilla verification always uses the caller's original position. */
	static int lightColorVerified(World world, BlockPos pos, int x, int y, int z, int blockLight) {
		long hitsBefore = hits;
		int color = lightColor(world, x, y, z, blockLight);
		boolean hit = hits != hitsBefore;
		int expected = world.getLightColor(pos, blockLight);
		verified++;
		if (hit) {
			verifiedHits++;
		}
		if (color != expected) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("Particle light reuse mismatch at {} ({}): {} vs vanilla {}", pos, hit ? "reused" : "computed",
					Integer.toHexString(color), Integer.toHexString(expected));
			}
		}
		if (verified % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Particle light reuse verification: {}", verificationSummary());
		}
		return expected;
	}

	public static String verificationSummary() {
		return passes + " passes, " + hits + " reused, " + misses + " computed (" + unstored + " not stored); " + verified
			+ " queries compared (" + verifiedHits + " reused), " + mismatches + " mismatched";
	}

	/**
	 * Open-addressing (x, y, z) table emptied in O(1) by a generation stamp. {@link #find} returns
	 * the slot of a stored position, or the complement of the empty slot where it would go, or
	 * {@link #FULL} once three quarters of the slots are used.
	 */
	static final class Table {

		static final int FULL = Integer.MIN_VALUE;

		private final int mask;
		private final int limit;
		private final int[] stamps;
		private final int[] xs;
		private final int[] ys;
		private final int[] zs;
		final int[] sky;
		final int[] block;
		private int stamp = 1;
		private int size;

		Table(int capacity) {
			this.mask = capacity - 1;
			this.limit = capacity / 4 * 3;
			this.stamps = new int[capacity];
			this.xs = new int[capacity];
			this.ys = new int[capacity];
			this.zs = new int[capacity];
			this.sky = new int[capacity];
			this.block = new int[capacity];
		}

		void clear() {
			this.size = 0;
			if (++this.stamp == 0) {
				java.util.Arrays.fill(this.stamps, 0);
				this.stamp = 1;
			}
		}

		int find(int x, int y, int z) {
			int h = x * 0x9E3779B1 ^ y * 0x85EBCA77 ^ z * 0xC2B2AE3D;
			h ^= h >>> 15;
			h *= 0x2C1B3C6D;
			h ^= h >>> 13;
			for (int i = h & this.mask;; i = i + 1 & this.mask) {
				if (this.stamps[i] != this.stamp) {
					return this.size >= this.limit ? FULL : ~i;
				}
				if (this.xs[i] == x && this.ys[i] == y && this.zs[i] == z) {
					return i;
				}
			}
		}

		void put(int slot, int x, int y, int z, int skyValue, int blockValue) {
			this.stamps[slot] = this.stamp;
			this.xs[slot] = x;
			this.ys[slot] = y;
			this.zs[slot] = z;
			this.sky[slot] = skyValue;
			this.block[slot] = blockValue;
			this.size++;
		}
	}
}
