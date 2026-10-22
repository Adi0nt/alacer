package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModelListBatch;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;

@Mixin(BufferUploader.class)
public class ModelUploadMixin {
	@Inject(method = "end", at = @At("HEAD"))
	private void alacer$capture(BufferBuilder builder, CallbackInfo ci) {
		if (ModelListBatch.capturing) {
			ModelListBatch.capture(builder);
		}
	}
}
