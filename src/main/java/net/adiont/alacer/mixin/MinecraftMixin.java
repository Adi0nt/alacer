package net.adiont.alacer.mixin;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.profiler.PerformanceAreaGraphRenderer;
import net.adiont.alacer.profiler.PerformanceTracker;
import net.adiont.alacer.render.DynamicFramePacing;
import net.adiont.alacer.render.FastDisplaySync;
import net.adiont.alacer.render.GlErrorChecks;
import net.adiont.alacer.render.SwapOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;

/** Frame boundaries, the render/swap split of each frame, and vanilla's per-frame GL error checks. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Unique private long alacer$resourceReloadStart;

	@Inject(method = "reloadResources", at = @At("HEAD"))
	private void alacer$beginResourceTiming(CallbackInfo ci) {
		if (Boolean.getBoolean("alacer.benchmark.resourceTiming")) this.alacer$resourceReloadStart = System.nanoTime();
	}

	@Inject(method = "reloadResources", at = @At("RETURN"))
	private void alacer$endResourceTiming(CallbackInfo ci) {
		if (Boolean.getBoolean("alacer.benchmark.resourceTiming")) {
			Alacer.LOGGER.info("Resource timing: Minecraft reloadResources {} ns", System.nanoTime() - this.alacer$resourceReloadStart);
		}
	}

	@Inject(method = "runGame", at = @At("HEAD"))
	private void alacer$frameStart(CallbackInfo ci) {
		Alacer.get().onFrameStart();
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"))
	private void alacer$beforeRender(CallbackInfo ci) {
		SwapOptimizer.onPreRenderWorld();
		DynamicFramePacing.beginFrame();
		Alacer.get().begin(Section.RENDER);
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V", shift = At.Shift.AFTER))
	private void alacer$afterRender(CallbackInfo ci) {
		Alacer.get().end(Section.RENDER);
		DynamicFramePacing.endFrame();
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
		SwapOptimizer.beforeDisplayUpdate();
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V", shift = At.Shift.AFTER))
	private void alacer$afterSwap(CallbackInfo ci) {
		SwapOptimizer.afterDisplayUpdate();
		Alacer.get().end(Section.SWAP);
	}

	@WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
	private void alacer$postSwapYield(Operation<Void> original) {
		Alacer alacer = Alacer.get();
		if (alacer != null && alacer.config().suppressThreadYield) {
			SwapOptimizer.onPostSwapYield();
		} else {
			original.call();
		}
	}

	@WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"))
	private void alacer$displaySync(int fps, Operation<Void> original) {
		Alacer.get().begin(Section.SWAP);
		try {
			Alacer alacer = Alacer.get();
			if (alacer != null && alacer.config().fastDisplaySync) {
				FastDisplaySync.sync(fps);
			} else {
				original.call(fps);
			}
		} finally {
			Alacer.get().end(Section.SWAP);
		}
	}

	@Inject(method = "handleGuiKeyBindings", at = @At("HEAD"))
	private void alacer$handleKeyBindings(CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (minecraft.screen != null || !Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()
			|| Keyboard.getEventKey() != Keyboard.KEY_F4 || !PerformanceAreaGraphRenderer.isVisible(minecraft)) return;
		if (Screen.isShiftDown()) PerformanceTracker.toggleFrozen();
		else if (Screen.isControlDown()) PerformanceAreaGraphRenderer.cycleScale();
		else {
			PerformanceAreaGraphRenderer.cycleMode();
			Alacer.get().config().graphMode = PerformanceAreaGraphRenderer.getMode();
		}
	}

	@Inject(method = "renderProfilerChart", at = @At("HEAD"), cancellable = true)
	private void alacer$cancelVanillaProfilerChart(long time, CallbackInfo ci) {
		if (PerformanceAreaGraphRenderer.isVisible((Minecraft) (Object) this)) ci.cancel();
	}
}
