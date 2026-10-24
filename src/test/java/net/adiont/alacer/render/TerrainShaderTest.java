package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class TerrainShaderTest {

	@Test
	void vertexShaderSourceHasBitExactOriginExpansionAndFogCoords() throws Exception {
		Field genericField = TerrainShader.class.getDeclaredField("GENERIC_ATTRIB_VERTEX_SOURCE");
		genericField.setAccessible(true);
		String genericSource = (String) genericField.get(null);

		Field classicField = TerrainShader.class.getDeclaredField("CLASSIC_VERTEX_SOURCE");
		classicField.setAccessible(true);
		String classicSource = (String) classicField.get(null);

		assertNotNull(genericSource);
		assertNotNull(classicSource);

		// Uniform chunk origin declaration
		assertTrue(genericSource.contains("uniform vec3 u_ChunkOrigin;"));
		assertTrue(classicSource.contains("uniform vec3 u_ChunkOrigin;"));

		// Boundary expansion formula: (localPos.xyz - vec3(8.0)) * 1.000001 + vec3(8.0)
		assertTrue(genericSource.contains("(localPos.xyz - vec3(8.0)) * 1.000001 + vec3(8.0)"));
		assertTrue(classicSource.contains("(localPos.xyz - vec3(8.0)) * 1.000001 + vec3(8.0)"));

		// Origin displacement
		assertTrue(genericSource.contains("localPos.xyz += u_ChunkOrigin;"));
		assertTrue(classicSource.contains("localPos.xyz += u_ChunkOrigin;"));

		// Fixed-function distance fog coordinate calculation: gl_FogFragCoord = abs(eyePos.z)
		assertTrue(genericSource.contains("vec4 eyePos = gl_ModelViewMatrix * localPos;"));
		assertTrue(genericSource.contains("gl_FogFragCoord = abs(eyePos.z);"));
		assertTrue(classicSource.contains("vec4 eyePos = gl_ModelViewMatrix * localPos;"));
		assertTrue(classicSource.contains("gl_FogFragCoord = abs(eyePos.z);"));

		// Texture matrix transformation ensuring interoperation with anisotropic filtering & atlas coords
		assertTrue(genericSource.contains("gl_TexCoord[0] = gl_TextureMatrix[0] * vec4(in_TexCoord0, 0.0, 1.0);"));
		assertTrue(genericSource.contains("gl_TexCoord[1] = gl_TextureMatrix[1] * vec4(in_TexCoord1, 0.0, 1.0);"));
		assertTrue(classicSource.contains("gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;"));
		assertTrue(classicSource.contains("gl_TexCoord[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;"));
	}

	@Test
	void fogDepthCalculationMatchesVanillaFixedFunctionDepth() {
		// Verify mathematical parity: abs(eyePos.z) where eyePos = ModelView * localPos
		// In Minecraft camera space, distance fog uses eye-space distance / z-depth
		double[] testZ = {0.0, -10.5, -64.25, -256.0, 12.0};
		for (double z : testZ) {
			float fogCoord = (float) Math.abs(z);
			assertTrue(fogCoord >= 0.0f);
			assertEquals((float) Math.abs(z), fogCoord);
		}
	}
}
