package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.RenderChunkOrigin;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Eliminates thousands of per-frame {@link BlockPos} allocations during camera updates by testing
 * primitive chunk coordinates before allocating candidate origins.
 */
@Mixin(RenderChunkStorage.class)
public abstract class RenderChunkStorageMixin {

	@Shadow protected int sizeX;
	@Shadow protected int sizeY;
	@Shadow protected int sizeZ;
	@Shadow public RenderChunk[] chunks;

	@Unique
	private static int alacer$coordinate(int origin, int chunk, int size) {
		int i = size * 16;
		int j = i - origin + chunk / 2;
		if (j < 0) {
			j -= chunk - 1;
		}
		return i - j / chunk * chunk;
	}

	@Inject(method = "updateCameraPos", at = @At("HEAD"), cancellable = true)
	private void alacer$updateCameraPos(double x, double z, CallbackInfo ci) {
		int i = MathHelper.floor(x) - 8;
		int j = MathHelper.floor(z) - 8;
		int k = this.sizeX * 16;

		int sizeY = this.sizeY;
		int sizeX = this.sizeX;
		int sizeZ = this.sizeZ;

		for (int l = 0; l < sizeX; l++) {
			int m = alacer$coordinate(i, k, l);

			for (int n = 0; n < sizeZ; n++) {
				int o = alacer$coordinate(j, k, n);
				int baseIndex = (n * sizeY) * sizeX + l;

				for (int p = 0; p < sizeY; p++) {
					int q = p << 4;
					RenderChunk renderChunk = this.chunks[baseIndex + p * sizeX];
					if (renderChunk instanceof RenderChunkOrigin ro) {
						if (ro.alacer$originX() != m || ro.alacer$originY() != q || ro.alacer$originZ() != o) {
							renderChunk.setOrigin(new BlockPos(m, q, o));
						}
					} else {
						BlockPos currentOrigin = renderChunk.getOrigin();
						if (currentOrigin.getX() != m || currentOrigin.getY() != q || currentOrigin.getZ() != o) {
							renderChunk.setOrigin(new BlockPos(m, q, o));
						}
					}
				}
			}
		}
		ci.cancel();
	}

	@Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
	private void alacer$fastGetChunk(BlockPos pos, CallbackInfoReturnable<RenderChunk> cir) {
		int cy = pos.getY() >> 4;
		if (cy < 0 || cy >= this.sizeY) {
			cir.setReturnValue(null);
			return;
		}
		int cx = (pos.getX() >> 4) % this.sizeX;
		if (cx < 0) {
			cx += this.sizeX;
		}
		int cz = (pos.getZ() >> 4) % this.sizeZ;
		if (cz < 0) {
			cz += this.sizeZ;
		}
		int index = (cz * this.sizeY + cy) * this.sizeX + cx;
		cir.setReturnValue(this.chunks[index]);
	}
}
