package net.adiont.alacer.render;

import java.util.List;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.RenderChunkStorageAccessor;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkStorage;
import net.minecraft.entity.Entity;

/**
 * High-performance entity pipeline that stamps render chunk sections populated by entities,
 * eliminating empty-chunk traversal overhead across thousands of visible chunks while preserving
 * exact vanilla rendering order, checks, and behavior.
 */
public final class FastEntityPipeline {

	private static int currentEntityStamp = 1;
	private static long verifiedPasses;
	private static long pipelineMismatches;

	private FastEntityPipeline() {
	}

	public static boolean isEnabled() {
		AlacerConfig config = Alacer.get().config();
		return config != null && (config.fastEntityPipeline || config.verifyEntityPipeline);
	}

	public static boolean isVerifying() {
		AlacerConfig config = Alacer.get().config();
		return config != null && config.verifyEntityPipeline;
	}

	public static int nextStamp() {
		if (++currentEntityStamp == Integer.MAX_VALUE) {
			currentEntityStamp = 1;
		}
		return currentEntityStamp;
	}

	public static int currentStamp() {
		return currentEntityStamp;
	}

	public static int slotIndex(int cx, int cy, int cz, int sizeX, int sizeY, int sizeZ) {
		if (cy < 0 || cy >= sizeY) return -1;
		int rx = cx % sizeX; if (rx < 0) rx += sizeX;
		int rz = cz % sizeZ; if (rz < 0) rz += sizeZ;
		return (rz * sizeY + cy) * sizeX + rx;
	}

	/**
	 * Identifies and stamps only the render chunks that currently contain entities in the world,
	 * executed with zero object allocations.
	 */
	public static int stampActiveChunks(List<Entity> entities, RenderChunkStorage storage, int stamp) {
		if (entities == null || storage == null || entities.isEmpty()) {
			return 0;
		}
		RenderChunkStorageAccessor acc = (RenderChunkStorageAccessor) storage;
		int sizeX = acc.alacer$getSizeX();
		int sizeY = acc.alacer$getSizeY();
		int sizeZ = acc.alacer$getSizeZ();
		RenderChunk[] chunks = storage.chunks;
		if (chunks == null) {
			return 0;
		}

		int entityTotal = entities.size();
		int stamped = 0;
		for (int i = 0; i < entityTotal; i++) {
			Entity e = entities.get(i);
			if (e == null) continue;
			int cy = e.chunkY;
			if (cy < 0) cy = 0;
			else if (cy >= sizeY) cy = sizeY - 1;

			int cx = e.chunkX;
			int cz = e.chunkZ;
			int idx = slotIndex(cx, cy, cz, sizeX, sizeY, sizeZ);
			if (idx >= 0 && idx < chunks.length) {
				RenderChunk rc = chunks[idx];
				if (rc != null) {
					RenderChunkOrigin ro = (RenderChunkOrigin) rc;
					if (ro.alacer$originX() == (cx << 4) && ro.alacer$originY() == (cy << 4) && ro.alacer$originZ() == (cz << 4)) {
						EntityChunkSlot slot = (EntityChunkSlot) rc;
						if (slot.alacer$getEntityStamp() != stamp) {
							slot.alacer$setEntityStamp(stamp);
							stamped++;
						}
					}
				}
			}
		}
		return stamped;
	}

	public static void recordVerification(int fastCount, int vanillaCount, boolean orderMatch) {
		verifiedPasses++;
		if (fastCount != vanillaCount || !orderMatch) {
			pipelineMismatches++;
			if (pipelineMismatches <= 10) {
				Alacer.LOGGER.warn("Entity pipeline verification mismatch: fast count {}, vanilla count {}, orderMatch {}",
					fastCount, vanillaCount, orderMatch);
			}
		}
		if (verifiedPasses % 10_000 == 0) {
			Alacer.LOGGER.info("Entity pipeline verification: {} passes compared, {} mismatched",
				verifiedPasses, pipelineMismatches);
		}
	}

	public static String verificationSummary() {
		return verifiedPasses + " passes compared, " + pipelineMismatches + " mismatched";
	}
}
