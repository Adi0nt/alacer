package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Allocation-free entity lighting queries (optimization Phase 2).
 *
 * <p>Every frame, every visible entity queries its light level and brightness during rendering
 * (e.g., in {@code LivingEntityRenderer.setupOverlayColor} and {@code render}). Vanilla's
 * {@code Entity.getLightLevel} and {@code Entity.getBrightness} allocate a {@code new BlockPos}
 * at the entity's eye position on every call and perform un-cached chunk hash-map lookups.
 * In scenes with 200 mobs, this generates tens of thousands of {@code BlockPos} allocations
 * per second (over 30 KB per frame).
 *
 * <p>With {@code render.fastEntityLighting}, integer coordinates are evaluated directly from
 * {@code entity.x}, {@code entity.y + entity.getEyeHeight()}, and {@code entity.z} without
 * object allocation, the loaded chunk is checked via the chunk source, and light/brightness
 * are evaluated via the cached chunk reference tables of {@link ParticleLight} and {@link EntityShadows}.
 *
 * <p>Only runs on the client thread for {@link ClientWorld}. Particles continue through their
 * specialized {@link ParticleLight} pipeline. Verification mode ({@code render.fastEntityLighting.verify})
 * runs vanilla's exact calculation, checks bit-level identity, and logs mismatches.
 */
public final class EntityLighting {

	private static final int LIMIT = 30000000;
	private static final long SUMMARY_EVERY = 1_000_000L;

	private static Thread clientThread;
	private static long verified;
	private static long mismatches;

	private EntityLighting() {
	}

	public static void init() {
		clientThread = Thread.currentThread();
	}

	public static boolean isEnabled(Entity entity) {
		if (entity == null || entity instanceof Particle) {
			return false;
		}
		World world = entity.world;
		if (world == null || world.getClass() != ClientWorld.class) {
			return false;
		}
		if (clientThread != null && Thread.currentThread() != clientThread) {
			return false;
		}
		AlacerConfig config = Alacer.get().config();
		return config != null && config.fastEntityLighting;
	}

	public static boolean isVerify() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyFastEntityLighting;
	}

	/**
	 * Allocation-free replacement for {@code Entity.getLightLevel(float)}.
	 */
	public static int getLightLevel(Entity entity, float tickDelta) {
		World world = entity.world;
		double eyeY = entity.y + (double) entity.getEyeHeight();
		int x = MathHelper.floor(entity.x);
		int y = MathHelper.floor(eyeY);
		int z = MathHelper.floor(entity.z);

		int light;
		if (x < -LIMIT || z < -LIMIT || x >= LIMIT || z >= LIMIT || y < 0 || y >= 256) {
			light = 0;
		} else if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			light = 0;
		} else {
			light = ParticleLight.fastLightColor(world, x, y, z, 0);
		}

		if (isVerify()) {
			BlockPos pos = new BlockPos(entity.x, eyeY, entity.z);
			int expected = world.isChunkLoaded(pos) ? world.getLightColor(pos, 0) : 0;
			if (light != expected) {
				mismatch("getLightLevel", entity, pos, String.valueOf(light), String.valueOf(expected));
			}
			compared();
			return expected;
		}
		return light;
	}

	/**
	 * Allocation-free replacement for {@code Entity.getBrightness(float)}.
	 */
	public static float getBrightness(Entity entity, float tickDelta) {
		World world = entity.world;
		double eyeY = entity.y + (double) entity.getEyeHeight();
		int x = MathHelper.floor(entity.x);
		int y = MathHelper.floor(eyeY);
		int z = MathHelper.floor(entity.z);

		float brightness;
		if (x < -LIMIT || z < -LIMIT || x >= LIMIT || z >= LIMIT || y < 0 || y >= 256) {
			brightness = 0.0F;
		} else if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			brightness = 0.0F;
		} else {
			brightness = EntityShadows.fastBrightness(world, x, y, z);
		}

		if (isVerify()) {
			BlockPos pos = new BlockPos(entity.x, eyeY, entity.z);
			float expected = world.isChunkLoaded(pos) ? world.getBrightness(pos) : 0.0F;
			if (Float.floatToIntBits(brightness) != Float.floatToIntBits(expected)) {
				mismatch("getBrightness", entity, pos, String.valueOf(brightness), String.valueOf(expected));
			}
			compared();
			return expected;
		}
		return brightness;
	}

	private static void compared() {
		verified++;
		if (verified % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Entity lighting verification: {}", verificationSummary());
		}
	}

	private static void mismatch(String what, Entity entity, BlockPos pos, String fast, String expected) {
		mismatches++;
		if (mismatches <= 10) {
			Alacer.LOGGER.warn("Entity lighting {} mismatch for {} at {}: fast {} vs vanilla {}", what, entity.getClass().getSimpleName(), pos, fast, expected);
		}
	}

	public static String verificationSummary() {
		return verified + " queries compared, " + mismatches + " mismatched";
	}
}
