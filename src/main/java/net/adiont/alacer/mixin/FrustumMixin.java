package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.render.GlStateReads;
import net.minecraft.client.render.Frustum;

/** The projection and modelview reads in {@code Frustum.compute}. */
@Mixin(Frustum.class)
public class FrustumMixin {

	@WrapOperation(method = "compute", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;getFloat(ILjava/nio/FloatBuffer;)V"))
	private void alacer$readMatrix(int pname, FloatBuffer buffer, Operation<Void> original) {
		GlStateReads.readMatrix(pname, buffer, original);
	}
}
