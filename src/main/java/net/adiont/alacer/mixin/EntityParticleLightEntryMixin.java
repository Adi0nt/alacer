package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleLightEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The position copy and chunk check in front of every particle's light query in
 * {@code Entity.getLightLevel}. Logic lives in {@link ParticleLightEntry}; other entities keep
 * vanilla's calls. Plain redirects: once per particle per frame. Another mod's {@code @Redirect}
 * of the same calls would conflict; injections around them still compose. Applied only when the
 * option, its verification or a benchmark toggle of it is on at startup.
 */
@Mixin(Entity.class)
public class EntityParticleLightEntryMixin {

	@Redirect(method = "getLightLevel", at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/util/math/BlockPos;"))
	private BlockPos alacer$position(double x, double y, double z) {
		return ParticleLightEntry.position(this, ((Entity) (Object) this).world, x, y, z);
	}

	@Redirect(method = "getLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isChunkLoaded(Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean alacer$isChunkLoaded(World world, BlockPos pos) {
		return ParticleLightEntry.isChunkLoaded(this, world, pos);
	}
}
