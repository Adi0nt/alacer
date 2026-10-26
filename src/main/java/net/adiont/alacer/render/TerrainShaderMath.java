package net.adiont.alacer.render;

/** CPU reference for the experimental shader; native driver rounding still needs qualification. */
final class TerrainShaderMath {
	static final float SCALE = 1.000001F;
	static final float OFFSET = 8.0F * SCALE - 8.0F;
	static final int DEPTH = 0, RADIAL = 1, SIGNED_DEPTH = 2;
	static final String EXPANSION = "    localPos.xyz = localPos.xyz * " + SCALE
		+ " + vec3(" + Double.toString(OFFSET) + ");\n";
	static final String FOG = "    gl_FogFragCoord = u_FogDistanceMode == 1 ? length(eyePos.xyz)"
		+ " : (u_FogDistanceMode == 2 ? eyePos.z : abs(eyePos.z));\n";

	private TerrainShaderMath() { }
	static float expand(float position) { return position * SCALE + OFFSET; }
	static int fogMode(int mode) {
		return switch (mode) {
			case 0x855B -> RADIAL; // GL_EYE_RADIAL_NV
			case 0x2502 -> SIGNED_DEPTH; // GL_EYE_PLANE
			default -> DEPTH;
		};
	}
	static float fogCoordinate(float x, float y, float z, int mode) {
		return switch (mode) {
			case RADIAL -> (float) Math.sqrt(x * x + y * y + z * z);
			case SIGNED_DEPTH -> z;
			default -> Math.abs(z);
		};
	}
}
