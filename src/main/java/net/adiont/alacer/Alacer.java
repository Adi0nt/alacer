package net.adiont.alacer;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.adiont.alacer.benchmark.BenchmarkRunner;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.ChunkRenderDispatcherAccessor;
import net.adiont.alacer.mixin.WorldRendererAccessor;
import net.adiont.alacer.profiler.FrameProfiler;
import net.adiont.alacer.profiler.ProfileReport;
import net.adiont.alacer.profiler.ProfilerHud;
import net.adiont.alacer.render.EntityRendering;
import net.adiont.alacer.render.EntityShadows;
import net.adiont.alacer.render.GlErrorChecks;
import net.adiont.alacer.render.ParticleLight;
import net.adiont.alacer.render.ParticleRemoval;
import net.adiont.alacer.render.ParticleTimings;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.text.LiteralText;
import net.ornithemc.osl.entrypoints.api.client.ClientModInitializer;
import net.ornithemc.osl.keybinds.api.KeybindEvents;
import net.ornithemc.osl.keybinds.api.KeybindRegistry;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;

/**
 * Client entrypoint. Owns the configuration and the long-lived components, and is the single
 * place mixins call into, so the mixins themselves stay one-line hooks.
 */
public final class Alacer implements ClientModInitializer, FrameProfiler.ChunkCounters {

	public static final String MOD_ID = "alacer";
	public static final Logger LOGGER = LogManager.getLogger("Alacer");

	private static Alacer instance;

	private AlacerConfig config;
	private FrameProfiler profiler;
	private ProfilerHud hud;
	private BenchmarkRunner benchmark;
	private KeyBinding hudKey;
	private KeyBinding exportKey;
	private KeyBinding zoomKey;
	private long nextLog;

	public static Alacer get() {
		return instance;
	}

	@Override
	public void initClient() {
		instance = this;
		Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
		this.config = AlacerConfig.load(configFile, LOGGER);
		LOGGER.info("Alacer initializing: {}", this.config.describe());

		if (this.config.profilerEnabled) {
			this.profiler = new FrameProfiler(this.config.profilerHistory);
			this.profiler.setChunkCounters(this);
			this.hud = new ProfilerHud(this.profiler);
			if (!this.profiler.measuresAllocation()) {
				LOGGER.warn("Per-thread allocation counters are unavailable on this JVM; allocation stats disabled");
			}

			MinecraftClientEvents.TICK_START.register(minecraft -> {
				this.profiler.tickStart();
				if (this.benchmark != null) {
					this.benchmark.onTickStart();
				}
			});
			MinecraftClientEvents.TICK_END.register(minecraft -> {
				this.profiler.tickEnd();
				handleInput(minecraft);
				logIfDue();
			});
			KeybindEvents.REGISTER_KEYBINDS.register(() -> {
				this.hudKey = KeybindRegistry.register("key.alacer.hud", Keyboard.KEY_F7, "key.categories.alacer");
				this.exportKey = KeybindRegistry.register("key.alacer.export", Keyboard.KEY_F9, "key.categories.alacer");
			});

			if (Boolean.getBoolean("alacer.benchmark")) {
				this.benchmark = new BenchmarkRunner(BenchmarkRunner.defaultLabel(this.config.describe()));
				this.profiler.setListener(this.benchmark);
				LOGGER.info("Benchmark mode enabled");
			}
		} else if (Boolean.getBoolean("alacer.benchmark")) {
			LOGGER.error("Benchmark mode requires profiler.enabled=true; ignoring");
		}

		// Unbound by default (key code 0), so the camera is vanilla's until the user assigns it.
		KeybindEvents.REGISTER_KEYBINDS.register(() -> this.zoomKey = KeybindRegistry.register("key.alacer.zoom", Keyboard.KEY_NONE, "key.categories.alacer"));

		EntityRendering.init(this.profiler, this.config.profilerEntityDetail, this.config.profilerEntityStages);
		EntityShadows.init(this.profiler);
		net.adiont.alacer.render.EntityLighting.init();
		ParticleTimings.init(this.profiler);
		ParticleLight.init();
		net.adiont.alacer.render.ParticleLightEntry.init();
		net.adiont.alacer.render.ParticleLightReuse.init();
		net.adiont.alacer.render.ParticleMovement.init();
		net.adiont.alacer.render.ParticleBoxes.init();
		net.adiont.alacer.render.ParticleEntityQuery.init();
		ParticleRemoval.init();
		net.adiont.alacer.render.ParticleVertices.init();
		MinecraftClientEvents.READY.register(minecraft -> LOGGER.info("Environment: {}", describeEnvironment()));
	}

