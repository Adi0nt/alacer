package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.GlErrorChecks;
import net.minecraft.client.Minecraft;

/** Frame boundaries, the render/swap split of each frame, and vanilla's per-frame GL error checks. */
@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(method = "runGame", at = @At("HEAD"))
	private void alacer$frameStart(CallbackInfo ci) {
		Alacer.get().onFrameStart();
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"))
	private void alacer$beforeRender(CallbackInfo ci) {
		Alacer.get().begin(Section.RENDER);
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V", shift = At.Shift.AFTER))
	private void alacer$afterRender(CallbackInfo ci) {
		Alacer.get().end(Section.RENDER);
	}

	/** The two per-frame checks only; the startup checks are in other methods. */
	@WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;logGlError(Ljava/lang/String;)V"))
	private void alacer$glErrorCheck(Minecraft minecraft, String location, Operation<Void> original) {
		GlErrorChecks.check(minecraft, location, original);
	}

	/** LWJGL throws on a pending GL error when the display mode or swap interval changes. */
	@Inject(method = "toggleFullscreen", at = @At("HEAD"))
	private void alacer$beforeToggleFullscreen(CallbackInfo ci) {
		GlErrorChecks.beforeDisplayChange();
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"))
	private void alacer$beforeSwap(CallbackInfo ci) {
		Alacer.get().begin(Section.SWAP);
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V", shift = At.Shift.AFTER))
	private void alacer$afterSwap(CallbackInfo ci) {
		Alacer.get().end(Section.SWAP);
	}
}
