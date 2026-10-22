package net.adiont.alacer.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoSettingsPersistenceTest {
	@TempDir Path dir;
	@AfterEach void clear() {
		System.clearProperty("alacer.render.cacheClouds"); System.clearProperty("alacer.benchmark");
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
}
