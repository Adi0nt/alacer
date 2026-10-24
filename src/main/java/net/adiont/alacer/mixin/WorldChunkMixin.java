package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.world.FastChunkUnpack;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(WorldChunk.class)
public class WorldChunkMixin {
	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void alacer$update(byte[] data, int sectionsMask, boolean isFull, CallbackInfo ci) {
		if (FastChunkUnpack.isEnabled()) {
			FastChunkUnpack.updateChunk((WorldChunk) (Object) this, data, sectionsMask, isFull);
			ci.cancel();
		}
	}
}
