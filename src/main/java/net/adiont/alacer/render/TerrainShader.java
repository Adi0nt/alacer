package net.adiont.alacer.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.NVFogDistance;

import net.minecraft.client.render.platform.GLX;

import net.minecraft.util.math.BlockPos;

/**
 * Capability-gated terrain vertex shader replacing per-chunk matrix stack operations.
 * Evaluates origin translation and chunk boundary scaling directly in the vertex shader,
 * eliminating 4,500+ native glPushMatrix, glPopMatrix, and glMultMatrix calls per frame.
 * When supported (OpenGL 4.3+ / ARB_vertex_attrib_binding), uses a single static VAO and
 * decoupled attribute binding to eliminate 7,500+ vertex pointer calls per frame.
 * Retains the fixed-function fragment pipeline. Experimental: GPU rounding and rendered output
 * require native qualification before promotion.
 */
public final class TerrainShader {
	private static final Logger LOGGER = LogManager.getLogger();
	private static boolean initialized;
	private static boolean supported;
	private static boolean attribBindingSupported;
	private static int programId;
	private static int singleVaoId;
	private static int originUniformLocation;
	private static int fogUniformLocation;
	private static int instancedOriginUniformLocation = -1;
	private static final TerrainGlState DRAW_STATE = new TerrainGlState(new TerrainGlState.Driver() {
		public int program() { return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); }
		public int vao() { return GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING); }
		public int arrayBuffer() { return GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING); }
		public void program(int id) { GL20.glUseProgram(id); }
		public void vao(int id) { GL30.glBindVertexArray(id); }
		public void arrayBuffer(int id) { GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, id); }
	});
	private static double cameraX, cameraY, cameraZ;
	private static int lastOriginX = Integer.MIN_VALUE;
	private static int lastOriginY = Integer.MIN_VALUE;
	private static int lastOriginZ = Integer.MIN_VALUE;

	private static final String GENERIC_ATTRIB_VERTEX_SOURCE =
		"#version 120\n" +
		"attribute vec3 in_Position;\n" +
		"attribute vec4 in_Color;\n" +
		"attribute vec2 in_TexCoord0;\n" +
		"attribute vec2 in_TexCoord1;\n" +
		"attribute vec3 in_ChunkOrigin;\n" +
		"uniform vec3 u_ChunkOrigin;\n" +
		"uniform int u_FogDistanceMode;\n" +
		"uniform int u_UseInstancedOrigin;\n" +
		"void main() {\n" +
		"    vec4 localPos = vec4(in_Position, 1.0);\n" +
		TerrainShaderMath.EXPANSION +
		"    localPos.xyz += (u_UseInstancedOrigin == 1 ? in_ChunkOrigin : u_ChunkOrigin);\n" +
		"    gl_Position = gl_ModelViewProjectionMatrix * localPos;\n" +
		"    gl_FrontColor = in_Color;\n" +
		"    gl_TexCoord[0] = gl_TextureMatrix[0] * vec4(in_TexCoord0, 0.0, 1.0);\n" +
		"    gl_TexCoord[1] = gl_TextureMatrix[1] * vec4(in_TexCoord1, 0.0, 1.0);\n" +
		"    vec4 eyePos = gl_ModelViewMatrix * localPos;\n" +
		TerrainShaderMath.FOG +
		"}\n";

	private static final String CLASSIC_VERTEX_SOURCE =
		"#version 120\n" +
		"uniform vec3 u_ChunkOrigin;\n" +
		"uniform int u_FogDistanceMode;\n" +
		"void main() {\n" +
		"    vec4 localPos = gl_Vertex;\n" +
		TerrainShaderMath.EXPANSION +
		"    localPos.xyz += u_ChunkOrigin;\n" +
		"    gl_Position = gl_ModelViewProjectionMatrix * localPos;\n" +
		"    gl_FrontColor = gl_Color;\n" +
		"    gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n" +
		"    gl_TexCoord[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n" +
		"    vec4 eyePos = gl_ModelViewMatrix * localPos;\n" +
		TerrainShaderMath.FOG +
		"}\n";

	private TerrainShader() { }

	public static boolean isActive() {
		return ModernGl.pipelineActive() && ensureInitialized();
	}

	public static boolean isAttribBindingActive() {
		return attribBindingSupported;
	}

	public static boolean initializedAndSupported() { return initialized && supported; }

	public static boolean ensureInitialized() {
		if (initialized) return supported;
		initialized = true;
		if (!GLContext.getCapabilities().OpenGL20) {
			LOGGER.warn("Terrain shader pipeline unavailable: OpenGL 2.0 required");
			return false;
		}
		boolean decoupled = (GLContext.getCapabilities().OpenGL43
			|| GLContext.getCapabilities().GL_ARB_vertex_attrib_binding) && GLContext.getCapabilities().OpenGL30;
		if (decoupled && tryInitialize(true)) return supported = true;
		if (tryInitialize(false)) return supported = true;
		// Cache failure until lifecycle cleanup, rather than retrying compilation every draw.
		LOGGER.warn("Terrain shaders unavailable; retaining vanilla terrain rendering");
		return false;
	}

	private static boolean tryInitialize(boolean decoupled) {
		int shader = 0, program = 0, vao = 0;
		boolean installed = false;
		try {
			shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(shader, decoupled ? GENERIC_ATTRIB_VERTEX_SOURCE : CLASSIC_VERTEX_SOURCE);
			GL20.glCompileShader(shader);
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
				throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
			program = GL20.glCreateProgram();
			GL20.glAttachShader(program, shader);
			if (decoupled) {
				GL20.glBindAttribLocation(program, 0, "in_Position");
				GL20.glBindAttribLocation(program, 1, "in_Color");
				GL20.glBindAttribLocation(program, 2, "in_TexCoord0");
				GL20.glBindAttribLocation(program, 3, "in_TexCoord1");
			}
			GL20.glLinkProgram(program);
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
				throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
			int origin = GL20.glGetUniformLocation(program, "u_ChunkOrigin");
			int fog = GL20.glGetUniformLocation(program, "u_FogDistanceMode");
			if (origin < 0 || fog < 0) throw new IllegalStateException("Missing terrain shader uniform");
			instancedOriginUniformLocation = GL20.glGetUniformLocation(program, "u_UseInstancedOrigin");
			if (decoupled) vao = createVao();
			programId = program;
			singleVaoId = vao;
			originUniformLocation = origin;
			fogUniformLocation = fog;
			attribBindingSupported = decoupled;
			installed = true;
			LOGGER.info("Terrain shader initialized: decoupled={}, program={}, vao={}", decoupled, program, vao);
			return true;
		} catch (RuntimeException | LinkageError failure) {
			LOGGER.warn("Terrain shader initialization failed (decoupled={}); trying fallback", decoupled, failure);
			return false;
		} finally {
			if (shader != 0) GL20.glDeleteShader(shader);
			if (!installed) {
				if (vao != 0) GL30.glDeleteVertexArrays(vao);
				if (program != 0) GL20.glDeleteProgram(program);
			}
		}
	}

	private static int createVao() {
		int previous = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		int vao = GL30.glGenVertexArrays();
		boolean configured = false;
		try {
			try {
				GL30.glBindVertexArray(vao);
				for (int attribute = 0; attribute < 4; attribute++) {
					GL20.glEnableVertexAttribArray(attribute);
					ARBVertexAttribBinding.glVertexAttribBinding(attribute, 0);
				}
				ARBVertexAttribBinding.glVertexAttribFormat(0, 3, GL11.GL_FLOAT, false, 0);
				ARBVertexAttribBinding.glVertexAttribFormat(1, 4, GL11.GL_UNSIGNED_BYTE, true, 12);
				ARBVertexAttribBinding.glVertexAttribFormat(2, 2, GL11.GL_FLOAT, false, 16);
				ARBVertexAttribBinding.glVertexAttribFormat(3, 2, GL11.GL_SHORT, false, 24);

				// Instanced attribute 4: in_ChunkOrigin on binding index 1 (divisor 1)
				GL20.glEnableVertexAttribArray(4);
				ARBVertexAttribBinding.glVertexAttribBinding(4, 1);
				ARBVertexAttribBinding.glVertexAttribFormat(4, 3, GL11.GL_FLOAT, false, 0);
				ARBVertexAttribBinding.glVertexBindingDivisor(1, 1);

				int error = GL11.glGetError();
				if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("Terrain VAO setup GL error " + error);
			} finally {
				GL30.glBindVertexArray(previous);
			}
			configured = true;
			return vao;
		} finally {
			if (!configured) GL30.glDeleteVertexArrays(vao);
		}
	}

	public static void begin(double x, double y, double z) {
		cameraX = x;
		cameraY = y;
		cameraZ = z;
		lastOriginX = Integer.MIN_VALUE;
		lastOriginY = Integer.MIN_VALUE;
		lastOriginZ = Integer.MIN_VALUE;
		// Classic pointers retain vanilla's current VAO and client-array enable state.
		DRAW_STATE.begin(programId, singleVaoId, attribBindingSupported);
		try {
			int mode = GLContext.getCapabilities().GL_NV_fog_distance
				? TerrainShaderMath.fogMode(GL11.glGetInteger(NVFogDistance.GL_FOG_DISTANCE_MODE_NV))
				: TerrainShaderMath.DEPTH;
			GL20.glUniform1i(fogUniformLocation, mode);
			setInstancedOrigin(false);
		} catch (RuntimeException | Error failure) {
			try { end(); } catch (RuntimeException | Error restore) { failure.addSuppressed(restore); }
			throw failure;
		}
	}

	public static void setOrigin(BlockPos pos) {
		setOrigin(pos.getX(), pos.getY(), pos.getZ());
	}

	public static void setOrigin(int blockX, int blockY, int blockZ) {
		if (blockX == lastOriginX && blockY == lastOriginY && blockZ == lastOriginZ) {
			return;
		}
		lastOriginX = blockX;
		lastOriginY = blockY;
		lastOriginZ = blockZ;
		float relX = (float) (blockX - cameraX);
		float relY = (float) (blockY - cameraY);
		float relZ = (float) (blockZ - cameraZ);
		GL20.glUniform3f(originUniformLocation, relX, relY, relZ);
	}

	public static void setInstancedOrigin(boolean enabled) {
		if (instancedOriginUniformLocation >= 0) {
			GL20.glUniform1i(instancedOriginUniformLocation, enabled ? 1 : 0);
		}
	}

	public static void end() { DRAW_STATE.end(); }

	public static void cleanup() {
		end();
		if (singleVaoId != 0) {
			GL30.glDeleteVertexArrays(singleVaoId);
			singleVaoId = 0;
		}
		if (programId != 0) {
			GL20.glDeleteProgram(programId);
			programId = 0;
		}
		instancedOriginUniformLocation = -1;
		initialized = false;
		supported = false;
		attribBindingSupported = false;
	}
}
