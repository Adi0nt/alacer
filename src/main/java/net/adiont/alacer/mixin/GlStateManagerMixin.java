package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlStateReads;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Mirrors matrix operations into {@link GlStateReads}' tracker while it tracks the camera
 * setup, and records the viewport. Each hook is a field check outside that window.
 */
@Mixin(GlStateManager.class)
public class GlStateManagerMixin {

	@Inject(method = "viewport", at = @At("HEAD"))
	private static void alacer$viewport(int x, int y, int width, int height, CallbackInfo ci) {
		GlStateReads.onViewport(x, y, width, height);
	}

	@Inject(method = "matrixMode", at = @At("HEAD"))
	private static void alacer$matrixMode(int mode, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().matrixMode(mode);
		}
	}

	@Inject(method = "loadIdentity", at = @At("HEAD"))
	private static void alacer$loadIdentity(CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().loadIdentity();
		}
	}

	@Inject(method = "pushMatrix", at = @At("HEAD"))
	private static void alacer$pushMatrix(CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().push();
		}
	}

	@Inject(method = "popMatrix", at = @At("HEAD"))
	private static void alacer$popMatrix(CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().pop();
		}
	}

	@Inject(method = "ortho", at = @At("HEAD"))
	private static void alacer$ortho(double l, double r, double b, double t, double n, double f, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().ortho(l, r, b, t, n, f);
		}
	}

	@Inject(method = "rotatef", at = @At("HEAD"))
	private static void alacer$rotatef(float angle, float x, float y, float z, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().rotate(angle, x, y, z);
		}
	}

	@Inject(method = "scalef", at = @At("HEAD"))
	private static void alacer$scalef(float x, float y, float z, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().scale(x, y, z);
		}
	}

	@Inject(method = "scaled", at = @At("HEAD"))
	private static void alacer$scaled(double x, double y, double z, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().scale((float) x, (float) y, (float) z);
		}
	}

	@Inject(method = "translatef", at = @At("HEAD"))
	private static void alacer$translatef(float x, float y, float z, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().translate(x, y, z);
		}
	}

	@Inject(method = "translated", at = @At("HEAD"))
	private static void alacer$translated(double x, double y, double z, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().translate((float) x, (float) y, (float) z);
		}
	}

	@Inject(method = "multMatrix", at = @At("HEAD"))
	private static void alacer$multMatrix(FloatBuffer matrix, CallbackInfo ci) {
		if (GlStateReads.tracking) {
			GlStateReads.tracker().multiply(matrix);
		}
	}
}
