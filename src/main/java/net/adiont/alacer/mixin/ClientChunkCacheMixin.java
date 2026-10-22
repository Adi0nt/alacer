package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.EntityRendering;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.world.chunk.WorldChunk;

/** Reports client chunk loads and unloads so cached world-chunk references can be invalidated. */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

	@Inject(method = "loadChunk", at = @At("HEAD"))
	private void alacer$chunkLoaded(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
		EntityRendering.onChunkMapChanged(chunkX, chunkZ);
	}

	@Inject(method = "unloadChunk", at = @At("HEAD"))
	private void alacer$chunkUnloaded(int chunkX, int chunkZ, CallbackInfo ci) {
		EntityRendering.onChunkMapChanged(chunkX, chunkZ);
	}
}
