package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModelListBatch;
import net.minecraft.client.render.model.ModelPart;

@Mixin(ModelPart.class)
public class ModelPartBatchMixin {
	@Shadow private int glList;
	@Unique private int alacer$batchedList;
	@Unique private int alacer$sourceList;

	@Inject(method = "compile", at = @At("HEAD"))
	private void alacer$begin(float scale, CallbackInfo ci) {
		ModelListBatch.begin((ModelPart) (Object) this);
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void alacer$finish(float scale, CallbackInfo ci) {
		this.alacer$batchedList = ModelListBatch.finish((ModelPart) (Object) this, this.glList, this.alacer$batchedList);
		this.alacer$sourceList = this.glList;
	}

	@ModifyArg(method = {"render", "renderForceTransform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V"), index = 0)
	private int alacer$select(int list) {
		return ModelListBatch.select(list, this.alacer$sourceList, this.alacer$batchedList);
	}
}
