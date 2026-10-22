package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.RenderPasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;

/** HUD timing, and the profiler overlay drawn on top of the HUD (not included in the timing). */
@Mixin(GameGui.class)
public class GameGuiMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "render", at = @At("HEAD"))
	private void alacer$beginGui(float tickDelta, CallbackInfo ci) {
		RenderPasses.begin(Section.GUI);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void alacer$renderOverlay(float tickDelta, CallbackInfo ci) {
		RenderPasses.end(Section.GUI);
		Alacer.get().renderHud(this.minecraft);
	}
}
