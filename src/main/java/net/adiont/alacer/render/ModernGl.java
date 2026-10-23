package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLContext;
import net.adiont.alacer.Alacer;

/** Capability-gated client-thread GL experiments. No mapped storage, scheduling or geometry change. */
public final class ModernGl {
	private static final boolean FORCE_FALLBACK = Boolean.getBoolean("alacer.modernGl.forceFallback");
	private static long arraysCreated, arraysDeleted, arrayBinds, pointerSetups, arrayChecks, arrayFailures;
	private static long namedCreates, namedUploads, uploadChecks, uploadBytes, uploadFailures, contextDrops;
	private static ByteBuffer readback;
	private ModernGl() { }

	public static boolean verify() { return Alacer.get() != null && Alacer.get().config().verifyModernGl; }
	public static boolean direct() {
		return !FORCE_FALLBACK && Alacer.get() != null && (Alacer.get().config().directBufferUploads || verify())
			&& GLContext.getCapabilities().OpenGL45 && RenderCompatibility.buffers();
	}
	public static boolean pipelineActive() {
		return !FORCE_FALLBACK && Alacer.get() != null && (Alacer.get().config().terrainVaos || verify())
			&& RenderCompatibility.buffers();
	}

	/** Nonzero names must be real objects for named-buffer operations, not merely glGenBuffers reservations. */
	public static int createBuffer() { namedCreates++; return GL45.glCreateBuffers(); }

	public static void upload(int id, ByteBuffer data) {
		GL45.glNamedBufferData(id, data, GL15.GL_STATIC_DRAW);
		// Vanilla upload always ends with ARRAY_BUFFER=0, even if a different buffer was bound on entry.
		net.minecraft.client.render.platform.GLX.bindBuffer(net.minecraft.client.render.platform.GLX.GL_ARRAY_BUFFER, 0);
		namedUploads++;
		if (!verify()) { return; }
		int n = data.remaining();
		if (readback == null || readback.capacity() < n) {
			readback = BufferUtils.createByteBuffer(Math.max(1, n));
		}
		readback.clear().limit(n);
		if (n > 0) { GL45.glGetNamedBufferSubData(id, 0L, readback); }
		boolean equal = GL45.glGetNamedBufferParameteri(id, GL15.GL_BUFFER_SIZE) == n;
		for (int i = 0; equal && i < n; i++) { equal = readback.get(i) == data.get(data.position() + i); }
		uploadChecks++; uploadBytes += n;
		if (!equal) { uploadFailures++; throw new IllegalStateException("Named VBO upload readback differs"); }
	}

	/** Only the default compatibility VAO is handled. An external VAO keeps vanilla's path untouched. */
	public static boolean beginArrays() {
		if (FORCE_FALLBACK || Alacer.get() == null || !(Alacer.get().config().terrainVaos || verify()) || !GLContext.getCapabilities().OpenGL30) {
			return false;
		}
		return RenderCompatibility.buffers() && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == 0;
	}

	public static void endArrays() { GL30.glBindVertexArray(0); }
	static void created() { arraysCreated++; }
	static void deleted() { arraysDeleted++; }
	static void bound() { arrayBinds++; }
	static void setup() { pointerSetups++; }
	static void contextDrop() { contextDrops++; }
	static void checked(boolean equal) {
		arrayChecks++;
		if (!equal) { arrayFailures++; throw new IllegalStateException("Cached terrain VAO state differs from vanilla layout"); }
	}

	public static void describe(Map<String, String> out) {
		out.put("modernGl.forcedFallback", Boolean.toString(FORCE_FALLBACK));
		out.put("modernGl.vaoSupported", Boolean.toString(GLContext.getCapabilities().OpenGL30));
		out.put("modernGl.dsaSupported", Boolean.toString(GLContext.getCapabilities().OpenGL45));
		out.put("modernGl.vaosCreated", Long.toString(arraysCreated));
		out.put("modernGl.vaosDeleted", Long.toString(arraysDeleted));
		out.put("modernGl.vaoBinds", Long.toString(arrayBinds));
		out.put("modernGl.pointerSetups", Long.toString(pointerSetups));
		out.put("modernGl.vaoChecks", Long.toString(arrayChecks));
		out.put("modernGl.vaoFailures", Long.toString(arrayFailures));
		out.put("modernGl.namedCreates", Long.toString(namedCreates));
		out.put("modernGl.namedUploads", Long.toString(namedUploads));
		out.put("modernGl.uploadChecks", Long.toString(uploadChecks));
		out.put("modernGl.uploadBytesCompared", Long.toString(uploadBytes));
		out.put("modernGl.uploadFailures", Long.toString(uploadFailures));
		out.put("modernGl.contextDrops", Long.toString(contextDrops));
	}
}
