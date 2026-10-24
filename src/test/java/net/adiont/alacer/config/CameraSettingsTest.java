package net.adiont.alacer.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.adiont.alacer.settings.VideoSetting;
import net.adiont.alacer.settings.VideoSettingsCatalog;
import net.adiont.alacer.settings.VideoSettingsDraft;
import net.adiont.alacer.settings.VideoSettingsPresets;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraSettingsTest {
	private static final String[] KEYS = {"quality.viewBobbing", "quality.distortion", "quality.thirdPersonDistance",
		"quality.cameraCollision", "quality.fovTransition", "quality.zoom"};
	@TempDir Path dir;
	@AfterEach void clear() { System.clearProperty("alacer.benchmark"); System.clearProperty("alacer.quality.zoom"); }
	private AlacerConfig load(Path p) { return AlacerConfig.load(p, LogManager.getLogger("CameraSettingsTest")); }

	@Test void defaultsAreVanillaInTheFileTheMenuAndBenchmarks() throws IOException {
		Path p = this.dir.resolve("alacer.properties");
		AlacerConfig config = load(p);
		assertEquals(100, config.viewBobbing); assertEquals(100, config.distortion); assertEquals(4, config.thirdPersonDistance);
		assertEquals(1, config.cameraCollision); assertEquals(1, config.fovTransition); assertEquals(2, config.zoom);
		Map<String, String> labels = Map.of("quality.viewBobbing", "100%", "quality.distortion", "100%",
			"quality.thirdPersonDistance", "4 blocks", "quality.cameraCollision", "On", "quality.fovTransition", "Vanilla",
			"quality.zoom", "4x");
		for (String key : KEYS) {
			VideoSetting s = VideoSettingsCatalog.byKey(key);
			assertEquals(config.describe().get(key), Integer.toString(s.defaultValue()), key);
			assertEquals(labels.get(key), s.valueLabel(s.defaultValue()), key);
			assertEquals(VideoSetting.Category.DISPLAY, s.category(), key);
			assertEquals(VideoSetting.Effect.IMMEDIATE, s.effect(), key);
			assertFalse(s.experimental(), key);
			assertTrue(Files.readString(p).contains(key + "="), key + " written with its default");
		}
		Files.writeString(p, "quality.viewBobbing=0\nquality.thirdPersonDistance=12\nquality.cameraCollision=0\nquality.zoom=4\n");
		System.setProperty("alacer.benchmark", "true");
		AlacerConfig bench = load(p);
		assertEquals(100, bench.viewBobbing); assertEquals(4, bench.thirdPersonDistance); assertEquals(1, bench.cameraCollision);
		assertEquals(2, bench.zoom);
	}

	@Test void editsPersistApplyLiveAndReloadAndInvalidValuesAreRejectedOrClamped() throws IOException {
		Path p = this.dir.resolve("alacer.properties");
		AlacerConfig config = load(p);
		Map<String, String> edits = Map.of("quality.viewBobbing", "35", "quality.distortion", "0", "quality.thirdPersonDistance", "9",
			"quality.cameraCollision", "0", "quality.fovTransition", "3", "quality.zoom", "0");
		config.saveVideoChanges(edits);
		assertEquals(35, config.viewBobbing); assertEquals(0, config.distortion); assertEquals(9, config.thirdPersonDistance);
		assertEquals(0, config.cameraCollision); assertEquals(3, config.fovTransition); assertEquals(0, config.zoom);
		assertFalse(config.videoRestartRequired());
		AlacerConfig reloaded = load(p);
		for (Map.Entry<String, String> e : edits.entrySet()) { assertEquals(e.getValue(), reloaded.describe().get(e.getKey()), e.getKey()); }
		byte[] before = Files.readAllBytes(p);
		assertThrows(IllegalArgumentException.class, () -> config.saveVideoChanges(Map.of("quality.viewBobbing", "33")));
		assertThrows(IllegalArgumentException.class, () -> config.saveVideoChanges(Map.of("quality.thirdPersonDistance", "17")));
		assertThrows(IllegalArgumentException.class, () -> config.saveVideoChanges(Map.of("quality.zoom", "5")));
		assertArrayEquals(before, Files.readAllBytes(p));
		Files.writeString(p, "quality.viewBobbing=42\nquality.thirdPersonDistance=0\nquality.distortion=900\nquality.fovTransition=-3\n");
		AlacerConfig clamped = load(p);
		assertEquals(40, clamped.viewBobbing); assertEquals(1, clamped.thirdPersonDistance); assertEquals(100, clamped.distortion);
		assertEquals(0, clamped.fovTransition);
		System.setProperty("alacer.quality.zoom", "3");
		assertThrows(IOException.class, () -> load(p).saveVideoChanges(Map.of("quality.zoom", "1")), "launch override locks it");
	}

	@Test void labelsSearchAndPresetsLeaveTheCameraAlone() {
		assertEquals("Off", VideoSettingsCatalog.byKey("quality.viewBobbing").valueLabel(0));
		assertEquals("Off", VideoSettingsCatalog.byKey("quality.distortion").valueLabel(0));
		assertEquals("1 block", VideoSettingsCatalog.byKey("quality.thirdPersonDistance").valueLabel(1));
		assertEquals("16 blocks", VideoSettingsCatalog.byKey("quality.thirdPersonDistance").valueLabel(16));
		assertEquals("Off", VideoSettingsCatalog.byKey("quality.cameraCollision").valueLabel(0));
		assertEquals("Instant", VideoSettingsCatalog.byKey("quality.fovTransition").valueLabel(0));
		assertEquals("Very smooth", VideoSettingsCatalog.byKey("quality.fovTransition").valueLabel(3));
		assertEquals("8x", VideoSettingsCatalog.byKey("quality.zoom").valueLabel(4));
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of());
		for (String query : new String[] {"nausea", "third-person", "zoom", "bobbing", "walls"}) {
			assertFalse(draft.matching(VideoSetting.Category.GRAPHICS, query).isEmpty(), query);
		}
		for (VideoSettingsPresets preset : VideoSettingsPresets.values()) {
			for (String key : KEYS) { assertFalse(preset.settings().containsKey(key), preset + " " + key); }
		}
	}
}
