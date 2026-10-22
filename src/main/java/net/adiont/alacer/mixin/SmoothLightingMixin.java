package net.adiont.alacer.mixin;

import java.util.BitSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.SmoothLighting;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

/** Leaves vanilla's neighbor sampling, vertex remapping and packed lightmap untouched. */
@Mixin(targets = "net.minecraft.client.render.block.BlockModelRenderer$AmbientOcclusionFace")
public class SmoothLightingMixin {
	@Shadow @Final private float[] brightness;

	@Inject(method = "compute", at = @At("RETURN"))
	private void alacer$shapeOcclusion(WorldView world, Block block, BlockPos pos, Direction face,
			float[] faceShape, BitSet shapeState, CallbackInfo ci) {
		AlacerConfig config = Alacer.get().config();
		SmoothLighting.apply(this.brightness, config.smoothLightingStyle, config.ambientOcclusionStrength);
	}
}
