package net.adiont.alacer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.ParticleMovementCensus;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Coarse gathering scope only. Does not cancel, replace results or inspect collision elements. */
@Mixin(World.class)
public class WorldParticleMovementCensusMixin {

	@Inject(method = "getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;",
		at = @At("HEAD"), require = 1)
	private void alacer$censusGatherHead(Entity entity, Box shape, CallbackInfoReturnable<List<Box>> cir) {
		ParticleMovementCensus.beginCollision(entity);
	}

	@Inject(method = "getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;",
		at = @At("RETURN"), require = 1)
	private void alacer$censusGatherReturn(Entity entity, Box shape, CallbackInfoReturnable<List<Box>> cir) {
		ParticleMovementCensus.endCollision(entity, cir.getReturnValue());
	}
}
