package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Particle light entry (session 21, {@code render.fastParticleLightEntry}).
 *
 * <p>Before the light query that {@link ParticleLight} answers, every drawn particle's
 * {@code Entity.getLightLevel} copies its eye position into a new {@code BlockPos} and asks
 * {@code World.isChunkLoaded(pos)}; block-break particles ask it again for their source block in
 * {@code BlockParticle.getLightLevel}. For the client world that check is {@code World.contains}
 * (the +-30,000,000 horizontal limits and build height 0-255) and then the chunk source's
 * {@code hasChunk}, which {@code ClientChunkCache} answers with a constant {@code true}; the
 * empty-chunk test is skipped because vanilla passes {@code allowEmpty = true}. The call goes
 * through {@code World.isChunkLoadedAt}'s interface call, whose call site is shared with the
 * integrated server's worlds and their hash-backed {@code ServerChunkCache}.
 *
 * <p>With the option on, for particles in the exact {@link ClientWorld} class whose chunk source is
 * exactly a {@link ClientChunkCache}, on the client thread captured at startup, the check is
 * answered with the same range test on the same coordinates, without the chunk-source call.
 * Every other caller and world takes vanilla's call.
 *
 * <p>The position copy is avoided only where vanilla's value cannot reach other code: when this
 * option answers the chunk check and {@link ParticleLight} answers the light query (both on, neither
 * verifying), {@code getLightLevel}'s position is one reused mutable position set to vanilla's
 * floored coordinates, which only those two answers read before {@code getLightLevel} returns.
 * Otherwise vanilla's {@code BlockPos} is allocated as before. Nothing is cached; no GL objects.
 *
 * <p>Verification ({@code render.fastParticleLightEntry.verify}) keeps vanilla's position copy,
 * checks that the floored coordinates the mutable position would take equal vanilla's, runs
 * vanilla's {@code isChunkLoaded} for every answered check, uses vanilla's result and logs
 * differences.
 */
public final class ParticleLightEntry {

	private static final int LIMIT = 30000000;
	private static final long SUMMARY_EVERY = 10_000_000L;

	private static final BlockPos.Mutable POS = new BlockPos.Mutable();
	private static final BlockPos.Mutable VERIFY_POS = new BlockPos.Mutable();

	private static Thread owner;
	private static long checks;
	private static long checkMismatches;
	private static long positions;
	private static long positionMismatches;
	private static long notLoaded;

	private ParticleLightEntry() {
	}

	/** Called on the client thread at startup; only that thread answers checks or reuses the position. */
	public static void init() {
		owner = Thread.currentThread();
	}

	/** Replaces {@code new BlockPos(x, y, z)} in {@code Entity.getLightLevel}. */
	public static BlockPos position(Object entity, World world, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		if (config.fastParticleLightEntry && !config.verifyFastParticleLightEntry && config.fastParticleLight
			&& !config.verifyFastParticleLight && answers(entity, world)) {
			return floored(POS, x, y, z);
		}
		BlockPos pos = new BlockPos(x, y, z);
		if (config.fastParticleLightEntry && config.verifyFastParticleLightEntry && answers(entity, world)) {
			positions++;
			BlockPos.Mutable reused = floored(VERIFY_POS, x, y, z);
			if (reused.getX() != pos.getX() || reused.getY() != pos.getY() || reused.getZ() != pos.getZ()) {
				positionMismatches++;
				if (positionMismatches <= 10) {
					Alacer.LOGGER.warn("Particle light entry position mismatch for ({}, {}, {}): vanilla {}", x, y, z, pos);
				}
			}
		}
		return pos;
	}

	/** Replaces {@code world.isChunkLoaded(pos)} in the particle light methods. */
	public static boolean isChunkLoaded(Object entity, World world, BlockPos pos) {
		AlacerConfig config = Alacer.get().config();
		if (!config.fastParticleLightEntry || !answers(entity, world)) {
			return world.isChunkLoaded(pos);
		}
		// ClientChunkCache.hasChunk is true and allowEmpty skips the empty-chunk test.
		boolean loaded = contains(pos.getX(), pos.getY(), pos.getZ());
		if (config.verifyFastParticleLightEntry) {
			boolean expected = world.isChunkLoaded(pos);
			checks++;
			if (!expected) {
				notLoaded++;
			}
			if (loaded != expected) {
				checkMismatches++;
				if (checkMismatches <= 10) {
					Alacer.LOGGER.warn("Particle light entry mismatch at {}: {} vs vanilla {}", pos, loaded, expected);
				}
			}
			if (checks % SUMMARY_EVERY == 0) {
				Alacer.LOGGER.info("Particle light entry verification: {}", verificationSummary());
			}
			return expected;
		}
		return loaded;
	}

	/** {@code new BlockPos(x, y, z)}'s coordinates ({@code Vec3i} floors each with {@code MathHelper.floor}). */
	static BlockPos.Mutable floored(BlockPos.Mutable pos, double x, double y, double z) {
		return pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
	}

	/** {@code World.contains(BlockPos)}: inside the horizontal limits and build height. */
	static boolean contains(int x, int y, int z) {
		return x >= -LIMIT && z >= -LIMIT && x < LIMIT && z < LIMIT && y >= 0 && y < 256;
	}

	/** A particle in the exact client world class with vanilla's client chunk source, on the client thread. */
	private static boolean answers(Object entity, World world) {
		return entity instanceof Particle && world.getClass() == ClientWorld.class
			&& world.getChunkSource().getClass() == ClientChunkCache.class && Thread.currentThread() == owner;
	}

	public static String verificationSummary() {
		return checks + " chunk checks compared (" + notLoaded + " not loaded), " + checkMismatches + " mismatched; "
			+ positions + " positions compared, " + positionMismatches + " mismatched";
	}
}
