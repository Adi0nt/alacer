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
			assertFalse(s.key().contains("verify"));
			// The overlay is the only profiler option offered: it changes what is shown, not what is measured.
			assertTrue(!s.key().startsWith("profiler.") || s.key().equals("profiler.hud"),s.key());
			if (s.key().startsWith("render.")) { assertEquals(1,s.defaultValue()); }
		}
		assertEquals(14,keys.stream().filter(k -> k.startsWith("render.")).count());
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
	@Test void atmosphereControlsAreDiscoverableAndResetWithGraphicsOnly() {
		VideoSettingsDraft draft = new VideoSettingsDraft(Map.of("quality.cloudHeight",32,"quality.starBrightness",160,"quality.stars",0));
		assertEquals("+32 blocks",VideoSettingsCatalog.byKey("quality.cloudHeight").valueLabel(32));
		assertEquals("Default",VideoSettingsCatalog.byKey("quality.cloudHeight").valueLabel(0));
		assertEquals("160%",VideoSettingsCatalog.byKey("quality.starBrightness").valueLabel(160));
		assertEquals("75%",VideoSettingsCatalog.byKey("quality.fogStart").valueLabel(75));
		assertTrue(draft.matching(VideoSetting.Category.DETAILS,"fog").stream().anyMatch(s -> s.key().equals("quality.fogStart")));
		draft.reset(VideoSetting.Category.GRAPHICS,s -> true);
		assertEquals(0,draft.value(VideoSettingsCatalog.byKey("quality.cloudHeight")));
		assertEquals(100,draft.value(VideoSettingsCatalog.byKey("quality.starBrightness")));
		assertEquals(0,draft.value(VideoSettingsCatalog.byKey("quality.stars")));
	}
	@Test void newSettingsKeepVanillaDefaultsAndLabels() {
		Map<String,String> defaults = Map.ofEntries(Map.entry("quality.entityDistance","100%"),Map.entry("quality.blockEntityDistance","100%"),
			Map.entry("quality.fogStart","75%"),Map.entry("quality.sunMoon","On"),Map.entry("quality.waterAnimation","On"),
			Map.entry("quality.otherAnimations","On"),Map.entry("quality.particleLimit","4000"),Map.entry("quality.biomeBlend","3x3"),
			Map.entry("quality.anisotropicFiltering","Off"),Map.entry("quality.fovEffects","100%"),Map.entry("quality.damageTilt","100%"),
			Map.entry("advanced.chunkUpdateBudget","Vanilla"),Map.entry("advanced.chunkThreads","2 threads"),Map.entry("profiler.hud","Off"));
		for (Map.Entry<String,String> e : defaults.entrySet()) {
			VideoSetting s = VideoSettingsCatalog.byKey(e.getKey());
			assertEquals(e.getValue(),s.valueLabel(s.defaultValue()),e.getKey());
		}
		assertEquals("Off",VideoSettingsCatalog.byKey("quality.fogStart").valueLabel(100));
		assertEquals("Off",VideoSettingsCatalog.byKey("quality.fovEffects").valueLabel(0));
		assertEquals("1 thread",VideoSettingsCatalog.byKey("advanced.chunkThreads").valueLabel(1));
		assertEquals(VideoSetting.Effect.REBUILD,VideoSettingsCatalog.byKey("quality.biomeBlend").effect());
		assertEquals(VideoSetting.Effect.RESTART,VideoSettingsCatalog.byKey("advanced.chunkThreads").effect());
		assertTrue(VideoSettingsCatalog.byKey("advanced.chunkThreads").experimental());
		assertFalse(VideoSettingsCatalog.byKey("quality.anisotropicFiltering").experimental());
		assertEquals(VideoSetting.Effect.RESOURCES,VideoSettingsCatalog.byKey("quality.anisotropicFiltering").effect());
		assertEquals(1,VideoSettingsCatalog.ALL.stream().filter(VideoSetting::experimental).count());
		for (VideoSetting.Category c : VideoSetting.Category.values()) {
			assertFalse(new VideoSettingsDraft(Map.of()).matching(c,"").isEmpty(),c.title);
		}
		assertTrue(new VideoSettingsDraft(Map.of()).matching(VideoSetting.Category.GRAPHICS,"anisotropic").stream().anyMatch(s -> s.key().equals("quality.anisotropicFiltering")));
	}
}
