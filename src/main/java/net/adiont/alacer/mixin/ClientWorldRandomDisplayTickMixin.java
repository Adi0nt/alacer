package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.world.ClientWorld;

import net.adiont.alacer.render.FastRandomDisplayTicks;

@Mixin(ClientWorld.class)
public abstract class ClientWorldRandomDisplayTickMixin {

	@Inject(method = "doRandomDisplayTicks", at = @At("HEAD"), cancellable = true)
	private void onDoRandomDisplayTicks(int x, int y, int z, CallbackInfo ci) {
		if (FastRandomDisplayTicks.isEnabled()) {
			FastRandomDisplayTicks.doRandomDisplayTicks((ClientWorld) (Object) this, x, y, z, FastRandomDisplayTicks.isVerifying());
			ci.cancel();
		}
	}
}
