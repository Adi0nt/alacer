package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FastMipmapsTest {

	@BeforeEach
	@AfterEach
	void resetState() {
		FastTextureUpload.invalidate();
	}

	@Test
	void zeroBlocksEvaluateToZeroInstantly() {
		assertEquals(0, FastMipmaps.blendPixels(0, 0, 0, 0, false));
		assertEquals(0, FastMipmaps.blendPixels(0, 0, 0, 0, true));
		// Zero alpha in transparent mode evaluates to zero
		assertEquals(0, FastMipmaps.blendPixels(0x00FFFFFF, 0x00123456, 0x00AABBCC, 0x00000000, true));
	}

	@Test
	void gammaLutMatchesExactFloatingPointCurve() {
		for (int i = 0; i < 256; i++) {
			float expected = (float) Math.pow(i / 255.0f, 2.2);
			assertEquals(expected, FastMipmaps.gammaToLinear(i), "Mismatch at component " + i);
		}
	}

	@Test
	void linearToGammaClampsAndMatchesVanillaCurve() {
		assertEquals(0, FastMipmaps.linearToGamma(-0.5f));
		assertEquals(0, FastMipmaps.linearToGamma(0.0f));
		assertEquals(255, FastMipmaps.linearToGamma(1.0f));
		assertEquals(255, FastMipmaps.linearToGamma(1.5f));

		for (int i = 0; i <= 100; i++) {
			float f = i / 100.0f;
			int expected = (int) (Math.pow(f, 0.45454545454545453) * 255.0);
			assertEquals(expected, FastMipmaps.linearToGamma(f), "Mismatch at fraction " + f);
		}
	}

	@Test
	void blendPixelsMatchesVanillaAcrossSyntheticColorCombinations() {
		int[] testColors = {
			0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
			0xFF123456, 0xFF789ABC, 0xFFDEADBF, 0xFF808080, 0xFF3F7FBF,
			0x00000000, 0x50123456, 0x80ABCDEF, 0xA055AAFF, 0x00FFFFFF
		};

		for (int c0 : testColors) {
			for (int c1 : testColors) {
				for (int c2 : testColors) {
					for (int c3 : testColors) {
						// Test opaque mode
						int fastOpaque = FastMipmaps.blendPixels(c0, c1, c2, c3, false);
						int vanillaOpaque = vanillaBlend(c0, c1, c2, c3, false);
						assertEquals(vanillaOpaque, fastOpaque,
							String.format("Opaque mismatch for [0x%X, 0x%X, 0x%X, 0x%X]", c0, c1, c2, c3));

						// Test cutout / alpha mode
						int fastAlpha = FastMipmaps.blendPixels(c0, c1, c2, c3, true);
						int vanillaAlpha = vanillaBlend(c0, c1, c2, c3, true);
						assertEquals(vanillaAlpha, fastAlpha,
							String.format("Alpha mismatch for [0x%X, 0x%X, 0x%X, 0x%X]", c0, c1, c2, c3));
					}
				}
			}
		}
	}

	@Test
	void generateMipmapsMatchesVanillaOnFullTextures() {
		Random random = new Random(42);
		int width = 16;
		int height = 16;
		int levels = 4;

		// 1. Fully opaque texture
		int[] opaquePixels = new int[width * height];
		for (int i = 0; i < opaquePixels.length; i++) {
			opaquePixels[i] = 0xFF000000 | (random.nextInt(0x01000000));
		}
		int[][] dataOpaque = new int[][] { opaquePixels };
		int[][] fastOpaque = FastMipmaps.generateMipmaps(levels, width, dataOpaque, false);
		int[][] vanillaOpaque = FastMipmaps.vanillaGenerateMipmaps(levels, width, dataOpaque);
		assertMipmapsEqual(vanillaOpaque, fastOpaque, levels);

		// 2. Texture with transparent pixels (cutout)
		int[] cutoutPixels = new int[width * height];
		for (int i = 0; i < cutoutPixels.length; i++) {
			if (random.nextBoolean()) {
				cutoutPixels[i] = 0;
			} else {
				cutoutPixels[i] = (random.nextInt(256) << 24) | random.nextInt(0x01000000);
			}
		}
		int[][] dataCutout = new int[][] { cutoutPixels };
		int[][] fastCutout = FastMipmaps.generateMipmaps(levels, width, dataCutout, false);
		int[][] vanillaCutout = FastMipmaps.vanillaGenerateMipmaps(levels, width, dataCutout);
		assertMipmapsEqual(vanillaCutout, fastCutout, levels);

		// 3. Texture verified with verify flag active
		int[][] verifiedResult = FastMipmaps.generateMipmaps(levels, width, dataCutout, true);
		assertMipmapsEqual(vanillaCutout, verifiedResult, levels);
	}

	@Test
	void cutoutPreservedModePreventsEdgeErosionAndColorBleed() {
		// A single vivid green texel surrounded by 3 transparent texels (typical foliage edge/tip)
		int leafTexel = 0xFF00FF00;
		int transparent = 0x00000000;

		// 1. In vanilla cutout mode, the 1 opaque texel is blended as:
		// fR * 0.25f, fG * 0.25f, fB * 0.25f. The green component drops from 255 to ~135 (severe darkening).
		int vanillaBlend = FastMipmaps.blendPixels(leafTexel, transparent, transparent, transparent, true, FastMipmaps.CUTOUT_VANILLA);
		int vanillaGreen = (vanillaBlend >>> 8) & 0xFF;
		int vanillaAlpha = (vanillaBlend >>> 24) & 0xFF;
		assertEquals(135, vanillaGreen, "Vanilla downsampling darkens green channel to ~135");
		assertEquals(135, vanillaAlpha);

		// 2. In CUTOUT_PRESERVED mode, alpha weighting divides by weightSum:
		// invWeight = 1.0f / 1.0f. Green channel retains 100% full intensity (255)!
		// Alpha coverage is boosted: (135 * 3) / 2 = 202 to prevent foliage erosion.
		int preservedBlend = FastMipmaps.blendPixels(leafTexel, transparent, transparent, transparent, true, FastMipmaps.CUTOUT_PRESERVED);
		int preservedGreen = (preservedBlend >>> 8) & 0xFF;
		int preservedAlpha = (preservedBlend >>> 24) & 0xFF;
		assertEquals(255, preservedGreen, "Preserved downsampling retains full 100% green brightness");
		assertEquals(202, preservedAlpha, "Preserved downsampling boosts alpha coverage");

		// 3. Fully transparent block in CUTOUT_PRESERVED returns 0
		assertEquals(0, FastMipmaps.blendPixels(transparent, transparent, transparent, transparent, true, FastMipmaps.CUTOUT_PRESERVED));
	}

	@Test
	void contrastPreservedModeRetainsOpaqueVariance() {
		// 4 texels representing grass/dirt variation: 3 darker texels, 1 bright fleck
		int dark = 0xFF408020;
		int bright = 0xFF80FF40;

		// Vanilla averages them linearly
		int vanilla = FastMipmaps.blendPixels(dark, dark, dark, bright, false, FastMipmaps.CUTOUT_VANILLA);
		int preserved = FastMipmaps.blendPixels(dark, dark, dark, bright, false, FastMipmaps.CUTOUT_PRESERVED);

		// Preserved mode blends in salient feature, retaining higher contrast
		int vanillaGreen = (vanilla >>> 8) & 0xFF;
		int preservedGreen = (preserved >>> 8) & 0xFF;
		assertTrue(preservedGreen > vanillaGreen, "Preserved mode retains high-frequency bright flecks: " + preservedGreen + " > " + vanillaGreen);

		// Uniform color remains exact
		int uniform = 0xFF506070;
		assertEquals(uniform, FastMipmaps.blendPixels(uniform, uniform, uniform, uniform, false, FastMipmaps.CUTOUT_PRESERVED));
	}

	@Test
	void fastTextureUploadEliminatesRedundantStateCalls() {
		// First upload sets filter and clamp
		assertTrue(FastTextureUpload.shouldSetFilter(false, true));
		assertTrue(FastTextureUpload.shouldSetClamp(false));

		// Immediate repeat with identical arguments is skipped
		assertFalse(FastTextureUpload.shouldSetFilter(false, true));
		assertFalse(FastTextureUpload.shouldSetClamp(false));

		// Changing blur or clamp re-triggers
		assertTrue(FastTextureUpload.shouldSetFilter(true, true));
		assertFalse(FastTextureUpload.shouldSetFilter(true, true));
		assertTrue(FastTextureUpload.shouldSetClamp(true));
		assertFalse(FastTextureUpload.shouldSetClamp(true));

		// Invalidation forces re-application
		FastTextureUpload.invalidate();
		assertTrue(FastTextureUpload.shouldSetFilter(true, true));
		assertTrue(FastTextureUpload.shouldSetClamp(true));
	}

	private static void assertMipmapsEqual(int[][] expected, int[][] actual, int levels) {
		assertEquals(expected.length, actual.length, "Mipmap levels length mismatch");
		for (int level = 0; level <= levels; level++) {
			assertArrayEquals(expected[level], actual[level], "Mismatch at mip level " + level);
		}
	}

	private static int vanillaBlend(int c0, int c1, int c2, int c3, boolean hasAlpha) {
		if (!hasAlpha) {
			int a = vanillaBlendComponent(c0, c1, c2, c3, 24);
			int r = vanillaBlendComponent(c0, c1, c2, c3, 16);
			int g = vanillaBlendComponent(c0, c1, c2, c3, 8);
			int b = vanillaBlendComponent(c0, c1, c2, c3, 0);
			return (a << 24) | (r << 16) | (g << 8) | b;
		}
		int[] buf = {c0, c1, c2, c3};
		float fA = 0.0f, fR = 0.0f, fG = 0.0f, fB = 0.0f;
		for (int i = 0; i < 4; ++i) {
			if ((buf[i] >> 24) != 0) {
				fA += (float) Math.pow(((buf[i] >> 24) & 0xFF) / 255.0f, 2.2);
				fR += (float) Math.pow(((buf[i] >> 16) & 0xFF) / 255.0f, 2.2);
				fG += (float) Math.pow(((buf[i] >> 8) & 0xFF) / 255.0f, 2.2);
				fB += (float) Math.pow((buf[i] & 0xFF) / 255.0f, 2.2);
			}
		}
		int a = (int) (Math.pow(fA / 4.0f, 0.45454545454545453) * 255.0);
		int r = (int) (Math.pow(fR / 4.0f, 0.45454545454545453) * 255.0);
		int g = (int) (Math.pow(fG / 4.0f, 0.45454545454545453) * 255.0);
		int b = (int) (Math.pow(fB / 4.0f, 0.45454545454545453) * 255.0);
		if (a < 96) {
			a = 0;
		}
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int vanillaBlendComponent(int c0, int c1, int c2, int c3, int shift) {
		float f0 = (float) Math.pow(((c0 >> shift) & 0xFF) / 255.0f, 2.2);
		float f1 = (float) Math.pow(((c1 >> shift) & 0xFF) / 255.0f, 2.2);
		float f2 = (float) Math.pow(((c2 >> shift) & 0xFF) / 255.0f, 2.2);
		float f3 = (float) Math.pow(((c3 >> shift) & 0xFF) / 255.0f, 2.2);
		float avg = (float) Math.pow((f0 + f1 + f2 + f3) * 0.25f, 0.45454545454545453);
		return (int) (avg * 255.0);
	}
}
