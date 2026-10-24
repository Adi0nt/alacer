package net.adiont.alacer.render;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Texture filtering parameters for the block and item atlas:
 * - Anisotropic filtering (EXT_texture_filter_anisotropic)
 * - Mipmap filtering mode: Bilinear (GL_NEAREST_MIPMAP_LINEAR) vs Trilinear (GL_LINEAR_MIPMAP_LINEAR)
 * - Texture LOD bias (GL_TEXTURE_LOD_BIAS)
 * - Max mip level clamping (GL_TEXTURE_MAX_LEVEL)
 */
public final class TextureFiltering {
	public static final int BILINEAR = 0;
	public static final int TRILINEAR = 1;

	private static float maximum = -1;
	private TextureFiltering() { }

	/** Requested sampling level: 1 (off), 2, 4, 8 or 16. */
	public static float level(int choice) { return 1 << Math.max(0, Math.min(4, choice)); }

	public static float clamp(float requested, float supported) { return Math.max(1f, Math.min(requested, supported)); }

	/** Client thread with a current context only. */
	public static boolean supported() { return GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic; }

	public static float maximum() {
		if (maximum < 0) { maximum = supported() ? GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) : 1f; }
		return maximum;
	}

	public static float lodBiasFraction(int lodBiasPercent) {
		return Math.max(-2.0f, Math.min(2.0f, lodBiasPercent / 100.0f));
	}

	public static int minFilter(int filteringMode, boolean mipmap) {
		if (!mipmap) {
			return GL11.GL_NEAREST;
		}
		return filteringMode == TRILINEAR ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR;
	}

	/** Applies anisotropic filtering to the texture bound to GL_TEXTURE_2D. */
	public static void applyToBound(int choice) {
		if (!supported()) { return; }
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, clamp(level(choice), maximum()));
	}

	/** Applies anisotropic filtering, LOD bias, and mipmap min filter to the texture bound to GL_TEXTURE_2D. */
	public static void applyToBound(int choice, int filteringMode, int lodBiasPercent) {
		applyToBound(choice, filteringMode, lodBiasPercent, true);
	}

	public static void applyToBound(int choice, int filteringMode, int lodBiasPercent, boolean mipmap) {
		applyToBound(choice);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 34049 /* GL_TEXTURE_LOD_BIAS */, lodBiasFraction(lodBiasPercent));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter(filteringMode, mipmap));
	}

	public static void applyToBlocksAtlas(Minecraft minecraft, int choice) {
		GlStateManager.bindTexture(minecraft.getBlocksAtlas().getGlId());
		applyToBound(choice);
	}

	public static void applyToBlocksAtlas(Minecraft minecraft, int choice, int filteringMode, int lodBiasPercent) {
		applyToBlocksAtlas(minecraft, choice, filteringMode, lodBiasPercent, minecraft.options.mipmapLevels > 0);
	}

	public static void applyToBlocksAtlas(Minecraft minecraft, int choice, int filteringMode, int lodBiasPercent, boolean mipmap) {
		GlStateManager.bindTexture(minecraft.getBlocksAtlas().getGlId());
		applyToBound(choice, filteringMode, lodBiasPercent, mipmap);
	}

	public static void clampMaxMipLevel(Minecraft minecraft, int level) {
		GlStateManager.bindTexture(minecraft.getBlocksAtlas().getGlId());
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 33085 /* GL_TEXTURE_MAX_LEVEL */, Math.max(0, level));
	}
}
