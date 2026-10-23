package net.adiont.alacer.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies profiling-only mixins just when they are enabled, so they cost nothing otherwise.
 * Mixins are applied before the mod initializes, so the option is read here directly, with the
 * same precedence as {@code AlacerConfig}: system property first, then the config file.
 */
public class AlacerMixinPlugin implements IMixinConfigPlugin {

	/** Mixins that only exist to measure, applied with {@code profiler.entityDetail} (shadow timing: its own flag). */
	private static final Set<String> DETAIL_MIXINS = new HashSet<>(Arrays.asList(
		"EntityRenderDetailMixin", "GlCallCountMixin", "GlStateCountMixin", "DrawCountMixin", "LightmapCountMixin"));
	private static final Set<String> STAGE_MIXINS = new HashSet<>(Arrays.asList(
		"LivingEntityDrawMixin", "EntityModelAnimationMixin", "EntityShadowDetailMixin"));

	private boolean entityDetail;
	private boolean entityStages;
	private boolean modelBatch;
	private boolean shadowTiming;
	private boolean chunkDetail;
	private boolean particleDetail;
	private boolean particleDrawDiagnostics;
	private boolean particleMovementCensus;
	private boolean particleMovementRegions;
	private boolean particleMovement;
	private boolean particleBoxes;
	private boolean particleEntityQuery;
	private boolean particleVertices;
	private boolean particleLightEntry;
	private boolean particleLightReuse;
	private boolean shadowPositions;

