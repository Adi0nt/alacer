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
import org.lwjgl.input.Keyboard;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.settings.VideoSetting;
import net.adiont.alacer.settings.VideoSettingsBackend;
import net.adiont.alacer.settings.VideoSettingsCatalog;
import net.adiont.alacer.settings.VideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

/**
 * Temporary live-client harness for the 2026-10-23 camera settings (not shipped, not built).
 * For the runs it lived at src/main/java/net/adiont/alacer/devcheck/ and was called from the end of
 * Alacer.onFrameStart(): if (CameraCheck.ACTIVE) CameraCheck.frame(Minecraft.getInstance());
 * Launch: runClient -Pwindow=854x480 -Palacer.cameraCheck=true -Palacer.cameraCheck.mode=full|readback
 * Settings change through VideoSettingsBackend.apply, the same path as the menu's Apply.
 */
public final class CameraCheck {
	public static final boolean ACTIVE = Boolean.getBoolean("alacer.cameraCheck");
	private static final String MODE = System.getProperty("alacer.cameraCheck.mode", "full");
	private static CameraCheck instance;
	private final Minecraft mc;
	private final List<Step> steps = new ArrayList<>();
	private final PrintWriter log;
	private int index = -1, waited;
	private int x0, z0;
	private Runnable everyFrame;

	private record Step(String name, Runnable action, BooleanSupplier done) { }

	private CameraCheck(Minecraft mc) throws IOException {
		this.mc = mc;
		File out = new File(mc.gameDir, "screenshots/camera-check");
		Files.createDirectories(out.toPath());
		mc.options.pauseOnUnfocus = false;
		this.log = new PrintWriter(Files.newBufferedWriter(out.toPath().resolve("results-" + MODE + ".txt")), true);
		if (MODE.equals("readback")) { readback(); } else { plan(); }
	}

	public static void frame(Minecraft mc) {
		try {
			if (instance == null) { instance = new CameraCheck(mc); }
			instance.tick();
		} catch (Throwable t) {
			Alacer.LOGGER.error("Camera check failed", t);
			if (instance != null) { instance.log.println("FAILED " + t); instance.log.close(); }
			mc.shutdown();
		}
	}

