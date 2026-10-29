package net.adiont.alacer.render;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.adiont.alacer.mixin.ModernVertexBufferMixin;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual handlers with captured GL operations; native mixin application is tested separately. */
class ResidentVertexBufferFallbackTest {

	@Test void fallbackBindsResidentPageAndDrawsItsNonzeroVertexRange() throws ReflectiveOperationException {
		ModernVertexBufferMixin buffer = residentBuffer();
		List<Object[]> binds = new ArrayList<>(), draws = new ArrayList<>();
		invoke(buffer, "alacer$bindStorage", new Class<?>[]{int.class, int.class, Operation.class},
			34962, 99, capture(binds));
		invoke(buffer, "alacer$drawStorage", new Class<?>[]{int.class, int.class, int.class, Operation.class},
			7, 0, 24, capture(draws));
		assertArrayEquals(new Object[]{34962, 17}, binds.getFirst(), "the individual VBO is empty while resident");
		assertArrayEquals(new Object[]{7, 32, 24}, draws.getFirst(), "draw from the slice, not the start of its page");
	}

	@Test void ordinaryStoragePreservesBindingModeRangeAndCount() throws ReflectiveOperationException {
		ModernVertexBufferMixin buffer = new ModernVertexBufferMixin();
		List<Object[]> binds = new ArrayList<>(), draws = new ArrayList<>();
		invoke(buffer, "alacer$bindStorage", new Class<?>[]{int.class, int.class, Operation.class},
			34962, 99, capture(binds));
		invoke(buffer, "alacer$drawStorage", new Class<?>[]{int.class, int.class, int.class, Operation.class},
			4, 3, 12, capture(draws));
		assertArrayEquals(new Object[]{34962, 99}, binds.getFirst());
		assertArrayEquals(new Object[]{4, 3, 12}, draws.getFirst());
	}

	@Test void residentRangePreservesAnAdditionalFirstVertexOffset() throws ReflectiveOperationException {
		List<Object[]> draws = new ArrayList<>();
		invoke(residentBuffer(), "alacer$drawStorage", new Class<?>[]{int.class, int.class, int.class, Operation.class},
			4, 4, 12, capture(draws));
		assertArrayEquals(new Object[]{4, 36, 12}, draws.getFirst());
	}

	@Test void replacementUploadDropsOldSliceBeforeOrdinaryUploadBinds() throws ReflectiveOperationException {
		ModernVertexBufferMixin buffer = residentBuffer();
		CallbackInfo callback = new CallbackInfo("upload", true);
		// This handler fixture is not the exact vanilla class, so its replacement
		// takes the ordinary upload path after releasing the old resident storage.
		invoke(buffer, "alacer$namedUpload", new Class<?>[]{ByteBuffer.class, CallbackInfo.class},
			ByteBuffer.allocate(0), callback);
		assertNull(buffer.alacer$slice());
		assertFalse(callback.isCancelled());
		List<Object[]> binds = new ArrayList<>();
		invoke(buffer, "alacer$bindStorage", new Class<?>[]{int.class, int.class, Operation.class},
			34962, 99, capture(binds));
		assertArrayEquals(new Object[]{34962, 99}, binds.getFirst(), "do not overwrite a freed arena page");
	}

	@Test void deletionClearsResidentBindingAndIsIdempotent() throws ReflectiveOperationException {
		ModernVertexBufferMixin buffer = residentBuffer();
		for (int i = 0; i < 2; i++) {
			invoke(buffer, "alacer$delete", new Class<?>[]{CallbackInfo.class}, new CallbackInfo("delete", false));
			assertNull(buffer.alacer$slice());
		}
	}

	private static ModernVertexBufferMixin residentBuffer() {
		ModernVertexBufferMixin buffer = new ModernVertexBufferMixin();
		buffer.alacer$setSlice(new ResidentMeshArena.Slice(0, 17, 32 * ResidentMeshArena.VERTEX_STRIDE, 24));
		return buffer;
	}

	private static Operation<Void> capture(List<Object[]> calls) {
		return args -> { calls.add(args); return null; };
	}

	private static void invoke(Object buffer, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
		Method method = ModernVertexBufferMixin.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		method.invoke(buffer, args);
	}
}
