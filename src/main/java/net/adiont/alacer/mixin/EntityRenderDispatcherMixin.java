package net.adiont.alacer.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.EntityCulling;
import net.adiont.alacer.render.EntityRendererCache;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

/**
 * Mixin into {@link EntityRenderDispatcher} providing fast MRU renderer caching
 * and pre-cull distance/frustum evaluation.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

	@Shadow
	private Map<Class<? extends Entity>, EntityRenderer<? extends Entity>> renderers;

	@Inject(method = "getRenderer(Ljava/lang/Class;)Lnet/minecraft/client/render/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
	private void alacer$getRendererCached(Class<? extends Entity> clazz, CallbackInfoReturnable<EntityRenderer<?>> cir) {
		if (EntityRendererCache.isEnabled()) {
			EntityRenderer<?> cached = EntityRendererCache.get(clazz);
			if (cached != null) {
				if (EntityRendererCache.isVerifying()) {
					EntityRendererCache.verify(this.renderers, clazz, cached);
				}
				cir.setReturnValue(cached);
			}
		}
	}

	@Inject(method = "getRenderer(Ljava/lang/Class;)Lnet/minecraft/client/render/entity/EntityRenderer;", at = @At("RETURN"))
	private void alacer$cacheRendererOnMiss(Class<? extends Entity> clazz, CallbackInfoReturnable<EntityRenderer<?>> cir) {
		if (EntityRendererCache.isEnabled()) {
			EntityRenderer<?> result = cir.getReturnValue();
			if (result != null) {
				EntityRendererCache.put(clazz, result);
			}
		}
	}

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void alacer$shouldRenderCulling(Entity entity, Culler culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (EntityCulling.isEnabled()) {
			EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
			boolean visible = EntityCulling.shouldRender(dispatcher, entity, culler, camX, camY, camZ);
			if (EntityCulling.isVerifying()) {
				EntityCulling.verify(dispatcher, entity, culler, camX, camY, camZ, visible);
			}
			cir.setReturnValue(visible);
		}
	}
}
