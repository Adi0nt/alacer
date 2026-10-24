package net.adiont.alacer.devcheck;

import net.adiont.alacer.Alacer;
import net.minecraft.client.Minecraft;

/** Temporary reload/distance diagnostic, removed before performance freeze. */
public final class ModernCheck {
 public static final boolean ACTIVE = Boolean.getBoolean("alacer.modernCheck");
 private static long start;
 private static int step;
 public static void frame(Minecraft mc) {
  if (mc.world == null || mc.player == null) { return; }
  long now = System.nanoTime(); if (start == 0) { start = now; }
  double seconds = (now - start) / 1e9;
  if (step == 0 && seconds > 20) { step++; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern check: explicit reload"); }
  if (step == 1 && seconds > 35) { step++; mc.options.viewDistance = 8; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern check: distance 8"); }
  if (step == 2 && seconds > 45) { step++; mc.options.viewDistance = 12; mc.worldRenderer.reload(); Alacer.LOGGER.info("Modern check: distance 12"); }
 }
}
