package net.adiont.alacer.settings;

import java.util.List;

/** Game-independent settings metadata shared by the menu, staged edits and tests. */
public record VideoSetting(String key, Category category, String title, String description,
		List<String> choices, int minimum, int maximum, int step, int defaultValue, Effect effect, boolean experimental) {
	public enum Category {
		GRAPHICS("Graphics", "World quality and viewing distance"),
		DETAILS("Details", "Independent control over individual effects"),
		DISPLAY("Display", "Window, interface, camera and frame pacing"),
		PERFORMANCE("Performance", "Proven optimizations that preserve appearance"),
		ADVANCED("Advanced", "Chunk scheduling trade-offs and diagnostics");
		public final String title, description;
		Category(String title, String description) { this.title = title; this.description = description; }
	}
	public enum Effect {
		IMMEDIATE("Applies immediately"), REBUILD("Rebuilds terrain"), RESOURCES("Reloads textures"),
		DISPLAY("Updates display"), RESTART("Requires restart");
		public final String label;
		Effect(String label) { this.label = label; }
	}
	public VideoSetting { choices = List.copyOf(choices); }
	public VideoSetting asExperimental() {
		return new VideoSetting(this.key,this.category,this.title,this.description,this.choices,this.minimum,this.maximum,this.step,this.defaultValue,this.effect,true);
	}
	public boolean slider() { return this.choices.isEmpty(); }
	public int normalize(int value) {
		return Math.max(this.minimum, Math.min(this.maximum, this.minimum + Math.round((float)(value-this.minimum)/this.step)*this.step));
	}
	public String valueLabel(int value) {
		if (!slider()) { return this.choices.get(normalize(value)); }
		return switch (this.key) {
			case "distance" -> value + " chunks";
			case "fps" -> value == 260 ? "Unlimited" : value + " FPS";
			case "gamma", "quality.aoStrength", "quality.entityDistance", "quality.blockEntityDistance", "quality.starBrightness", "quality.weatherOpacity" -> value + "%";
			case "quality.fogStart" -> value == 100 ? "Off" : value + "%";
			case "quality.cloudHeight" -> value == 0 ? "Default" : (value > 0 ? "+" : "") + value + " blocks";
			case "quality.fovEffects", "quality.damageTilt", "quality.viewBobbing", "quality.distortion" -> value == 0 ? "Off" : value + "%";
			case "quality.thirdPersonDistance" -> value + (value == 1 ? " block" : " blocks");
			case "advanced.chunkThreads" -> value + (value == 1 ? " thread" : " threads");
			case "fov" -> value + " degrees";
			default -> Integer.toString(value);
		};
	}
}
