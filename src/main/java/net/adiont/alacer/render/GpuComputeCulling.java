package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * GPU-driven compute shader frustum culling (Phase 7.1).
 *
 * <p>Dispatches an OpenGL 4.3 compute shader to evaluate frustum plane discriminants directly on the GPU,
 * writing a compact visibility bitmask into a Shader Storage Buffer Object (SSBO), eliminating CPU
 * visibility search overhead.
 */
public final class GpuComputeCulling {

	public static final int WORKGROUP_SIZE = 64;
	public static final int BOX_STRIDE_FLOATS = 8; // 2 vec4s: (minX, minY, minZ, pad), (maxX, maxY, maxZ, pad)
	public static final int BOX_STRIDE_BYTES = BOX_STRIDE_FLOATS * 4;

	private static final Logger LOGGER = LogManager.getLogger("Alacer/GpuComputeCulling");

	private static final String COMPUTE_SOURCE =
		"#version 430\n" +
		"layout(local_size_x = 64) in;\n" +
		"struct ChunkBox {\n" +
		"    vec4 minCorner;\n" +
		"    vec4 maxCorner;\n" +
		"};\n" +
		"layout(std430, binding = 0) readonly buffer InputBoxes {\n" +
		"    ChunkBox boxes[];\n" +
		"};\n" +
		"layout(std430, binding = 1) buffer OutputVisibility {\n" +
		"    uint visibleMask[];\n" +
		"};\n" +
		"uniform vec4 u_FrustumPlanes[6];\n" +
		"uniform uint u_TotalChunks;\n" +
		"void main() {\n" +
		"    uint id = gl_GlobalInvocationID.x;\n" +
		"    if (id >= u_TotalChunks) return;\n" +
		"    ChunkBox box = boxes[id];\n" +
		"    bool visible = true;\n" +
		"    for (int p = 0; p < 6; p++) {\n" +
		"        vec4 plane = u_FrustumPlanes[p];\n" +
		"        vec3 pCorner = vec3(\n" +
		"            plane.x >= 0.0 ? box.maxCorner.x : box.minCorner.x,\n" +
		"            plane.y >= 0.0 ? box.maxCorner.y : box.minCorner.y,\n" +
		"            plane.z >= 0.0 ? box.maxCorner.z : box.minCorner.z\n" +
		"        );\n" +
		"        if (dot(plane.xyz, pCorner) + plane.w <= 0.0) {\n" +
		"            visible = false;\n" +
		"            break;\n" +
		"        }\n" +
		"    }\n" +
		"    if (visible) {\n" +
		"        uint wordIdx = id / 32u;\n" +
		"        uint bitIdx = id % 32u;\n" +
		"        atomicOr(visibleMask[wordIdx], 1u << bitIdx);\n" +
		"    }\n" +
		"}\n";

	private static int computeProgramId;
	private static int boxSsboId;
	private static int visibilitySsboId;
	private static int planesUniformLocation;
	private static int countUniformLocation;
	private static boolean initialized;

	private static ByteBuffer boxByteBuffer;
	private static FloatBuffer boxFloatBuffer;
	private static ByteBuffer visibilityByteBuffer;
	private static IntBuffer visibilityIntBuffer;
	private static int currentCapacity;

	private static final LongAdder dispatches = new LongAdder();
	private static final LongAdder culledChunks = new LongAdder();
	private static final LongAdder passedChunks = new LongAdder();

	private GpuComputeCulling() {}

	public static boolean isSupported() {
		try {
			return GLContext.getCapabilities().OpenGL43
				|| (GLContext.getCapabilities().GL_ARB_compute_shader
					&& GLContext.getCapabilities().GL_ARB_shader_storage_buffer_object);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean isEnabled() {
		return ModernGl.pipelineActive() && isSupported();
	}

	public static synchronized boolean init() {
		if (initialized) return true;
		if (!isSupported()) return false;

		int shader = 0;
		int program = 0;
		try {
			shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
			GL20.glShaderSource(shader, COMPUTE_SOURCE);
			GL20.glCompileShader(shader);
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				LOGGER.warn("Failed to compile compute culling shader: {}", GL20.glGetShaderInfoLog(shader, 4096));
				return false;
			}

			program = GL20.glCreateProgram();
			GL20.glAttachShader(program, shader);
			GL20.glLinkProgram(program);
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				LOGGER.warn("Failed to link compute culling program: {}", GL20.glGetProgramInfoLog(program, 4096));
				return false;
			}

			computeProgramId = program;
			planesUniformLocation = GL20.glGetUniformLocation(program, "u_FrustumPlanes");
			countUniformLocation = GL20.glGetUniformLocation(program, "u_TotalChunks");

			boxSsboId = GL15.glGenBuffers();
			visibilitySsboId = GL15.glGenBuffers();
			initialized = true;
			LOGGER.info("GPU compute shader culling initialized (program={}, boxSsbo={}, visSsbo={})",
				program, boxSsboId, visibilitySsboId);
			return true;
		} catch (Throwable t) {
			LOGGER.warn("GPU compute shader initialization error", t);
			if (program != 0) GL20.glDeleteProgram(program);
			return false;
		} finally {
			if (shader != 0) GL20.glDeleteShader(shader);
		}
	}

