package net.adiont.alacer.devcheck;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ModernGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;

/** Temporary standalone lifecycle diagnostic. Never installed in the shipping build. */
public final class ModernCheck {
 public static final boolean ACTIVE = Boolean.getBoolean("alacer.modernCheck");
 private static long start;
 private static int step = -1;
 private static int oldDistance, oldFps;
 private static boolean oldVbo, oldVsync, oldPause;
 public static void frame(Minecraft mc) {
  try { tick(mc); }
  catch (Throwable e) { Alacer.LOGGER.error("Modern standalone lifecycle FAILED", e); restore(mc); mc.shutdown(); }
 }
 private static void restore(Minecraft mc) {
  if (step < 0) return;
  mc.options.viewDistance = oldDistance; mc.options.fpsLimit = oldFps; mc.options.useVbo = oldVbo;
  mc.options.vsync = oldVsync; mc.options.pauseOnUnfocus = oldPause; Display.setVSyncEnabled(oldVsync);
 }
 private static void tick(Minecraft mc) throws Exception {
  if (step == -1) {
   if (!(mc.screen instanceof TitleScreen)) return;
   oldDistance = mc.options.viewDistance; oldFps = mc.options.fpsLimit; oldVbo = mc.options.useVbo;
   oldVsync = mc.options.vsync; oldPause = mc.options.pauseOnUnfocus; step = 0;
   mc.options.viewDistance = 12; mc.options.fpsLimit = 260; mc.options.useVbo = true;
   mc.options.vsync = false; mc.options.pauseOnUnfocus = false; Display.setVSyncEnabled(false);
   String name = "alacer-modern-lifecycle-" + System.currentTimeMillis();
   Path base = mc.gameDir.toPath().resolve("saves/alacer-benchmark");
   Path target = mc.gameDir.toPath().resolve("saves/" + name);
   try (Stream<Path> files = Files.walk(base)) {
    for (Path p : (Iterable<Path>) files::iterator) {
     Path dest = target.resolve(base.relativize(p));
     if (Files.isDirectory(p)) Files.createDirectories(dest);
     else Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
    }
   }
   mc.openScreen(null);
   mc.startGame(name, name, new WorldSettings(1789L, WorldSettings.GameMode.SPECTATOR, true, false, WorldGeneratorType.DEFAULT).enableCommands());
   return;
  }
  if (mc.world == null || mc.player == null) return;
  long now = System.nanoTime();
  if (start == 0) { start = now; mc.player.sendChat("/gamemode 3"); mc.player.sendChat("/tp @p -200 90 267 -90 20"); }
  double seconds = (now - start) / 1e9;
  if (step == 0 && seconds > 12) { step++; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern standalone: reload"); }
  if (step == 1 && seconds > 24) { step++; mc.options.viewDistance = 8; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern standalone: distance 8"); }
  if (step == 2 && seconds > 36) { step++; mc.options.viewDistance = 12; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern standalone: distance 12"); }
  if (step == 3 && seconds > 48) {
   step++; mc.worldRenderer.setWorld(null); receipt("detached"); mc.worldRenderer.setWorld(mc.world);
   Alacer.LOGGER.info("Modern standalone: renderer world reattached");
  }
  if (step == 4 && seconds > 60) {
   step++; mc.worldRenderer.setWorld(null); receipt("final-detached"); restore(mc); mc.shutdown();
  }
 }
 private static void receipt(String label) throws Exception {
  Map<String,String> values = new LinkedHashMap<>(); ModernGl.describe(values);
  if (!values.get("modernGl.vaosCreated").equals(values.get("modernGl.vaosDeleted"))) throw new IllegalStateException("VAO retirement incomplete: " + values);
  Path out = Minecraft.getInstance().gameDir.toPath().resolve("alacer/modern-lifecycle"); Files.createDirectories(out);
  Files.writeString(out.resolve(label + ".txt"), "PASS " + values + "\n");
  Alacer.LOGGER.info("Modern standalone {} PASS: {}", label, values);
 }
}
