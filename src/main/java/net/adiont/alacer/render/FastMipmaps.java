package net.adiont.alacer.render;

import java.util.Collection;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;

/**
 * High-performance gamma 2.2 mipmap generation with precomputed LUTs, parallel sprite processing,
 * and alpha-coverage color preservation for foliage cutouts.
 */
public final class FastMipmaps {
	public static final int CUTOUT_VANILLA = 0;
	public static final int CUTOUT_PRESERVED = 1;

	private static final float[] GAMMA_TO_LINEAR = new float[256];

	static {
		for (int i = 0; i < 256; i++) {
			GAMMA_TO_LINEAR[i] = (float) Math.pow(i / 255.0f, 2.2);
		}
	}

	private FastMipmaps() { }

	public static float gammaToLinear(int component) {
		return GAMMA_TO_LINEAR[component & 0xFF];
	}

	public static int linearToGamma(float f) {
		return linearToGammaFloat(f);
	}

	public static int linearToGammaFloat(float f) {
		if (f <= 0.0f) {
			return 0;
		}
		if (f >= 1.0f) {
			return 255;
		}
		float pow = (float) Math.pow(f, 0.45454545454545453);
		return (int) ((double) pow * 255.0);
	}

	public static int linearToGammaExact(float f) {
		if (f <= 0.0f) {
			return 0;
		}
		if (f >= 1.0f) {
			return 255;
		}
		return (int) (Math.pow(f, 0.45454545454545453) * 255.0);
	}

	public static int blendPixels(int c0, int c1, int c2, int c3, boolean hasAlpha) {
		return blendPixels(c0, c1, c2, c3, hasAlpha, CUTOUT_VANILLA);
	}

