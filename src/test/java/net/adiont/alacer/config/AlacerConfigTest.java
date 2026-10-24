package net.adiont.alacer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlacerConfigTest {

	private static final Logger LOGGER = LogManager.getLogger("AlacerConfigTest");

	@TempDir
	Path dir;

	@AfterEach
	void clearOverrides() {
		System.clearProperty("alacer.render.fastChunkVisibility");
		System.clearProperty("alacer.render.fastParticleRemoval");
		System.clearProperty("alacer.render.fastParticleVertices");
		System.clearProperty("alacer.benchmark");
	}

	@Test
	void benchmarkIgnoresStoredOptionsWithoutRewritingSharedFile() throws IOException {
		Path file = this.dir.resolve("alacer.properties");
		String stored = "# shared settings\nrender.fastParticleLight=false\n"
			+ "render.fastParticleRemoval=false\nprofiler.particleDetail=true\nprofiler.enabled=false\n";
		Files.writeString(file, stored);
		System.setProperty("alacer.benchmark", "true");
		AlacerConfig config = AlacerConfig.load(file, LOGGER);
		assertTrue(config.fastParticleLight);
		assertTrue(config.fastParticleRemoval);
		assertTrue(config.fastParticleVertices);
		assertTrue(config.profilerEnabled);
		assertFalse(config.profilerParticleDetail);
		System.setProperty("alacer.render.fastParticleRemoval", "false");
		assertFalse(AlacerConfig.load(file, LOGGER).fastParticleRemoval);
		assertEquals(stored, Files.readString(file));
	}

	@Test
	void benchmarkDoesNotCreateConfigAndNormalLaunchKeepsStoredOptions() throws IOException {
		Path file = this.dir.resolve("alacer.properties");
		System.setProperty("alacer.benchmark", "true");
		assertTrue(AlacerConfig.load(file, LOGGER).fastParticleLight);
		assertFalse(Files.exists(file));
		System.setProperty("alacer.benchmark", "false");
		Files.writeString(file, "render.fastParticleLight=false\nrender.fastParticleRemoval=false\n");
		AlacerConfig config = AlacerConfig.load(file, LOGGER);
		assertFalse(config.fastParticleLight);
		assertFalse(config.fastParticleRemoval);
	}

	@Test
	void writesDefaultsWhenMissing() throws IOException {
		Path file = this.dir.resolve("config").resolve("alacer.properties");
		AlacerConfig config = AlacerConfig.load(file, LOGGER);
		assertTrue(config.profilerEnabled);
		assertTrue(config.fastChunkVisibility);
		assertFalse(config.verifyChunkVisibility);
		assertEquals(8192, config.profilerHistory);

		String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		assertTrue(written.contains("render.fastChunkVisibility=true"));
		assertTrue(written.contains("profiler.history=8192"));
	}

	@Test
	void readsValuesAndFallsBackOnInvalidOnes() throws IOException {
		Path file = this.dir.resolve("alacer.properties");
		Files.write(file, ("render.fastChunkVisibility=false\n"
			+ "profiler.history=banana\n"
			+ "profiler.hud=maybe\n"
			+ "profiler.logInterval=-5\n").getBytes(StandardCharsets.UTF_8));
		AlacerConfig config = AlacerConfig.load(file, LOGGER);
		assertFalse(config.fastChunkVisibility);
		assertEquals(8192, config.profilerHistory);
		assertFalse(config.hudVisible);
		assertEquals(0, config.profilerLogInterval);

		String rewritten = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		assertTrue(rewritten.contains("render.fastChunkVisibility=false"));
		assertTrue(rewritten.contains("profiler.history=8192"));
	}

	@Test
	void newOptionsHaveSafeDefaults() throws IOException {
		AlacerConfig config = AlacerConfig.load(this.dir.resolve("alacer.properties"), LOGGER);
		assertFalse(config.profilerEntityDetail);
		assertFalse(config.profilerFrustumTiming);
		assertFalse(config.profilerChunkDetail);
		assertEquals("false", config.describe().get("profiler.chunkDetail"));
		assertFalse(config.fastChunkFrustum);
		assertFalse(config.verifyChunkFrustum);
		assertTrue(config.profilerEntityStages);
		assertTrue(config.fastEntityChunkLookup);
		assertFalse(config.verifyEntityChunkLookup);
		assertTrue(config.throttleGlErrorChecks);
		assertFalse(config.verifyGlErrorChecks);
		assertTrue(config.avoidMatrixReads);
		assertFalse(config.verifyAvoidMatrixReads);
		assertFalse(config.skipZeroTranslates);
		assertFalse(config.verifySkipZeroTranslates);
		assertTrue(config.cacheClouds);
		assertFalse(config.verifyCacheClouds);
		assertFalse(config.batchModelLists);
		assertFalse(config.verifyBatchModelLists);
		assertFalse(config.profilerShadowTiming);
		assertTrue(config.fastEntityShadows);
		assertFalse(config.verifyFastEntityShadows);
		assertTrue(config.fastEntityRendererLookup);
		assertFalse(config.verifyEntityRendererLookup);
		assertTrue(config.fastEntityCulling);
		assertFalse(config.verifyEntityCulling);
		assertTrue(config.fastRayTrace);
		assertFalse(config.verifyFastRayTrace);
		assertTrue(config.fastRandomDisplayTicks);
		assertFalse(config.verifyFastRandomDisplayTicks);
		assertTrue(config.fastSoundThread);
		assertFalse(config.verifyFastSoundThread);
		assertFalse(config.multiDrawIndirect);
		assertFalse(config.verifyMultiDrawIndirect);
		assertTrue(config.cameraHysteresis);
		assertFalse(config.profilerParticleDetail);
		assertFalse(config.profilerParticleLightRepeats);
		assertTrue(config.fastParticleLight);
		assertFalse(config.verifyFastParticleLight);
		assertTrue(config.fastParticleRemoval);
		assertFalse(config.verifyFastParticleRemoval);
		assertTrue(config.fastParticleVertices);
		assertFalse(config.verifyFastParticleVertices);
		assertFalse(config.fastParticleLightEntry);
		assertFalse(config.verifyFastParticleLightEntry);
		assertTrue(config.reuseParticleLight == AlacerConfig.REUSE_PARTICLE_LIGHT);
		assertFalse(config.verifyReuseParticleLight);
		assertFalse(config.reuseParticleBoxes);
		assertFalse(config.verifyReuseParticleBoxes);
		assertFalse(config.scalarParticleEntityQuery);
		assertFalse(config.verifyScalarParticleEntityQuery);
		assertFalse(config.reuseShadowPositions);
		assertFalse(config.verifyReuseShadowPositions);
		assertFalse(config.fastCameraSectionFaces);
		assertFalse(config.verifyCameraSectionFaces);
	}

	@Test
	void cameraSectionFacesIsAnUnmeasuredExperiment() throws IOException {
		AlacerConfig config = AlacerConfig.load(this.dir.resolve("faces.properties"), LOGGER);
		assertEquals("false", config.describe().get("render.fastCameraSectionFaces"));
		assertEquals("false", config.describe().get("render.fastCameraSectionFaces.verify"));
		config.setToggle("render.fastCameraSectionFaces", true);
		assertTrue(config.fastCameraSectionFaces);
		// Not part of the shipped-defaults preset: B keeps it off.
		config.setToggle("render.defaults", true);
		assertFalse(config.fastCameraSectionFaces);
		config.setToggle("render.fastCameraSectionFaces", true);
		config.setToggle("render.defaults", false);
		assertFalse(config.fastCameraSectionFaces);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastCameraSectionFaces.verify", true));
	}

	@Test
	void runtimeTogglesOnlyAcceptToggleableKeys() throws IOException {
		AlacerConfig config = AlacerConfig.load(this.dir.resolve("alacer.properties"), LOGGER);
		config.setToggle("render.fastEntityChunkLookup", false);
		assertFalse(config.fastEntityChunkLookup);
		config.setToggle("render.fastChunkVisibility", false);
		assertFalse(config.fastChunkVisibility);
		config.setToggle("render.fastChunkFrustum", false);
		assertFalse(config.fastChunkFrustum);
		config.setToggle("render.fastChunkFrustum", true);
		assertTrue(config.fastChunkFrustum);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastChunkFrustum.verify", true));
		config.setToggle("render.throttleGlErrorChecks", false);
		assertFalse(config.throttleGlErrorChecks);
		config.setToggle("render.avoidMatrixReads", false);
		assertFalse(config.avoidMatrixReads);
		config.setToggle("render.skipZeroTranslates", true);
		assertTrue(config.skipZeroTranslates);
		config.setToggle("render.cacheClouds", false);
		assertFalse(config.cacheClouds);
		config.setToggle("render.batchModelLists", true);
		assertTrue(config.batchModelLists);
		config.setToggle("render.fastEntityShadows", false);
		assertFalse(config.fastEntityShadows);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastEntityShadows.verify", true));
		config.setToggle("render.fastEntityRendererLookup", false);
		assertFalse(config.fastEntityRendererLookup);
		config.setToggle("render.fastEntityCulling", false);
		assertFalse(config.fastEntityCulling);
		config.setToggle("render.fastRayTrace", false);
		assertFalse(config.fastRayTrace);
		config.setToggle("render.fastRandomDisplayTicks", false);
		assertFalse(config.fastRandomDisplayTicks);
		config.setToggle("render.fastSoundThread", false);
		assertFalse(config.fastSoundThread);
		config.setToggle("render.fastSoundThread.verify", true);
		assertTrue(config.verifyFastSoundThread);
		config.setToggle("render.cameraHysteresis", false);
		assertFalse(config.cameraHysteresis);
		config.setToggle("render.multiDrawIndirect", true);
		assertTrue(config.multiDrawIndirect);
		config.setToggle("render.multiDrawIndirect.verify", true);
		assertTrue(config.verifyMultiDrawIndirect);
		// The shadow-off attribution toggle changes a game option in the benchmark, never config.
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("benchmark.disableEntityShadows", true));
		config.setToggle("render.fastParticleLight", false);
		assertFalse(config.fastParticleLight);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastParticleLight.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("benchmark.disableParticles", true));
		config.setToggle("render.fastParticleRemoval", false);
		assertFalse(config.fastParticleRemoval);
		config.setToggle("render.fastParticleVertices", false);
		assertFalse(config.fastParticleVertices);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastParticleVertices.verify", true));
		config.setToggle("render.fastParticleLightEntry", true);
		assertTrue(config.fastParticleLightEntry);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastParticleLightEntry.verify", true));
		config.setToggle("render.reuseParticleLight", true);
		assertTrue(config.reuseParticleLight);
		config.setToggle("render.reuseParticleLight", false);
		assertFalse(config.reuseParticleLight);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.reuseParticleLight.verify", true));
		config.setToggle("render.fastParticleMovement", true);
		config.setToggle("render.scalarParticleEntityQuery", true);
		assertTrue(config.scalarParticleEntityQuery);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.scalarParticleEntityQuery.verify", true));
		config.setToggle("render.reuseParticleBoxes", true);
		assertTrue(config.reuseParticleBoxes);
		config.setToggle("render.reuseShadowPositions", true);
		assertTrue(config.reuseShadowPositions);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.reuseShadowPositions.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.reuseParticleBoxes.verify", true));
		assertTrue(config.fastParticleMovement);
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastParticleMovement.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.fastParticleRemoval.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.throttleGlErrorChecks.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.avoidMatrixReads.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.skipZeroTranslates.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.cacheClouds.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("render.batchModelLists.verify", true));
		assertThrows(IllegalArgumentException.class, () -> config.setToggle("profiler.history", true));
	}

	@Test
	void particleVerticesAreOnByDefaultAndInThePreset() throws IOException {
		AlacerConfig config = AlacerConfig.load(this.dir.resolve("vertices.properties"), LOGGER);
		assertTrue(config.fastParticleVertices);
		config.setToggle("render.defaults", false);
		assertFalse(config.fastParticleVertices);
		config.setToggle("render.defaults", true);
		assertTrue(config.fastParticleVertices);
		// Session 21's light entry failed its gates: off by default and kept off by the preset.
		assertFalse(config.fastParticleLightEntry);
		config.setToggle("render.fastParticleLightEntry", true);
		config.setToggle("render.defaults", true);
		assertFalse(config.fastParticleLightEntry);
		config.setToggle("render.reuseParticleLight", !AlacerConfig.REUSE_PARTICLE_LIGHT);
		config.setToggle("render.defaults", true);
		assertTrue(config.reuseParticleLight == AlacerConfig.REUSE_PARTICLE_LIGHT);
		System.setProperty("alacer.render.fastParticleVertices", "false");
		assertFalse(AlacerConfig.load(this.dir.resolve("vertices.properties"), LOGGER).fastParticleVertices);
	}

	@Test
	void benchmarkPresetMatchesShippedDefaultsAndDisablesEveryRenderOptimization() throws IOException {
		AlacerConfig defaults = AlacerConfig.load(this.dir.resolve("defaults.properties"), LOGGER);
		AlacerConfig config = AlacerConfig.load(this.dir.resolve("comparison.properties"), LOGGER);
		config.batchModelLists = true;
		config.skipZeroTranslates = true;
		config.fastParticleMovement = true;
		config.reuseParticleBoxes = true;
		config.scalarParticleEntityQuery = true;
		config.reuseShadowPositions = true;
		config.fastCameraSectionFaces = true;
		for (boolean enabled : new boolean[]{false, true, false, true}) {
			config.setToggle("render.defaults", enabled);
			for (java.util.Map.Entry<String, String> option : config.describe().entrySet()) {
				if (option.getKey().startsWith("render.") && !option.getKey().endsWith(".verify")) {
					assertEquals(enabled ? defaults.describe().get(option.getKey()) : "false",
						option.getValue(), option.getKey());
				}
			}
			assertFalse(config.profilerEntityDetail);
			assertTrue(config.profilerEnabled);
		}
		// The aggregate preset is a benchmark selector, not a persisted feature/default change.
		assertFalse(config.describe().containsKey("render.defaults"));
	}

	@Test
	void clampsHistory() throws IOException {
		Path file = this.dir.resolve("alacer.properties");
		Files.write(file, "profiler.history=3\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(256, AlacerConfig.load(file, LOGGER).profilerHistory);
	}

	@Test
	void systemPropertyOverridesWithoutBeingPersisted() throws IOException {
		Path file = this.dir.resolve("alacer.properties");
		System.setProperty("alacer.render.fastChunkVisibility", "false");
		AlacerConfig config = AlacerConfig.load(file, LOGGER);
		assertFalse(config.fastChunkVisibility);

		String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		assertTrue(written.contains("render.fastChunkVisibility=true"));
	}
}
