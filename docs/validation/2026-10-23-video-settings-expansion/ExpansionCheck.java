package net.adiont.alacer.devcheck;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.TextureFiltering;
import net.adiont.alacer.settings.VideoSetting;
import net.adiont.alacer.settings.VideoSettingsBackend;
import net.adiont.alacer.settings.VideoSettingsCatalog;
import net.adiont.alacer.settings.VideoSettingsDraft;
import net.adiont.alacer.settings.VideoSettingsPresets;
import net.adiont.alacer.settings.VideoSettingsScreen;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.WorldGeneratorType;

/**
 * Temporary live-client harness for the 2026-10-23 video settings expansion (not shipped, not built).
 * For the runs it lived at src/main/java/net/adiont/alacer/devcheck/ and was called from the end of
 * Alacer.onFrameStart(): if (ExpansionCheck.ACTIVE) ExpansionCheck.frame(Minecraft.getInstance());
 * Launch: runClient -Pwindow=1280x720 -Palacer.expansionCheck=true -Palacer.expansionCheck.mode=full|readback|particles (particles was added after full run 1 to diagnose the particle-limit fix)
 * Settings change through VideoSettingsBackend.apply, the same path as the menu's Apply.
 */
public final class ExpansionCheck {
	public static final boolean ACTIVE = Boolean.getBoolean("alacer.expansionCheck");
	private static final String MODE = System.getProperty("alacer.expansionCheck.mode", "full");
	private static ExpansionCheck instance;
	private final Minecraft mc;
	private final List<Step> steps = new ArrayList<>();
	private final File out;
	private final PrintWriter log;
	private int index = -1, waited;
	private long last;
	private final List<Long> frameTimes = new ArrayList<>();
	private boolean timing;
	private int x0, z0;
	private Runnable everyFrame;

	private record Step(String name, Runnable action, BooleanSupplier done) { }

	private ExpansionCheck(Minecraft mc) throws IOException {
		this.mc = mc;
		this.out = new File(mc.gameDir, "expansion-check");
		Files.createDirectories(this.out.toPath());
		mc.options.pauseOnUnfocus = false; // Xvfb never focuses the window; a paused server would freeze the scene.
		this.log = new PrintWriter(Files.newBufferedWriter(this.out.toPath().resolve("results-" + MODE + ".txt")), true);
		if (MODE.equals("readback")) { readback(); } else if (MODE.equals("particles")) { particles(); } else { plan(); }
	}

	public static void frame(Minecraft mc) {
		try {
			if (instance == null) { instance = new ExpansionCheck(mc); }
			instance.tick();
		} catch (Throwable t) {
			Alacer.LOGGER.error("Expansion check failed", t);
			if (instance != null) { instance.log.println("FAILED " + t); instance.log.close(); }
			mc.shutdown();
		}
	}

	private void tick() {
		long now = System.nanoTime();
		if (this.timing && this.last != 0) { this.frameTimes.add(now - this.last); }
		this.last = now;
		if (this.everyFrame != null) { this.everyFrame.run(); }
		if (this.index >= 0 && this.index < this.steps.size()) {
			if (!this.steps.get(this.index).done().getAsBoolean()) { this.waited++; return; }
		}
		this.index++;
		this.waited = 0;
		if (this.index >= this.steps.size()) { this.log.println("DONE"); this.log.close(); this.mc.shutdown(); return; }
		Step step = this.steps.get(this.index);
		Alacer.LOGGER.info("Expansion check step {}: {}", this.index, step.name());
		step.action().run();
	}

