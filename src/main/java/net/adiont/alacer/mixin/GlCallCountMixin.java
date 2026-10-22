package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlCallStats;
import net.adiont.alacer.render.GlCallStats.State;
import net.minecraft.client.render.platform.GlStateManager;

/**
 * Counts {@code GlStateManager} calls during entity draws for {@link GlCallStats}. Only applied
 * when {@code profiler.entityDetail} is enabled. Matrix operations and display list calls always
 * reach OpenGL; state setters are counted as requests at their start and as changes where they
 * call OpenGL (only when the cached value differs). Capability toggles are counted in
 * {@link GlStateCountMixin}.
 */
@Mixin(GlStateManager.class)
public class GlCallCountMixin {

	private static final String GL11 = "Lorg/lwjgl/opengl/GL11;";
	private static final String GLX = "Lnet/minecraft/client/render/platform/GLX;";

	// --- Matrix operations and display lists ---

	@Inject(method = {"matrixMode", "loadIdentity", "pushMatrix", "popMatrix", "rotatef", "scalef", "scaled", "ortho", "multMatrix"}, at = @At("HEAD"))
	private static void alacer$matrix(CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.matrix();
		}
	}

	@Inject(method = "translatef", at = @At("HEAD"))
	private static void alacer$translatef(float x, float y, float z, CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.translate(x, y, z);
		}
	}

	@Inject(method = "translated", at = @At("HEAD"))
	private static void alacer$translated(double x, double y, double z, CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.translate(x, y, z);
		}
	}

	@Inject(method = "callList", at = @At("HEAD"))
	private static void alacer$callList(CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.callList();
		}
	}

	// --- Deduplicated state setters: every request ...

	@Inject(method = {"alphaFunc", "colorMaterial", "depthFunc", "depthMask", "blendFunc", "blendFuncSeparate", "cullFace",
		"activeTexture", "bindTexture", "shadeModel", "colorMask", "color4f"}, at = @At("HEAD"))
	private static void alacer$stateRequest(CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.stateRequest();
		}
	}

	// --- ... and the ones that reach OpenGL ---

	@Inject(method = "alphaFunc", at = @At(value = "INVOKE", target = GL11 + "glAlphaFunc(IF)V"))
	private static void alacer$alphaFunc(CallbackInfo ci) {
		change(State.ALPHA_FUNC);
	}

	@Inject(method = "colorMaterial", at = @At(value = "INVOKE", target = GL11 + "glColorMaterial(II)V"))
	private static void alacer$colorMaterial(CallbackInfo ci) {
		change(State.COLOR_MATERIAL);
	}

	@Inject(method = "depthFunc", at = @At(value = "INVOKE", target = GL11 + "glDepthFunc(I)V"))
	private static void alacer$depthFunc(CallbackInfo ci) {
		change(State.DEPTH_FUNC);
	}

	@Inject(method = "depthMask", at = @At(value = "INVOKE", target = GL11 + "glDepthMask(Z)V"))
	private static void alacer$depthMask(CallbackInfo ci) {
		change(State.DEPTH_MASK);
	}

	@Inject(method = "blendFunc", at = @At(value = "INVOKE", target = GL11 + "glBlendFunc(II)V"))
	private static void alacer$blendFunc(CallbackInfo ci) {
		change(State.BLEND_FUNC);
	}

	@Inject(method = "blendFuncSeparate", at = @At(value = "INVOKE", target = GLX + "blendFuncSeparate(IIII)V"))
	private static void alacer$blendFuncSeparate(CallbackInfo ci) {
		change(State.BLEND_FUNC);
	}

	@Inject(method = "cullFace", at = @At(value = "INVOKE", target = GL11 + "glCullFace(I)V"))
	private static void alacer$cullFace(CallbackInfo ci) {
		change(State.CULL_FACE);
	}

	@Inject(method = "activeTexture", at = @At(value = "INVOKE", target = GLX + "activeTexture(I)V"))
	private static void alacer$activeTexture(CallbackInfo ci) {
		change(State.ACTIVE_TEXTURE);
	}

	@Inject(method = "bindTexture", at = @At(value = "INVOKE", target = GL11 + "glBindTexture(II)V"))
	private static void alacer$bindTexture(CallbackInfo ci) {
		change(State.BIND_TEXTURE);
	}

	@Inject(method = "shadeModel", at = @At(value = "INVOKE", target = GL11 + "glShadeModel(I)V"))
	private static void alacer$shadeModel(CallbackInfo ci) {
		change(State.SHADE_MODEL);
	}

	@Inject(method = "colorMask", at = @At(value = "INVOKE", target = GL11 + "glColorMask(ZZZZ)V"))
	private static void alacer$colorMask(CallbackInfo ci) {
		change(State.COLOR_MASK);
	}

	@Inject(method = "color4f", at = @At(value = "INVOKE", target = GL11 + "glColor4f(FFFF)V"))
	private static void alacer$color(CallbackInfo ci) {
		change(State.COLOR);
	}

	private static void change(State state) {
		if (GlCallStats.counting) {
			GlCallStats.stateChange(state);
		}
	}
}
