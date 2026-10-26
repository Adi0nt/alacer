package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.RenderChunkOrigin;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.RenderChunkList;

/**
 * Optimizes terrain chunk translation by querying primitive coordinates directly from
 * {@link RenderChunkOrigin}, bypassing {@code BlockPos} getters and allocations.
 */
@Mixin(RenderChunkList.class)
public abstract class RenderChunkListMixin {

	@Shadow private double x;
	@Shadow private double y;
	@Shadow private double z;

	@Inject(method = "glTranslatefOrigin", at = @At("HEAD"), cancellable = true)
	private void alacer$fastTranslate(RenderChunk chunk, CallbackInfo ci) {
		if (chunk instanceof RenderChunkOrigin ro) {
			GlStateManager.translatef(
				(float) (ro.alacer$originX() - this.x),
				(float) (ro.alacer$originY() - this.y),
				(float) (ro.alacer$originZ() - this.z)
			);
			ci.cancel();
		}
	}
}
