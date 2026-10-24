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

	@Inject(method = "rayTrace(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/world/HitResult;", at = @At("HEAD"), cancellable = true)
	private void alacer$fastRayTrace(Vec3d from, Vec3d to, boolean allowLiquids, boolean ignoreBlocksWithoutCollision, boolean allowHitInStartPosition, CallbackInfoReturnable<HitResult> cir) {
		World world = (World) (Object) this;
		if (FastRayTrace.isEnabled(world)) {
			HitResult result = FastRayTrace.rayTrace(world, from, to, allowLiquids, ignoreBlocksWithoutCollision, allowHitInStartPosition);
			if (FastRayTrace.isVerifying()) {
				FastRayTrace.verify(world, from, to, allowLiquids, ignoreBlocksWithoutCollision, allowHitInStartPosition, result);
			}
			cir.setReturnValue(result);
		}
	}
}
