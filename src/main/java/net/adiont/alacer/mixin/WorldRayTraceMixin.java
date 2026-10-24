package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.adiont.alacer.render.FastRayTrace;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

/**
 * Mixin into {@link World} intercepting rayTrace to eliminate intermediate object allocations.
 */
@Mixin(World.class)
public abstract class WorldRayTraceMixin {

	@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "rayTrace(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/world/HitResult;")
	private HitResult alacer$fastRayTrace(Vec3d from, Vec3d to, boolean liquids, boolean ignoreEmpty, boolean lastMiss,
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<HitResult> original) {
		World world = (World) (Object) this;
		if (!FastRayTrace.isEnabled(world)) return original.call(from, to, liquids, ignoreEmpty, lastMiss);
		HitResult fast = FastRayTrace.rayTrace(world, from, to, liquids, ignoreEmpty, lastMiss);
		if (!FastRayTrace.isVerifying()) return fast;
		HitResult vanilla = original.call(from, to, liquids, ignoreEmpty, lastMiss);
		FastRayTrace.verifyResult(fast, vanilla);
		return vanilla;
	}
}