	public static void ensureCapacity(int chunkCount) {
		if (chunkCount <= currentCapacity && boxByteBuffer != null) {
			return;
		}
		int cap = Math.max(4096, Integer.highestOneBit(chunkCount) << 1);
		boxByteBuffer = BufferUtils.createByteBuffer(cap * BOX_STRIDE_BYTES);
		boxFloatBuffer = boxByteBuffer.asFloatBuffer();

		int maskWords = (cap + 31) / 32;
		visibilityByteBuffer = BufferUtils.createByteBuffer(maskWords * 4);
		visibilityIntBuffer = visibilityByteBuffer.asIntBuffer();
		currentCapacity = cap;
	}

	public static void packBox(FloatBuffer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		buffer.put(minX).put(minY).put(minZ).put(0.0f);
		buffer.put(maxX).put(maxY).put(maxZ).put(0.0f);
	}

	public static boolean evaluateCpu(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float[][] planes) {
		for (int p = 0; p < 6; p++) {
			float[] pl = planes[p];
			float px = pl[0] >= 0.0f ? maxX : minX;
			float py = pl[1] >= 0.0f ? maxY : minY;
			float pz = pl[2] >= 0.0f ? maxZ : minZ;
			if (pl[0] * px + pl[1] * py + pl[2] * pz + pl[3] <= 0.0f) {
				return false;
			}
		}
		return true;
	}

	public static synchronized void dispatch(int chunkCount, float[] planeData) {
		if (!initialized || chunkCount <= 0) return;
		ensureCapacity(chunkCount);

		// Zero the visibility mask SSBO
		int maskWords = (chunkCount + 31) / 32;
		visibilityIntBuffer.clear();
		for (int i = 0; i < maskWords; i++) visibilityIntBuffer.put(0);
		visibilityByteBuffer.position(0).limit(maskWords * 4);

		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visibilitySsboId);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, visibilityByteBuffer, GL15.GL_STREAM_DRAW);
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, visibilitySsboId);

		// Upload bounding boxes
		boxByteBuffer.position(0).limit(chunkCount * BOX_STRIDE_BYTES);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boxSsboId);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, boxByteBuffer, GL15.GL_STREAM_DRAW);
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, boxSsboId);

		GL20.glUseProgram(computeProgramId);
		FloatBuffer planeBuf = BufferUtils.createFloatBuffer(24);
		planeBuf.put(planeData).flip();
		GL20.glUniform4(planesUniformLocation, planeBuf);
		GL30.glUniform1ui(countUniformLocation, chunkCount);

		int numGroups = (chunkCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
		GL43.glDispatchCompute(numGroups, 1, 1);
		GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

		GL20.glUseProgram(0);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
		dispatches.increment();
	}

	public static synchronized void cleanup() {
		if (computeProgramId != 0) {
			GL20.glDeleteProgram(computeProgramId);
			computeProgramId = 0;
		}
		if (boxSsboId != 0) {
			GL15.glDeleteBuffers(boxSsboId);
			boxSsboId = 0;
		}
		if (visibilitySsboId != 0) {
			GL15.glDeleteBuffers(visibilitySsboId);
			visibilitySsboId = 0;
		}
		boxByteBuffer = null;
		boxFloatBuffer = null;
		visibilityByteBuffer = null;
		visibilityIntBuffer = null;
		currentCapacity = 0;
		initialized = false;
	}

	public static long dispatches() { return dispatches.sum(); }
	public static long culledChunks() { return culledChunks.sum(); }
	public static long passedChunks() { return passedChunks.sum(); }
	public static void clearCounters() {
		dispatches.reset();
		culledChunks.reset();
		passedChunks.reset();
	}
}
