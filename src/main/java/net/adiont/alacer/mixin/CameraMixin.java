package net.adiont.alacer.mixin;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.render.GlStateReads;
import net.minecraft.client.render.Camera;

/** The modelview, projection and viewport reads in {@code Camera.setup}. */
@Mixin(Camera.class)
public class CameraMixin {

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;getFloat(ILjava/nio/FloatBuffer;)V"))
	private static void alacer$readMatrix(int pname, FloatBuffer buffer, Operation<Void> original) {
		GlStateReads.readMatrix(pname, buffer, original);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetInteger(ILjava/nio/IntBuffer;)V", remap = false))
	private static void alacer$readViewport(int pname, IntBuffer buffer, Operation<Void> original) {
		GlStateReads.readViewport(pname, buffer, original);
	}
}
