package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class TerrainShaderTest {
	private static String source(String field) throws Exception {
		Field value = TerrainShader.class.getDeclaredField(field);
		value.setAccessible(true);
		return (String) value.get(null);
	}

	@Test void bothShaderVariantsUseCorrectedExpansionAndFogPolicy() throws Exception {
		for (String name : new String[] {"GENERIC_ATTRIB_VERTEX_SOURCE", "CLASSIC_VERTEX_SOURCE"}) {
			String shader = source(name);
			assertTrue(shader.contains(TerrainShaderMath.EXPANSION));
			assertFalse(shader.contains("localPos.xyz - vec3(8.0)"));
			assertTrue(shader.contains("uniform int u_FogDistanceMode;"));
			assertTrue(shader.contains(TerrainShaderMath.FOG));
			assertTrue(shader.contains("gl_TextureMatrix[0] *"));
			assertTrue(shader.contains("gl_TextureMatrix[1] *"));
		}
	}

	@Test void expansionMatchesVanillaChunkMatrixBeforeCameraProjection() {
		MatrixTracker vanilla = new MatrixTracker();
		vanilla.loadIdentity();
		vanilla.translate(-8, -8, -8);
		vanilla.scale(1.000001F, 1.000001F, 1.000001F);
		vanilla.translate(8, 8, 8);
		float[] matrix = new float[16];
		vanilla.get(MatrixTracker.MODELVIEW, matrix);
		for (float position : new float[] {0, -0.0F, 8, 16, -16, Float.MIN_VALUE,
			-Float.MIN_NORMAL, Math.nextDown(16.0F), Math.nextUp(16.0F), -30_000_000, 30_000_000}) {
			float expected = matrix[0] * position + matrix[12];
			assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(TerrainShaderMath.expand(position)));
		}
		// Regression: the old expression translated the origin in the opposite direction.
		assertTrue(TerrainShaderMath.expand(0) > 0);
		assertTrue((0 - 8) * 1.000001F + 8 < 0);
	}

	@Test void radialAndPlanarFogModesHaveDistinctCoordinates() {
		assertEquals(TerrainShaderMath.RADIAL, TerrainShaderMath.fogMode(0x855B));
		assertEquals(TerrainShaderMath.SIGNED_DEPTH, TerrainShaderMath.fogMode(0x2502));
		assertEquals(TerrainShaderMath.DEPTH, TerrainShaderMath.fogMode(0x855C));
		assertEquals(5.0F, TerrainShaderMath.fogCoordinate(3, 0, -4, TerrainShaderMath.RADIAL));
		assertEquals(4.0F, TerrainShaderMath.fogCoordinate(3, 0, -4, TerrainShaderMath.DEPTH));
		assertEquals(-4.0F, TerrainShaderMath.fogCoordinate(3, 0, -4, TerrainShaderMath.SIGNED_DEPTH));
	}
}
