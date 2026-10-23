package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VisualSettingsMathTest {
	@Test void distancesKeepVanillaValuesBitForBitAtDefaultAndScaleOtherwise() {
		for (double d : new double[] {0, 0.1, 4095.999, 4096, 1e9, Double.MAX_VALUE}) {
			assertEquals(Double.doubleToRawLongBits(d),Double.doubleToRawLongBits(RenderDistances.entitySquaredDistance(d,100)));
			assertEquals(Double.doubleToRawLongBits(d),Double.doubleToRawLongBits(RenderDistances.blockEntitySquaredLimit(d,100)));
		}
		// At 200% an entity 128 blocks away is judged as if it were 64 blocks away; the block entity limit grows to 128 blocks.
		assertEquals(64.0*64.0,RenderDistances.entitySquaredDistance(128.0*128.0,200),1e-9);
		assertEquals(128.0*128.0,RenderDistances.blockEntitySquaredLimit(4096,200),1e-9);
		assertEquals(32.0*32.0,RenderDistances.blockEntitySquaredLimit(4096,50),1e-9);
		assertTrue(RenderDistances.entitySquaredDistance(100,25) > 100);
	}
	@Test void fogDefaultIsVanillaAndOffMovesTheBandBeyondTheFarPlane() {
		assertEquals(Float.floatToRawIntBits(0.75F),Float.floatToRawIntBits(FogDistance.startFraction(0.75F,75)));
		assertEquals(Float.floatToRawIntBits(192F),Float.floatToRawIntBits(FogDistance.end(192F,75)));
		assertEquals(0.5F,FogDistance.startFraction(0.75F,50));
		assertEquals(192F,FogDistance.end(192F,95));
		// The camera's far plane is at most twice the render distance.
		assertTrue(FogDistance.startFraction(0.75F,FogDistance.OFF) >= 2F);
		assertTrue(FogDistance.end(192F,FogDistance.OFF) > FogDistance.startFraction(0.75F,FogDistance.OFF)*192F);
	}
	@Test void animationGroupsAndAllOnFastPath() {
		assertEquals(TextureAnimations.WATER,TextureAnimations.group("minecraft:blocks/water_still"));
		assertEquals(TextureAnimations.WATER,TextureAnimations.group("minecraft:blocks/water_flow"));
		assertEquals(TextureAnimations.LAVA,TextureAnimations.group("minecraft:blocks/lava_flow"));
		assertEquals(TextureAnimations.FIRE,TextureAnimations.group("minecraft:blocks/fire_layer_1"));
		assertEquals(TextureAnimations.PORTAL,TextureAnimations.group("minecraft:blocks/portal"));
		assertEquals(TextureAnimations.OTHER,TextureAnimations.group("minecraft:blocks/sea_lantern"));
		assertEquals(TextureAnimations.OTHER,TextureAnimations.group("somepack:blocks/water_still"));
		assertTrue(TextureAnimations.animate("minecraft:blocks/water_still",1,1,1,1,1));
		assertFalse(TextureAnimations.animate("minecraft:blocks/water_still",0,1,1,1,1));
		assertTrue(TextureAnimations.animate("minecraft:blocks/lava_still",0,1,1,1,1));
		assertFalse(TextureAnimations.animate("minecraft:blocks/prismarine_rough",1,1,1,1,0));
		assertFalse(TextureAnimations.animate("minecraft:blocks/portal",1,1,1,0,1));
		assertFalse(TextureAnimations.animate("minecraft:blocks/fire_layer_0",1,1,0,1,1));
	}
	@Test void biomeBlendDefaultIsVanillaThreeByThree() {
		assertEquals(1,BiomeBlend.radius(BiomeBlend.VANILLA)); assertEquals(9,BiomeBlend.samples(BiomeBlend.VANILLA));
		assertEquals(1,BiomeBlend.samples(0)); assertEquals(25,BiomeBlend.samples(2)); assertEquals(49,BiomeBlend.samples(3));
		assertEquals(49,BiomeBlend.samples(99)); assertEquals(1,BiomeBlend.samples(-5));
	}
	@Test void cameraEffectsKeepVanillaFloatsAtFullStrength() {
		for (float f : new float[] {0.1f, 0.85f, 1f, 1.1f, 1.3000001f, 1.5f}) {
			assertEquals(Float.floatToRawIntBits(f),Float.floatToRawIntBits(CameraEffects.fovModifier(f,100)));
		}
		assertEquals(1f,CameraEffects.fovModifier(1.3f,0)); assertEquals(1.15f,CameraEffects.fovModifier(1.3f,50),1e-6);
		assertEquals(Float.floatToRawIntBits(14f),Float.floatToRawIntBits(CameraEffects.hurtTilt(14f,100)));
		assertEquals(0f,CameraEffects.hurtTilt(14f,0)); assertEquals(7f,CameraEffects.hurtTilt(14f,50));
	}
	@Test void chunkSchedulingKeepsVanillaDeadlineAndBuffers() {
		long now = 1_000_000_000L, vanilla = now + 4_000_000L;
		assertEquals(vanilla,ChunkScheduling.deadline(vanilla,now,ChunkScheduling.VANILLA_BUDGET));
		assertEquals(now+1_000_000L,ChunkScheduling.deadline(vanilla,now,0));
		assertEquals(now+16_000_000L,ChunkScheduling.deadline(vanilla,now,4));
		// Already over budget: vanilla's deadline (which still allows one rebuild to be queued).
		assertEquals(now-5,ChunkScheduling.deadline(now-5,now,4));
		assertEquals(5,ChunkScheduling.buffers(ChunkScheduling.VANILLA_THREADS));
		assertEquals(1,ChunkScheduling.threads(0)); assertEquals(ChunkScheduling.MAX_THREADS,ChunkScheduling.threads(99));
		assertEquals(ChunkScheduling.MAX_THREADS+3,ChunkScheduling.buffers(99));
	}
	@Test void particleLimitAndFilteringLevels() {
		assertEquals(4000,ParticleLimit.limit(ParticleLimit.VANILLA_CHOICE));
		assertEquals(250,ParticleLimit.limit(0)); assertEquals(4000,ParticleLimit.limit(9));
		// Vanilla's single removal at the cap; a list left above a lowered limit is cut back to it in one step.
		assertEquals(1,ParticleLimit.excess(4000,4000)); assertEquals(1,ParticleLimit.excess(250,250));
		assertEquals(2001,ParticleLimit.excess(2250,250)); assertEquals(250,2250-ParticleLimit.excess(2250,250)+1);
		assertEquals(1f,TextureFiltering.level(0)); assertEquals(2f,TextureFiltering.level(1)); assertEquals(16f,TextureFiltering.level(4));
		assertEquals(8f,TextureFiltering.clamp(16f,8f)); assertEquals(1f,TextureFiltering.clamp(1f,16f)); assertEquals(1f,TextureFiltering.clamp(4f,0f));
	}
}
