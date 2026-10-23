package net.adiont.alacer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class AlacerMixinPluginTest {

	@Test
	void movementCensusRequiresItsOwnBenchmarkAndProfilerGates() throws Exception {
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String entity = "net.adiont.alacer.mixin.EntityParticleMovementCensusMixin";
		String world = "net.adiont.alacer.mixin.WorldParticleMovementCensusMixin";
		assertFalse(plugin.shouldApplyMixin("unused", entity));
		assertFalse(plugin.shouldApplyMixin("unused", world));
		assertFalse(AlacerMixinPlugin.particleMovementCensusEnabled());
		try {
			System.setProperty("alacer.profiler.particleMovementCensus", "true");
			assertFalse(AlacerMixinPlugin.particleMovementCensusEnabled());
			System.setProperty("alacer.benchmark", "true");
			assertTrue(AlacerMixinPlugin.particleMovementCensusEnabled());
			System.setProperty("alacer.profiler.enabled", "false");
			assertFalse(AlacerMixinPlugin.particleMovementCensusEnabled());
		} finally {
			System.clearProperty("alacer.profiler.particleMovementCensus");
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.profiler.enabled");
		}
		var field = AlacerMixinPlugin.class.getDeclaredField("particleMovementCensus");
		field.setAccessible(true); field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", entity));
		assertTrue(plugin.shouldApplyMixin("unused", world));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleDrawDiagnosticMixin"));
	}

	@Test
	void movementRegionsRequireTheCensusGatesAndTheirOwnFlag() throws Exception {
		String[] keys = {"alacer.profiler.particleMovementRegions", "alacer.profiler.particleMovementCensus",
			"alacer.benchmark", "alacer.profiler.enabled"};
		String[] previous = new String[keys.length];
		for (int i = 0; i < keys.length; i++) { previous[i] = System.getProperty(keys[i]); System.clearProperty(keys[i]); }
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String entity = "net.adiont.alacer.mixin.EntityParticleMovementRegionsMixin";
		String world = "net.adiont.alacer.mixin.WorldParticleMovementRegionsMixin";
		try {
			assertFalse(plugin.shouldApplyMixin("unused", entity));
			assertFalse(plugin.shouldApplyMixin("unused", world));
			assertFalse(AlacerMixinPlugin.particleMovementRegionsEnabled());
			System.setProperty(keys[0], "true");
			assertFalse(AlacerMixinPlugin.particleMovementRegionsEnabled());
			System.setProperty(keys[1], "true");
			assertFalse(AlacerMixinPlugin.particleMovementRegionsEnabled());
			System.setProperty(keys[2], "true");
			assertTrue(AlacerMixinPlugin.particleMovementRegionsEnabled());
			System.setProperty(keys[3], "false");
			assertFalse(AlacerMixinPlugin.particleMovementRegionsEnabled());
			System.setProperty(keys[3], "true");
			System.setProperty(keys[0], "false");
			assertFalse(AlacerMixinPlugin.particleMovementRegionsEnabled());
			assertTrue(AlacerMixinPlugin.particleMovementCensusEnabled());
		} finally {
			for (int i = 0; i < keys.length; i++) {
				if (previous[i] == null) { System.clearProperty(keys[i]); } else { System.setProperty(keys[i], previous[i]); }
			}
		}
		var census = AlacerMixinPlugin.class.getDeclaredField("particleMovementCensus");
		census.setAccessible(true); census.setBoolean(plugin, true);
		assertFalse(plugin.shouldApplyMixin("unused", entity));
		assertFalse(plugin.shouldApplyMixin("unused", world));
		var field = AlacerMixinPlugin.class.getDeclaredField("particleMovementRegions");
		field.setAccessible(true); field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", entity));
		assertTrue(plugin.shouldApplyMixin("unused", world));
	}

	@Test
	void particleDrawDiagnosticIsCommandLineOnlyAndRequiresBenchmarkAndProfiler() throws Exception {
		String[] keys = {"alacer.profiler.particleDrawDiagnostics", "alacer.benchmark", "alacer.profiler.enabled"};
		String[] previous = new String[keys.length];
		for (int i = 0; i < keys.length; i++) { previous[i] = System.getProperty(keys[i]); System.clearProperty(keys[i]); }
		try {
			assertFalse(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[0], "true");
			assertFalse(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[1], "true");
			assertTrue(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[2], "false");
			assertFalse(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[2], "invalid");
			assertFalse(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[2], "true");
			assertTrue(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
			System.setProperty(keys[0], "false");
			assertFalse(AlacerMixinPlugin.particleDrawDiagnosticsEnabled());
		} finally {
			for (int i = 0; i < keys.length; i++) {
				if (previous[i] == null) { System.clearProperty(keys[i]); } else { System.setProperty(keys[i], previous[i]); }
			}
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String hook = "net.adiont.alacer.mixin.ParticleDrawDiagnosticMixin";
		assertFalse(plugin.shouldApplyMixin("unused", hook));
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("particleDrawDiagnostics");
		field.setAccessible(true);
		field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", hook));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleDetailMixin"));
	}

	@Test
	void scalarEntityQueryStartupIsIndependentAndBenchmarkIgnoresFile() throws Exception {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.particleEntityQueryEnabled(file));
		file.setProperty("render.scalarParticleEntityQuery.verify", "true");
		assertTrue(AlacerMixinPlugin.particleEntityQueryEnabled(file));
		try {
			System.setProperty("alacer.benchmark", "true");
			assertFalse(AlacerMixinPlugin.particleEntityQueryEnabled(file));
			System.setProperty("alacer.benchmark.toggle", " render.scalarParticleEntityQuery ");
			assertTrue(AlacerMixinPlugin.particleEntityQueryEnabled(file));
			assertFalse(AlacerMixinPlugin.particleBoxesEnabled(file));
			assertFalse(AlacerMixinPlugin.particleMovementEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark"); System.clearProperty("alacer.benchmark.toggle");
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleEntityQueryMixin"));
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("particleEntityQuery");
		field.setAccessible(true); field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleEntityQueryMixin"));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityParticleBoxMixin"));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleCollisionMixin"));
	}

	@Test
	void shadowPositionHookIsAbsentUnlessOptionVerificationOrToggle() throws Exception {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.shadowPositionsEnabled(file));
		file.setProperty("render.reuseShadowPositions.verify", "true");
		assertTrue(AlacerMixinPlugin.shadowPositionsEnabled(file));
		file.clear();
		file.setProperty("render.reuseShadowPositions", "true");
		assertTrue(AlacerMixinPlugin.shadowPositionsEnabled(file));
		try {
			System.setProperty("alacer.benchmark", "true");
			assertFalse(AlacerMixinPlugin.shadowPositionsEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.defaults");
			assertFalse(AlacerMixinPlugin.shadowPositionsEnabled(file));
			System.setProperty("alacer.benchmark.toggle", " render.reuseShadowPositions ");
			assertTrue(AlacerMixinPlugin.shadowPositionsEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark"); System.clearProperty("alacer.benchmark.toggle");
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityShadowPositionMixin"));
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("shadowPositions");
		field.setAccessible(true); field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityShadowPositionMixin"));
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityShadowMixin"));
	}

	@Test
	void particleVertexHooksFollowTheOptionVerificationOrToggle() {
		Properties file = new Properties();
		assertTrue(AlacerMixinPlugin.particleVerticesEnabled(file) == AlacerConfig.FAST_PARTICLE_VERTICES);
		file.setProperty("render.fastParticleVertices", "false");
		assertFalse(AlacerMixinPlugin.particleVerticesEnabled(file));
		file.setProperty("render.fastParticleVertices.verify", "true");
		assertTrue(AlacerMixinPlugin.particleVerticesEnabled(file));
		file.setProperty("render.fastParticleVertices.verify", "false");
		try {
			System.setProperty("alacer.benchmark.toggle", " render.fastParticleVertices ");
			assertTrue(AlacerMixinPlugin.particleVerticesEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.defaults");
			assertTrue(AlacerMixinPlugin.particleVerticesEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.fastParticleRemoval");
			assertFalse(AlacerMixinPlugin.particleVerticesEnabled(file));
			System.setProperty("alacer.render.fastParticleVertices", "true");
			assertTrue(AlacerMixinPlugin.particleVerticesEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark.toggle");
			System.clearProperty("alacer.render.fastParticleVertices");
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleVertexMixin"));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleBufferAccessor"));
	}

	@Test
	void particleLightEntryHooksFollowTheOptionVerificationOrToggle() throws Exception {
		Properties file = new Properties();
		assertTrue(AlacerMixinPlugin.particleLightEntryEnabled(file) == AlacerConfig.FAST_PARTICLE_LIGHT_ENTRY);
		file.setProperty("render.fastParticleLightEntry", "false");
		assertFalse(AlacerMixinPlugin.particleLightEntryEnabled(file));
		file.setProperty("render.fastParticleLightEntry.verify", "true");
		assertTrue(AlacerMixinPlugin.particleLightEntryEnabled(file));
		file.setProperty("render.fastParticleLightEntry.verify", "false");
		try {
			System.setProperty("alacer.benchmark.toggle", " render.fastParticleLightEntry ");
			assertTrue(AlacerMixinPlugin.particleLightEntryEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.fastParticleLight");
			assertFalse(AlacerMixinPlugin.particleLightEntryEnabled(file));
			System.setProperty("alacer.benchmark", "true");
			file.setProperty("render.fastParticleLightEntry", String.valueOf(!AlacerConfig.FAST_PARTICLE_LIGHT_ENTRY));
			assertTrue(AlacerMixinPlugin.particleLightEntryEnabled(file) == AlacerConfig.FAST_PARTICLE_LIGHT_ENTRY);
			System.setProperty("alacer.render.fastParticleLightEntry", "true");
			assertTrue(AlacerMixinPlugin.particleLightEntryEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.benchmark.toggle");
			System.clearProperty("alacer.render.fastParticleLightEntry");
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String[] hooks = {"EntityParticleLightEntryMixin", "BlockParticleLightEntryMixin"};
		for (String hook : hooks) { assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("particleLightEntry");
		field.setAccessible(true);
		field.setBoolean(plugin, true);
		for (String hook : hooks) { assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityParticleLightMixin"));
	}

	@Test
	void particleLightReuseHooksFollowTheOptionVerificationOrToggle() throws Exception {
		Properties file = new Properties();
		assertTrue(AlacerMixinPlugin.particleLightReuseEnabled(file) == AlacerConfig.REUSE_PARTICLE_LIGHT);
		file.setProperty("render.reuseParticleLight", "false");
		assertFalse(AlacerMixinPlugin.particleLightReuseEnabled(file));
		file.setProperty("render.reuseParticleLight.verify", "true");
		assertTrue(AlacerMixinPlugin.particleLightReuseEnabled(file));
		file.setProperty("render.reuseParticleLight.verify", "false");
		try {
			System.setProperty("alacer.benchmark.toggle", " render.reuseParticleLight ");
			assertTrue(AlacerMixinPlugin.particleLightReuseEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.fastParticleLight");
			assertFalse(AlacerMixinPlugin.particleLightReuseEnabled(file));
			System.setProperty("alacer.benchmark", "true");
			file.setProperty("render.reuseParticleLight", String.valueOf(!AlacerConfig.REUSE_PARTICLE_LIGHT));
			assertTrue(AlacerMixinPlugin.particleLightReuseEnabled(file) == AlacerConfig.REUSE_PARTICLE_LIGHT);
			System.setProperty("alacer.render.reuseParticleLight", "true");
			assertTrue(AlacerMixinPlugin.particleLightReuseEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.benchmark.toggle");
			System.clearProperty("alacer.render.reuseParticleLight");
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String hook = "net.adiont.alacer.mixin.ParticleManagerLightReuseMixin";
		assertFalse(plugin.shouldApplyMixin("unused", hook));
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("particleLightReuse");
		field.setAccessible(true);
		field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", hook));
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleManagerMixin"));
	}

	@Test
	void boxExperimentIsSeparateAndStartupGated() {
		Properties file = new Properties();
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityParticleBoxMixin"));
		assertFalse(AlacerMixinPlugin.particleBoxesEnabled(file));
		file.setProperty("render.reuseParticleBoxes.verify", "true");
		assertTrue(AlacerMixinPlugin.particleBoxesEnabled(file));
		try {
			System.setProperty("alacer.benchmark", "true");
			assertFalse(AlacerMixinPlugin.particleBoxesEnabled(file));
			System.setProperty("alacer.benchmark.toggle", " render.reuseParticleBoxes ");
			assertTrue(AlacerMixinPlugin.particleBoxesEnabled(file));
			assertFalse(AlacerMixinPlugin.particleMovementEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.benchmark.toggle");
		}
	}

	@Test
	void movementExperimentHooksAreAbsentUnlessRequested() {
		Properties file = new Properties();
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.EntityParticleMovementMixin"));
		assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleCollisionMixin"));
		assertFalse(AlacerMixinPlugin.particleMovementEnabled(file));
		file.setProperty("render.fastParticleMovement.verify", "true");
		assertTrue(AlacerMixinPlugin.particleMovementEnabled(file));
		try {
			System.setProperty("alacer.benchmark", "true");
			assertFalse(AlacerMixinPlugin.particleMovementEnabled(file));
			System.setProperty("alacer.benchmark.toggle", " render.fastParticleMovement ");
			assertTrue(AlacerMixinPlugin.particleMovementEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.benchmark.toggle");
		}
	}

	@Test
	void benchmarkStartupHooksIgnoreFileAndHonorExplicitOverrides() {
		Properties file = new Properties();
		file.setProperty("profiler.particleDetail", "true");
		file.setProperty("render.batchModelLists", "true");
		try {
			System.setProperty("alacer.benchmark", "true");
			assertFalse(AlacerMixinPlugin.particleDetailEnabled(file));
			assertFalse(AlacerMixinPlugin.modelBatchEnabled(file));
			file.setProperty("profiler.enabled", "false");
			System.setProperty("alacer.profiler.particleDetail", "true");
			assertTrue(AlacerMixinPlugin.particleDetailEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.batchModelLists");
			assertTrue(AlacerMixinPlugin.modelBatchEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark");
			System.clearProperty("alacer.profiler.particleDetail");
			System.clearProperty("alacer.benchmark.toggle");
		}
	}

	@Test
	void everyChunkDiagnosticMixinIsGated() throws Exception {
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String[] hooks = {"ChunkBuildTimingMixin", "ChunkDispatcherTimingMixin", "ChunkTaskTimingMixin", "WorldRendererBackendTimingMixin"};
		for (String hook : hooks) { assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("chunkDetail");
		field.setAccessible(true);
		field.setBoolean(plugin, true);
		for (String hook : hooks) { assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.WorldRendererMixin"));
	}

	@Test
	void chunkTimingHooksAreAbsentUnlessRequestedAndProfilingEnabled() {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.chunkDetailEnabled(file));
		file.setProperty("profiler.chunkDetail", "true");
		assertTrue(AlacerMixinPlugin.chunkDetailEnabled(file));
		file.setProperty("profiler.enabled", "false");
		assertFalse(AlacerMixinPlugin.chunkDetailEnabled(file));
		try {
			System.setProperty("alacer.profiler.enabled", "true");
			assertTrue(AlacerMixinPlugin.chunkDetailEnabled(file));
			System.setProperty("alacer.profiler.chunkDetail", "false");
			assertFalse(AlacerMixinPlugin.chunkDetailEnabled(file));
		} finally {
			System.clearProperty("alacer.profiler.enabled");
			System.clearProperty("alacer.profiler.chunkDetail");
		}
	}

	@Test
	void modelExperimentHooksAreAbsentUnlessRequested() {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.modelBatchEnabled(file));
		file.setProperty("render.batchModelLists.verify", "true");
		assertTrue(AlacerMixinPlugin.modelBatchEnabled(file));
		file.clear();
		try {
			System.setProperty("alacer.benchmark.toggle", " render.batchModelLists ");
			assertTrue(AlacerMixinPlugin.modelBatchEnabled(file));
			System.setProperty("alacer.benchmark.toggle", "render.cacheClouds");
			assertFalse(AlacerMixinPlugin.modelBatchEnabled(file));
		} finally {
			System.clearProperty("alacer.benchmark.toggle");
		}
		file.setProperty("render.batchModelLists", "true");
		assertTrue(AlacerMixinPlugin.modelBatchEnabled(file));
	}

	@Test
	void shadowTimingHookIsAbsentUnlessRequested() {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.shadowTimingEnabled(file));
		file.setProperty("profiler.shadowTiming", "true");
		assertTrue(AlacerMixinPlugin.shadowTimingEnabled(file));
		file.setProperty("profiler.enabled", "false");
		assertFalse(AlacerMixinPlugin.shadowTimingEnabled(file));
	}

	@Test
	void particleDetailHooksAreAbsentUnlessRequested() throws Exception {
		Properties file = new Properties();
		assertFalse(AlacerMixinPlugin.particleDetailEnabled(file));
		file.setProperty("profiler.particleDetail", "true");
		assertTrue(AlacerMixinPlugin.particleDetailEnabled(file));
		file.setProperty("profiler.enabled", "false");
		assertFalse(AlacerMixinPlugin.particleDetailEnabled(file));
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String[] hooks = {"ParticleDetailMixin", "EntityParticleDetailMixin"};
		for (String hook : hooks) { assertFalse(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("particleDetail");
		field.setAccessible(true);
		field.setBoolean(plugin, true);
		for (String hook : hooks) { assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin." + hook)); }
		assertTrue(plugin.shouldApplyMixin("unused", "net.adiont.alacer.mixin.ParticleManagerMixin"));
	}

	@Test
	void mobTickFreezeExistsOnlyInExplicitFrozenMobBenchmarks() throws Exception {
		String[] keys = {"alacer.benchmark", "alacer.benchmark.freezeMobs"};
		String[] saved = {System.getProperty(keys[0]), System.getProperty(keys[1])};
		try {
			System.clearProperty(keys[0]); System.clearProperty(keys[1]);
			assertFalse(AlacerMixinPlugin.benchmarkMobFreezeEnabled());
			System.setProperty(keys[1], "true");
			assertFalse(AlacerMixinPlugin.benchmarkMobFreezeEnabled());
			System.setProperty(keys[0], "true");
			assertTrue(AlacerMixinPlugin.benchmarkMobFreezeEnabled());
			System.setProperty(keys[1], "false");
			assertFalse(AlacerMixinPlugin.benchmarkMobFreezeEnabled());
			System.setProperty(keys[1], "invalid");
			assertFalse(AlacerMixinPlugin.benchmarkMobFreezeEnabled());
		} finally {
			for (int i = 0; i < keys.length; i++) {
				if (saved[i] == null) { System.clearProperty(keys[i]); } else { System.setProperty(keys[i], saved[i]); }
			}
		}
		AlacerMixinPlugin plugin = new AlacerMixinPlugin();
		String hook = "net.adiont.alacer.mixin.BenchmarkMobFreezeMixin";
		assertFalse(plugin.shouldApplyMixin("unused", hook));
		java.lang.reflect.Field field = AlacerMixinPlugin.class.getDeclaredField("benchmarkMobFreeze");
		field.setAccessible(true); field.setBoolean(plugin, true);
		assertTrue(plugin.shouldApplyMixin("unused", hook));
	}

	@Test
	void startupFlagsKeepTheConfiguredDefaultForInvalidValues() {
		Properties file = new Properties();
		assertTrue(AlacerMixinPlugin.flag(file, "test.stageFlag", true));
		assertFalse(AlacerMixinPlugin.flag(file, "test.stageFlag", false));
		file.setProperty("test.stageFlag", "invalid");
		assertTrue(AlacerMixinPlugin.flag(file, "test.stageFlag", true));
		assertFalse(AlacerMixinPlugin.flag(file, "test.stageFlag", false));
		file.setProperty("test.stageFlag", " FALSE ");
		assertFalse(AlacerMixinPlugin.flag(file, "test.stageFlag", true));
		file.setProperty("test.stageFlag", " TRUE ");
		assertTrue(AlacerMixinPlugin.flag(file, "test.stageFlag", false));
	}
}
