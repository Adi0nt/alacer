package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.ParticleDrawDiagnostics;
import net.minecraft.client.ParticleManager;

/** Attribution only: entirely absent unless the command-line benchmark diagnostic is enabled. */
@Mixin(ParticleManager.class)
public class ParticleDrawDiagnosticMixin {

	@Inject(method = "render", at = @At("HEAD"))
	private void alacer$beginDiagnosticPass(CallbackInfo ci) {
		ParticleDrawDiagnostics.beginPass();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void alacer$endDiagnosticPass(CallbackInfo ci) {
		ParticleDrawDiagnostics.endPass();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
	private void alacer$beginDiagnosticDraw(CallbackInfo ci) {
		ParticleDrawDiagnostics.beginDraw();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", shift = At.Shift.AFTER))
	private void alacer$endDiagnosticDraw(CallbackInfo ci) {
		ParticleDrawDiagnostics.endDraw();
	}
}
