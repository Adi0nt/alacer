package net.adiont.alacer.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModernRenderingConfigTest {
	@TempDir Path dir;
	@AfterEach void clean() {
		for (String key : new String[] {"alacer.benchmark", "alacer.benchmark.toggle", "alacer.render.terrainVaos"}) { System.clearProperty(key); }
	}
	@Test void allExperimentsDefaultOffAndStayOutsideTheDefaultsPreset() throws Exception {
		AlacerConfig c = AlacerConfig.load(this.dir.resolve("alacer.properties"), LogManager.getLogger());
		for (String key : new String[] {"render.fastChunkNeighbours", "render.terrainVaos", "render.directBufferUploads", "render.modernGl.verify", "render.fastChunkNeighbours.verify"}) {
			assertEquals("false", c.describe().get(key));
		}
		c.setToggle("render.modernPipeline", true);
		assertTrue(c.fastChunkNeighbours && c.terrainVaos && c.directBufferUploads);
		for (boolean defaults : new boolean[] {true, false}) {
			c.setToggle("render.defaults", defaults);
			assertFalse(c.fastChunkNeighbours || c.terrainVaos || c.directBufferUploads);
		}
	}
	@Test void startupGatingAndBenchmarkFileIsolationAgree() throws Exception {
		Properties p = new Properties(); p.setProperty("render.terrainVaos", "true");
		assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		Path path = this.dir.resolve("alacer.properties"); Files.writeString(path, "render.terrainVaos=true\nrender.fastChunkNeighbours=true\n");
		System.setProperty("alacer.benchmark", "true");
		assertFalse(AlacerMixinPlugin.modernGlEnabled(p));
		AlacerConfig c = AlacerConfig.load(path, LogManager.getLogger()); assertFalse(c.terrainVaos || c.fastChunkNeighbours);
		System.setProperty("alacer.benchmark.toggle", "render.modernPipeline"); assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		System.setProperty("alacer.benchmark.toggle", "render.fastChunkNeighbours"); assertFalse(AlacerMixinPlugin.modernGlEnabled(p));
		System.setProperty("alacer.render.terrainVaos", "true"); assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
	}
}
