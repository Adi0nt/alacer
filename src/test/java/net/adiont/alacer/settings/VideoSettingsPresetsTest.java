package net.adiont.alacer.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VideoSettingsPresetsTest {
	@Test void presetValuesAreValidAndNeverTouchDistanceDisplayPerformanceOrAdvanced() {
		for (VideoSettingsPresets preset : VideoSettingsPresets.values()) {
			for (Map.Entry<String,Integer> e : preset.settings().entrySet()) {
				VideoSetting s = VideoSettingsCatalog.byKey(e.getKey());
				assertEquals((int)e.getValue(),s.normalize(e.getValue()),preset+" "+e.getKey());
				assertTrue(s.category()==VideoSetting.Category.GRAPHICS || s.category()==VideoSetting.Category.DETAILS,e.getKey());
				assertFalse(e.getKey().equals("distance") || e.getKey().equals("gamma"),e.getKey());
				if (s.experimental()) { assertEquals(s.defaultValue(),(int)e.getValue(),preset+" selects experimental "+e.getKey()); }
			}
			assertEquals(VideoSettingsPresets.KEYS,preset.settings().keySet().stream().toList());
		}
	}
	@Test void vanillaPresetIsEveryDefault() {
		for (Map.Entry<String,Integer> e : VideoSettingsPresets.VANILLA.settings().entrySet()) {
			assertEquals(VideoSettingsCatalog.byKey(e.getKey()).defaultValue(),(int)e.getValue(),e.getKey());
		}
		assertTrue(new VideoSettingsDraft(Map.of()).changes().isEmpty());
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of());
		draft.applyPreset(VideoSettingsPresets.VANILLA,s -> true);
		assertTrue(draft.changes().isEmpty());
	}
	@Test void presetIsStagedOnlyForEditableSettingsAndCanBeUndoneByAnotherPreset() {
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of("distance",8));
		draft.applyPreset(VideoSettingsPresets.PERFORMANCE,s -> !s.key().equals("shadows"));
		assertEquals(0,draft.value(VideoSettingsCatalog.byKey("graphics")));
		assertEquals(1,draft.value(VideoSettingsCatalog.byKey("shadows")));
		assertEquals(8,draft.value(VideoSettingsCatalog.byKey("distance")));
		assertFalse(draft.changes().containsKey("shadows")); assertFalse(draft.changes().containsKey("distance"));
		draft.applyPreset(VideoSettingsPresets.VANILLA,s -> true);
		assertTrue(draft.changes().isEmpty());
		draft.applyPreset(VideoSettingsPresets.ENHANCED,s -> true);
		assertEquals(Map.of("quality.entityDistance",150,"quality.blockEntityDistance",150,"quality.biomeBlend",2),draft.changes());
	}
}