	public AlacerConfig config() {
		return this.config;
	}

	/** The frame profiler, or null when profiling is disabled. */
	public FrameProfiler profiler() {
		return this.profiler;
	}

	/** Start of a {@code runGame()} iteration. */
	public void onFrameStart() {
		GlErrorChecks.onFrameStart();
		if (this.profiler != null) {
			if (this.config.profilerChunkDetail) {
				net.adiont.alacer.render.ChunkPipelineTimings.frameBoundary(this.profiler);
			}
			if (this.config.profilerChunkLatency) {
				net.adiont.alacer.render.ChunkUpdateLatency.frameBoundary(this.profiler);
			}
			this.profiler.frameBoundary();
		}
		if (this.benchmark != null) {
			this.benchmark.onFrameStart();
		}
	}

	/** True while the zoom key is held. */
	public boolean zooming() {
		return this.zoomKey != null && this.zoomKey.isPressed();
	}

	public void begin(FrameProfiler.Section section) {
		if (this.profiler != null) {
			this.profiler.begin(section);
		}
	}

	public void end(FrameProfiler.Section section) {
		if (this.profiler != null) {
			this.profiler.end(section);
		}
	}

	public void renderHud(Minecraft minecraft) {
		if (this.hud != null && this.config.hudVisible && !minecraft.options.debugEnabled) {
			this.hud.render(minecraft);
		}
	}

	private void handleInput(Minecraft minecraft) {
		if (this.hudKey != null) {
			while (this.hudKey.consumeClick()) {
				this.config.hudVisible = !this.config.hudVisible;
			}
		}
		if (this.exportKey != null) {
			while (this.exportKey.consumeClick()) {
				exportProfile(minecraft);
			}
		}
	}

	private void exportProfile(Minecraft minecraft) {
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
		String name = "profile-" + stamp;
		Path dir = minecraft.gameDir.toPath().resolve(MOD_ID).resolve("profiles");
		ProfileReport report = ProfileReport.capture(name, this.profiler, describeEnvironment(), this.config.describe());

		// The snapshot is detached, so file I/O can happen off the client thread.
		Thread writer = new Thread(() -> {
			try {
				report.write(dir, name);
				LOGGER.info("Profile with {} frames written to {}", report.rows(), dir.resolve(name + ".json").toAbsolutePath());
			} catch (IOException e) {
				LOGGER.error("Could not write profile to {}", dir, e);
			}
		}, "Alacer profile export");
		writer.setDaemon(true);
		writer.start();

		if (minecraft.gui != null) {
			minecraft.gui.getChat().addMessage(new LiteralText("[Alacer] Exporting " + report.rows() + " frames to alacer/profiles/" + name + ".json"));
		}
	}

	private void logIfDue() {
		int interval = this.config.profilerLogInterval;
		if (interval <= 0) {
			return;
		}
		long now = System.nanoTime();
		if (now - this.nextLog < 0) {
			return;
		}
		this.nextLog = now + interval * 1_000_000_000L;
		LOGGER.info("Performance: {}", String.join(" | ", this.hud.lines(true)));
	}

	@Override
	public int visibleChunks() {
		WorldRenderer worldRenderer = Minecraft.getInstance().worldRenderer;
		return worldRenderer == null ? 0 : ((WorldRendererAccessor) worldRenderer).alacer$getVisibleChunks().size();
	}