	private void add(String name, Runnable action, BooleanSupplier done) { this.steps.add(new Step(name, action, done)); }
	private void now(String name, Runnable action) { add(name, action, () -> true); }
	private void wait(int frames) { add("wait " + frames, () -> { }, () -> this.waited >= frames); }
	private void cmd(String command) { add(command, () -> this.mc.player.sendChat(command), () -> this.waited >= 5); }
	private void set(Map<String,Integer> edits) {
		add("apply " + edits, () -> {
			try { new VideoSettingsBackend(this.mc).apply(edits); this.log.println("applied " + edits); }
			catch (IOException e) { throw new IllegalStateException(e); }
		}, () -> this.waited >= 3);
	}
	private void set(String key, int value) { set(Map.of(key, value)); }
	private void shot(String name) {
		add("prepare " + name, () -> {
			if (this.mc.world != null && !(this.mc.screen instanceof VideoSettingsScreen)) { this.mc.openScreen(null); }
			if (this.mc.gui != null) { this.mc.gui.getChat().clear(); }
		}, () -> this.waited >= 4);
		now("screenshot " + name, () -> {
			Screenshot.take(this.mc.gameDir, "expansion-check/" + name + ".png", this.mc.width, this.mc.height, this.mc.getRenderTarget());
			this.log.println("screenshot " + name);
		});
	}
	private void tp(double dx, double y, double dz, float yaw, float pitch) {
		add("tp", () -> this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %.1f %.1f %.1f %.0f %.0f", this.x0 + dx, y, this.z0 + dz, yaw, pitch)), () -> this.waited >= 10);
	}
	private void server(String name, java.util.function.Consumer<ServerWorld> action) {
		add(name, () -> this.mc.getServer().executeTask(() -> action.accept(this.mc.getServer().getWorld(0))), () -> this.waited >= 10);
	}
	private int pending() { return Alacer.get().pendingChunks(); }
	private void untilTerrainSettled(String label) {
		long[] start = new long[1]; List<Long> frames = new ArrayList<>();
		add("settle " + label, () -> { start[0] = System.nanoTime(); this.frameTimes.clear(); this.timing = true; }, () -> {
			if (pending() > 0 || this.waited < 3) { return this.waited > 6000; }
			this.timing = false;
			frames.addAll(this.frameTimes);
			double mean = frames.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
			double max = frames.stream().mapToLong(Long::longValue).max().orElse(0) / 1e6;
			this.log.printf(Locale.ROOT, "settle %s ms=%.1f frames=%d meanFrameMs=%.2f maxFrameMs=%.2f%n", label, (System.nanoTime() - start[0]) / 1e6, frames.size(), mean, max);
			return true;
		});
	}
	/** Alternating blocks, so slow drift affects both choices equally. Indicative only. */
	private void timeAb(String key, int a, int b, int blocks, int settle, int frames, Runnable perFrame) {
		for (int i = 0; i < blocks; i++) {
			final int block = i;
			for (int value : new int[] {i % 2 == 0 ? a : b, i % 2 == 0 ? b : a}) {
				set(key, value);
				now("load " + key, () -> this.everyFrame = perFrame);
				wait(settle);
				add("time " + key + "=" + value, () -> { this.frameTimes.clear(); this.timing = true; }, () -> {
					if (this.frameTimes.size() < frames) { return false; }
					this.timing = false;
					double mean = this.frameTimes.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
					List<Long> sorted = new ArrayList<>(this.frameTimes); sorted.sort(null);
					double p95 = sorted.get((int) (sorted.size() * 0.95)) / 1e6;
					this.log.printf(Locale.ROOT, "timing %s=%d block=%d meanMs=%.3f p95Ms=%.3f frames=%d particles=%s%n", key, value, block, mean, p95, frames, this.mc.particleManager.getDebugInfo().trim());
					return true;
				});
				now("unload", () -> this.everyFrame = null);
			}
		}
	}

	private void plan() {
		WorldSettings world = new WorldSettings(2203L, WorldSettings.GameMode.SPECTATOR, false, false, WorldGeneratorType.FLAT).enableCommands();
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		set(new LinkedHashMap<>(Map.of("distance", 8, "fps", 260, "vsync", 0)));
		now("driver", () -> this.log.println("anisotropy supported=" + TextureFiltering.supported() + " max=" + TextureFiltering.maximum()
			+ " lock=" + new VideoSettingsBackend(this.mc).lockedReason(VideoSettingsCatalog.byKey("quality.anisotropicFiltering"))
			+ " note=" + new VideoSettingsBackend(this.mc).note(VideoSettingsCatalog.byKey("quality.anisotropicFiltering"))));
		now("threads", this::logThreads);
		for (VideoSetting.Category c : VideoSetting.Category.values()) {
			add("menu " + c, () -> openMenu(c, 0, -1, null), () -> this.waited >= 20);
			shot("menu-" + c.title.toLowerCase(Locale.ROOT));
		}
		add("menu details new", () -> openMenu(VideoSetting.Category.DETAILS, 10, -1, null), () -> this.waited >= 20);
		shot("menu-details-new");
		add("menu tooltip threads", () -> openMenu(VideoSetting.Category.ADVANCED, 0, 1001, null), () -> this.waited >= 20);
		shot("menu-advanced-tooltip-threads");
		add("menu tooltip af", () -> openMenu(VideoSetting.Category.GRAPHICS, 12, 1001, null), () -> this.waited >= 20);
		shot("menu-graphics-tooltip-anisotropy");
		add("menu presets", () -> openMenu(VideoSetting.Category.GRAPHICS, 0, -1, "preset"), () -> this.waited >= 20);
		shot("menu-presets");
		now("close", () -> this.mc.openScreen(new TitleScreen()));

		add("load world", () -> { this.mc.openScreen(null); String name = "alacer-expansion-check-" + System.currentTimeMillis(); this.log.println("world " + name); this.mc.startGame(name, name, world); },
			() -> this.mc.world != null && this.mc.player != null && this.waited > 60);
		now("origin", () -> { this.x0 = (int) Math.floor(this.mc.player.x); this.z0 = (int) Math.floor(this.mc.player.z); this.log.println("origin " + this.x0 + "," + this.z0); });
		cmd("/gamerule doDaylightCycle false");
		cmd("/gamerule doMobSpawning false");
		cmd("/time set 6000");
		cmd("/weather clear 1000000");
		wait(120);

		// Biome blend: split the loaded client chunks into jungle (west) and ice plains (east) at the origin's chunk edge.
		tp(0, 40, 0, 0, 90);
		wait(200);
		now("biomes", () -> {
			int cx0 = this.x0 >> 4;
			for (int cx = cx0 - 8; cx <= cx0 + 8; cx++) {
				for (int cz = (this.z0 >> 4) - 8; cz <= (this.z0 >> 4) + 8; cz++) {
					WorldChunk chunk = this.mc.world.getChunkAt(cx, cz);
					byte[] biomes = chunk.getBiomes();
					java.util.Arrays.fill(biomes, (byte) (cx < cx0 ? 21 : 12));
				}
			}
			this.log.println("biome border at x=" + ((cx0) << 4));
		});
		add("tp border", () -> this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %d 40 %d 0 90", (this.x0 >> 4) << 4, this.z0)), () -> this.waited >= 10);
		for (int v : new int[] {1, 0, 2, 3, 1}) {
			set("quality.biomeBlend", v);
			untilTerrainSettled("biomeBlend=" + label("quality.biomeBlend", v));
			wait(20);
			shot("biome-blend-" + label("quality.biomeBlend", v));
		}

		// Entity distance: one dropped stack every 4 blocks along +x (vanilla draws items to 16 blocks).
		server("item lane", w -> { for (int i = 1; i <= 25; i++) { drop(w, this.x0 + 4 * i + 0.5, 4.2, this.z0 - 40.5, new ItemStack(Items.DIAMOND, 1)); } });
		tp(0, 5.6, -40.5, -90, 10);
		wait(120);
		for (int v : new int[] {100, 25, 50, 200, 500, 100}) {
			set("quality.entityDistance", v);
			wait(20);
			now("count " + v, () -> this.log.println("entityDistance=" + v + " " + this.mc.worldRenderer.getEntityDebugInfo()));
		}
		set("quality.entityDistance", 200); wait(10); shot("entity-distance-200");
		set("quality.entityDistance", 100); wait(10); shot("entity-distance-100");

		// Block entity distance: a chest every 8 blocks along +x (vanilla draws them to 64 blocks).
		add("chest lane", () -> {
			for (int i = 1; i <= 15; i++) { this.mc.player.sendChat("/setblock " + (this.x0 + 8 * i) + " 4 " + (this.z0 - 60) + " minecraft:chest"); }
		}, () -> this.waited >= 30);
		tp(0, 5.6, -60.5, -90, 15);
		wait(150);
		for (int v : new int[] {100, 25, 300}) { set("quality.blockEntityDistance", v); wait(10); shot("block-entity-distance-" + v); }
		set("quality.blockEntityDistance", 100);

		// Fog, looking at the horizon.
		tp(0, 6, 20, 0, 0);
		wait(120);
		for (int v : new int[] {75, 30, 100}) { set("quality.fogStart", v); wait(10); shot("fog-" + label("quality.fogStart", v)); }
		set("quality.fogStart", 75);

		// Sun and moon: the sun is overhead at noon, the moon at midnight.
		tp(0, 6, 20, 0, -90);
		wait(30);
		for (int v : new int[] {0, 1}) { set("quality.sunMoon", v); wait(10); shot("sun-" + label("quality.sunMoon", v)); }
		cmd("/time set 18000");
		wait(30);
		for (int v : new int[] {0, 1}) { set("quality.sunMoon", v); wait(10); shot("moon-" + label("quality.sunMoon", v)); }
		cmd("/time set 6000");

		// Texture animations: record frame indices before and after 60 frames for each group, on and off.
		String[] sprites = {"minecraft:blocks/water_still", "minecraft:blocks/lava_still", "minecraft:blocks/fire_layer_0", "minecraft:blocks/portal", "minecraft:blocks/sea_lantern", "minecraft:blocks/prismarine_rough"};
		String[] keys = {"quality.waterAnimation", "quality.lavaAnimation", "quality.fireAnimation", "quality.portalAnimation", "quality.otherAnimations", "quality.otherAnimations"};
		for (int v : new int[] {0, 1}) {
			Map<String,Integer> all = new LinkedHashMap<>();
			for (String k : keys) { all.put(k, v); }
			set(all);
			int[][] before = new int[sprites.length][];
			now("frames before " + v, () -> { for (int i = 0; i < sprites.length; i++) { before[i] = frame(sprites[i]); } });
			wait(80);
			now("frames after " + v, () -> {
				for (int i = 0; i < sprites.length; i++) {
					int[] after = frame(sprites[i]);
					this.log.println("animation " + keys[i] + "=" + v + " " + sprites[i] + " frame " + before[i][0] + "->" + after[0] + " ticks " + before[i][1] + "->" + after[1]);
				}
			});
		}
		set("quality.otherAnimations", 0);
		int[] clock = new int[1];
		now("clock before", () -> clock[0] = frame("minecraft:items/clock")[0]);
		cmd("/time set 18000");
		wait(40);
		now("clock after", () -> this.log.println("clock with otherAnimations=Off frame " + clock[0] + "->" + frame("minecraft:items/clock")[0]));
		cmd("/time set 6000");
		set("quality.otherAnimations", 1);

		// Particle limit: spawn 400 redstone particles per frame next to the camera.
		tp(0, 6, 40, 0, 20);
		wait(60);
		Runnable spray = () -> {
			for (int i = 0; i < 400; i++) {
				this.mc.world.addParticle(ParticleType.REDSTONE, this.mc.player.x + (i % 20 - 10) * 0.3, this.mc.player.y, this.mc.player.z + 4 + (i / 20) * 0.3, 0, 0, 0);
			}
		};
		timeAb("quality.particleLimit", 4, 0, 4, 120, 200, spray);
		set("quality.particleLimit", 4);

		// Anisotropic filtering: read the atlas parameter back after each change and after a resource reload.
		now("af default", () -> this.log.println("anisotropy choice=0 atlas=" + atlasAnisotropy()));
		set("quality.anisotropicFiltering", 4);
		now("af 16", () -> this.log.println("anisotropy choice=4 atlas=" + atlasAnisotropy()));
		add("reload resources", () -> this.mc.reloadResources(), () -> this.waited >= 30);
		now("af reload", () -> this.log.println("anisotropy choice=4 after resource reload atlas=" + atlasAnisotropy()));
		tp(0, 4.7, 40, 0, 4);
		wait(30);
		shot("anisotropic-16x");
		set("quality.anisotropicFiltering", 0);
		now("af off", () -> this.log.println("anisotropy choice=0 atlas=" + atlasAnisotropy()));
		wait(10);
		shot("anisotropic-off");
		tp(0, 6, 40, 0, 4);
		timeAb("quality.anisotropicFiltering", 0, 4, 4, 60, 300, null);
		set("quality.anisotropicFiltering", 0);

		// FOV effects: a flying spectator's modifier is 1.1 at full strength.
		for (int v : new int[] {100, 50, 0, 100}) {
			set("quality.fovEffects", v);
			wait(40);
			now("fov " + v, () -> this.log.println("fovEffects=" + v + " flying=" + this.mc.player.abilities.flying + " fovMultiplier=" + field(GameRenderer.class, "fov", this.mc.gameRenderer)));
		}

		// Damage tilt: hold the hurt timer near its strongest tilt while the screenshot is taken.
		tp(0, 6, 20, 0, 0);
		wait(20);
		for (int v : new int[] {100, 0}) {
			set("quality.damageTilt", v);
			now("hurt on", () -> this.everyFrame = () -> { this.mc.player.damagedTime = 10; this.mc.player.damagedTimer = 9; });
			wait(5);
			shot("damage-tilt-" + label("quality.damageTilt", v));
			now("hurt off", () -> this.everyFrame = null);
		}
		set("quality.damageTilt", 100);

		// Chunk update budget: time to rebuild all terrain after a reload, and frame times meanwhile.
		tp(0, 30, 0, 0, 30);
		wait(60);
		for (int v : new int[] {0, 4, 0, 4}) {
			set("advanced.chunkUpdateBudget", v);
			now("reload terrain", () -> this.mc.worldRenderer.reload());
			untilTerrainSettled("chunkUpdateBudget=" + label("advanced.chunkUpdateBudget", v));
		}
		set("advanced.chunkUpdateBudget", 2);

		// Overlay.
		set("profiler.hud", 1); wait(30); shot("overlay-on");
		set("profiler.hud", 0);

		// Presets through the menu's draft and Apply path.
		for (VideoSettingsPresets preset : new VideoSettingsPresets[] {VideoSettingsPresets.PERFORMANCE, VideoSettingsPresets.ENHANCED, VideoSettingsPresets.VANILLA}) {
			add("preset " + preset, () -> {
				VideoSettingsBackend backend = new VideoSettingsBackend(this.mc);
				VideoSettingsDraft draft = new VideoSettingsDraft(backend.snapshot());
				draft.applyPreset(preset, s -> backend.lockedReason(s) == null);
				try { backend.apply(draft.changes()); } catch (IOException e) { throw new IllegalStateException(e); }
				Map<String,Integer> live = new VideoSettingsBackend(this.mc).snapshot();
				boolean match = preset.settings().entrySet().stream().allMatch(e -> live.get(e.getKey()).equals(e.getValue()));
				this.log.println("preset " + preset.title + " applied=" + draft.changes() + " liveMatches=" + match + " distance=" + live.get("distance"));
			}, () -> this.waited >= 60);
			shot("preset-" + preset.title.toLowerCase(Locale.ROOT));
		}

		// Persist a non-default combination for the relaunch read-back.
		set(new LinkedHashMap<>(Map.of("quality.entityDistance", 250, "quality.fogStart", 40, "quality.particleLimit", 1, "quality.waterAnimation", 0, "advanced.chunkThreads", 4)));
		now("pending", () -> this.log.println("restartRequired=" + new VideoSettingsBackend(this.mc).restartRequired() + " live chunkThreads=" + Alacer.get().config().chunkThreads));
		now("threads", this::logThreads);
		add("menu", () -> openMenu(VideoSetting.Category.ADVANCED, 0, -1, null), () -> this.waited >= 20);
		shot("menu-advanced-restart-pending");
		now("close", () -> this.mc.openScreen(null));
	}

	/** Per-list particle counts while spraying, for each limit. */
	private void particles() {
		WorldSettings world = new WorldSettings(2203L, WorldSettings.GameMode.SPECTATOR, false, false, WorldGeneratorType.FLAT).enableCommands();
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		add("load world", () -> { String name = "alacer-particles-" + System.currentTimeMillis(); this.mc.startGame(name, name, world); },
			() -> this.mc.world != null && this.mc.player != null && this.waited > 60);
		cmd("/gamerule doDaylightCycle false");
		wait(60);
		Runnable spray = () -> {
			for (int i = 0; i < 400; i++) {
				this.mc.world.addParticle(ParticleType.REDSTONE, this.mc.player.x + (i % 20 - 10) * 0.3, this.mc.player.y, this.mc.player.z + 4 + (i / 20) * 0.3, 0, 0, 0);
			}
		};
		for (int v : new int[] {4, 0, 2, 4}) {
			set("quality.particleLimit", v);
			now("spray on", () -> this.everyFrame = spray);
			for (int k = 0; k < 6; k++) {
				wait(20);
				now("lists", () -> {
					List<?>[][] lists = ((net.adiont.alacer.mixin.ParticleManagerAccessor) this.mc.particleManager).alacer$particles();
					StringBuilder b = new StringBuilder("limit=" + label("quality.particleLimit", v) + " lists");
					for (List<?>[] layer : lists) { for (List<?> l : layer) { b.append(' ').append(l.size()); } }
					this.log.println(b);
				});
			}
			now("spray off", () -> this.everyFrame = null);
		}
	}

	private void readback() {
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		now("read", () -> {
			Map<String,String> d = Alacer.get().config().describe();
			for (String k : new String[] {"quality.entityDistance", "quality.fogStart", "quality.particleLimit", "quality.waterAnimation", "advanced.chunkThreads"}) { this.log.println("readback " + k + "=" + d.get(k)); }
		});
		now("threads", this::logThreads);
		add("menu", () -> openMenu(VideoSetting.Category.ADVANCED, 0, -1, null), () -> this.waited >= 20);
		shot("menu-advanced-relaunch");
		add("menu presets", () -> openMenu(VideoSetting.Category.GRAPHICS, 0, -1, "preset"), () -> this.waited >= 20);
		shot("menu-presets-fixed");
		add("menu af", () -> openMenu(VideoSetting.Category.GRAPHICS, 12, 1001, null), () -> this.waited >= 20);
		shot("menu-graphics-anisotropy-experimental");
		Map<String,Integer> defaults = new LinkedHashMap<>();
		for (String k : new String[] {"quality.entityDistance", "quality.fogStart", "quality.particleLimit", "quality.waterAnimation", "advanced.chunkThreads"}) { defaults.put(k, VideoSettingsCatalog.byKey(k).defaultValue()); }
		set(defaults);
		now("restored", () -> this.log.println("restored " + defaults.keySet().stream().map(k -> k + "=" + Alacer.get().config().videoValue(k)).toList()
			+ " restartRequired=" + new VideoSettingsBackend(this.mc).restartRequired()));
	}

	private void logThreads() {
		long batchers = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("Chunk Batcher")).count();
		Object dispatcher = field(net.minecraft.client.render.world.WorldRenderer.class, "chunkRenderDispatcher", this.mc.worldRenderer);
		Object buffers = field(dispatcher.getClass(), "availableBuffers", dispatcher);
		this.log.println("chunk batcher threads=" + batchers + " available buffer sets=" + ((java.util.Collection<?>) buffers).size()
			+ " remaining capacity=" + ((java.util.concurrent.BlockingQueue<?>) buffers).remainingCapacity() + " config=" + Alacer.get().config().chunkThreads);
	}

	private int[] frame(String sprite) {
		TextureAtlasSprite s = this.mc.getBlocksAtlas().getSprite(sprite);
		return new int[] {(Integer) field(TextureAtlasSprite.class, "activeFrame", s), (Integer) field(TextureAtlasSprite.class, "frameTicks", s)};
	}

	private float atlasAnisotropy() {
		GlStateManager.bindTexture(this.mc.getBlocksAtlas().getGlId());
		return TextureFiltering.supported() ? GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT) : -1;
	}

