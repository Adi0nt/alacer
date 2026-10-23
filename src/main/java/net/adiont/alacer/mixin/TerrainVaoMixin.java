package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.TerrainVertexArray;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.VboRenderChunkList;

/** Startup-gated. Draw order, draw calls, matrices and buffer bytes remain vanilla's. */
@Mixin(VboRenderChunkList.class)
public class TerrainVaoMixin {
	@Unique private boolean alacer$scope, alacer$reuse;
	@Unique private TerrainVertexArray alacer$array;
	@Unique private int alacer$buffer;
	@Inject(method = "render", at = @At("HEAD"))
	private void alacer$begin(BlockLayer layer, CallbackInfo ci) { this.alacer$scope = ModernGl.beginArrays(); this.alacer$array = null; }
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexBuffer;bind()V"))
	private boolean alacer$bind(VertexBuffer buffer) {
		this.alacer$reuse = false; this.alacer$array = null;
		if (!this.alacer$scope) { return true; }
		if (buffer.getClass() != VertexBuffer.class) { ModernGl.endArrays(); this.alacer$scope = false; return true; }
		ModernVertexBuffer access = (ModernVertexBuffer) buffer;
		TerrainVertexArray array = access.alacer$vertexArray();
		this.alacer$reuse = array.bind();
		if (array.blocked()) { ModernGl.endArrays(); this.alacer$scope = false; return true; }
		this.alacer$array = array; this.alacer$buffer = access.alacer$bufferId();
		if (this.alacer$reuse) { array.check(this.alacer$buffer); }
		return !this.alacer$reuse;
	}
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/VboRenderChunkList;prepareDrawState()V"))
	private boolean alacer$prepare(VboRenderChunkList owner) { return !this.alacer$reuse; }
	@Inject(method = "prepareDrawState", at = @At("RETURN"))
	private void alacer$configured(CallbackInfo ci) { if (this.alacer$array != null) { this.alacer$array.configured(this.alacer$buffer); } }
	@Inject(method = "render", at = @At("RETURN"))
	private void alacer$end(BlockLayer layer, CallbackInfo ci) { if (this.alacer$scope) { ModernGl.endArrays(); } this.alacer$array = null; }
}
