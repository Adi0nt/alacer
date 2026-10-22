package net.adiont.alacer.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.adiont.alacer.render.ParticleEntityQuery;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Two adjacent call sites; the original input identity carries the scalar-query decision. */
@Mixin(World.class)
public abstract class ParticleEntityQueryMixin implements ParticleEntityQuery.LoadedCheck {
	@Override @Invoker("isChunkLoadedAt")
	public abstract boolean alacer$isQueryChunkLoaded(int x, int z, boolean allowEmpty);

	@Redirect(method = "getCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;grown(DDD)Lnet/minecraft/util/math/Box;"))
	private Box alacer$grow(Box box, double dx, double dy, double dz, Entity entity, Box shape) {
		return ParticleEntityQuery.grow((World) (Object) this, entity, box, dx, dy, dz);
	}

	@Redirect(method = "getCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
	private List<Entity> alacer$query(World world, Entity entity, Box bounds, Entity caller, Box shape) {
		return ParticleEntityQuery.entities(world, entity, bounds, shape);
	}
}