	/**
	 * Blends a 2x2 texel block into one mip texel in gamma 2.2 color space.
	 * In cutoutMode == 0 (Vanilla): matches vanilla bit-for-bit.
	 * In cutoutMode == 1 (Preserved): preserves edge colors via alpha-weighted blending and retains foliage volume.
	 */
	public static int blendPixels(int c0, int c1, int c2, int c3, boolean hasAlpha, int cutoutMode) {
		if ((c0 | c1 | c2 | c3) == 0) {
			return 0;
		}
		if (hasAlpha) {
			if (((c0 | c1 | c2 | c3) & 0xFF000000) == 0) {
				return 0;
			}
			if (cutoutMode == CUTOUT_PRESERVED) {
				float fA_sum = 0.0f, fR_weighted = 0.0f, fG_weighted = 0.0f, fB_weighted = 0.0f;
				float weightSum = 0.0f;

				int a0 = (c0 >>> 24) & 0xFF;
				if (a0 != 0) {
					float aLin = GAMMA_TO_LINEAR[a0];
					fA_sum += aLin;
					weightSum += aLin;
					fR_weighted += GAMMA_TO_LINEAR[(c0 >>> 16) & 0xFF] * aLin;
					fG_weighted += GAMMA_TO_LINEAR[(c0 >>> 8) & 0xFF] * aLin;
					fB_weighted += GAMMA_TO_LINEAR[c0 & 0xFF] * aLin;
				}
				int a1 = (c1 >>> 24) & 0xFF;
				if (a1 != 0) {
					float aLin = GAMMA_TO_LINEAR[a1];
					fA_sum += aLin;
					weightSum += aLin;
					fR_weighted += GAMMA_TO_LINEAR[(c1 >>> 16) & 0xFF] * aLin;
					fG_weighted += GAMMA_TO_LINEAR[(c1 >>> 8) & 0xFF] * aLin;
					fB_weighted += GAMMA_TO_LINEAR[c1 & 0xFF] * aLin;
				}
				int a2 = (c2 >>> 24) & 0xFF;
				if (a2 != 0) {
					float aLin = GAMMA_TO_LINEAR[a2];
					fA_sum += aLin;
					weightSum += aLin;
					fR_weighted += GAMMA_TO_LINEAR[(c2 >>> 16) & 0xFF] * aLin;
					fG_weighted += GAMMA_TO_LINEAR[(c2 >>> 8) & 0xFF] * aLin;
					fB_weighted += GAMMA_TO_LINEAR[c2 & 0xFF] * aLin;
				}
				int a3 = (c3 >>> 24) & 0xFF;
				if (a3 != 0) {
					float aLin = GAMMA_TO_LINEAR[a3];
					fA_sum += aLin;
					weightSum += aLin;
					fR_weighted += GAMMA_TO_LINEAR[(c3 >>> 16) & 0xFF] * aLin;
					fG_weighted += GAMMA_TO_LINEAR[(c3 >>> 8) & 0xFF] * aLin;
					fB_weighted += GAMMA_TO_LINEAR[c3 & 0xFF] * aLin;
				}

				if (weightSum <= 0.0f) {
					return 0;
				}
				float invWeight = 1.0f / weightSum;
				int a = linearToGammaExact(fA_sum * 0.25f);
				int r = linearToGammaExact(fR_weighted * invWeight);
				int g = linearToGammaExact(fG_weighted * invWeight);
				int b = linearToGammaExact(fB_weighted * invWeight);

				if (a < 26) {
					a = 0;
				} else {
					a = Math.min(255, (a * 3) >> 1);
				}
				return (a << 24) | (r << 16) | (g << 8) | b;
			}

			float fA = 0.0f, fR = 0.0f, fG = 0.0f, fB = 0.0f;
			if ((c0 >>> 24) != 0) {
				fA += GAMMA_TO_LINEAR[(c0 >>> 24) & 0xFF];
				fR += GAMMA_TO_LINEAR[(c0 >>> 16) & 0xFF];
				fG += GAMMA_TO_LINEAR[(c0 >>> 8) & 0xFF];
				fB += GAMMA_TO_LINEAR[c0 & 0xFF];
			}
			if ((c1 >>> 24) != 0) {
				fA += GAMMA_TO_LINEAR[(c1 >>> 24) & 0xFF];
				fR += GAMMA_TO_LINEAR[(c1 >>> 16) & 0xFF];
				fG += GAMMA_TO_LINEAR[(c1 >>> 8) & 0xFF];
				fB += GAMMA_TO_LINEAR[c1 & 0xFF];
			}
			if ((c2 >>> 24) != 0) {
				fA += GAMMA_TO_LINEAR[(c2 >>> 24) & 0xFF];
				fR += GAMMA_TO_LINEAR[(c2 >>> 16) & 0xFF];
				fG += GAMMA_TO_LINEAR[(c2 >>> 8) & 0xFF];
				fB += GAMMA_TO_LINEAR[c2 & 0xFF];
			}
			if ((c3 >>> 24) != 0) {
				fA += GAMMA_TO_LINEAR[(c3 >>> 24) & 0xFF];
				fR += GAMMA_TO_LINEAR[(c3 >>> 16) & 0xFF];
				fG += GAMMA_TO_LINEAR[(c3 >>> 8) & 0xFF];
				fB += GAMMA_TO_LINEAR[c3 & 0xFF];
			}
			int a = linearToGammaExact(fA * 0.25f);
			int r = linearToGammaExact(fR * 0.25f);
			int g = linearToGammaExact(fG * 0.25f);
			int b = linearToGammaExact(fB * 0.25f);
			if (a < 96) {
				a = 0;
			}
			return (a << 24) | (r << 16) | (g << 8) | b;
		} else {
			float r0 = GAMMA_TO_LINEAR[(c0 >>> 16) & 0xFF];
			float r1 = GAMMA_TO_LINEAR[(c1 >>> 16) & 0xFF];
			float r2 = GAMMA_TO_LINEAR[(c2 >>> 16) & 0xFF];
			float r3 = GAMMA_TO_LINEAR[(c3 >>> 16) & 0xFF];
			float fR = (r0 + r1 + r2 + r3) * 0.25f;

			float g0 = GAMMA_TO_LINEAR[(c0 >>> 8) & 0xFF];
			float g1 = GAMMA_TO_LINEAR[(c1 >>> 8) & 0xFF];
			float g2 = GAMMA_TO_LINEAR[(c2 >>> 8) & 0xFF];
			float g3 = GAMMA_TO_LINEAR[(c3 >>> 8) & 0xFF];
			float fG = (g0 + g1 + g2 + g3) * 0.25f;

			float b0 = GAMMA_TO_LINEAR[c0 & 0xFF];
			float b1 = GAMMA_TO_LINEAR[c1 & 0xFF];
			float b2 = GAMMA_TO_LINEAR[c2 & 0xFF];
			float b3 = GAMMA_TO_LINEAR[c3 & 0xFF];
			float fB = (b0 + b1 + b2 + b3) * 0.25f;

			float fA = (GAMMA_TO_LINEAR[(c0 >>> 24) & 0xFF] + GAMMA_TO_LINEAR[(c1 >>> 24) & 0xFF] +
			            GAMMA_TO_LINEAR[(c2 >>> 24) & 0xFF] + GAMMA_TO_LINEAR[(c3 >>> 24) & 0xFF]) * 0.25f;

			if (cutoutMode == CUTOUT_PRESERVED) {
				// Contrast-preserving salient feature blend (retains high-frequency textural variance on opaque blocks)
				float d0 = Math.abs(r0 - fR) + Math.abs(g0 - fG) + Math.abs(b0 - fB);
				float d1 = Math.abs(r1 - fR) + Math.abs(g1 - fG) + Math.abs(b1 - fB);
				float d2 = Math.abs(r2 - fR) + Math.abs(g2 - fG) + Math.abs(b2 - fB);
				float d3 = Math.abs(r3 - fR) + Math.abs(g3 - fG) + Math.abs(b3 - fB);

				float maxD = d0;
				float salR = r0, salG = g0, salB = b0;
				if (d1 > maxD) { maxD = d1; salR = r1; salG = g1; salB = b1; }
				if (d2 > maxD) { maxD = d2; salR = r2; salG = g2; salB = b2; }
				if (d3 > maxD) { maxD = d3; salR = r3; salG = g3; salB = b3; }

				fR = fR * 0.75f + salR * 0.25f;
				fG = fG * 0.75f + salG * 0.25f;
				fB = fB * 0.75f + salB * 0.25f;
			}

			return (linearToGammaFloat(fA) << 24) | (linearToGammaFloat(fR) << 16) | (linearToGammaFloat(fG) << 8) | linearToGammaFloat(fB);
		}
	}

