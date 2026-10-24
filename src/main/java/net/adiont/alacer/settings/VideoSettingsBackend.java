package net.adiont.alacer.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.AtlasMipmapAccess;
import net.adiont.alacer.render.AtlasPadding;
import net.adiont.alacer.render.FastTextureUpload;
import net.adiont.alacer.render.TextureFiltering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GLX;

/** Client-thread adapter. Minecraft retains ownership of its options, resource reloads and display changes. */
public final class VideoSettingsBackend {
	private final Minecraft minecraft;
	private final AlacerConfig config;
	public VideoSettingsBackend(Minecraft minecraft) { this.minecraft = minecraft; this.config = Alacer.get().config(); }
	public Map<String,Integer> snapshot() {
		Map<String,Integer> result = new LinkedHashMap<>();
		for (VideoSetting s : VideoSettingsCatalog.ALL) { result.put(s.key(),read(s)); }
		return result;
	}
	public String lockedReason(VideoSetting s) {
		if (Boolean.getBoolean("alacer.benchmark")) { return "Read-only during a benchmark launch"; }
		if (alacerOption(s.key()) && System.getProperty("alacer."+s.key()) != null) {
			return "Controlled by a launch override";
		}
		if (s.key().equals("vbo") && !GLX.useVbos) { return "VBOs are not supported by this graphics driver"; }
		if (s.key().equals("quality.anisotropicFiltering") && !TextureFiltering.supported()) { return "Anisotropic filtering is not supported by this graphics driver"; }
		if (s.key().equals("profiler.hud") && !this.config.profilerEnabled) { return "The profiler is disabled (profiler.enabled=false in config/alacer.properties)"; }
		return null;
	}
	/** Extra information about how the current system applies a setting, or null. */
	public String note(VideoSetting s) {
		if (s.key().equals("quality.anisotropicFiltering") && TextureFiltering.supported() && TextureFiltering.maximum() < 16) {
			return "This graphics driver supports up to " + Math.round(TextureFiltering.maximum()) + "x; higher choices use that level.";
		}
		return null;
	}
	/** Vanilla option keys have no namespace; Alacer's are stored in config/alacer.properties. */
	private static boolean alacerOption(String key) { return key.indexOf('.') >= 0; }
	private static boolean integerOption(String key) { return key.startsWith("quality.") || key.startsWith("advanced."); }
	public boolean restartRequired() { return this.config.videoRestartRequired(); }
	private int read(VideoSetting s) {
		String key = s.key();
		if (integerOption(key)) { return Integer.parseInt(this.config.videoValue(key)); }
		if (alacerOption(key)) { return Boolean.parseBoolean(this.config.videoValue(key)) ? 1 : 0; }
		GameOptions o = this.minecraft.options;
		return switch (key) {
			case "graphics" -> o.fancyGraphics ? 1 : 0;
			case "distance" -> o.viewDistance;
			case "lighting" -> o.ambientOcclusion;
			case "gamma" -> Math.round(o.gamma*100);
			case "clouds" -> o.cloudRenderMode;
			case "particles" -> o.particles;
			case "mipmaps" -> o.mipmapLevels;
			case "shadows" -> o.entityShadows ? 1 : 0;
			case "bobbing" -> o.viewBobbing ? 1 : 0;
			case "alternatives" -> o.allowBlockAlternatives ? 1 : 0;
			case "fullscreen" -> this.minecraft.isFullscreen() ? 1 : 0;
			case "vsync" -> o.vsync ? 1 : 0;
			case "fps" -> o.fpsLimit;
			case "guiScale" -> o.guiScale;
			case "fov" -> Math.round(o.fov);
			case "anaglyph" -> o.anaglyph ? 1 : 0;
			case "vbo" -> o.useVbo ? 1 : 0;
			default -> throw new IllegalArgumentException(key);
		};
	}
	public void apply(Map<String,Integer> edits) throws IOException {
		if (edits.isEmpty()) { return; }
		Map<String,String> mod = new LinkedHashMap<>();
		boolean vanilla = false;
		for (Map.Entry<String,Integer> e : edits.entrySet()) {
			VideoSetting s = VideoSettingsCatalog.byKey(e.getKey());
			if (lockedReason(s) != null) { throw new IOException(lockedReason(s)); }
			if (s.normalize(e.getValue()) != e.getValue()) { throw new IllegalArgumentException("Invalid setting value"); }
			if (integerOption(s.key())) { mod.put(s.key(),Integer.toString(e.getValue())); }
			else if (alacerOption(s.key())) { mod.put(s.key(),Boolean.toString(e.getValue()!=0)); }
			else { vanilla = true; }
		}
		// Fail before modifying live options if Minecraft's settings directory cannot accept writes.
		Path options = this.minecraft.gameDir.toPath().resolve("options.txt");
		if (vanilla) {
			if (Files.exists(options) && !Files.isWritable(options)) { throw new IOException("Minecraft options.txt is read-only"); }
			Path probe = Files.createTempFile(this.minecraft.gameDir.toPath(),"alacer-write-check-",".tmp");
			Files.delete(probe);
		}
		this.config.saveVideoChanges(mod); // Atomic Alacer file update, with no JVM override leakage.
		boolean terrainReload = edits.containsKey("quality.leaves") || edits.containsKey("quality.lightingStyle") || edits.containsKey("quality.aoStrength")
			|| edits.containsKey("quality.biomeBlend");
		boolean mipmapsChanged = edits.containsKey("mipmaps");
		int newMip = mipmapsChanged ? edits.get("mipmaps") : this.minecraft.options.mipmapLevels;
		int stitchedLevel = (this.minecraft.getBlocksAtlas() instanceof AtlasMipmapAccess a) ? a.alacer$getStitchedMipLevel() : 0;
		boolean cutoutsChanged = edits.containsKey("quality.mipmapCutouts");
		boolean dynamicMipmapClamp = mipmapsChanged && !cutoutsChanged && newMip <= stitchedLevel;

		for (Map.Entry<String,Integer> e : edits.entrySet()) {
			if (alacerOption(e.getKey())) { continue; }
			if (e.getKey().equals("mipmaps") && dynamicMipmapClamp) {
				// Avoid vanilla's submitResourceReload() when existing mipmap levels can be clamped dynamically.
				this.minecraft.options.mipmapLevels = newMip;
				continue;
			}
			applyVanilla(e.getKey(),e.getValue());
		}
		if (terrainReload) { this.minecraft.worldRenderer.reload(); }
		if (cutoutsChanged) {
			this.minecraft.submitResourceReload();
		} else if (edits.containsKey("quality.anisotropicFiltering") && AtlasPadding.layoutChanges(this.config.anisotropicFiltering)) {
			this.minecraft.submitResourceReload();
		} else {
			boolean updateTextureParams = false;
			if (dynamicMipmapClamp) {
				TextureFiltering.clampMaxMipLevel(this.minecraft, newMip);
				updateTextureParams = true;
			}
			if (edits.containsKey("quality.anisotropicFiltering") || edits.containsKey("quality.mipmapFiltering") || edits.containsKey("quality.lodBias") || dynamicMipmapClamp) {
				updateTextureParams = true;
			}
			if (updateTextureParams) {
				TextureFiltering.applyToBlocksAtlas(this.minecraft, this.config.anisotropicFiltering, this.config.mipmapFiltering, this.config.lodBias, this.minecraft.options.mipmapLevels > 0);
				FastTextureUpload.invalidate();
			}
		}
		if (vanilla) { this.minecraft.options.save(); }
	}
	private void applyVanilla(String key, int value) {
		GameOptions o = this.minecraft.options;
		GameOptions.Option option = switch (key) {
			case "graphics" -> GameOptions.Option.GRAPHICS;
			case "distance" -> GameOptions.Option.RENDER_DISTANCE;
			case "lighting" -> GameOptions.Option.AMBIENT_OCCLUSION;
			case "gamma" -> GameOptions.Option.GAMMA;
			case "clouds" -> GameOptions.Option.RENDER_CLOUDS;
			case "particles" -> GameOptions.Option.PARTICLES;
			case "mipmaps" -> GameOptions.Option.MIPMAP_LEVELS;
			case "shadows" -> GameOptions.Option.ENTITY_SHADOWS;
			case "bobbing" -> GameOptions.Option.VIEW_BOBBING;
			case "alternatives" -> GameOptions.Option.BLOCK_ALTERNATIVES;
			case "fullscreen" -> GameOptions.Option.USE_FULLSCREEN;
			case "vsync" -> GameOptions.Option.ENABLE_VSYNC;
			case "fps" -> GameOptions.Option.FRAMERATE_LIMIT;
			case "guiScale" -> GameOptions.Option.GUI_SCALE;
			case "fov" -> GameOptions.Option.FOV;
			case "anaglyph" -> GameOptions.Option.ANAGLYPH;
			case "vbo" -> GameOptions.Option.USE_VBO;
			default -> throw new IllegalArgumentException(key);
		};
		if (option.isFloat()) { o.set(option, key.equals("gamma") ? value/100f : (float)value); }
		else {
			int current = read(VideoSettingsCatalog.byKey(key));
			int modulus = VideoSettingsCatalog.byKey(key).maximum()+1;
			if (current != value) { o.set(option,Math.floorMod(value-current,modulus)); }
		}
	}
}
