package net.adiont.alacer.render;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Anisotropic filtering for the block and item atlas through the standard EXT_texture_filter_anisotropic parameter.
 * Choice 0 issues no call at atlas creation, keeping the driver default of 1.
 */
public final class TextureFiltering {
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

	/** Applies to the texture bound to GL_TEXTURE_2D. */
	public static void applyToBound(int choice) {
		if (!supported()) { return; }
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, clamp(level(choice), maximum()));
	}

	public static void applyToBlocksAtlas(Minecraft minecraft, int choice) {
		GlStateManager.bindTexture(minecraft.getBlocksAtlas().getGlId());
		applyToBound(choice);
	}
}
