package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.RenderDistances;
import net.minecraft.entity.Entity;

/** Every entity's own distance rule still decides; only the camera distance it is given is rescaled. */
@Mixin(Entity.class)
public class EntityDistanceMixin {
	@ModifyArg(method = "shouldRender(DDD)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;shouldRender(D)Z"))
	private double alacer$entityDistance(double squaredDistance) {
		return RenderDistances.entitySquaredDistance(squaredDistance, Alacer.get().config().entityDistance);
	}
}
