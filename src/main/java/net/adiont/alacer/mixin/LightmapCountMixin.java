package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlCallStats;
import net.minecraft.client.render.platform.GLX;

/**
 * Counts lightmap coordinate changes during entity draws for {@link GlCallStats}. Only applied
 * when {@code profiler.entityDetail} is enabled.
 */
@Mixin(GLX.class)
public class LightmapCountMixin {

	@Inject(method = "multiTexCoord2f", at = @At("HEAD"))
	private static void alacer$lightmap(int texture, float s, float t, CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.lightmap();
		}
	}
}
