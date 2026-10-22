package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleBoxes;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/** Applied only when the separate Box experiment is requested at startup. */
@Mixin(Entity.class)
public class EntityParticleBoxMixin {

	@Redirect(method = "move", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/math/Box;moved(DDD)Lnet/minecraft/util/math/Box;"))
	private Box alacer$moved(Box box, double dx, double dy, double dz) {
		return ParticleBoxes.moved((Entity) (Object) this, box, dx, dy, dz);
	}

	@Redirect(method = "move", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/math/Box;expanded(DDD)Lnet/minecraft/util/math/Box;"))
	private Box alacer$expanded(Box box, double dx, double dy, double dz) {
		return ParticleBoxes.expanded((Entity) (Object) this, box, dx, dy, dz);
	}
}