	public static int[][] generateMipmaps(int mipLevels, int width, int[][] data, boolean verify) {
		return generateMipmaps(mipLevels, width, data, verify, CUTOUT_VANILLA);
	}

	public static int[][] generateMipmaps(int mipLevels, int width, int[][] data, boolean verify, int cutoutMode) {
		int[][] result = new int[mipLevels + 1][];
		result[0] = data[0];
		if (mipLevels <= 0) {
			return result;
		}
		boolean hasAlpha = false;
		for (int i = 0; i < data.length; ++i) {
			if ((data[0][i] >> 24) == 0) {
				hasAlpha = true;
				break;
			}
		}
		for (int level = 1; level <= mipLevels; ++level) {
			if (data.length > level && data[level] != null) {
				result[level] = data[level];
			} else {
				int[] prev = result[level - 1];
				int[] next = new int[prev.length >> 2];
				int w = width >> level;
				int h = next.length / w;
				int stride = w << 1;
				for (int x = 0; x < w; ++x) {
					for (int y = 0; y < h; ++y) {
						int srcIdx = 2 * (x + y * stride);
						next[x + y * w] = blendPixels(
							prev[srcIdx],
							prev[srcIdx + 1],
							prev[srcIdx + stride],
							prev[srcIdx + stride + 1],
							hasAlpha,
							cutoutMode
						);
					}
				}
				result[level] = next;
			}
		}

		if (verify && cutoutMode == CUTOUT_VANILLA) {
			verifyAgainstVanilla(mipLevels, width, data, result);
		}
		return result;
	}

