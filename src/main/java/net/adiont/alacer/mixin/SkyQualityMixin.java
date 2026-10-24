package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.adiont.alacer.Alacer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.world.WorldRenderer;

@Mixin(GameRenderer.class)
public class SkyQualityMixin {
	/** Skip the entire pass, including its paired profiler hooks and GL state changes. */
	@WrapWithCondition(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V"))
	private boolean alacer$sky(WorldRenderer renderer, float tickDelta, int pass) {
		return Alacer.get().config().skyQuality != 0;
	}
}
