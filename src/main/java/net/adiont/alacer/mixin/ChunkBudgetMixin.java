package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ChunkScheduling;
import net.minecraft.client.render.GameRenderer;

/** renderWorld passes this deadline only to WorldRenderer.compileChunksUntil (uploads and rebuild scheduling). */
@Mixin(GameRenderer.class)
public class ChunkBudgetMixin {
	@ModifyArg(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJ)V"), index = 1)
	private long alacer$chunkBudget(long deadline) {
		if (Alacer.get() == null) {
			return deadline;
		}
		int choice = Alacer.get().config().chunkUpdateBudget;
		return choice == ChunkScheduling.VANILLA_BUDGET ? deadline : ChunkScheduling.deadline(deadline, System.nanoTime(), choice);
	}
}
