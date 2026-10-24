package net.adiont.alacer.render;

import java.lang.reflect.Method;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * High-performance allocation-free implementation of {@link ClientWorld#doRandomDisplayTicks(int, int, int)}.
 *
 * <p>Vanilla allocates a new {@link Random} instance and {@link BlockPos.Mutable} instance on every
 * client tick (20 times per second), and performs 1,000 hash-table chunk lookups and section queries.
 * This class retains vanilla's per-tick {@link Random}, pools mutable block positions,
 * caches chunk references across the sampling volume, and skips empty
 * chunk sections and blocks without display tick handlers.
 */
public final class FastRandomDisplayTicks {

	private static final int[] EMPTY_INT_ARRAY = new int[0];
	private static final ThreadLocal<BlockPos.Mutable> POOLED_POS = ThreadLocal.withInitial(BlockPos.Mutable::new);

	private static final ClassValue<Boolean> HAS_DISPLAY_TICK = new ClassValue<>() {
		@Override protected Boolean computeValue(Class<?> type) { return checkHasDisplayTick(type); }
	};
	private static final String DISPLAY_TICK_METHOD = displayTickMethod();

	private static long ticks;
	private static long blocksChecked;
	private static long blocksTicked;
	private static long emptySectionsSkipped;
	private static long airSkipped;
	private static long mismatches;

	private FastRandomDisplayTicks() {}

	public static boolean isEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return false;
		}
		AlacerConfig config = alacer.config();
		return config != null && (config.fastRandomDisplayTicks || config.verifyFastRandomDisplayTicks) && RenderCompatibility.displayTicks();
	}

	public static boolean isVerifying() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return false;
		}
		AlacerConfig config = alacer.config();
		return config != null && config.verifyFastRandomDisplayTicks;
	}

	/**
	 * Execute random display ticks for a 32x32x32 block volume around the player.
	 *
	 * @param world the client world
	 * @param x center X
	 * @param y center Y
	 * @param z center Z
	 * @param verify whether to verify state lookups against vanilla
	 */
	public static void doRandomDisplayTicks(ClientWorld world, int x, int y, int z, boolean verify) {
		ticks++;
		// Preserve vanilla's per-tick RNG lifetime instead of changing the effect stream.
		Random displayRandom = new Random();
		Minecraft mc = Minecraft.getInstance();
		LocalClientPlayerEntity player = mc.player;
		ClientPlayerInteractionManager interactionManager = mc.interactionManager;

		boolean holdingBarrier = false;
		if (player != null && interactionManager != null && interactionManager.getGameMode() == WorldSettings.GameMode.CREATIVE) {
			ItemStack itemStack = player.getDisplayItemInHand();
			if (itemStack != null && Block.byItem(itemStack.getItem()) == Blocks.BARRIER) {
				holdingBarrier = true;
			}
		}

		WorldChunk cachedChunk = null;
		int cachedChunkX = Integer.MIN_VALUE;
		int cachedChunkZ = Integer.MIN_VALUE;
		BlockPos.Mutable pos = POOLED_POS.get();

		for (int i = 0; i < 1000; i++) {
			int bx = x + world.random.nextInt(16) - world.random.nextInt(16);
			int by = y + world.random.nextInt(16) - world.random.nextInt(16);
			int bz = z + world.random.nextInt(16) - world.random.nextInt(16);

			if (by < 0 || by >= 256) {
				continue;
			}

			int cx = bx >> 4;
			int cz = bz >> 4;
			if (cachedChunk == null || cachedChunkX != cx || cachedChunkZ != cz) {
				cachedChunkX = cx;
				cachedChunkZ = cz;
				cachedChunk = world.getChunkAt(cx, cz);
			}

			if (cachedChunk == null) {
				continue;
			}

			WorldChunkSection[] sections = cachedChunk.getSections();
			int sectionIndex = by >> 4;
			if (sectionIndex < 0 || sectionIndex >= sections.length) {
				continue;
			}

			WorldChunkSection section = sections[sectionIndex];
			if (section == null || section.isEmpty()) {
				emptySectionsSkipped++;
				if (verify) {
					verifyEmpty(world, bx, by, bz);
				}
				continue;
			}

			BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
			Block block = state.getBlock();
			if (block == Blocks.AIR) {
				airSkipped++;
				continue;
			}

			blocksChecked++;

			boolean hasTick = hasRandomDisplayTick(block);
			if (verify) {
				verifyBlock(world, bx, by, bz, state, block, hasTick);
			}

			if (hasTick) {
				pos.set(bx, by, bz);
				block.randomDisplayTick(world, pos, state, displayRandom);
				blocksTicked++;
			}

			if (holdingBarrier && block == Blocks.BARRIER) {
				world.addParticle(ParticleType.BARRIER, (float) bx + 0.5F, (float) by + 0.5F, (float) bz + 0.5F, 0.0D, 0.0D, 0.0D, EMPTY_INT_ARRAY);
			}
		}
	}

	/**
	 * Returns {@code true} if the given block class overrides {@code randomDisplayTick}.
	 */
	public static boolean hasRandomDisplayTick(Block block) {
		return HAS_DISPLAY_TICK.get(block.getClass());
	}

	private static String displayTickMethod() {
		return DisplayTickMethodResolver.resolve(Block.class, "randomDisplayTick", () ->
				FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary",
					"net.minecraft.unmapped.C_81592558", "m_41538657",
					"(Lnet/minecraft/unmapped/C_64041439;Lnet/minecraft/unmapped/C_32594356;Lnet/minecraft/unmapped/C_15815210;Ljava/util/Random;)V"),
				World.class, BlockPos.class, BlockState.class, Random.class);
	}

	private static boolean checkHasDisplayTick(Class<?> type) {
		return DisplayTickMethodResolver.overrides(Block.class, type, DISPLAY_TICK_METHOD,
			World.class, BlockPos.class, BlockState.class, Random.class);
	}

	private static void verifyEmpty(World world, int bx, int by, int bz) {
		BlockPos pos = new BlockPos(bx, by, bz);
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() != Blocks.AIR) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("FastRandomDisplayTicks mismatch: empty section skipped, but world has {}", state);
			}
		}
	}

	private static void verifyBlock(World world, int bx, int by, int bz, BlockState fastState, Block fastBlock, boolean fastHasTick) {
		BlockPos pos = new BlockPos(bx, by, bz);
		BlockState vanillaState = world.getBlockState(pos);
		if (!vanillaState.equals(fastState)) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("FastRandomDisplayTicks mismatch: state {} vs vanilla {}", fastState, vanillaState);
			}
		}
		if (!fastHasTick) {
			Class<?> decl = null;
			try {
				decl = fastBlock.getClass().getMethod(DISPLAY_TICK_METHOD, World.class, BlockPos.class, BlockState.class, Random.class).getDeclaringClass();
			} catch (NoSuchMethodException ignored) {}
			if (decl != null && decl != Block.class) {
				mismatches++;
				if (mismatches <= 10) {
					Alacer.LOGGER.warn("FastRandomDisplayTicks mismatch: block {} declares display tick on {} but was marked no-tick", fastBlock, decl);
				}
			}
		}
	}

	public static void clear() {
		ticks = blocksChecked = blocksTicked = emptySectionsSkipped = airSkipped = mismatches = 0;
	}

	public static long ticks() {
		return ticks;
	}

	public static long blocksChecked() {
		return blocksChecked;
	}

	public static long blocksTicked() {
		return blocksTicked;
	}

	public static long emptySectionsSkipped() {
		return emptySectionsSkipped;
	}

	public static long airSkipped() {
		return airSkipped;
	}

	public static long mismatches() {
		return mismatches;
	}
}
