package net.adiont.alacer.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.ParticleMovementCensus;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/** Must be absent unless the census's three startup gates are true. No cancellation or replacement. */
@Mixin(Entity.class)
public class EntityParticleMovementCensusMixin {

	@Inject(method = "move(DDD)V", at = @At("HEAD"), require = 1)
	private void alacer$censusMoveHead(double dx, double dy, double dz, CallbackInfo ci) {
		ParticleMovementCensus.beginMove((Entity) (Object) this, dx, dy, dz);
	}

	@Inject(method = "move(DDD)V", at = @At("RETURN"), require = 1)
	private void alacer$censusMoveReturn(double dx, double dy, double dz, CallbackInfo ci) {
		ParticleMovementCensus.endMove((Entity) (Object) this);
	}

	// Raw getCollisions ordinals 0..2 are player sneak-edge checks. Slice makes ordinary=0, step=1.
	@ModifyExpressionValue(method = "move(DDD)V", require = 1, allow = 1,
		slice = @Slice(from = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/util/math/Box;expanded(DDD)Lnet/minecraft/util/math/Box;")),
		at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/world/World;getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
	private List<Box> alacer$censusOrdinary(List<Box> original) {
		ParticleMovementCensus.queryResult((Entity) (Object) this, original, false);
		return original;
	}

	@ModifyExpressionValue(method = "move(DDD)V", require = 1, allow = 1,
		slice = @Slice(from = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/util/math/Box;expanded(DDD)Lnet/minecraft/util/math/Box;")),
		at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/world/World;getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
	private List<Box> alacer$censusStep(List<Box> original) {
		ParticleMovementCensus.queryResult((Entity) (Object) this, original, true);
		return original;
	}
}
