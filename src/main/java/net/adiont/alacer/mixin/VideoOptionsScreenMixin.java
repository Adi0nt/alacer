package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.settings.VideoSettingsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen;

/** Preserve the Options menu entry and its parent; only replace the vanilla screen after initialization. */
@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin extends Screen {
	@Shadow private Screen parent;
	@Inject(method = "init", at = @At("HEAD"), cancellable = true)
	private void alacer$openVideoSettings(CallbackInfo ci) {
		this.minecraft.openScreen(new VideoSettingsScreen(this.parent));
		ci.cancel();
	}
}
