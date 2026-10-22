package net.adiont.alacer.mixin;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.adiont.alacer.render.ParticleMovementCensus;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Session 25 region marks in World.getCollisions. Absent unless the census gates and profiler.particleMovementRegions
 * are true. Handlers return the given value or receiver and read only sizes and bounds; no element is inspected.
 */
@Mixin(World.class)
public class WorldParticleMovementRegionsMixin {

	private static final String GET_COLLISIONS =
		"getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;";

	// The method's first call: the query's bounds are its argument, unchanged.
	@ModifyExpressionValue(method = GET_COLLISIONS, require = 1, allow = 1,
		at = @At(value = "INVOKE", ordinal = 0, target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"))
	private ArrayList<Box> alacer$regionGatherShape(ArrayList<Box> list, @Local(argsOnly = true) Entity entity,
			@Local(argsOnly = true) Box shape) {
		ParticleMovementCensus.regionGatherShape(entity, shape);
		return list;
	}

	// After the block loop, before the grown entity-query bounds are built.
	@ModifyReceiver(method = GET_COLLISIONS, require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;grown(DDD)Lnet/minecraft/util/math/Box;"))
	private Box alacer$regionEntityQuery(Box receiver, double dx, double dy, double dz,
			@Local(argsOnly = true) Entity entity, @Local(ordinal = 0) List<Box> list) {
		ParticleMovementCensus.regionEntityQuery(entity, list);
		return receiver;
	}

	@ModifyExpressionValue(method = GET_COLLISIONS, require = 1, allow = 1,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
	private List<Entity> alacer$regionEntityResults(List<Entity> found, @Local(argsOnly = true) Entity entity) {
		ParticleMovementCensus.regionEntityResults(entity, found);
		return found;
	}
}
