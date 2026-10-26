package net.adiont.alacer.render;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

class BlockEntityRendererCacheTest {

	@BeforeEach
	void setUp() {
		BlockEntityRendererCache.clear();
	}

	@Test
	void emptyCacheReturnsNullAndRecordsMiss() {
		assertNull(BlockEntityRendererCache.get(ChestBlockEntity.class));
		assertEquals(1, BlockEntityRendererCache.queries());
		assertEquals(0, BlockEntityRendererCache.hits());
		assertEquals(1, BlockEntityRendererCache.misses());
	}

	@Test
	void putAndGetSingleRendererHitsMRUCache() {
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<ChestBlockEntity> chestRenderer = (BlockEntityRenderer<ChestBlockEntity>) (Object) new DummyRenderer();

		BlockEntityRendererCache.put(ChestBlockEntity.class, chestRenderer);
		assertSame(chestRenderer, BlockEntityRendererCache.get(ChestBlockEntity.class));
		assertEquals(1, BlockEntityRendererCache.queries());
		assertEquals(1, BlockEntityRendererCache.hits());
		assertEquals(0, BlockEntityRendererCache.misses());

		// Second query hits slot 0 directly:
		assertSame(chestRenderer, BlockEntityRendererCache.get(ChestBlockEntity.class));
		assertEquals(2, BlockEntityRendererCache.queries());
		assertEquals(2, BlockEntityRendererCache.hits());
	}

	@Test
	void fourSlotsPromoteCorrectlyAcrossMultipleLookups() {
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<ChestBlockEntity> rChest = (BlockEntityRenderer<ChestBlockEntity>) (Object) new DummyRenderer();
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<SignBlockEntity> rSign = (BlockEntityRenderer<SignBlockEntity>) (Object) new DummyRenderer();
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<SkullBlockEntity> rSkull = (BlockEntityRenderer<SkullBlockEntity>) (Object) new DummyRenderer();
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<EnderChestBlockEntity> rEnder = (BlockEntityRenderer<EnderChestBlockEntity>) (Object) new DummyRenderer();

		BlockEntityRendererCache.put(ChestBlockEntity.class, rChest);
		BlockEntityRendererCache.put(SignBlockEntity.class, rSign);
		BlockEntityRendererCache.put(SkullBlockEntity.class, rSkull);
		BlockEntityRendererCache.put(EnderChestBlockEntity.class, rEnder);

		// All 4 should be retrievable:
		assertSame(rChest, BlockEntityRendererCache.get(ChestBlockEntity.class));
		assertSame(rSign, BlockEntityRendererCache.get(SignBlockEntity.class));
		assertSame(rSkull, BlockEntityRendererCache.get(SkullBlockEntity.class));
		assertSame(rEnder, BlockEntityRendererCache.get(EnderChestBlockEntity.class));
		assertEquals(4, BlockEntityRendererCache.hits());

		// Evict oldest by adding a 5th:
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<BannerBlockEntity> rBanner = (BlockEntityRenderer<BannerBlockEntity>) (Object) new DummyRenderer();
		BlockEntityRendererCache.put(BannerBlockEntity.class, rBanner);

		assertSame(rBanner, BlockEntityRendererCache.get(BannerBlockEntity.class));
		// The least recently used among the 4 was evicted
	}

	@Test
	void nullPutIgnoredGracefully() {
		BlockEntityRendererCache.put(null, null);
		assertEquals(0, BlockEntityRendererCache.queries());
		assertNull(BlockEntityRendererCache.get(ChestBlockEntity.class));
	}

	@Test
	void verificationDetectsMatchesAndMismatches() {
		Map<Class<? extends BlockEntity>, BlockEntityRenderer<? extends BlockEntity>> map = new HashMap<>();
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<ChestBlockEntity> r1 = (BlockEntityRenderer<ChestBlockEntity>) (Object) new DummyRenderer();
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<ChestBlockEntity> r2 = (BlockEntityRenderer<ChestBlockEntity>) (Object) new DummyRenderer();
		map.put(ChestBlockEntity.class, r1);

		BlockEntityRendererCache.verify(map, ChestBlockEntity.class, r1);
		assertEquals(0, BlockEntityRendererCache.mismatches());

		BlockEntityRendererCache.verify(map, ChestBlockEntity.class, r2);
		assertEquals(1, BlockEntityRendererCache.mismatches());
	}

	@Test
	void clearResetsAllSlotsAndStatistics() {
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<ChestBlockEntity> r = (BlockEntityRenderer<ChestBlockEntity>) (Object) new DummyRenderer();
		BlockEntityRendererCache.put(ChestBlockEntity.class, r);
		assertSame(r, BlockEntityRendererCache.get(ChestBlockEntity.class));
		assertEquals(1, BlockEntityRendererCache.hits());

		BlockEntityRendererCache.clear();
		assertEquals(0, BlockEntityRendererCache.queries());
		assertEquals(0, BlockEntityRendererCache.hits());
		assertNull(BlockEntityRendererCache.get(ChestBlockEntity.class));
	}

	private static class DummyRenderer extends BlockEntityRenderer<BlockEntity> {
		@Override
		public void render(BlockEntity be, double x, double y, double z, float tickDelta, int progress) {
		}
	}
}
