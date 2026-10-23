package net.adiont.alacer.devcheck;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.settings.VideoSetting;
import net.adiont.alacer.settings.VideoSettingsBackend;
import net.adiont.alacer.settings.VideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

/**
 * Temporary live-client harness for the 2026-10-22 video settings validation (not shipped, not built).
 * For the runs it lived at src/main/java/net/adiont/alacer/devcheck/ and was called from the end of
 * Alacer.onFrameStart(): if (VisualCheck.ACTIVE) VisualCheck.frame(Minecraft.getInstance());
 * Launch: runClient -Pwindow=1280x720 -Palacer.visualCheck=true -Palacer.visualCheck.mode=full|readback|rerun|vignette
 * Settings change through VideoSettingsBackend.apply, the same path as the menu's Apply.
 */
public final class VisualCheck {
	public static final boolean ACTIVE = Boolean.getBoolean("alacer.visualCheck");
	private static final String MODE = System.getProperty("alacer.visualCheck.mode", "full");
	private static VisualCheck instance;
	private final Minecraft mc;
	private final List<Step> steps = new ArrayList<>();
	private final File out;
	private final PrintWriter log;
	private int index = -1, waited;
	private long last;
	private final List<Long> frameTimes = new ArrayList<>();
	private boolean timing;
	private int x0, z0;

	private record Step(String name, Runnable action, BooleanSupplier done) { }

	private VisualCheck(Minecraft mc) throws IOException {
		this.mc = mc;
		this.out = new File(mc.gameDir, "visual-check");
		Files.createDirectories(this.out.toPath());
		mc.options.pauseOnUnfocus = false; // Xvfb never focuses the window; a paused server would freeze the scene.
		this.log = new PrintWriter(Files.newBufferedWriter(this.out.toPath().resolve("results-" + MODE + ".txt")), true);
		if (MODE.equals("readback")) { readback(); } else { plan(); }
	}

	public static void frame(Minecraft mc) {
		try {
			if (instance == null) { instance = new VisualCheck(mc); }
			instance.tick();
		} catch (Throwable t) {
			Alacer.LOGGER.error("Visual check failed", t);
			mc.shutdown();
		}
	}

	private void tick() {
		long now = System.nanoTime();
		if (this.timing && this.last != 0) { this.frameTimes.add(now - this.last); }
		this.last = now;
		if (this.index >= 0 && this.index < this.steps.size()) {
			if (!this.steps.get(this.index).done().getAsBoolean()) { this.waited++; return; }
		}
		this.index++;
		this.waited = 0;
		if (this.index >= this.steps.size()) { this.log.println("DONE"); this.log.close(); this.mc.shutdown(); return; }
		Step step = this.steps.get(this.index);
		Alacer.LOGGER.info("Visual check step {}: {}", this.index, step.name());
		step.action().run();
	}