	private void tick() {
		if (this.everyFrame != null) { this.everyFrame.run(); }
		if (this.index >= 0 && this.index < this.steps.size()) {
			if (!this.steps.get(this.index).done().getAsBoolean()) { this.waited++; return; }
		}
		this.index++;
		this.waited = 0;
		if (this.index >= this.steps.size()) { this.log.println("DONE"); this.log.close(); this.mc.shutdown(); return; }
		Step step = this.steps.get(this.index);
		Alacer.LOGGER.info("Camera check step {}: {}", this.index, step.name());
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
			Screenshot.take(this.mc.gameDir, "camera-check/" + name + ".png", this.mc.width, this.mc.height, this.mc.getRenderTarget());
			this.log.println("screenshot " + name);
		});
	}
	private void tp(double dx, double y, double dz, float yaw, float pitch) {
		add("tp", () -> this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %.1f %.1f %.1f %.0f %.0f", this.x0 + dx, y, this.z0 + dz, yaw, pitch)), () -> this.waited >= 10);
	}
	/** Median over {@code frames} frames of Camera.offset(), the eye-to-camera vector vanilla derives from the view matrix. */
	private void offsets(String label, int frames) {
		List<double[]> seen = new ArrayList<>();
		add("offset " + label, () -> { }, () -> {
			Vec3d o = Camera.offset();
			seen.add(new double[] {o.x, o.y, o.z});
			if (seen.size() < frames) { return false; }
			double[] m = new double[3];
			for (int c = 0; c < 3; c++) {
				final int axis = c;
				m[c] = seen.stream().mapToDouble(v -> v[axis]).sorted().toArray()[seen.size() / 2];
			}
			this.log.printf(Locale.ROOT, "%s offset=(%.5f, %.5f, %.5f) length=%.5f frames=%d%n", label, m[0], m[1], m[2], Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]), frames);
			return true;
		});
	}

	private void plan() {
		WorldSettings world = new WorldSettings(2203L, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands();
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		set(new LinkedHashMap<>(Map.of("distance", 6, "fps", 260, "vsync", 0)));
		add("menu display", () -> openMenu(VideoSetting.Category.DISPLAY, 6, -1), () -> this.waited >= 20);
		shot("menu-display-camera");
		add("menu tooltip collision", () -> openMenu(VideoSetting.Category.DISPLAY, 6, 1004), () -> this.waited >= 20);
		shot("menu-display-tooltip-collision");
		add("menu search zoom", () -> { openMenu(VideoSetting.Category.DISPLAY, 0, -1); search("zoom"); }, () -> this.waited >= 20);
		shot("menu-search-zoom");
		now("close", () -> this.mc.openScreen(new TitleScreen()));

		add("load world", () -> { this.mc.openScreen(null); String name = "alacer-camera-check-" + System.currentTimeMillis(); this.log.println("world " + name); this.mc.startGame(name, name, world); },
			() -> this.mc.world != null && this.mc.player != null && this.waited > 60);
		now("origin", () -> { this.x0 = (int) Math.floor(this.mc.player.x); this.z0 = (int) Math.floor(this.mc.player.z); this.log.println("origin " + this.x0 + "," + this.z0); });
		cmd("/gamerule doDaylightCycle false");
		cmd("/gamerule doMobSpawning false");
		cmd("/time set 6000");
		cmd("/weather clear 1000000");
		// A stone wall west of x0 and pillars as a reference scene.
		add("scene", () -> {
			this.mc.player.sendChat("/fill " + (this.x0 - 3) + " 4 " + (this.z0 - 6) + " " + (this.x0 - 3) + " 8 " + (this.z0 + 6) + " minecraft:stone");
			this.mc.player.sendChat("/fill " + (this.x0 + 6) + " 4 " + (this.z0 - 1) + " " + (this.x0 + 6) + " 9 " + (this.z0 + 1) + " minecraft:gold_block");
		}, () -> this.waited >= 30);
		wait(100);
		now("defaults", () -> { this.log.println("defaults " + cameraValues()); this.log.println("bobbing option " + this.mc.options.viewBobbing); });

		// View bobbing: hold a fixed walking phase (sin = -1) and bob amount, standing on the ground facing east.
		tp(0.5, 4, 0.5, -90, 0);
		wait(40);
		now("bob on", () -> this.everyFrame = () -> {
			ClientPlayerEntity p = this.mc.player;
			p.walkDistance = 0.5F; p.lastWalkDistance = 0.5F; p.bob = 0.1F; p.lastBob = 0.1F;
			setFloat(p, "tilt", 0F); setFloat(p, "lastTilt", 0F);
		});
		for (int v : new int[] {100, 50, 0, 100}) {
			set("quality.viewBobbing", v);
			wait(5);
			offsets("viewBobbing=" + v, 20);
			if (v != 100 || !seenBob) { shot("bobbing-" + label("quality.viewBobbing", v)); }
			seenBob |= v == 100;
		}
		add("bobbing option off", () -> this.mc.options.viewBobbing = false, () -> this.waited >= 3);
		offsets("vanilla View bobbing Off", 20);
		add("bobbing option on", () -> this.mc.options.viewBobbing = true, () -> this.waited >= 3);
		now("bob off", () -> this.everyFrame = null);

		// Distortion: nausea plus a held portal time; 0% must look like no distortion at all.
		add("nausea", () -> this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffect.NAUSEA.id, 20 * 600, 0)), () -> this.waited >= 5);
		now("portal on", () -> this.everyFrame = () -> { setFloat(this.mc.player, "portalTime", 0.8F); setFloat(this.mc.player, "lastPortalTime", 0.8F); });
		for (int v : new int[] {100, 50, 0}) {
			set("quality.distortion", v);
			wait(5);
			now("stretch " + v, () -> this.log.println("distortion=" + v + " portalTime=" + getFloat(this.mc.player, "portalTime")));
			shot("distortion-" + label("quality.distortion", v));
		}
		now("portal off", () -> { this.everyFrame = null; setFloat(this.mc.player, "portalTime", 0F); setFloat(this.mc.player, "lastPortalTime", 0F); });
		add("clear nausea", () -> this.mc.player.removeStatusEffect(StatusEffect.NAUSEA.id), () -> this.waited >= 5);
		now("hold none", () -> this.everyFrame = () -> { setFloat(this.mc.player, "portalTime", 0F); setFloat(this.mc.player, "lastPortalTime", 0F); });
		wait(10);
		shot("distortion-none");
		now("release", () -> this.everyFrame = null);
		set("quality.distortion", 100);

		// Third person: open air first (flying above the floor facing east), then with the wall 3 blocks behind.
		add("fly", () -> { this.mc.player.abilities.flying = true; this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %d 12 %d -90 20", this.x0 + 20, this.z0)); }, () -> this.waited >= 20);
		add("third person", () -> this.mc.options.perspective = 1, () -> this.waited >= 5);
		for (int v : new int[] {4, 1, 8, 16, 4}) {
			set("quality.thirdPersonDistance", v);
			wait(5);
			offsets("open air thirdPersonDistance=" + v, 10);
		}
		set("quality.thirdPersonDistance", 8); wait(5); shot("third-person-8");
		set("quality.thirdPersonDistance", 4); wait(5); shot("third-person-4");
		add("front view", () -> this.mc.options.perspective = 2, () -> this.waited >= 5);
		set("quality.thirdPersonDistance", 8); wait(5); offsets("front view thirdPersonDistance=8", 10);
		set("quality.thirdPersonDistance", 4);
		add("back view", () -> this.mc.options.perspective = 1, () -> this.waited >= 5);
		// Facing east 0.5 blocks from the wall's east face at x0-2.5.. stand at x0 + 0.5, wall at x0-3 (face at x0-2).
		add("tp wall", () -> this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %.1f 5 %.1f -90 0", this.x0 + 0.5, this.z0 + 0.5)), () -> this.waited >= 20);
		for (int collision : new int[] {1, 0, 1}) {
			set("quality.cameraCollision", collision);
			wait(5);
			offsets("wall behind collision=" + collision + " distance=4", 10);
		}
		set("quality.cameraCollision", 1); wait(5); shot("collision-on");
		set("quality.cameraCollision", 0); wait(5); shot("collision-off");
		set("quality.cameraCollision", 1);
		add("first person", () -> this.mc.options.perspective = 0, () -> this.waited >= 5);

		// FOV transitions: start flying (target modifier 1.1) and record the per-tick field of view.
		for (int v : new int[] {1, 0, 2, 3, 1}) {
			set("quality.fovTransition", v);
			add("land", () -> { this.mc.player.abilities.flying = false; }, () -> this.waited >= 60);
			List<Float> fov = new ArrayList<>();
			long[] lastTick = {-1};
			add("fly fov " + v, () -> this.mc.player.abilities.flying = true, () -> {
				long t = this.mc.world.getTime();
				this.mc.player.abilities.flying = true;
				if (t != lastTick[0]) { lastTick[0] = t; fov.add((Float) field(GameRenderer.class, "fov", this.mc.gameRenderer)); }
				if (fov.size() < 10) { return false; }
				StringBuilder b = new StringBuilder("fovTransition=" + label("quality.fovTransition", v) + " per-tick fov");
				for (float f : fov) { b.append(String.format(Locale.ROOT, " %.5f", f)); }
				this.log.println(b);
				return true;
			});
		}
		add("land", () -> { this.mc.player.abilities.flying = false; }, () -> this.waited >= 60);

		// Zoom: bind the key to C for this run only, hold it, read both projections, release and unbind.
		add("tp zoom", () -> this.mc.player.sendChat(String.format(Locale.ROOT, "/tp @p %d 5 %d -90 0", this.x0 - 1, this.z0)), () -> this.waited >= 20);
		now("fov unzoomed", () -> this.log.println("zoom key unbound: " + fovs() + " pressed=" + Alacer.get().zooming()));
		shot("zoom-off");
		now("bind", () -> { KeyBinding key = zoomKey(); key.setKeyCode(Keyboard.KEY_C); KeyBinding.resetMapping(); KeyBinding.set(Keyboard.KEY_C, true); });
		for (int v : new int[] {2, 0, 4}) {
			set("quality.zoom", v);
			now("hold", () -> KeyBinding.set(Keyboard.KEY_C, true));
			wait(3);
			now("fov zoom " + v, () -> this.log.println("zoom=" + label("quality.zoom", v) + " held: " + fovs() + " pressed=" + Alacer.get().zooming()));
			if (v == 2) { shot("zoom-4x"); }
		}
		now("release", () -> { KeyBinding.set(Keyboard.KEY_C, false); this.log.println("released: " + fovs() + " pressed=" + Alacer.get().zooming()); });
		now("unbind", () -> { zoomKey().setKeyCode(Keyboard.KEY_NONE); KeyBinding.resetMapping(); });
		set("quality.zoom", 2);

		// Persist a non-default combination for the relaunch read-back.
		set(new LinkedHashMap<>(Map.of("quality.viewBobbing", 35, "quality.distortion", 20, "quality.thirdPersonDistance", 9,
			"quality.cameraCollision", 0, "quality.fovTransition", 3, "quality.zoom", 4)));
		now("saved", () -> this.log.println("saved " + cameraValues()));
	}

	private boolean seenBob;

	private void readback() {
		add("title", () -> { }, () -> this.mc.screen instanceof TitleScreen);
		now("read", () -> this.log.println("relaunch " + cameraValues() + " restartRequired=" + new VideoSettingsBackend(this.mc).restartRequired()));
		add("menu", () -> openMenu(VideoSetting.Category.DISPLAY, 6, -1), () -> this.waited >= 20);
		shot("menu-display-readback");
		now("close", () -> this.mc.openScreen(new TitleScreen()));
		set(new LinkedHashMap<>(Map.of("quality.viewBobbing", 100, "quality.distortion", 100, "quality.thirdPersonDistance", 4,
			"quality.cameraCollision", 1, "quality.fovTransition", 1, "quality.zoom", 2)));
		now("restored", () -> this.log.println("restored " + cameraValues()));
	}

	private String cameraValues() {
		Map<String,String> all = Alacer.get().config().describe();
		Map<String,String> camera = new LinkedHashMap<>();
		for (String key : new String[] {"quality.viewBobbing", "quality.distortion", "quality.thirdPersonDistance", "quality.cameraCollision", "quality.fovTransition", "quality.zoom"}) {
			camera.put(key, all.get(key) + " (" + VideoSettingsCatalog.byKey(key).valueLabel(Integer.parseInt(all.get(key))) + ")");
		}
		return camera.toString();
	}

	private String fovs() {
		try {
			Method m = GameRenderer.class.getDeclaredMethod("getFov", float.class, boolean.class);
			m.setAccessible(true);
			return "world=" + m.invoke(this.mc.gameRenderer, 1.0F, true) + " hand=" + m.invoke(this.mc.gameRenderer, 1.0F, false);
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}

	private static KeyBinding zoomKey() { return (KeyBinding) field(Alacer.class, "zoomKey", Alacer.get()); }

	private static Object field(Class<?> owner, String name, Object target) {
		try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
		catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}
	/** The most-derived declared float field of that name (ClientPlayerEntity's portalTime hides Entity's int). */
	private static Field floatField(Object target, String name) {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try { Field f = c.getDeclaredField(name); if (f.getType() == float.class) { f.setAccessible(true); return f; } }
			catch (NoSuchFieldException ignored) { }
		}
		throw new IllegalStateException("No float field " + name);
	}
	private static void setFloat(Object target, String name, float value) {
		try { floatField(target, name).setFloat(target, value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
	}
	private static float getFloat(Object target, String name) {
		try { return floatField(target, name).getFloat(target); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
	}
	private static void setField(Object target, String name, Object value) {
		try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
		catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}

	private void openMenu(VideoSetting.Category category, int offset, int focus) {
		VideoSettingsScreen screen = new VideoSettingsScreen(this.mc.screen);
		this.mc.openScreen(screen);
		setField(screen, "category", category);
		setField(screen, "offset", offset);
		screen.init(this.mc, screen.width, screen.height);
		if (focus >= 0) { setField(screen, "keyboardFocus", true); setField(screen, "focusId", focus); }
	}

	private void search(String text) {
		VideoSettingsScreen screen = (VideoSettingsScreen) this.mc.screen;
		Object field = field(VideoSettingsScreen.class, "search", screen);
		try {
			Method set = field.getClass().getMethod("setText", String.class);
			set.invoke(field, text);
			setField(screen, "query", text);
			setField(screen, "offset", 0);
			Method m = VideoSettingsScreen.class.getDeclaredMethod("rebuild"); m.setAccessible(true); m.invoke(screen);
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}

	private static String label(String key, int value) {
		return VideoSettingsCatalog.byKey(key).valueLabel(value).replace(' ', '_').replace("%", "pct");
	}

	static { if (ACTIVE) { Alacer.LOGGER.warn("Camera check harness active ({}); settings will be changed", MODE); } }
}