	private static Object field(Class<?> owner, String name, Object target) {
		try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
		catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}
	private static void setField(Object target, String name, Object value) {
		try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
		catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}

	private void openMenu(VideoSetting.Category category, int offset, int focus, String modal) {
		VideoSettingsScreen screen = new VideoSettingsScreen(this.mc.screen);
		this.mc.openScreen(screen);
		setField(screen, "category", category);
		setField(screen, "offset", offset);
		screen.init(this.mc, screen.width, screen.height);
		if (focus >= 0) { setField(screen, "keyboardFocus", true); setField(screen, "focusId", focus); }
		if (modal != null) {
			setField(screen, "modal", modal);
			try { Method m = VideoSettingsScreen.class.getDeclaredMethod("rebuild"); m.setAccessible(true); m.invoke(screen); }
			catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
		}
	}

	private static String label(String key, int value) {
		return VideoSettingsCatalog.byKey(key).valueLabel(value).replace(' ', '_').replace("%", "pct");
	}

	private static void drop(ServerWorld w, double x, double y, double z, ItemStack stack) {
		ItemEntity entity = new ItemEntity(w, x, y, z, stack);
		entity.velocityX = 0; entity.velocityY = 0; entity.velocityZ = 0;
		entity.setNeverPickUp();
		try { // -32768 is vanilla's "never despawn" age.
			Field age = ItemEntity.class.getDeclaredField("age"); age.setAccessible(true); age.setInt(entity, -32768);
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
		w.addEntity(entity);
	}

	static { if (ACTIVE) { Alacer.LOGGER.warn("Expansion check harness active ({}); settings will be changed", MODE); } }
}
