package net.adiont.alacer.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Capability-gated terrain vertex shader replacing per-chunk matrix stack operations.
 * Evaluates origin translation and chunk boundary scaling directly in the vertex shader,
 * eliminating 4,500+ native glPushMatrix, glPopMatrix, and glMultMatrix calls per frame.
 * When supported (OpenGL 4.3+ / ARB_vertex_attrib_binding), uses a single static VAO and
 * decoupled attribute binding to eliminate 7,500+ vertex pointer calls per frame.
 * Employs fixed-function fragment pipeline for bit-identical texturing, lighting, alpha test, and fog.
 */
public final class TerrainShader {
	private static final Logger LOGGER = LogManager.getLogger();
	private static boolean initialized;
	private static boolean supported;
	private static boolean attribBindingSupported;
	private static int programId;
	private static int singleVaoId;
	private static int originUniformLocation;
	private static double cameraX, cameraY, cameraZ;

	private static final String GENERIC_ATTRIB_VERTEX_SOURCE =
		"#version 120\n" +
		"attribute vec3 in_Position;\n" +
		"attribute vec4 in_Color;\n" +
		"attribute vec2 in_TexCoord0;\n" +
		"attribute vec2 in_TexCoord1;\n" +
		"uniform vec3 u_ChunkOrigin;\n" +
		"void main() {\n" +
		"    vec4 localPos = vec4(in_Position, 1.0);\n" +
		"    localPos.xyz = (localPos.xyz - vec3(8.0)) * 1.000001 + vec3(8.0);\n" +
		"    localPos.xyz += u_ChunkOrigin;\n" +
		"    gl_Position = gl_ModelViewProjectionMatrix * localPos;\n" +
		"    gl_FrontColor = in_Color;\n" +
		"    gl_TexCoord[0] = gl_TextureMatrix[0] * vec4(in_TexCoord0, 0.0, 1.0);\n" +
		"    gl_TexCoord[1] = gl_TextureMatrix[1] * vec4(in_TexCoord1, 0.0, 1.0);\n" +
		"    vec4 eyePos = gl_ModelViewMatrix * localPos;\n" +
		"    gl_FogFragCoord = abs(eyePos.z);\n" +
		"}\n";

	private static final String CLASSIC_VERTEX_SOURCE =
		"#version 120\n" +
		"uniform vec3 u_ChunkOrigin;\n" +
		"void main() {\n" +
		"    vec4 localPos = gl_Vertex;\n" +
		"    localPos.xyz = (localPos.xyz - vec3(8.0)) * 1.000001 + vec3(8.0);\n" +
		"    localPos.xyz += u_ChunkOrigin;\n" +
		"    gl_Position = gl_ModelViewProjectionMatrix * localPos;\n" +
		"    gl_FrontColor = gl_Color;\n" +
		"    gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n" +
		"    gl_TexCoord[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n" +
		"    vec4 eyePos = gl_ModelViewMatrix * localPos;\n" +
		"    gl_FogFragCoord = abs(eyePos.z);\n" +
		"}\n";

	private TerrainShader() { }

	public static boolean isActive() {
		return ModernGl.pipelineActive() && ensureInitialized();
	}

	public static boolean isAttribBindingActive() {
		return attribBindingSupported;
	}

	public static boolean ensureInitialized() {
		if (initialized) {
			return supported;
		}
		initialized = true;

		if (!GLContext.getCapabilities().OpenGL20) {
			LOGGER.warn("Terrain shader pipeline unavailable: OpenGL 2.0 required");
			return false;
		}

		boolean canAttribBind = (GLContext.getCapabilities().OpenGL43 || GLContext.getCapabilities().GL_ARB_vertex_attrib_binding)
			&& GLContext.getCapabilities().OpenGL30;

		if (canAttribBind) {
			if (tryInitDecoupledPipeline()) {
				supported = true;
				return true;
			}
			LOGGER.warn("Decoupled attribute pipeline setup failed; attempting fallback classic pipeline");
			cleanup();
			initialized = true;
		}

		if (tryInitClassicPipeline()) {
			supported = true;
			return true;
		}

		LOGGER.error("Failed to initialize any terrain shader pipeline");
		return false;
	}

	private static boolean tryInitDecoupledPipeline() {
		try {
			int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(vertexShader, GENERIC_ATTRIB_VERTEX_SOURCE);
			GL20.glCompileShader(vertexShader);

			if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetShaderInfoLog(vertexShader, 4096);
				LOGGER.error("Failed to compile decoupled terrain vertex shader:\n{}", log);
				GL20.glDeleteShader(vertexShader);
				return false;
			}

			programId = GL20.glCreateProgram();
			GL20.glAttachShader(programId, vertexShader);

			GL20.glBindAttribLocation(programId, 0, "in_Position");
			GL20.glBindAttribLocation(programId, 1, "in_Color");
			GL20.glBindAttribLocation(programId, 2, "in_TexCoord0");
			GL20.glBindAttribLocation(programId, 3, "in_TexCoord1");

			GL20.glLinkProgram(programId);
			GL20.glDeleteShader(vertexShader);

			if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetProgramInfoLog(programId, 4096);
				LOGGER.error("Failed to link decoupled terrain shader program:\n{}", log);
				GL20.glDeleteProgram(programId);
				programId = 0;
				return false;
			}

