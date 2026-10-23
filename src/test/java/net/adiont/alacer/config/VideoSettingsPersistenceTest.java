package net.adiont.alacer.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import net.adiont.alacer.settings.VideoSetting;
import net.adiont.alacer.settings.VideoSettingsCatalog;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoSettingsPersistenceTest {
	@TempDir Path dir;
	@AfterEach void clear() {
		System.clearProperty("alacer.render.cacheClouds"); System.clearProperty("alacer.benchmark");
		System.clearProperty("alacer.quality.stars"); System.clearProperty("alacer.advanced.chunkThreads");
	}
	private AlacerConfig load(Path p) { return AlacerConfig.load(p,LogManager.getLogger("VideoSettingsTest")); }
	private Properties properties(Path p) throws IOException {
		Properties values=new Properties(); try (Reader in=Files.newBufferedReader(p)) { values.load(in); } return values;
	}
	@Test void explicitEditsPersistWithoutLeakingLaunchOverridesOrUnrelatedRuntimeToggles() throws IOException {
		Path p=this.dir.resolve("alacer.properties");
		Files.writeString(p,"render.cacheClouds=true\nrender.fastChunkVisibility=true\n");
		System.setProperty("alacer.render.cacheClouds","false");
		AlacerConfig config=load(p); config.setToggle("render.fastChunkVisibility",false);
		config.saveVideoChanges(Map.of("quality.leaves","2"));
		assertEquals("true",properties(p).getProperty("render.cacheClouds"));
		assertEquals("true",properties(p).getProperty("render.fastChunkVisibility"));
		assertEquals("2",properties(p).getProperty("quality.leaves"));
		assertEquals(2,config.leavesQuality);
		assertThrows(IOException.class,() -> config.saveVideoChanges(Map.of("render.cacheClouds","true")));
	}
	@Test void startupSelectedOptionIsSavedButOnlyChangesLiveStateAfterRelaunch() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		config.saveVideoChanges(Map.of("render.fastParticleVertices","false"));
		assertTrue(config.fastParticleVertices); assertEquals("false",config.videoValue("render.fastParticleVertices"));
		assertEquals("true",config.describe().get("render.fastParticleVertices")); assertTrue(config.videoRestartRequired());
		assertFalse(load(p).fastParticleVertices);
		config.saveVideoChanges(Map.of("render.fastParticleVertices","true")); assertFalse(config.videoRestartRequired());
	}
	@Test void failedSaveRetainsFileAndLiveStateAndRejectsNonMenuKeys() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		byte[] before=Files.readAllBytes(p);
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("render.reuseParticleLight","true")));
		assertArrayEquals(before,Files.readAllBytes(p)); assertFalse(config.reuseParticleLight);
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.leaves","99")));
		Files.delete(p); Files.createDirectory(p);
		assertThrows(IOException.class,() -> config.saveVideoChanges(Map.of("render.cacheClouds","false")));
		assertTrue(config.cacheClouds);
	}
	@Test void benchmarkNeverWritesUserPreferences() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); Files.writeString(p,"quality.weather=3\n");
		System.setProperty("alacer.benchmark","true"); AlacerConfig config=load(p);
		assertEquals(0,config.weatherQuality);
		assertThrows(IOException.class,() -> config.saveVideoChanges(Map.of("quality.weather","1")));
		assertEquals("quality.weather=3\n",Files.readString(p));
	}
	@Test void lightingPreferencesPersistAndKeepVanillaDefaults() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		assertEquals(0,config.smoothLightingStyle); assertEquals(100,config.ambientOcclusionStrength);
		config.saveVideoChanges(Map.of("quality.lightingStyle","1","quality.aoStrength","70"));
		assertEquals(1,config.smoothLightingStyle); assertEquals(70,config.ambientOcclusionStrength);
		AlacerConfig reloaded=load(p);
		assertEquals(1,reloaded.smoothLightingStyle); assertEquals(70,reloaded.ambientOcclusionStrength);
		assertEquals("70",reloaded.describe().get("quality.aoStrength"));
		assertFalse(config.videoRestartRequired());
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.aoStrength","101")));
	}
	@Test void fileMergeRetainsUnknownValuesAndMalformedFileIsNotOverwritten() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); Files.writeString(p,"future.option=keep me\nquality.leaves=0\n");
		SettingsFile.update(p,Map.of("quality.leaves","1")); assertEquals("keep me",properties(p).getProperty("future.option"));
		Files.writeString(p,"bad=\\uZZZZ\n"); byte[] before=Files.readAllBytes(p);
		assertThrows(IOException.class,() -> SettingsFile.update(p,Map.of("quality.leaves","2")));
		assertArrayEquals(before,Files.readAllBytes(p));
	}
	@Test void everyQualityMenuDefaultMatchesTheConfigDefaultAndIsExported() throws IOException {
		AlacerConfig config=load(this.dir.resolve("alacer.properties"));
		for (VideoSetting s : VideoSettingsCatalog.ALL) {
			if (s.key().startsWith("quality.") || s.key().startsWith("advanced.")) { assertEquals(Integer.toString(s.defaultValue()),config.describe().get(s.key()),s.key()); }
			if (s.key().startsWith("render.") || s.key().startsWith("profiler.")) { assertEquals(Boolean.toString(s.defaultValue()!=0),config.describe().get(s.key()),s.key()); }
		}
		assertEquals(0,config.rainSplashQuality); assertEquals(0,config.vignetteQuality);
		assertEquals(1,config.starsQuality); assertEquals(1,config.enchantGlintQuality); assertEquals(1,config.droppedItemsQuality);
	}
	@Test void detailQualityPreferencesPersistApplyLiveAndRejectInvalidValues() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		config.saveVideoChanges(Map.of("quality.rainSplashes","3","quality.vignette","1","quality.stars","0","quality.enchantGlint","0","quality.droppedItems","0"));
		assertEquals(3,config.rainSplashQuality); assertEquals(1,config.vignetteQuality); assertEquals(0,config.starsQuality);
		assertEquals(0,config.enchantGlintQuality); assertEquals(0,config.droppedItemsQuality); assertFalse(config.videoRestartRequired());
		AlacerConfig reloaded=load(p);
		assertEquals(3,reloaded.rainSplashQuality); assertEquals(1,reloaded.vignetteQuality); assertEquals(0,reloaded.starsQuality);
		assertEquals(0,reloaded.enchantGlintQuality); assertEquals(0,reloaded.droppedItemsQuality);
		assertEquals("0",reloaded.describe().get("quality.droppedItems"));
		byte[] before=Files.readAllBytes(p);
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.vignette","3")));
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.stars","2")));
		assertArrayEquals(before,Files.readAllBytes(p)); assertEquals(1,config.vignetteQuality);
	}
	@Test void detailQualityFileValuesAreClampedAndLaunchOverridesAreNotSaved() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); Files.writeString(p,"quality.rainSplashes=9\nquality.droppedItems=-4\n");
		System.setProperty("alacer.quality.stars","0");
		AlacerConfig config=load(p);
		assertEquals(3,config.rainSplashQuality); assertEquals(0,config.droppedItemsQuality); assertEquals(0,config.starsQuality);
		assertEquals("1",properties(p).getProperty("quality.stars"));
		assertThrows(IOException.class,() -> config.saveVideoChanges(Map.of("quality.stars","1")));
	}
	@Test void benchmarkUsesVanillaQualityDefaultsRegardlessOfSavedPreferences() throws IOException {
		Path p=this.dir.resolve("alacer.properties");
		Files.writeString(p,"quality.rainSplashes=3\nquality.vignette=1\nquality.stars=0\nquality.enchantGlint=0\nquality.droppedItems=0\n");
		System.setProperty("alacer.benchmark","true"); AlacerConfig config=load(p);
		assertEquals("0",config.describe().get("quality.rainSplashes")); assertEquals("0",config.describe().get("quality.vignette"));
		assertEquals("1",config.describe().get("quality.stars")); assertEquals("1",config.describe().get("quality.enchantGlint"));
		assertEquals("1",config.describe().get("quality.droppedItems"));
	}
	@Test void expandedSettingsPersistApplyLiveAndReloadWithVanillaDefaults() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		assertEquals(100,config.entityDistance); assertEquals(100,config.blockEntityDistance); assertEquals(75,config.fogStart);
		assertEquals(1,config.sunMoonQuality); assertEquals(4,config.particleLimit); assertEquals(1,config.biomeBlend);
		assertEquals(0,config.anisotropicFiltering); assertEquals(100,config.fovEffects); assertEquals(100,config.damageTilt);
		assertEquals(2,config.chunkUpdateBudget); assertEquals(2,config.chunkThreads);
		Map<String,String> edits=Map.ofEntries(Map.entry("quality.entityDistance","250"),Map.entry("quality.blockEntityDistance","50"),
			Map.entry("quality.fogStart","100"),Map.entry("quality.sunMoon","0"),Map.entry("quality.waterAnimation","0"),
			Map.entry("quality.lavaAnimation","0"),Map.entry("quality.fireAnimation","0"),Map.entry("quality.portalAnimation","0"),
			Map.entry("quality.otherAnimations","0"),Map.entry("quality.particleLimit","1"),Map.entry("quality.biomeBlend","3"),
			Map.entry("quality.anisotropicFiltering","4"),Map.entry("quality.fovEffects","0"),Map.entry("quality.damageTilt","35"),
			Map.entry("advanced.chunkUpdateBudget","0"),Map.entry("profiler.hud","true"));
		config.saveVideoChanges(edits);
		assertEquals(250,config.entityDistance); assertEquals(50,config.blockEntityDistance); assertEquals(100,config.fogStart);
		assertEquals(0,config.sunMoonQuality); assertEquals(0,config.waterAnimation); assertEquals(0,config.otherAnimations);
		assertEquals(1,config.particleLimit); assertEquals(3,config.biomeBlend); assertEquals(4,config.anisotropicFiltering);
		assertEquals(0,config.fovEffects); assertEquals(35,config.damageTilt); assertEquals(0,config.chunkUpdateBudget);
		assertTrue(config.hudVisible); assertFalse(config.videoRestartRequired());
		AlacerConfig reloaded=load(p);
		for (Map.Entry<String,String> e : edits.entrySet()) { assertEquals(e.getValue(),reloaded.describe().get(e.getKey()),e.getKey()); }
	}
	@Test void chunkThreadsAreSavedForTheNextLaunchOnly() throws IOException {
		Path p=this.dir.resolve("alacer.properties"); AlacerConfig config=load(p);
		config.saveVideoChanges(Map.of("advanced.chunkThreads","4"));
		assertEquals(2,config.chunkThreads); assertEquals("4",config.videoValue("advanced.chunkThreads"));
		assertEquals("2",config.describe().get("advanced.chunkThreads")); assertTrue(config.videoRestartRequired());
		assertEquals(4,load(p).chunkThreads);
		config.saveVideoChanges(Map.of("advanced.chunkThreads","2")); assertFalse(config.videoRestartRequired());
	}
	@Test void expandedSettingsRejectInvalidMenuValuesAndClampOrSnapFileValues() throws IOException {
		Path p=this.dir.resolve("alacer.properties");
		Files.writeString(p,"quality.entityDistance=9000\nquality.blockEntityDistance=110\nquality.fogStart=3\nquality.particleLimit=-1\n"
			+"quality.biomeBlend=banana\nadvanced.chunkThreads=64\nquality.fovEffects=42\nprofiler.hud=maybe\n");
		AlacerConfig config=load(p);
		assertEquals(500,config.entityDistance); assertEquals(100,config.blockEntityDistance); assertEquals(10,config.fogStart);
		assertEquals(0,config.particleLimit); assertEquals(1,config.biomeBlend); assertEquals(6,config.chunkThreads);
		assertEquals(40,config.fovEffects); assertFalse(config.hudVisible);
		byte[] before=Files.readAllBytes(p);
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.entityDistance","110")));
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("quality.anisotropicFiltering","5")));
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("advanced.chunkThreads","7")));
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("profiler.hud","2")));
		assertThrows(IllegalArgumentException.class,() -> config.saveVideoChanges(Map.of("profiler.entityDetail","true")));
		assertArrayEquals(before,Files.readAllBytes(p)); assertEquals(500,config.entityDistance);
		System.setProperty("alacer.advanced.chunkThreads","3");
		assertEquals(3,load(p).chunkThreads);
		assertThrows(IOException.class,() -> load(p).saveVideoChanges(Map.of("advanced.chunkThreads","4")));
	}
	@Test void benchmarkUsesVanillaDefaultsForExpandedSettings() throws IOException {
		Path p=this.dir.resolve("alacer.properties");
		Files.writeString(p,"quality.entityDistance=500\nquality.fogStart=100\nquality.biomeBlend=3\nadvanced.chunkThreads=6\nadvanced.chunkUpdateBudget=4\nquality.particleLimit=0\n");
		System.setProperty("alacer.benchmark","true"); AlacerConfig config=load(p);
		assertEquals(100,config.entityDistance); assertEquals(75,config.fogStart); assertEquals(1,config.biomeBlend);
		assertEquals(2,config.chunkThreads); assertEquals(2,config.chunkUpdateBudget); assertEquals(4,config.particleLimit);
	}
}
