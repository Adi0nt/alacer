package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlCallStats;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;

/**
 * Counts immediate-mode draws during entity draws for {@link GlCallStats}. Only applied when
 * {@code profiler.entityDetail} is enabled.
 */
@Mixin(BufferUploader.class)
public class DrawCountMixin {

	@Inject(method = "end", at = @At("HEAD"))
	private void alacer$draw(BufferBuilder builder, CallbackInfo ci) {
		if (GlCallStats.counting) {
			GlCallStats.draw(builder.getFormat(), builder.getVertexCount());
		}
	}
}
