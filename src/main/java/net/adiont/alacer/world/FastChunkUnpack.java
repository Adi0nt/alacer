package net.adiont.alacer.world;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.WorldChunkAccessor;
import net.adiont.alacer.mixin.WorldChunkSectionAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

/**
 * High-performance chunk packet unpacking and section validation.
 * Replaces scalar byte-by-byte bit manipulation with direct native little-endian
 * vectorized char extraction, and replaces the 4,096-iteration 3D loop and map lookups
 * with a linear sequential memory scan and cached block-type flags.
 */
public final class FastChunkUnpack {
	private static final Logger LOGGER = LogManager.getLogger();

	// Flags bitmask: bit 0 = non-air (1), bit 1 = ticks randomly (2), bit 2 = resolved (4)
	private static final byte[] BLOCK_FLAGS = new byte[65536];
	private static volatile boolean airInitialized = false;

	private FastChunkUnpack() { }

	public static boolean isEnabled() {
		return Alacer.get() != null && Alacer.get().config().fastChunkUnpack;
	}

	public static boolean isVerify() {
		return Alacer.get() != null && Alacer.get().config().verifyChunkUnpack;
	}

	/**
	 * Unpacks 4,096 little-endian 16-bit block states from packet byte array directly into char[].
	 */
	public static void unpackBlockStates(byte[] data, int offset, char[] blockStates) {
		ByteBuffer.wrap(data, offset, blockStates.length * 2)
			.order(ByteOrder.LITTLE_ENDIAN)
			.asCharBuffer()
			.get(blockStates);

		if (isVerify()) {
			verifyUnpack(data, offset, blockStates);
		}
	}

	/**
	 * Computes and sets nonAirBlockCount and randomTickingBlockCount on the given section.
	 */
	public static void validateBlockCounters(WorldChunkSection section) {
		char[] states = section.getBlockStates();
		if (states == null || states.length == 0) {
			return;
		}

		ensureAirInitialized();

		int nonAir = 0;
		int ticking = 0;
		for (int i = 0; i < states.length; i++) {
			char id = states[i];
			if (id == 0) {
				continue;
			}
			byte flags = BLOCK_FLAGS[id];
			if ((flags & 4) == 0) {
				flags = resolveFlags(id);
			}
			if ((flags & 1) != 0) {
				nonAir++;
			}
			if ((flags & 2) != 0) {
				ticking++;
			}
		}

		if (section instanceof WorldChunkSectionAccessor acc) {
			acc.alacer$setNonAirBlockCount(nonAir);
			acc.alacer$setRandomTickingBlockCount(ticking);
		} else {
			setCountersReflectively(section, nonAir, ticking);
		}

		if (isVerify()) {
			verifyCounters(section, nonAir, ticking);
		}
	}

