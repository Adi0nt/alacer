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
		for (String key : new String[] {"alacer.benchmark", "alacer.benchmark.toggle", "alacer.render.terrainShader", "alacer.render.terrainVaos"}) { System.clearProperty(key); }
	}
	@Test void allExperimentsDefaultOffAndStayOutsideTheDefaultsPreset() throws Exception {
		AlacerConfig c = AlacerConfig.load(this.dir.resolve("alacer.properties"), LogManager.getLogger());
		for (String key : new String[] {"render.fastChunkNeighbours", "render.directBufferUploads", "render.modernGl.verify", "render.fastChunkNeighbours.verify"}) {
			assertEquals("false", c.describe().get(key));
		}
		assertEquals("true", c.describe().get("render.terrainShader"));
		assertEquals("true", c.describe().get("render.terrainVaos"));
		c.setToggle("render.modernPipeline", true);
		assertTrue(c.fastChunkNeighbours && c.terrainShader && c.terrainVaos && c.directBufferUploads);
		c.setToggle("render.defaults", false);
		assertFalse(c.fastChunkNeighbours || c.terrainShader || c.terrainVaos || c.directBufferUploads);
		c.setToggle("render.defaults", true);
		assertTrue(c.terrainShader && c.terrainVaos);
		assertFalse(c.fastChunkNeighbours || c.directBufferUploads);
	}
	@Test void startupGatingAndBenchmarkFileIsolationAgree() throws Exception {
		Properties p = new Properties(); p.setProperty("render.terrainShader", "true");
		assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		p.clear(); p.setProperty("render.terrainVaos", "true");
		assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		Path path = this.dir.resolve("alacer.properties"); Files.writeString(path, "render.terrainShader=true\nrender.fastChunkNeighbours=true\n");
		System.setProperty("alacer.benchmark", "true");
		assertFalse(AlacerMixinPlugin.modernGlEnabled(p));
		AlacerConfig c = AlacerConfig.load(path, LogManager.getLogger()); assertFalse(c.terrainShader || c.fastChunkNeighbours);
		System.setProperty("alacer.benchmark.toggle", "render.modernPipeline"); assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		System.setProperty("alacer.benchmark.toggle", "render.fastChunkNeighbours"); assertFalse(AlacerMixinPlugin.modernGlEnabled(p));
		System.setProperty("alacer.render.terrainShader", "true"); assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
		System.setProperty("alacer.render.terrainVaos", "true"); assertTrue(AlacerMixinPlugin.modernGlEnabled(p));
	}
}
