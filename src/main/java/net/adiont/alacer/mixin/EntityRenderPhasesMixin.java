package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.adiont.alacer.render.EntityRendering;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Phase timing for {@code renderEntities} and the world-chunk lookup in its entity loop.
 * Phase boundaries are vanilla's own profiler section changes: "global", "entityOutlines",
 * "entities" and "blockentities", in that order.
 */
@Mixin(WorldRenderer.class)
public class EntityRenderPhasesMixin {

	private static final String SWAP = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V";

	@Inject(method = "renderEntities", at = @At("HEAD"))
	private void alacer$beginEntities(CallbackInfo ci) {
		EntityRendering.begin();
	}

	@Inject(method = "renderEntities", at = @At(value = "INVOKE", target = SWAP, ordinal = 0))
	private void alacer$globalPhase(CallbackInfo ci) {
		EntityRendering.phase(Section.ENTITY_GLOBAL);
	}

	@Inject(method = "renderEntities", at = @At(value = "INVOKE", target = SWAP, ordinal = 1))
	private void alacer$outlinePhase(CallbackInfo ci) {
		EntityRendering.phase(Section.ENTITY_OUTLINE);
	}

	@Inject(method = "renderEntities", at = @At(value = "INVOKE", target = SWAP, ordinal = 2))
	private void alacer$entityPhase(CallbackInfo ci) {
		EntityRendering.phase(Section.ENTITY_LOOP);
	}

	@Inject(method = "renderEntities", at = @At(value = "INVOKE", target = SWAP, ordinal = 3))
	private void alacer$blockEntityPhase(CallbackInfo ci) {
		EntityRendering.phase(Section.BLOCK_ENTITY_LOOP);
	}

	@Inject(method = "renderEntities", at = @At("RETURN"))
	private void alacer$endEntities(CallbackInfo ci) {
		EntityRendering.finish();
	}

	@WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/WorldChunk;"))
	private WorldChunk alacer$chunkLookup(ClientWorld world, BlockPos origin, Operation<WorldChunk> original, @Local WorldRenderer.RenderChunkInfo info) {
		return EntityRendering.lookup(world, origin, info.chunk, original);
	}
}