	@Override
	public void onLoad(String mixinPackage) {
		Properties file = new Properties();
		Path path = FabricLoader.getInstance().getConfigDir().resolve("alacer.properties");
		if (!Boolean.getBoolean("alacer.benchmark") && Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				file.load(reader);
			} catch (IOException | IllegalArgumentException ignored) {
				// Fall back to defaults; AlacerConfig reports unreadable files.
			}
		}
		this.entityDetail = flag(file, "profiler.enabled", true) && flag(file, "profiler.entityDetail", false);
		this.entityStages = this.entityDetail && flag(file, "profiler.entityStages", true);
		this.modelBatch = modelBatchEnabled(file);
		this.shadowTiming = shadowTimingEnabled(file);
		this.chunkDetail = chunkDetailEnabled(file);
		this.particleDetail = particleDetailEnabled(file);
		this.particleDrawDiagnostics = particleDrawDiagnosticsEnabled();
		this.particleMovementCensus = particleMovementCensusEnabled();
		this.particleMovementRegions = particleMovementRegionsEnabled();
		this.particleMovement = particleMovementEnabled(file);
		this.particleBoxes = particleBoxesEnabled(file);
		this.particleEntityQuery = particleEntityQueryEnabled(file);
		this.particleVertices = particleVerticesEnabled(file);
		this.particleLightEntry = particleLightEntryEnabled(file);
		this.particleLightReuse = particleLightReuseEnabled(file);
		this.shadowPositions = shadowPositionsEnabled(file);
	}

	static boolean shadowPositionsEnabled(Properties file) {
		return flag(file, "render.reuseShadowPositions", false) || flag(file, "render.reuseShadowPositions.verify", false)
			|| "render.reuseShadowPositions".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleMovementCensusEnabled() {
		return Boolean.parseBoolean(System.getProperty("alacer.profiler.particleMovementCensus", "false").trim())
			&& Boolean.parseBoolean(System.getProperty("alacer.benchmark", "false").trim())
			&& Boolean.parseBoolean(System.getProperty("alacer.profiler.enabled", "true").trim());
	}

	/** Session 25 region marks: command line only, and only on top of the movement census's own gates. */
	static boolean particleMovementRegionsEnabled() {
		return particleMovementCensusEnabled()
			&& Boolean.parseBoolean(System.getProperty("alacer.profiler.particleMovementRegions", "false").trim());
	}

	static boolean particleLightReuseEnabled(Properties file) {
		return flag(file, "render.reuseParticleLight", AlacerConfig.REUSE_PARTICLE_LIGHT)
			|| flag(file, "render.reuseParticleLight.verify", false)
			|| "render.reuseParticleLight".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleLightEntryEnabled(Properties file) {
		return flag(file, "render.fastParticleLightEntry", AlacerConfig.FAST_PARTICLE_LIGHT_ENTRY)
			|| flag(file, "render.fastParticleLightEntry.verify", false)
			|| "render.fastParticleLightEntry".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleVerticesEnabled(Properties file) {
		return flag(file, "render.fastParticleVertices", AlacerConfig.FAST_PARTICLE_VERTICES)
			|| flag(file, "render.fastParticleVertices.verify", false)
			|| "render.fastParticleVertices".equals(System.getProperty("alacer.benchmark.toggle", "").trim())
			|| "render.defaults".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleEntityQueryEnabled(Properties file) {
		return flag(file, "render.scalarParticleEntityQuery", false) || flag(file, "render.scalarParticleEntityQuery.verify", false)
			|| "render.scalarParticleEntityQuery".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleBoxesEnabled(Properties file) {
		return flag(file, "render.reuseParticleBoxes", false) || flag(file, "render.reuseParticleBoxes.verify", false)
			|| "render.reuseParticleBoxes".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleMovementEnabled(Properties file) {
		return flag(file, "render.fastParticleMovement", false) || flag(file, "render.fastParticleMovement.verify", false)
			|| "render.fastParticleMovement".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean particleDetailEnabled(Properties file) {
		return flag(file, "profiler.enabled", true) && flag(file, "profiler.particleDetail", false);
	}

	/** Command-line-only benchmark diagnostic; keep in sync with ParticleDrawDiagnostics.ENABLED. */
	static boolean particleDrawDiagnosticsEnabled() {
		return Boolean.getBoolean("alacer.profiler.particleDrawDiagnostics")
			&& Boolean.getBoolean("alacer.benchmark")
			&& Boolean.parseBoolean(System.getProperty("alacer.profiler.enabled", "true"));
	}

	static boolean chunkDetailEnabled(Properties file) {
		return flag(file, "profiler.enabled", true) && flag(file, "profiler.chunkDetail", false);
	}

	static boolean modelBatchEnabled(Properties file) {
		return flag(file, "render.batchModelLists", false) || flag(file, "render.batchModelLists.verify", false)
			|| "render.batchModelLists".equals(System.getProperty("alacer.benchmark.toggle", "").trim());
	}

	static boolean shadowTimingEnabled(Properties file) {
		return flag(file, "profiler.enabled", true) && flag(file, "profiler.shadowTiming", false);
	}

	static boolean flag(Properties file, String key, boolean def) {
		String value = System.getProperty("alacer." + key,
			Boolean.getBoolean("alacer.benchmark") ? null : file.getProperty(key));
		if (value == null) {
			return def;
		}
		if ("true".equalsIgnoreCase(value.trim())) {
			return true;
		}
		if ("false".equalsIgnoreCase(value.trim())) {
			return false;
		}
		return def;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (name.equals("ParticleDrawDiagnosticMixin")) { return this.particleDrawDiagnostics; }
		if (name.equals("EntityParticleMovementCensusMixin") || name.equals("WorldParticleMovementCensusMixin")) {
			return this.particleMovementCensus;
		}
		if (name.equals("EntityParticleMovementRegionsMixin") || name.equals("WorldParticleMovementRegionsMixin")) {
			return this.particleMovementRegions;
		}
		if (name.equals("ParticleEntityQueryMixin")) { return this.particleEntityQuery; }
		if (name.equals("ParticleVertexMixin") || name.equals("ParticleBufferAccessor")) { return this.particleVertices; }
		if (name.equals("EntityParticleLightEntryMixin") || name.equals("BlockParticleLightEntryMixin")) {
			return this.particleLightEntry;
		}
		if (name.equals("ParticleManagerLightReuseMixin")) { return this.particleLightReuse; }
		if (name.equals("EntityParticleBoxMixin")) { return this.particleBoxes; }
		if (name.equals("EntityShadowPositionMixin")) { return this.shadowPositions; }
		if (name.equals("EntityParticleMovementMixin") || name.equals("ParticleCollisionMixin")) {
			return this.particleMovement;
		}
		if (name.equals("ChunkDispatcherTimingMixin") || name.equals("ChunkBuildTimingMixin")
			|| name.equals("ChunkTaskTimingMixin") || name.equals("WorldRendererBackendTimingMixin")) {
			return this.chunkDetail;
		}
		if (name.equals("ModelPartBatchMixin") || name.equals("ModelUploadMixin")
			|| name.equals("BoxAccessor") || name.equals("BufferBuilderAccessor")) {
			return this.modelBatch;
		}
		if (name.equals("ParticleDetailMixin") || name.equals("EntityParticleDetailMixin")) {
			return this.particleDetail;
		}
		if (name.equals("EntityShadowTimingMixin")) {
			return this.shadowTiming;
		}
		if (STAGE_MIXINS.contains(name)) {
			return this.entityStages;
		}
		return DETAIL_MIXINS.contains(name) ? this.entityDetail : true;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
