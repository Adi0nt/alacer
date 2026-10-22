package net.adiont.alacer.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VideoSettingsDraftTest {
	@Test void editsAreDetachedAndOnlySuccessfulApplyAcceptsThem() {
		Map<String,Integer> live = new LinkedHashMap<>(Map.of("clouds",2,"distance",12));
		VideoSettingsDraft draft = new VideoSettingsDraft(live);
		VideoSetting clouds = VideoSettingsCatalog.byKey("clouds");
		draft.cycle(clouds,1);
		assertEquals(0,draft.value(clouds));
		assertEquals(2,live.get("clouds"));
		assertEquals(Map.of("clouds",0),draft.changes());
		draft.accept(); assertTrue(draft.changes().isEmpty());
		draft.cycle(clouds,-1); assertEquals(2,draft.value(clouds));
		assertEquals(Map.of("clouds",2),draft.changes());
	}
	@Test void categoryResetDoesNotChangeOtherPagesOrLockedSettings() {
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of("quality.leaves",1,"quality.weather",3,"clouds",0));
		draft.reset(VideoSetting.Category.DETAILS,s -> !s.key().equals("quality.weather"));
		assertEquals(0,draft.value(VideoSettingsCatalog.byKey("quality.leaves")));
		assertEquals(3,draft.value(VideoSettingsCatalog.byKey("quality.weather")));
		assertEquals(0,draft.value(VideoSettingsCatalog.byKey("clouds")));
		assertEquals(Map.of("quality.leaves",0),draft.changes());
	}
	@Test void searchIsCaseInsensitiveGlobalAndDoesNotLoseEdits() {
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of());
		draft.set(VideoSettingsCatalog.byKey("clouds"),0);
		assertTrue(draft.matching(VideoSetting.Category.DISPLAY,"  LEAVES ").stream().anyMatch(s -> s.key().equals("quality.leaves")));
		assertTrue(draft.matching(VideoSetting.Category.DISPLAY,"").stream().allMatch(s -> s.category()==VideoSetting.Category.DISPLAY));
		assertTrue(draft.matching(VideoSetting.Category.GRAPHICS,"nonexistentsetting").isEmpty());
		assertEquals(Map.of("clouds",0),draft.changes());
	}
	@Test void catalogHasValidDefaultsUniqueKeysAndNoExperimentsOrDiagnosticControls() {
		HashSet<String> keys = new HashSet<>();
		for (VideoSetting s : VideoSettingsCatalog.ALL) {
			assertTrue(keys.add(s.key()),s.key()); assertEquals(s.defaultValue(),s.normalize(s.defaultValue()));
			assertEquals(s.minimum(),s.normalize(-10000)); assertEquals(s.maximum(),s.normalize(10000));
			assertFalse(s.key().contains("verify")); assertFalse(s.key().startsWith("profiler."));
			if (s.key().startsWith("render.")) { assertEquals(1,s.defaultValue()); }
		}
		assertEquals(9,keys.stream().filter(k -> k.startsWith("render.")).count());
		assertFalse(keys.contains("render.reuseParticleLight"));
		assertEquals(VideoSetting.Effect.RESTART,VideoSettingsCatalog.byKey("render.fastParticleVertices").effect());
	}
	@Test void graphicsSplitsFollowGraphicsAndNewDetailChoicesKeepVanillaDefaults() {
		for (String key : new String[] {"quality.leaves","quality.weather","quality.rainSplashes","quality.vignette"}) {
			VideoSetting s = VideoSettingsCatalog.byKey(key);
			assertEquals("Follow Graphics",s.valueLabel(s.defaultValue()),key);
			assertEquals(VideoSetting.Category.DETAILS,s.category(),key);
		}
		assertEquals("Off",VideoSettingsCatalog.byKey("quality.rainSplashes").valueLabel(3));
		assertEquals("Off",VideoSettingsCatalog.byKey("quality.vignette").valueLabel(1));
		assertEquals("On",VideoSettingsCatalog.byKey("quality.vignette").valueLabel(2));
		assertEquals("On",VideoSettingsCatalog.byKey("quality.stars").valueLabel(VideoSettingsCatalog.byKey("quality.stars").defaultValue()));
		assertEquals("On",VideoSettingsCatalog.byKey("quality.enchantGlint").valueLabel(VideoSettingsCatalog.byKey("quality.enchantGlint").defaultValue()));
		assertEquals("Fancy",VideoSettingsCatalog.byKey("quality.droppedItems").valueLabel(VideoSettingsCatalog.byKey("quality.droppedItems").defaultValue()));
		for (String key : new String[] {"quality.rainSplashes","quality.vignette","quality.stars","quality.enchantGlint","quality.droppedItems"}) {
			assertEquals(VideoSetting.Effect.IMMEDIATE,VideoSettingsCatalog.byKey(key).effect(),key);
		}
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of());
		assertTrue(draft.changes().isEmpty());
		assertTrue(draft.matching(VideoSetting.Category.GRAPHICS,"glint").stream().anyMatch(s -> s.key().equals("quality.enchantGlint")));
		assertTrue(draft.matching(VideoSetting.Category.GRAPHICS,"world-border").stream().anyMatch(s -> s.key().equals("quality.vignette")));
	}
}