	@Override
	public int pendingChunks() {
		WorldRenderer worldRenderer = Minecraft.getInstance().worldRenderer;
		if (worldRenderer == null) {
			return 0;
		}
		WorldRendererAccessor renderer = (WorldRendererAccessor) worldRenderer;
		ChunkRenderDispatcherAccessor dispatcher = (ChunkRenderDispatcherAccessor) renderer.alacer$getChunkRenderDispatcher();
		Queue<?> uploads = dispatcher.alacer$getPendingUploads();
		int pendingUploads;
		// Vanilla guards this queue with its own monitor; reading it unsynchronized would race.
		synchronized (uploads) {
			pendingUploads = uploads.size();
		}
		return renderer.alacer$getDirtyChunks().size() + dispatcher.alacer$getPendingTasks().size() + pendingUploads;
	}

	/** Hardware, JVM and game settings that affect results. Call on the client thread. */
	public Map<String, String> describeEnvironment() {
		Map<String, String> env = new LinkedHashMap<>();
		Minecraft minecraft = Minecraft.getInstance();
		GameOptions options = minecraft.options;
		env.put("alacer.version", FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
		env.put("java.version", System.getProperty("java.version"));
		env.put("java.vendor", System.getProperty("java.vendor"));
		env.put("java.vm", System.getProperty("java.vm.name"));
		env.put("java.runtime.version", System.getProperty("java.runtime.version"));
		env.put("java.vm.version", System.getProperty("java.vm.version"));
		env.put("jvm.args", String.join(" ", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
		env.put("jvm.maxHeapMB", String.valueOf(Runtime.getRuntime().maxMemory() >> 20));
		env.put("jvm.gc", this.profiler == null ? "" : this.profiler.jvm().collectorNames());
		env.put("alacer.configSource", Boolean.getBoolean("alacer.benchmark")
			? "build-defaults-and-jvm-overrides" : "file-and-jvm-overrides");
		if (this.profiler != null) {
			env.put("profiler.allocationAvailable", String.valueOf(this.profiler.measuresAllocation()));
			env.put("profiler.epochWallMillis", String.valueOf(this.profiler.epochWallMillis()));
			env.put("profiler.epochWallUncertaintyNanos", String.valueOf(this.profiler.epochWallUncertaintyNanos()));
		}
		env.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		env.put("cpu.threads", String.valueOf(Runtime.getRuntime().availableProcessors()));
		env.put("cpu", GLX.getCpuInfo());
		env.put("gl.vendor", GL11.glGetString(GL11.GL_VENDOR));
		env.put("gl.renderer", GL11.glGetString(GL11.GL_RENDERER));
		env.put("gl.version", GL11.glGetString(GL11.GL_VERSION));
		net.adiont.alacer.render.ModernGl.describe(env);
		env.put("chunkNeighbours.verification", net.adiont.alacer.render.ChunkVisibility.neighbourSummary());
		env.put("window", minecraft.width + "x" + minecraft.height);
		env.put("options.renderDistance", String.valueOf(options.viewDistance));
		env.put("options.fancyGraphics", String.valueOf(options.fancyGraphics));
		env.put("options.smoothLighting", String.valueOf(options.ambientOcclusion));
		env.put("options.fpsLimit", String.valueOf(options.fpsLimit));
		env.put("options.vsync", String.valueOf(options.vsync));
		env.put("options.useVbo", String.valueOf(options.useVbo));
		env.put("vboActive", String.valueOf(GLX.useVbo()));
		env.put("options.mipmapLevels", String.valueOf(options.mipmapLevels));
		env.put("options.clouds", String.valueOf(options.cloudRenderMode));
		env.put("options.particles", String.valueOf(options.particles));
		env.put("options.entityShadows", String.valueOf(options.entityShadows));
		StringBuilder mods = new StringBuilder();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			if (mods.length() > 0) {
				mods.append(' ');
			}
			mods.append(mod.getMetadata().getId()).append('@').append(mod.getMetadata().getVersion().getFriendlyString());
		}
		env.put("mods", mods.toString());
		return env;
	}
}
