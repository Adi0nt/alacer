package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.living.mob.MobEntity;

/**
 * Benchmark-only stable mobs, on the client and integrated server. NoAI alone still permits physics,
 * interpolation and body-control updates. The plugin omits this hook unless benchmark.freezeMobs is requested.
 */
@Mixin(MobEntity.class)
public class BenchmarkMobFreezeMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void alacer$freezeBenchmarkMob(CallbackInfo ci) {
		MobEntity mob = (MobEntity) (Object) this;
		mob.velocityX = 0.0;
		mob.velocityY = 0.0;
		mob.velocityZ = 0.0;
		ci.cancel();
	}
}
