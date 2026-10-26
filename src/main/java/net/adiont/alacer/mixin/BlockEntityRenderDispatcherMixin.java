package net.adiont.alacer.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.BlockEntityRendererCache;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Mixin into {@link BlockEntityRenderDispatcher} providing fast MRU renderer caching
 * for block entity renderers.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

	@Shadow
	private Map<Class<? extends BlockEntity>, BlockEntityRenderer<? extends BlockEntity>> renderers;

	@Inject(method = "getRenderer(Ljava/lang/Class;)Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;", at = @At("HEAD"), cancellable = true)
	private void alacer$getRendererCached(Class<? extends BlockEntity> clazz, CallbackInfoReturnable<BlockEntityRenderer<?>> cir) {
		if (BlockEntityRendererCache.isEnabled()) {
			BlockEntityRenderer<?> cached = BlockEntityRendererCache.get(clazz);
			if (cached != null) {
				if (BlockEntityRendererCache.isVerifying()) {
					BlockEntityRendererCache.verify(this.renderers, clazz, cached);
				}
				cir.setReturnValue(cached);
			}
		}
	}

	@Inject(method = "getRenderer(Ljava/lang/Class;)Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;", at = @At("RETURN"))
	private void alacer$cacheRendererOnMiss(Class<? extends BlockEntity> clazz, CallbackInfoReturnable<BlockEntityRenderer<?>> cir) {
		if (BlockEntityRendererCache.isEnabled()) {
			BlockEntityRenderer<?> result = cir.getReturnValue();
			if (result != null) {
				BlockEntityRendererCache.put(clazz, result);
			}
		}
	}
}
