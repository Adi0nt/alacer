package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.adiont.alacer.render.ParticleMovementCensus;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/**
 * Session 25 region marks in Entity.move. Absent unless the census gates and profiler.particleMovementRegions are
 * true. Every handler returns the value, receiver or argument it was given; none cancels, replaces or reorders a call.
 * Injector forms that pass no CallbackInfo keep the marks themselves allocation-free.
 */
@Mixin(Entity.class)
public abstract class EntityParticleMovementRegionsMixin {

	@Shadow private Box shape;

	// The second profiler push is "rest", reached after clipping, the three publications and any step trial.
	@ModifyArg(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V"))
	private String alacer$regionRest(String section) {
		ParticleMovementCensus.regionRest((Entity) (Object) this);
		return section;
	}

	// Ordinal 1 reads the block below an air block under the entity (the fence/wall/gate check).
	@ModifyExpressionValue(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"))
	private BlockState alacer$regionSecondUnderRead(BlockState state) {
		ParticleMovementCensus.regionSecondUnderRead((Entity) (Object) this);
		return state;
	}

	@ModifyArg(method = "move(DDD)V", require = 1, allow = 1, index = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;beforeCollision(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)V"))
	private Entity alacer$regionVerticalCallback(Entity entity) {
		ParticleMovementCensus.regionVerticalCallback((Entity) (Object) this);
		return entity;
	}

	@ModifyReceiver(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;checkBlockCollisions()V"))
	private Entity alacer$regionBlockCollisionsBegin(Entity receiver) {
		ParticleMovementCensus.regionBlockCollisionsBegin((Entity) (Object) this, this.shape);
		return receiver;
	}

	@ModifyReceiver(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInWaterOrRain()Z"))
	private Entity alacer$regionBlockCollisionsEnd(Entity receiver) {
		ParticleMovementCensus.regionBlockCollisionsEnd((Entity) (Object) this);
		return receiver;
	}

	@ModifyExpressionValue(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInWaterOrRain()Z"))
	private boolean alacer$regionWaterEnd(boolean wet) {
		ParticleMovementCensus.regionWaterEnd((Entity) (Object) this);
		return wet;
	}

	@ModifyArg(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;containsFireSource(Lnet/minecraft/util/math/Box;)Z"))
	private Box alacer$regionFireBounds(Box bounds) {
		ParticleMovementCensus.regionFireBounds((Entity) (Object) this, bounds);
		return bounds;
	}

	@ModifyExpressionValue(method = "move(DDD)V", require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;containsFireSource(Lnet/minecraft/util/math/Box;)Z"))
	private boolean alacer$regionFireResult(boolean found) {
		ParticleMovementCensus.regionFireResult((Entity) (Object) this, found);
		return found;
	}
}
