package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLContext;
import net.adiont.alacer.Alacer;

/** Capability-gated client-thread GL experiments. No mapped storage, scheduling or geometry change. */
public final class ModernGl {
	private static final boolean FORCE_FALLBACK = Boolean.getBoolean("alacer.modernGl.forceFallback");
	private static long namedCreates, namedUploads, uploadChecks, uploadBytes, uploadFailures;
	private static long decoupledDraws, fallbackDraws;
	private static ByteBuffer readback;
	private ModernGl() { }

	public static boolean verify() { return Alacer.get() != null && Alacer.get().config().verifyModernGl; }

	public static boolean direct() {
		return !FORCE_FALLBACK && Alacer.get() != null && (Alacer.get().config().directBufferUploads || verify())
			&& GLContext.getCapabilities().OpenGL45 && RenderCompatibility.buffers();
	}

	public static boolean pipelineActive() {
		return !FORCE_FALLBACK && Alacer.get() != null
			&& (Alacer.get().config().terrainShader || Alacer.get().config().terrainVaos || verify())
			&& RenderCompatibility.buffers();
	}

	public static void recordDecoupledDraws(long n) { decoupledDraws += n; }
	public static void recordFallbackDraws(long n) { fallbackDraws += n; }

	/** Nonzero names must be real objects for named-buffer operations, not merely glGenBuffers reservations. */
	public static int createBuffer() { namedCreates++; return GL45.glCreateBuffers(); }

	public static void upload(int id, ByteBuffer data) {
		GL45.glNamedBufferData(id, data, GL15.GL_STATIC_DRAW);
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

	public static void describe(Map<String, String> out) {
		out.put("modernGl.forcedFallback", Boolean.toString(FORCE_FALLBACK));
		out.put("modernGl.terrainShaderActive", Boolean.toString(pipelineActive()));
		out.put("modernGl.terrainShaderAttribBinding", Boolean.toString(TerrainShader.isAttribBindingActive()));
		out.put("modernGl.vaoSupported", Boolean.toString(GLContext.getCapabilities().OpenGL30));
		out.put("modernGl.dsaSupported", Boolean.toString(GLContext.getCapabilities().OpenGL45));
		out.put("modernGl.vaosCreated", "0");
		out.put("modernGl.vaosDeleted", "0");
		out.put("modernGl.vaoBinds", "0");
		out.put("modernGl.pointerSetups", "0");
		out.put("modernGl.vaoChecks", "0");
		out.put("modernGl.vaoFailures", "0");
		out.put("modernGl.decoupledDraws", Long.toString(decoupledDraws));
		out.put("modernGl.fallbackDraws", Long.toString(fallbackDraws));
		out.put("modernGl.namedCreates", Long.toString(namedCreates));
		out.put("modernGl.namedUploads", Long.toString(namedUploads));
		out.put("modernGl.uploadChecks", Long.toString(uploadChecks));
		out.put("modernGl.uploadBytesCompared", Long.toString(uploadBytes));
		out.put("modernGl.uploadFailures", Long.toString(uploadFailures));
		out.put("modernGl.contextDrops", "0");
	}
}
