package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.IndependentQuality;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.particle.ParticleType;

/** Only splash particle spawning changes. Positions are still sampled, so rain sounds keep vanilla's logic. */
@Mixin(GameRenderer.class)
public class RainSplashQualityMixin {
	@ModifyExpressionValue(method = "tickRain", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fancyGraphics:Z"))
	private boolean alacer$splashes(boolean original) {
		return IndependentQuality.fancy(Alacer.get().config().rainSplashQuality, original);
	}
	@WrapWithCondition(method = "tickRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;addParticle(Lnet/minecraft/entity/particle/ParticleType;DDDDDD[I)V"), require = 2)
	private boolean alacer$hideSplashes(ClientWorld world, ParticleType type, double x, double y, double z, double vx, double vy, double vz, int[] args) {
		return Alacer.get().config().rainSplashQuality != 3;
	}
}
