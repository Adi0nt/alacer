package net.adiont.alacer.settings;

import java.util.List;

/** Game-independent settings metadata shared by the menu, staged edits and tests. */
public record VideoSetting(String key, Category category, String title, String description,
		List<String> choices, int minimum, int maximum, int step, int defaultValue, Effect effect) {
	public enum Category {
		GRAPHICS("Graphics", "World quality and viewing distance"),
		DETAILS("Details", "Independent control over individual effects"),
		DISPLAY("Display", "Window, interface and frame pacing"),
		PERFORMANCE("Performance", "Proven optimizations that preserve appearance");
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
	public boolean slider() { return this.choices.isEmpty(); }
	public int normalize(int value) {
		return Math.max(this.minimum, Math.min(this.maximum, this.minimum + Math.round((float)(value-this.minimum)/this.step)*this.step));
	}
	public String valueLabel(int value) {
		if (!slider()) { return this.choices.get(normalize(value)); }
		return switch (this.key) {
			case "distance" -> value + " chunks";
			case "fps" -> value == 260 ? "Unlimited" : value + " FPS";
			case "gamma", "quality.aoStrength", "quality.fogStart", "quality.starBrightness", "quality.weatherOpacity" -> value + "%";
			case "quality.cloudHeight" -> value == 0 ? "Default" : (value > 0 ? "+" : "") + value + " blocks";
			case "fov" -> value + " degrees";
			default -> Integer.toString(value);
		};
	}
}
