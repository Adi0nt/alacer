package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.adiont.alacer.render.ParticleLightEntry;
import net.minecraft.client.entity.particle.BlockParticle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Block-break particles' second chunk check, for their source block; see {@link ParticleLightEntry}. */
@Mixin(BlockParticle.class)
public class BlockParticleLightEntryMixin {

	@Redirect(method = "getLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isChunkLoaded(Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean alacer$isChunkLoaded(World world, BlockPos pos) {
		return ParticleLightEntry.isChunkLoaded(this, world, pos);
	}
}