	private static void verifyAgainstVanilla(int mipLevels, int width, int[][] data, int[][] fastResult) {
		int[][] vanillaResult = vanillaGenerateMipmaps(mipLevels, width, data);
		for (int level = 1; level <= mipLevels; level++) {
			int[] fast = fastResult[level];
			int[] vanilla = vanillaResult[level];
			if (fast == null || vanilla == null || fast.length != vanilla.length) {
				Alacer.LOGGER.error("FastMipmaps verification failed: level {} length mismatch (fast={}, vanilla={})",
					level, fast == null ? -1 : fast.length, vanilla == null ? -1 : vanilla.length);
				continue;
			}
			for (int i = 0; i < fast.length; i++) {
				if (fast[i] != vanilla[i]) {
					Alacer.LOGGER.warn("FastMipmaps verification mismatch at level {} [{}]: fast=0x{}, vanilla=0x{}",
						level, i, Integer.toHexString(fast[i]), Integer.toHexString(vanilla[i]));
					fast[i] = vanilla[i];
				}
			}
		}
	}

	/** Exact vanilla reference logic for verification. */
	public static int[][] vanillaGenerateMipmaps(int mipLevels, int width, int[][] data) {
		int[][] result = new int[mipLevels + 1][];
		result[0] = data[0];
		if (mipLevels <= 0) {
			return result;
		}
		boolean hasAlpha = false;
		for (int i = 0; i < data.length; ++i) {
			if ((data[0][i] >> 24) == 0) {
				hasAlpha = true;
				break;
			}
		}
		for (int level = 1; level <= mipLevels; ++level) {
			if (data.length > level && data[level] != null) {
				result[level] = data[level];
			} else {
				int[] prev = result[level - 1];
				int[] next = new int[prev.length >> 2];
				int w = width >> level;
				int h = next.length / w;
				int stride = w << 1;
				for (int x = 0; x < w; ++x) {
					for (int y = 0; y < h; ++y) {
						int srcIdx = 2 * (x + y * stride);
						next[x + y * w] = vanillaBlendPixels(
							prev[srcIdx],
							prev[srcIdx + 1],
							prev[srcIdx + stride],
							prev[srcIdx + stride + 1],
							hasAlpha
						);
					}
				}
				result[level] = next;
			}
		}
		return result;
	}

	private static int vanillaBlendPixels(int c0, int c1, int c2, int c3, boolean hasAlpha) {
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

	public static void parallelApplyMipmaps(Collection<TextureAtlasSprite> sprites, TextureAtlasSprite missingSprite, int maxMipLevel) {
		if (sprites == null || sprites.isEmpty()) {
			if (missingSprite != null) {
				missingSprite.applyMipmaps(maxMipLevel);
			}
			return;
		}
		sprites.parallelStream().forEach(sprite -> {
			try {
				sprite.applyMipmaps(maxMipLevel);
			} catch (Throwable t) {
				CrashReport report = CrashReport.of(t, "Applying mipmap");
				CrashReportCategory cat = report.addCategory("Sprite being mipmapped");
				cat.add("Sprite name", sprite::getName);
				cat.add("Sprite size", () -> sprite.getWidth() + " x " + sprite.getHeight());
				cat.add("Sprite frames", () -> sprite.getFrameCount() + " frames");
				cat.add("Mipmap levels", maxMipLevel);
				throw new CrashException(report);
			}
		});
		if (missingSprite != null) {
			missingSprite.applyMipmaps(maxMipLevel);
		}
	}
}
