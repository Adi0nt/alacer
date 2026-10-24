package net.adiont.alacer.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Capability-gated terrain vertex shader replacing per-chunk matrix stack operations.
 * Evaluates origin translation and chunk boundary scaling directly in the vertex shader,
 * eliminating 4,500+ native glPushMatrix, glPopMatrix, and glMultMatrix calls per frame.
 * Employs fixed-function fragment pipeline for bit-identical texturing, lighting, alpha test, and fog.
 */
public final class TerrainShader {
	private static final Logger LOGGER = LogManager.getLogger();
	private static boolean initialized;
	private static boolean supported;
	private static int programId;
	private static int originUniformLocation;
	private static double cameraX, cameraY, cameraZ;

	private static final String VERTEX_SOURCE =
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

	private TerrainShader() {
	}

	public static boolean isActive() {
		return ModernGl.pipelineActive() && ensureInitialized();
	}

	public static boolean ensureInitialized() {
		if (initialized) {
			return supported;
		}
		initialized = true;
		if (!GLContext.getCapabilities().OpenGL20) {
			LOGGER.warn("Terrain shader requires OpenGL 2.0+; falling back to vanilla rendering");
			supported = false;
			return false;
		}
		try {
			int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(vertexShader, VERTEX_SOURCE);
			GL20.glCompileShader(vertexShader);
			if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetShaderInfoLog(vertexShader, 2048);
				LOGGER.error("Failed to compile terrain vertex shader:\n{}", log);
				GL20.glDeleteShader(vertexShader);
				supported = false;
				return false;
			}

			programId = GL20.glCreateProgram();
			GL20.glAttachShader(programId, vertexShader);
			GL20.glLinkProgram(programId);
			GL20.glDeleteShader(vertexShader);

			if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				String log = GL20.glGetProgramInfoLog(programId, 2048);
				LOGGER.error("Failed to link terrain shader program:\n{}", log);
				GL20.glDeleteProgram(programId);
				programId = 0;
				supported = false;
				return false;
			}

			originUniformLocation = GL20.glGetUniformLocation(programId, "u_ChunkOrigin");
			if (originUniformLocation == -1) {
				LOGGER.error("Could not find u_ChunkOrigin uniform in terrain shader");
				GL20.glDeleteProgram(programId);
				programId = 0;
				supported = false;
				return false;
			}

			supported = true;
			LOGGER.info("Terrain shader pipeline initialized successfully (program={})", programId);
			return true;
		} catch (Throwable t) {
			LOGGER.error("Exception initializing terrain shader pipeline", t);
			supported = false;
			return false;
		}
	}

	public static void begin(double x, double y, double z) {
		cameraX = x;
		cameraY = y;
		cameraZ = z;
		GL20.glUseProgram(programId);
	}

	public static void setOrigin(int blockX, int blockY, int blockZ) {
		GL20.glUniform3f(
			originUniformLocation,
			(float) (blockX - cameraX),
			(float) (blockY - cameraY),
			(float) (blockZ - cameraZ)
		);
	}

	public static void end() {
		GL20.glUseProgram(0);
	}
}
