package net.adiont.alacer.mixin;

import java.nio.ByteBuffer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.ModernGl;
import net.adiont.alacer.render.ModernVertexBuffer;
import net.adiont.alacer.render.MultiDrawIndirect;
import net.adiont.alacer.render.ResidentMeshArena;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;

@Mixin(VertexBuffer.class)
public class ModernVertexBufferMixin implements ModernVertexBuffer {
	@Shadow private int id;
	@Shadow private VertexFormat format;
	@Shadow private int vertexCount;
	@Unique private ContextCapabilities alacer$context;
	@Unique private boolean alacer$created;
	@Unique private ResidentMeshArena.Slice alacer$slice;

	@Override public int alacer$bufferId() {
		return this.alacer$slice != null ? this.alacer$slice.glBufferId : this.id;
	}
	@Override public int alacer$vertexCount() { return this.vertexCount; }
	@Override public ResidentMeshArena.Slice alacer$slice() { return this.alacer$slice; }
	@Override public void alacer$setSlice(ResidentMeshArena.Slice slice) { this.alacer$slice = slice; }

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;genBuffers()I"))
	private int alacer$create(Operation<Integer> original) {
		if (((Object) this).getClass() == VertexBuffer.class && ModernGl.direct()) {
			this.alacer$created = true; this.alacer$context = GLContext.getCapabilities(); return ModernGl.createBuffer();
		}
		return original.call();
	}

	@Inject(method = "upload", at = @At("HEAD"), cancellable = true)
	private void alacer$namedUpload(ByteBuffer data, CallbackInfo ci) {
		if (((Object) this).getClass() == VertexBuffer.class && MultiDrawIndirect.isEnabled() && MultiDrawIndirect.isSupported()) {
			int vCount = data.limit() / this.format.getVertexSize();
			if (vCount > 0) {
				if (this.alacer$slice != null) {
					ResidentMeshArena.get().free(this.alacer$slice);
					this.alacer$slice = null;
				}
				ResidentMeshArena.Slice slice = ResidentMeshArena.get().allocate(vCount);
				if (slice != null) {
					ResidentMeshArena.get().upload(slice, data);
					this.alacer$slice = slice;
					this.vertexCount = vCount;
					ci.cancel();
					return;
				}
			}
		}

		if (((Object) this).getClass() == VertexBuffer.class && ModernGl.direct() && this.alacer$created && this.alacer$context == GLContext.getCapabilities()) {
			ModernGl.upload(this.id, data); this.vertexCount = data.limit() / this.format.getVertexSize(); ci.cancel();
		}
	}

	@Inject(method = "delete", at = @At("HEAD"))
	private void alacer$delete(CallbackInfo ci) {
		if (this.alacer$slice != null) {
			ResidentMeshArena.get().free(this.alacer$slice);
			this.alacer$slice = null;
		}
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void alacer$checkSubclass(CallbackInfo ci) {
		if (((Object) this).getClass() != VertexBuffer.class) {
			ModernGl.notifyForeignBuffer();
		}
	}

	@Inject(method = "upload", at = @At("RETURN"))
	private void alacer$materialized(ByteBuffer data, CallbackInfo ci) {
		this.alacer$created = true; this.alacer$context = GLContext.getCapabilities();
	}
}
