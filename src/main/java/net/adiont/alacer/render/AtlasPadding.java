package net.adiont.alacer.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.StitcherAccessor;
import net.minecraft.client.render.texture.Stitcher;
import net.minecraft.client.render.texture.StitcherException;
import net.minecraft.client.render.texture.TextureAtlasSprite;

/**
 * Edge borders around block/item atlas sprites while anisotropic filtering is on.
 *
 * <p>Anisotropic filtering takes several samples along a surface's long axis, and some drivers also filter within a
 * mip level; near a sprite's edge those samples reach the neighboring sprite in the atlas and draw seams between
 * blocks. Each sprite is therefore given a border that repeats its own edge texels. The border is 2^mipLevels texels
 * wide, the smallest width that keeps every sprite aligned at every mip level and leaves one border texel at the
 * coarsest level. Texture coordinates still cover only the sprite, so nothing is drawn from the border directly.
 * With filtering off no border is added and the atlas layout is vanilla's.
 */
public final class AtlasPadding {
	/** Border of the sprites being registered with a Stitcher; set only while the atlas stitches (client thread). */
	public static int stitching;
	/** Border of the most recently stitched atlas. */
	private static int active;
	private AtlasPadding() { }

	/** A Stitcher holder or atlas sprite that carries its border. */
	public interface Bordered {
		int alacer$border();
		void alacer$setBorder(int border);
	}

	/** A sprite that keeps a reusable buffer for its bordered uploads. */
	public interface BufferedSprite extends Bordered {
		int[][] alacer$borderBuffer();
		void alacer$setBorderBuffer(int[][] buffer);
	}

	public static int border(int anisotropyChoice, int mipLevels) {
		return anisotropyChoice == 0 ? 0 : 1 << Math.max(0, mipLevels);
	}

	/** Called when an atlas finishes stitching. */
	public static void stitched(int border) { active = border; }

	/** True when turning filtering on or off changes the atlas layout, which needs a texture reload. */
	public static boolean layoutChanges(int anisotropyChoice) { return (anisotropyChoice != 0) != (active != 0); }

	/**
	 * Copies each mip level into a buffer {@code border >> level} texels larger on every side, repeating edge texels.
	 * Levels are row-major, {@code width >> level} by {@code height >> level}. {@code reuse} is returned when it fits.
	 */
	public static int[][] pad(int[][] levels, int width, int height, int border, int[][] reuse) {
		int[][] out = reuse != null && reuse.length == levels.length ? reuse : new int[levels.length][];
		for (int level = 0; level < levels.length; level++) {
			int w = width >> level, h = height >> level, b = border >> level;
			int pw = w + 2 * b, ph = h + 2 * b;
			if (out[level] == null || out[level].length != pw * ph) { out[level] = new int[pw * ph]; }
			int[] src = levels[level], dst = out[level];
			for (int y = 0; y < ph; y++) {
				int row = Math.max(0, Math.min(h - 1, y - b)) * w;
				int to = y * pw;
				for (int x = 0; x < b; x++) { dst[to + x] = src[row]; }
				System.arraycopy(src, row, dst, to + b, w);
				for (int x = b + w; x < pw; x++) { dst[to + x] = src[row + w - 1]; }
			}
		}
		return out;
	}

	/** Width or height of a bordered upload. */
	public static int padded(int size, int border) { return size + 2 * border; }

	/** A sprite upload ({@code TextureUtil.upload(levels, width, height, x, y, ...)}), widened to include the border. */
	public static void upload(TextureAtlasSprite sprite, int[][] levels, int width, int height, int x, int y, boolean blur, boolean clamp, Operation<Void> original) {
		BufferedSprite bordered = (BufferedSprite) sprite;
		int border = bordered.alacer$border();
		if (border == 0) { original.call(levels, width, height, x, y, blur, clamp); return; }
		int[][] padded = pad(levels, width, height, border, bordered.alacer$borderBuffer());
		bordered.alacer$setBorderBuffer(padded);
		original.call(padded, padded(width, border), padded(height, border), x - border, y - border, blur, clamp);
	}

	/**
	 * Stitches with borders; if the bordered atlas does not fit the driver's maximum texture size, stitches again
	 * without borders, as vanilla would, instead of failing the resource reload.
	 */
	public static void stitch(Stitcher stitcher, Operation<Void> original) {
		try {
			original.call(stitcher);
		} catch (StitcherException e) {
			if (stitching == 0) { throw e; }
			Alacer.LOGGER.warn("Anisotropic filtering: the bordered texture atlas does not fit ({}); using vanilla's layout, so seams between blocks may appear", e.getMessage());
			StitcherAccessor accessor = (StitcherAccessor) stitcher;
			for (Stitcher.Holder holder : accessor.alacer$getHolders()) { ((Bordered) holder).alacer$setBorder(0); }
			accessor.alacer$getRegions().clear();
			accessor.alacer$setWidth(0);
			accessor.alacer$setHeight(0);
			stitching = 0;
			original.call(stitcher);
		}
	}
}
