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

	// Resident storage also belongs to the fixed-function fallback. The vanilla ID
	// has no vertex data while a slice is resident, even if MDI is now disabled.
	@WrapOperation(method = "bind", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;bindBuffer(II)V"))
	private void alacer$bindStorage(int target, int buffer, Operation<Void> original) {
		original.call(target, this.alacer$slice != null ? this.alacer$slice.glBufferId : buffer);
	}

	@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDrawArrays(III)V"))
	private void alacer$drawStorage(int mode, int first, int count, Operation<Void> original) {
		original.call(mode, this.alacer$slice != null ? this.alacer$slice.firstVertex + first : first, count);
	}

	@Inject(method = "upload", at = @At("HEAD"), cancellable = true)
	private void alacer$namedUpload(ByteBuffer data, CallbackInfo ci) {
		// A replacement may use ordinary storage after an option/capability change,
		// an empty upload or a failed arena allocation. Never retain the old slice.
		this.alacer$releaseSlice();
		if (((Object) this).getClass() == VertexBuffer.class && MultiDrawIndirect.isEnabled() && MultiDrawIndirect.isSupported()) {
			int vCount = data.limit() / this.format.getVertexSize();
			if (vCount > 0) {
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
		this.alacer$releaseSlice();
	}

	@Unique
	private void alacer$releaseSlice() {
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
