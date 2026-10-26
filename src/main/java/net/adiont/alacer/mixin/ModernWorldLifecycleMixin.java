package net.adiont.alacer.mixin;

import java.util.LinkedHashMap;
import java.util.Map;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.MultiDrawIndirect;
import net.adiont.alacer.render.ResidentMeshArena;
import net.adiont.alacer.render.TerrainShader;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class ModernWorldLifecycleMixin {
	@Inject(method = "setWorld", at = @At("RETURN"))
	private void alacer$worldReceipt(ClientWorld world, CallbackInfo ci) {
		if (world == null) {
			TerrainShader.cleanup();
			ResidentMeshArena.get().clear();
			MultiDrawIndirect.cleanup();
		}
		if (ModernGl.verify()) { alacer$log(world == null ? "removed" : "loaded"); }
	}

	@Inject(method = "reload()V", at = @At("RETURN"))
	private void alacer$reloadReceipt(CallbackInfo ci) {
		TerrainShader.cleanup();
		ResidentMeshArena.get().clear();
		MultiDrawIndirect.cleanup();
		if (ModernGl.verify()) { alacer$log("reload"); }
	}

	@org.spongepowered.asm.mixin.Unique
	private static void alacer$log(String action) {
		Map<String, String> state = new LinkedHashMap<>(); ModernGl.describe(state);
		Alacer.LOGGER.info("Modern GL lifecycle {}: {}", action, state);
	}
}
