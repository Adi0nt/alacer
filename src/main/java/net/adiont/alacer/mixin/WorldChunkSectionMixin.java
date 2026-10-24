package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.world.FastChunkUnpack;
import net.minecraft.world.chunk.WorldChunkSection;

@Mixin(WorldChunkSection.class)
public class WorldChunkSectionMixin {
	@Inject(method = "validateBlockCounters", at = @At("HEAD"), cancellable = true)
	private void alacer$validateBlockCounters(CallbackInfo ci) {
		if (FastChunkUnpack.isEnabled()) {
			FastChunkUnpack.validateBlockCounters((WorldChunkSection) (Object) this);
			ci.cancel();
		}
	}
}
