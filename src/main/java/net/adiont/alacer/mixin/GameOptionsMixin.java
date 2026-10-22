package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.GlErrorChecks;
import net.minecraft.client.options.GameOptions;

/** LWJGL throws on a pending GL error when the swap interval changes (the vsync option). */
@Mixin(GameOptions.class)
public class GameOptionsMixin {

	@Inject(method = "set(Lnet/minecraft/client/options/GameOptions$Option;I)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setVSyncEnabled(Z)V", remap = false))
	private void alacer$beforeVsyncChange(CallbackInfo ci) {
		GlErrorChecks.beforeDisplayChange();
	}
}
