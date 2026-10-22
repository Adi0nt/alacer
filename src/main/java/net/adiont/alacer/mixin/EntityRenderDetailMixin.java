package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.EntityRendering;
import net.adiont.alacer.render.EntityDrawDetails;
import net.adiont.alacer.render.GlCallStats;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeInstanceMultiMap;

/**
 * Per-call timing inside {@code renderEntities}. Only applied when {@code profiler.entityDetail}
 * is enabled (see {@link net.adiont.alacer.config.AlacerMixinPlugin}), so normal play pays nothing for it.
 */
@Mixin(WorldRenderer.class)
public class EntityRenderDetailMixin {

	@WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/TypeInstanceMultiMap;isEmpty()Z"))
	private boolean alacer$sectionOccupied(TypeInstanceMultiMap<?> section, Operation<Boolean> original) {
		boolean empty = original.call(section);
		if (!empty) {
			EntityRendering.count(Counter.ENTITY_SECTIONS_OCCUPIED);
		}
		return empty;
	}

	@WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;DDD)Z"))
	private boolean alacer$timeVisibilityCheck(EntityRenderDispatcher dispatcher, Entity entity, Culler culler, double x, double y, double z, Operation<Boolean> original) {
		long start = System.nanoTime();
		boolean visible = original.call(dispatcher, entity, culler, x, y, z);
		EntityRendering.add(Section.ENTITY_VISIBILITY_CHECK, System.nanoTime() - start);
		EntityRendering.count(Counter.ENTITIES_CHECKED);
		return visible;
	}

	// Ordinal 2: the entity loop (0 is global entities, 1 the outline pass).
	@WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;F)Z", ordinal = 2))
	private boolean alacer$timeEntityDraw(EntityRenderDispatcher dispatcher, Entity entity, float tickDelta, Operation<Boolean> original) {
		GlCallStats.beginEntity(entity);
		EntityDrawDetails.beginEntity(entity);
		long start = System.nanoTime();
		boolean drawn;
		try {
			drawn = original.call(dispatcher, entity, tickDelta);
		} finally {
			EntityDrawDetails.endEntity();
			GlCallStats.endEntity();
		}
		EntityRendering.add(Section.ENTITY_DRAW, System.nanoTime() - start);
		EntityRendering.count(Counter.ENTITIES_DRAWN);
		return drawn;
	}

	// Ordinal 0: block entities from the visible chunks (1 is global ones, 2 mining progress).
	@WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FI)V", ordinal = 0))
	private void alacer$timeBlockEntityDraw(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float tickDelta, int progress, Operation<Void> original) {
		long start = System.nanoTime();
		original.call(dispatcher, blockEntity, tickDelta, progress);
		EntityRendering.add(Section.BLOCK_ENTITY_DRAW, System.nanoTime() - start);
		EntityRendering.count(Counter.BLOCK_ENTITIES_DRAWN);
	}
}