			originUniformLocation = GL20.glGetUniformLocation(programId, "u_ChunkOrigin");
			if (originUniformLocation == -1) {
				LOGGER.error("Could not find uniform u_ChunkOrigin in terrain shader");
				GL20.glDeleteProgram(programId);
				programId = 0;
				return false;
			}

			singleVaoId = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(singleVaoId);

			GL20.glEnableVertexAttribArray(0);
			GL20.glEnableVertexAttribArray(1);
			GL20.glEnableVertexAttribArray(2);
			GL20.glEnableVertexAttribArray(3);

			ARBVertexAttribBinding.glVertexAttribFormat(0, 3, GL11.GL_FLOAT, false, 0);
			ARBVertexAttribBinding.glVertexAttribBinding(0, 0);

			ARBVertexAttribBinding.glVertexAttribFormat(1, 4, GL11.GL_UNSIGNED_BYTE, true, 12);
			ARBVertexAttribBinding.glVertexAttribBinding(1, 0);

			ARBVertexAttribBinding.glVertexAttribFormat(2, 2, GL11.GL_FLOAT, false, 16);
			ARBVertexAttribBinding.glVertexAttribBinding(2, 0);

			ARBVertexAttribBinding.glVertexAttribFormat(3, 2, GL11.GL_SHORT, false, 24);
			ARBVertexAttribBinding.glVertexAttribBinding(3, 0);

			GL30.glBindVertexArray(0);

			int err = GL11.glGetError();
			if (err != GL11.GL_NO_ERROR) {
				LOGGER.error("OpenGL error {} during decoupled attribute binding setup", err);
				cleanup();
				return false;
			}

			attribBindingSupported = true;
			LOGGER.info("Decoupled attribute binding terrain pipeline initialized (program={}, vao={})", programId, singleVaoId);
			return true;
		} catch (Throwable t) {
			LOGGER.error("Exception during decoupled pipeline init", t);
			cleanup();
			return false;
		}
	}

	private static boolean tryInitClassicPipeline() {
		try {
			int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(vertexShader, CLASSIC_VERTEX_SOURCE);
			GL20.glCompileShader(vertexShader);

			if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetShaderInfoLog(vertexShader, 4096);
				LOGGER.error("Failed to compile classic terrain vertex shader:\n{}", log);
				GL20.glDeleteShader(vertexShader);
				return false;
			}

			programId = GL20.glCreateProgram();
			GL20.glAttachShader(programId, vertexShader);
			GL20.glLinkProgram(programId);
			GL20.glDeleteShader(vertexShader);

			if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetProgramInfoLog(programId, 4096);
				LOGGER.error("Failed to link classic terrain shader program:\n{}", log);
				GL20.glDeleteProgram(programId);
				programId = 0;
				return false;
			}

			originUniformLocation = GL20.glGetUniformLocation(programId, "u_ChunkOrigin");
			if (originUniformLocation == -1) {
				LOGGER.error("Could not find uniform u_ChunkOrigin in terrain shader");
				GL20.glDeleteProgram(programId);
				programId = 0;
				return false;
			}

			attribBindingSupported = false;
			LOGGER.info("Classic terrain shader pipeline initialized (program={})", programId);
			return true;
		} catch (Throwable t) {
			LOGGER.error("Exception during classic pipeline init", t);
			cleanup();
			return false;
		}
	}

	public static void begin(double x, double y, double z) {
		cameraX = x;
		cameraY = y;
		cameraZ = z;
		GL20.glUseProgram(programId);
		if (attribBindingSupported) {
			GL30.glBindVertexArray(singleVaoId);
		}
	}

	public static void setOrigin(int blockX, int blockY, int blockZ) {
		float relX = (float) (blockX - cameraX);
		float relY = (float) (blockY - cameraY);
		float relZ = (float) (blockZ - cameraZ);
		GL20.glUniform3f(originUniformLocation, relX, relY, relZ);
	}

	public static void end() {
		if (attribBindingSupported) {
			GL30.glBindVertexArray(0);
		}
		GL20.glUseProgram(0);
	}

	public static void cleanup() {
		if (singleVaoId != 0) {
			GL30.glDeleteVertexArrays(singleVaoId);
			singleVaoId = 0;
		}
		if (programId != 0) {
			GL20.glDeleteProgram(programId);
			programId = 0;
		}
		initialized = false;
		supported = false;
		attribBindingSupported = false;
	}
}
