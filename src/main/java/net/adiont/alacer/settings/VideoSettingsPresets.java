package net.adiont.alacer.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staged groups of world-appearance choices. Render distance, brightness, display, camera, Performance and
 * Advanced settings are never part of a preset, so a preset cannot change hardware-dependent or diagnostic choices.
 * Presets never select an experimental value. Anisotropic filtering is only reset to Off: turning it on reloads
 * textures and enlarges the block atlas, which is the user's choice to make.
 */
public enum VideoSettingsPresets {
	PERFORMANCE("Performance", "Fast graphics, no smooth lighting or entity shadows, shorter entity distances and fewer particles.",
		Map.of("graphics",0, "lighting",0, "clouds",1, "particles",1, "quality.entityDistance",75, "quality.blockEntityDistance",75,
			"quality.biomeBlend",0, "quality.particleLimit",2, "quality.droppedItems",0, "shadows",0)),
	VANILLA("Vanilla", "Minecraft's Fancy appearance: every preset setting at its default.", Map.of()),
	ENHANCED("Enhanced", "Vanilla Fancy plus 5x5 biome blending and longer entity and block entity distances.",
		Map.of("quality.entityDistance",150, "quality.blockEntityDistance",150, "quality.biomeBlend",2));

	/** Every setting a preset stages; each preset uses the default for any key it does not list. */
	public static final List<String> KEYS = List.of("graphics","lighting","clouds","particles","quality.leaves","quality.weather",
		"quality.rainSplashes","quality.vignette","quality.entityDistance","quality.blockEntityDistance","quality.biomeBlend",
		"quality.particleLimit","quality.anisotropicFiltering","quality.droppedItems","shadows");
	public final String title, description;
	private final Map<String,Integer> overrides;
	VideoSettingsPresets(String title, String description, Map<String,Integer> overrides) {
		this.title = title; this.description = description; this.overrides = overrides;
	}
	public Map<String,Integer> settings() {
		Map<String,Integer> result = new LinkedHashMap<>();
		for (String key : KEYS) { result.put(key,this.overrides.getOrDefault(key,VideoSettingsCatalog.byKey(key).defaultValue())); }
		return result;
	}
}