	/**
	 * High-performance overhaul of WorldChunk.update([BIZ).
	 */
	public static void updateChunk(WorldChunk chunk, byte[] data, int sectionsMask, boolean isFull) {
		World world = chunk.getWorld();
		boolean hasSky = !world.dimension.hasNoSky();
		WorldChunkSection[] sections = chunk.getSections();
		int offset = 0;

		// 1. Unpack block states (vectorized)
		for (int y = 0; y < sections.length; y++) {
			if ((sectionsMask & (1 << y)) != 0) {
				if (sections[y] == null) {
					sections[y] = new WorldChunkSection(y << 4, hasSky);
				}
				char[] blockStates = sections[y].getBlockStates();
				unpackBlockStates(data, offset, blockStates);
				offset += blockStates.length * 2;
			} else if (isFull && sections[y] != null) {
				sections[y] = null;
			}
		}

		// 2. Unpack block light
		for (int y = 0; y < sections.length; y++) {
			if ((sectionsMask & (1 << y)) != 0 && sections[y] != null) {
				ChunkNibbleStorage blockLight = sections[y].getBlockLightStorage();
				byte[] lightData = blockLight.getData();
				System.arraycopy(data, offset, lightData, 0, lightData.length);
				offset += lightData.length;
			}
		}

		// 3. Unpack sky light if world has sky
		if (hasSky) {
			for (int y = 0; y < sections.length; y++) {
				if ((sectionsMask & (1 << y)) != 0 && sections[y] != null) {
					ChunkNibbleStorage skyLight = sections[y].getSkyLightStorage();
					byte[] lightData = skyLight.getData();
					System.arraycopy(data, offset, lightData, 0, lightData.length);
					offset += lightData.length;
				}
			}
		}

		// 4. Unpack biomes if full chunk
		if (isFull) {
			byte[] biomes = chunk.getBiomes();
			System.arraycopy(data, offset, biomes, 0, biomes.length);
			offset += biomes.length;
		}

		// 5. Section counter validation (fast linear scan)
		for (int y = 0; y < sections.length; y++) {
			if (sections[y] != null && (sectionsMask & (1 << y)) != 0) {
				validateBlockCounters(sections[y]);
			}
		}

		chunk.setLightPopulated(true);
		chunk.setTerrainPopulated(true);
		((WorldChunkAccessor) chunk).alacer$populateHeightMapOnly();

		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			blockEntity.clearBlockCache();
		}
	}

	private static java.lang.reflect.Field nonAirField;
	private static java.lang.reflect.Field tickingField;

	private static void setCountersReflectively(WorldChunkSection section, int nonAir, int ticking) {
		try {
			if (nonAirField == null) {
				nonAirField = WorldChunkSection.class.getDeclaredField("nonAirBlockCount");
				nonAirField.setAccessible(true);
				tickingField = WorldChunkSection.class.getDeclaredField("randomTickingBlockCount");
				tickingField.setAccessible(true);
			}
			nonAirField.setInt(section, nonAir);
			tickingField.setInt(section, ticking);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set section block counters", e);
		}
	}

	private static void ensureAirInitialized() {
		if (!airInitialized) {
			BLOCK_FLAGS[0] = 4; // Air is resolved: nonAir = 0, ticking = 0, resolved = 4
			airInitialized = true;
		}
	}

	private static synchronized byte resolveFlags(int id) {
		byte flags = BLOCK_FLAGS[id];
		if ((flags & 4) != 0) {
			return flags;
		}
		byte result = 4; // resolved marker
		BlockState state = Block.STATE_REGISTRY.get(id);
		if (state != null) {
			Block block = state.getBlock();
			if (block != null && block != Blocks.AIR) {
				result |= 1;
				if (block.ticksRandomly()) {
					result |= 2;
				}
			}
		}
		BLOCK_FLAGS[id] = result;
		return result;
	}

	private static void verifyUnpack(byte[] data, int offset, char[] blockStates) {
		int pos = offset;
		for (int i = 0; i < blockStates.length; i++) {
			char expected = (char) (((data[pos + 1] & 0xFF) << 8) | (data[pos] & 0xFF));
			pos += 2;
			if (blockStates[i] != expected) {
				LOGGER.error("Chunk unpack verification mismatch at index {}: fast={} expected={}",
					i, (int) blockStates[i], (int) expected);
				throw new AssertionError("Chunk unpack mismatch at " + i);
			}
		}
	}

	private static void verifyCounters(WorldChunkSection section, int fastNonAir, int fastTicking) {
		int expectedNonAir = 0;
		int expectedTicking = 0;
		for (int x = 0; x < 16; ++x) {
			for (int y = 0; y < 16; ++y) {
				for (int z = 0; z < 16; ++z) {
					Block block = section.getBlock(x, y, z);
					if (block != Blocks.AIR) {
						++expectedNonAir;
						if (block.ticksRandomly()) {
							++expectedTicking;
						}
					}
				}
			}
		}
		if (fastNonAir != expectedNonAir || fastTicking != expectedTicking) {
			LOGGER.error("Block counter verification mismatch: nonAir({} vs {}) ticking({} vs {})",
				fastNonAir, expectedNonAir, fastTicking, expectedTicking);
			throw new AssertionError("Block counter mismatch");
		}
	}
}
