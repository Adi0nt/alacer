package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlCallStats;

/**
 * Counts capability toggles ({@code GlStateManager.enable*}/{@code disable*}) during entity
 * draws for {@link GlCallStats}. Only applied when {@code profiler.entityDetail} is enabled.
 */
@Mixin(targets = "net.minecraft.client.render.platform.GlStateManager$GlState")
public class GlStateCountMixin {

	@Shadow
	@Final
	private int cap;

	@Shadow
	private boolean enabled;

	@Inject(method = "setEnabled", at = @At("HEAD"))
	private void alacer$setEnabled(boolean enabled, CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.capability(this.cap, enabled != this.enabled);
		}
	}
}