	private void add(String name, Runnable action, BooleanSupplier done) { this.steps.add(new Step(name, action, done)); }
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
		add("screenshot " + name, () -> {
			Screenshot.take(this.mc.gameDir, "visual-check/" + name + ".png", this.mc.width, this.mc.height, this.mc.getRenderTarget());
			this.log.println("screenshot " + name);
		}, () -> true);
	}
	private void tp(double dx, double y, double dz, float yaw, float pitch) {
		add("tp", () -> this.mc.player.sendChat(String.format(java.util.Locale.ROOT, "/tp @p %.1f %.1f %.1f %.0f %.0f", this.x0 + dx, y, this.z0 + dz, yaw, pitch)), () -> this.waited >= 10);
	}
	private void server(String name, java.util.function.Consumer<ServerWorld> action) {
		add(name, () -> this.mc.getServer().executeTask(() -> action.accept(this.mc.getServer().getWorld(0))), () -> this.waited >= 10);
	}
	private void sampleParticles(String label, int frames) {
		long[] sum = new long[2];
		add("particles " + label, () -> { }, () -> {
			sum[0] += Long.parseLong(this.mc.particleManager.getDebugInfo().trim()); sum[1]++;
			if (this.waited + 1 < frames) { return false; }
			this.log.printf(java.util.Locale.ROOT, "particles %s mean=%.1f over %d frames%n", label, sum[0] / (double) sum[1], sum[1]);
			return true;
		});
	}
	/** Alternating blocks, so slow drift affects both choices equally. Indicative only. */
	private void timeAb(String key, int a, int b, int blocks, int settle, int frames) {
		for (int i = 0; i < blocks; i++) {
			final int block = i;
			for (int value : new int[] {i % 2 == 0 ? a : b, i % 2 == 0 ? b : a}) {
				set(key, value);
				wait(settle);
				add("time " + key + "=" + value, () -> { this.frameTimes.clear(); this.timing = true; }, () -> {
					if (this.frameTimes.size() < frames) { return false; }
					this.timing = false;
					double mean = this.frameTimes.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
					List<Long> sorted = new ArrayList<>(this.frameTimes); sorted.sort(null);
					double p95 = sorted.get((int) (sorted.size() * 0.95)) / 1e6;
					this.log.printf(java.util.Locale.ROOT, "timing %s=%d block=%d meanMs=%.3f p95Ms=%.3f frames=%d particles=%s%n", key, value, block, mean, p95, frames, this.mc.particleManager.getDebugInfo());
					return true;
				});
			}
		}
	}

	private void plan() {
		WorldSettings world = new WorldSettings(1789L, WorldSettings.GameMode.SPECTATOR, false, false, WorldGeneratorType.FLAT).enableCommands();
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		set(new LinkedHashMap<>(Map.of("distance", 8, "fps", 260, "vsync", 0)));
		add("menu", () -> openMenu(), () -> this.waited >= 20);
		shot("menu-details-defaults");
		add("load world", () -> { this.mc.openScreen(null); String name = "alacer-visual-check-" + System.currentTimeMillis(); this.log.println("world " + name); this.mc.startGame(name, name, world); },
			() -> this.mc.world != null && this.mc.player != null && this.waited > 60);
		add("origin", () -> { this.x0 = (int) Math.floor(this.mc.player.x); this.z0 = (int) Math.floor(this.mc.player.z); this.log.println("origin " + this.x0 + "," + this.z0); }, () -> true);
		cmd("/gamerule doDaylightCycle false");
		cmd("/gamerule doMobSpawning false");
		cmd("/time set 6000");
		cmd("/weather clear 1000000");
		// Leaves: an oak wall south of the origin, two layers thick so Fancy shows depth through the cutout.
		add("leaves", () -> this.mc.player.sendChat("/fill " + (this.x0 - 6) + " 4 " + (this.z0 + 6) + " " + (this.x0 + 6) + " 10 " + (this.z0 + 7) + " minecraft:leaves 4"), () -> this.waited >= 10);
		add("glass", () -> this.mc.player.sendChat("/fill " + (this.x0 - 6) + " 4 " + (this.z0 + 9) + " " + (this.x0 + 6) + " 10 " + (this.z0 + 9) + " minecraft:gold_block"), () -> this.waited >= 10);
		if (MODE.equals("rerun")) { planRerun(); return; }
		if (MODE.equals("vignette")) { planVignette(); return; }
		server("items", w -> spawnShowcase(w, this.x0 + 30, this.z0));
		server("item grid", w -> spawnGrid(w, this.x0 + 60, this.z0, false));
		server("glint grid", w -> spawnGrid(w, this.x0 + 90, this.z0, true));
		wait(120);

		// Leaves
		tp(0, 6, 0, 0, 0);
		wait(120);
		for (int v : new int[] {1, 2, 0}) { set("quality.leaves", v); wait(150); shot("leaves-" + label("quality.leaves", v)); }

		// Vignette (day, looking at the horizon)
		tp(0, 6, -20, 180, 0);
		wait(60);
		for (int v : new int[] {1, 2, 0}) { set("quality.vignette", v); wait(10); shot("vignette-" + label("quality.vignette", v)); }

		// Dropped items and glint showcase
		tp(30, 6.5, -3.5, 0, 25);
		wait(60);
		for (int v : new int[] {0, 1}) { set("quality.droppedItems", v); wait(10); shot("dropped-" + label("quality.droppedItems", v)); }
		for (int v : new int[] {0, 1}) { set("quality.enchantGlint", v); wait(10); shot("glint-" + label("quality.enchantGlint", v)); }

		// Stars (midnight, looking up)
		cmd("/time set 18000");
		tp(0, 6, -20, 180, -55);
		wait(60);
		for (int v : new int[] {0, 1}) { set("quality.stars", v); wait(10); shot("stars-" + label("quality.stars", v)); }
		cmd("/time set 6000");

		// Rain splashes: particle census for each choice, then screenshots.
		cmd("/weather rain 1000000");
		tp(0, 5, -20, 180, 35);
		wait(300);
		for (int v : new int[] {2, 1, 3, 0}) { set("quality.rainSplashes", v); wait(100); sampleParticles("rainSplashes=" + label("quality.rainSplashes", v), 200); shot("splashes-" + label("quality.rainSplashes", v)); }
		set("graphics", 0);
		wait(100); sampleParticles("rainSplashes=Follow Graphics, graphics=Fast", 200);
		set("graphics", 1);
		wait(30);

		if (MODE.equals("full")) {
			// Indicative frame-time A/B (software OpenGL; alternating blocks).
			timeAb("quality.rainSplashes", 2, 3, 4, 120, 300);
			cmd("/weather clear 1000000");
			wait(60);
			tp(0, 6, -20, 180, 0);
			timeAb("quality.vignette", 2, 1, 4, 60, 300);
			tp(60, 9, -6, 0, 35);
			wait(120);
			timeAb("quality.droppedItems", 1, 0, 4, 60, 300);
			tp(90, 9, -6, 0, 35);
			wait(120);
			timeAb("quality.enchantGlint", 1, 0, 4, 60, 300);
			tp(0, 6, 0, 0, 0);
			wait(120);
			timeAb("quality.leaves", 2, 1, 4, 200, 300);
		}

		// Persist a non-default combination for the relaunch read-back, then show it in the menu.
		set(new LinkedHashMap<>(Map.of("quality.rainSplashes", 3, "quality.vignette", 1, "quality.stars", 0, "quality.enchantGlint", 0, "quality.droppedItems", 0)));
		add("menu", () -> openMenu(), () -> this.waited >= 20);
		shot("menu-details-saved");
		add("close", () -> this.mc.openScreen(null), () -> this.waited >= 5);
	}

	/** Vanilla's vignette follows the player's light level, so measure it in shade looking out at daylight. */
	private void planVignette() {
		add("roof", () -> this.mc.player.sendChat("/fill " + (this.x0 - 15) + " 9 " + (this.z0 - 40) + " " + (this.x0 + 15) + " 9 " + (this.z0 - 10) + " minecraft:stone"), () -> this.waited >= 20);
		tp(0, 5, -25, 0, 5);
		wait(600);
		for (int v : new int[] {1, 2, 0}) { set("quality.vignette", v); wait(10); shot("shade-vignette-" + label("quality.vignette", v)); }
		set("graphics", 0); wait(30); shot("shade-vignette-Follow_Graphics-graphicsFast");
		set("graphics", 1); wait(30);
	}

	/** Night vignette shots, and item timings with grids spawned just before measuring. */
	private void planRerun() {
		cmd("/time set 18000");
		tp(0, 6, -20, 180, 0);
		wait(700); // vanilla eases vignette darkness by 1% per frame
		for (int v : new int[] {1, 2, 0}) { set("quality.vignette", v); wait(10); shot("night-vignette-" + label("quality.vignette", v)); }
		set("graphics", 0); wait(10); shot("night-vignette-Follow_Graphics-graphicsFast");
		set("graphics", 1); wait(10);
		cmd("/time set 6000");
		server("item grid", w -> spawnGrid(w, this.x0 + 60, this.z0, false));
		tp(60, 9, -6, 0, 35);
		wait(120);
		add("count", () -> this.log.println("entities " + this.mc.worldRenderer.getEntityDebugInfo()), () -> true);
		timeAb("quality.droppedItems", 1, 0, 4, 60, 300);
		add("count", () -> this.log.println("entities " + this.mc.worldRenderer.getEntityDebugInfo()), () -> true);
		server("glint grid", w -> spawnGrid(w, this.x0 + 90, this.z0, true));
		tp(90, 9, -6, 0, 35);
		wait(120);
		add("count", () -> this.log.println("entities " + this.mc.worldRenderer.getEntityDebugInfo()), () -> true);
		timeAb("quality.enchantGlint", 1, 0, 4, 60, 300);
		add("count", () -> this.log.println("entities " + this.mc.worldRenderer.getEntityDebugInfo()), () -> true);
		set(new LinkedHashMap<>(Map.of("quality.leaves", 0, "quality.vignette", 0, "quality.enchantGlint", 1, "quality.droppedItems", 1, "guiScale", 2)));
		add("menu", () -> openMenu(), () -> this.waited >= 20);
		shot("menu-details-scale2");
		add("close", () -> this.mc.openScreen(null), () -> this.waited >= 5);
		set("guiScale", 0);
	}

	private void readback() {
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		add("read", () -> {
			Map<String,String> d = Alacer.get().config().describe();
			for (String k : new String[] {"quality.rainSplashes", "quality.vignette", "quality.stars", "quality.enchantGlint", "quality.droppedItems"}) { this.log.println("readback " + k + "=" + d.get(k)); }
		}, () -> true);
		add("menu", () -> openMenu(), () -> this.waited >= 20);
		shot("menu-details-relaunch");
		set(new LinkedHashMap<>(Map.of("quality.rainSplashes", 0, "quality.vignette", 0, "quality.stars", 1, "quality.enchantGlint", 1, "quality.droppedItems", 1)));
		add("read defaults", () -> this.log.println("restored " + Alacer.get().config().describe().entrySet().stream().filter(e -> e.getKey().startsWith("quality.")).toList()), () -> true);
		add("menu", () -> openMenu(), () -> this.waited >= 20);
		shot("menu-details-defaults");
	}

	private void openMenu() {
		VideoSettingsScreen screen = new VideoSettingsScreen(this.mc.screen);
		this.mc.openScreen(screen);
		try {
			Field f = VideoSettingsScreen.class.getDeclaredField("category");
			f.setAccessible(true);
			f.set(screen, VideoSetting.Category.DETAILS);
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
		screen.init(this.mc, screen.width, screen.height);
	}

	private static String label(String key, int value) {
		return net.adiont.alacer.settings.VideoSettingsCatalog.byKey(key).valueLabel(value).replace(' ', '_');
	}

	private static ItemStack enchanted(Item item, Enchantment e) { ItemStack s = new ItemStack(item); s.addEnchantment(e, 1); return s; }

	private static void drop(ServerWorld w, double x, double y, double z, ItemStack stack) {
		ItemEntity entity = new ItemEntity(w, x, y, z, stack);
		entity.velocityX = 0; entity.velocityY = 0; entity.velocityZ = 0;
		entity.setNeverPickUp(); entity.setNeverDespawn();
		try { // setNeverDespawn only delays despawning by 5 minutes; -32768 is vanilla's "never" age.
			Field age = ItemEntity.class.getDeclaredField("age"); age.setAccessible(true); age.setInt(entity, -32768);
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
		w.addEntity(entity);
	}

	private static void spawnShowcase(ServerWorld w, int x, int z) {
		drop(w, x - 2.0, 4.2, z + 0.5, new ItemStack(Blocks.COBBLESTONE, 64));
		drop(w, x - 1.0, 4.2, z + 0.5, new ItemStack(Items.GOLD_INGOT, 64));
		drop(w, x, 4.2, z + 0.5, new ItemStack(Items.DIAMOND, 40));
		drop(w, x + 1.0, 4.2, z + 0.5, new ItemStack(Items.EMERALD, 20));
		drop(w, x + 2.0, 4.2, z + 0.5, new ItemStack(Items.APPLE, 1));
		drop(w, x - 1.0, 4.2, z + 2.0, enchanted(Items.DIAMOND_SWORD, Enchantment.SHARPNESS));
		drop(w, x + 1.0, 4.2, z + 2.0, new ItemStack(Items.GOLDEN_APPLE, 1, 1));
		ArmorStandEntity stand = new ArmorStandEntity(w, x + 0.5, 4.0, z + 4.5);
		stand.setPositionAndAngles(x + 0.5, 4.0, z + 4.5, 180f, 0f);
		stand.setEquipment(0, enchanted(Items.DIAMOND_SWORD, Enchantment.SHARPNESS));
		stand.setEquipment(1, enchanted(Items.DIAMOND_BOOTS, Enchantment.PROTECTION));
		stand.setEquipment(2, enchanted(Items.DIAMOND_LEGGINGS, Enchantment.PROTECTION));
		stand.setEquipment(3, enchanted(Items.DIAMOND_CHESTPLATE, Enchantment.PROTECTION));
		stand.setEquipment(4, enchanted(Items.DIAMOND_HELMET, Enchantment.PROTECTION));
		w.addEntity(stand);
	}

	/** 20 x 15 dropped stacks. Plain grid: full stacks of mixed items. Glint grid: enchanted swords. */
	private static void spawnGrid(ServerWorld w, int x, int z, boolean glint) {
		Item[] plain = {Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.REDSTONE, Items.ARROW};
		for (int i = 0; i < 20; i++) {
			for (int j = 0; j < 15; j++) {
				ItemStack stack = glint ? enchanted(Items.DIAMOND_SWORD, Enchantment.SHARPNESS)
					: (i + j) % 3 == 0 ? new ItemStack(Blocks.COBBLESTONE, 64) : new ItemStack(plain[(i * 15 + j) % plain.length], 64);
				drop(w, x - 10 + i + 0.5, 4.2, z + j + 0.5, stack);
			}
		}
	}

	static { if (ACTIVE) { Alacer.LOGGER.warn("Visual check harness active ({}); settings will be changed", MODE); } }
}
