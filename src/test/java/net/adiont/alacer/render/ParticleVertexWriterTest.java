package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexFormat;

/** Direct writes against a real vanilla BufferBuilder: exact bits except for NaN encodings in FLOAT lanes. */
class ParticleVertexWriterTest {

	private static final float[] FLOATS = {0.0F, -0.0F, 1.0F, -1.0F, 0.5F, 1.0000001F, 255.0F, -255.0F, 1e-40F, -1e-40F,
		Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
		0.003921569F, 0.99999994F, 3.7e8F, -2.5e9F};
	private static final double[] OFFSETS = {0.0, -0.0, 0.25, -1e9, 1e-300, Double.NaN};
	private static final int[] INTS = {0, 1, -1, 240, 15728880, 65535, 65536, -65536, Integer.MAX_VALUE, Integer.MIN_VALUE, 0x7fff, 0x8000};

	@Test
	void particleFormatHasTheExpectedLayout() {
		assertTrue(ParticleVertexWriter.particleLayout(DefaultVertexFormat.PARTICLE));
		assertTrue(ParticleVertexWriter.particleLayout(new VertexFormat(DefaultVertexFormat.PARTICLE)));
		assertFalse(ParticleVertexWriter.particleLayout(DefaultVertexFormat.POSITION_TEX_COLOR));
		VertexFormat padded = new VertexFormat(DefaultVertexFormat.PARTICLE).addElement(DefaultVertexFormat.PADDING_ELEMENT);
		assertFalse(ParticleVertexWriter.particleLayout(padded));
		VertexFormat reordered = new VertexFormat().addElement(DefaultVertexFormat.POSITION_ELEMENT)
			.addElement(DefaultVertexFormat.COLOR_ELEMENT).addElement(DefaultVertexFormat.UV0_ELEMENT).addElement(DefaultVertexFormat.UV1_ELEMENT);
		assertFalse(ParticleVertexWriter.particleLayout(reordered));
		assertFalse(ParticleVertexWriter.particleLayout(new VertexFormat()));
	}

	@Test
	void edgeValuesMatchVanilla() {
		Random random = new Random(20);
		for (int n = 0; n < 4000; n++) {
			float x = FLOATS[random.nextInt(FLOATS.length)];
			float y = FLOATS[random.nextInt(FLOATS.length)];
			float z = FLOATS[random.nextInt(FLOATS.length)];
			double offset = OFFSETS[random.nextInt(OFFSETS.length)];
			compare(offset, -offset, offset * 0.5, x, y, z, FLOATS[random.nextInt(FLOATS.length)], FLOATS[random.nextInt(FLOATS.length)],
				FLOATS[random.nextInt(FLOATS.length)], FLOATS[random.nextInt(FLOATS.length)], FLOATS[random.nextInt(FLOATS.length)],
				FLOATS[random.nextInt(FLOATS.length)], INTS[random.nextInt(INTS.length)], INTS[random.nextInt(INTS.length)]);
		}
	}

	@Test
	void comparisonOnlyIgnoresNaNEncodingsInFloatLanes() {
		int base = ParticleVertexWriter.VERTEX_BYTES;
		for (ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
			ByteBuffer expected = ByteBuffer.allocate(base * 2).order(order);
			ByteBuffer actual = ByteBuffer.allocate(base * 3).order(order);
			// Different vertex bases, as in the live scratch-buffer comparison.
			for (int lane = 0; lane < ParticleVertexWriter.COLOR; lane += Float.BYTES) {
				expected.putInt(base + lane, 0x7fc00000);
				actual.putInt(base * 2 + lane, 0xffc12345);
			}
			assertEquals(-1, ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, 0, base));
			assertEquals(-1, ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, 0, 12));
			assertEquals(-1, ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, ParticleVertexWriter.UV0, 8));
			for (int lane = 0; lane < base; lane += Integer.BYTES) {
				// Same NaN-shaped bits in packed color/light still have to match byte for byte.
				expected.putInt(base + lane, 0x7fc00000);
				actual.putInt(base * 2 + lane, 0xffc12345);
				if (lane >= ParticleVertexWriter.COLOR) {
					assertTrue(ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4) >= 0);
				}
				for (int bits : new int[] {0, 0x80000000, 0x7f800000, 0xff800000, 0x3f800000, 1}) {
					actual.putInt(base * 2 + lane, bits);
					assertTrue(ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4) >= 0);
					expected.putInt(base + lane, bits);
					assertEquals(-1, ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4));
					actual.putInt(base * 2 + lane, 0x7fc00000);
					assertTrue(ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4) >= 0);
					expected.putInt(base + lane, 0x7fc00000);
				}
				expected.putInt(base + lane, 0);
				actual.putInt(base * 2 + lane, 0x80000000);
				assertTrue(ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4) >= 0);
				expected.putInt(base + lane, 0x7f800000);
				actual.putInt(base * 2 + lane, 0xff800000);
				assertTrue(ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, lane, 4) >= 0);
			}
			// Every single-byte corruption, including integer fields, must be detected at its byte.
			for (int i = 0; i < base; i++) {
				expected.put(base + i, (byte)0);
				actual.put(base * 2 + i, (byte)0);
			}
			for (int i = 0; i < base; i++) {
				actual.put(base * 2 + i, (byte)1);
				assertEquals(i, ParticleVertexWriter.firstMismatch(expected, base, actual, base * 2, 0, base));
				actual.put(base * 2 + i, (byte)0);
			}
		}
	}

	@Test
	void randomValuesMatchVanilla() {
		Random random = new Random(2026);
		for (int n = 0; n < 20000; n++) {
			compare(random.nextGaussian() * 100, random.nextGaussian(), 0.0,
				(float)(random.nextGaussian() * 64), (float)(random.nextGaussian() * 64), (float)(random.nextGaussian() * 64),
				random.nextFloat(), random.nextFloat(), random.nextFloat() * 1.5F, random.nextFloat() * 1.2F - 0.1F,
				random.nextFloat(), random.nextFloat(), random.nextInt(), random.nextInt(0x10000));
		}
	}

	/** A sentinel and two varied vertices written by vanilla's chain and by the writer. */
	private static void compare(double offsetX, double offsetY, double offsetZ, float x, float y, float z, float u, float v,
		float red, float green, float blue, float alpha, int lightU, int lightV) {
		BufferBuilder vanilla = new BufferBuilder(64);
		vanilla.begin(7, DefaultVertexFormat.PARTICLE);
		vanilla.offset(offsetX, offsetY, offsetZ);
		vanilla.vertex(1, 2, 3).texture(0.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.0F).texture(0, 0).nextVertex();
		vanilla.vertex(x, y, z).texture(u, v).color(red, green, blue, alpha).texture(lightU, lightV).nextVertex();
		vanilla.vertex(y, z, x).texture(v, u).color(alpha, blue, green, red).texture(lightV, lightU).nextVertex();
		vanilla.end();

		ByteBuffer direct = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder());
		int base = ParticleVertexWriter.VERTEX_BYTES;
		ParticleVertexWriter.position(direct, 0, 1, 2, 3, offsetX, offsetY, offsetZ);
		ParticleVertexWriter.texture(direct, 0, 0.0, 0.0);
		ParticleVertexWriter.color(direct, 0, 0.0F, 0.0F, 0.0F, 0.0F);
		ParticleVertexWriter.light(direct, 0, 0, 0);
		ParticleVertexWriter.position(direct, base, x, y, z, offsetX, offsetY, offsetZ);
		ParticleVertexWriter.texture(direct, base, u, v);
		ParticleVertexWriter.color(direct, base, red, green, blue, alpha);
		ParticleVertexWriter.light(direct, base, lightU, lightV);
		ParticleVertexWriter.position(direct, base * 2, y, z, x, offsetX, offsetY, offsetZ);
		ParticleVertexWriter.texture(direct, base * 2, v, u);
		ParticleVertexWriter.color(direct, base * 2, alpha, blue, green, red);
		ParticleVertexWriter.light(direct, base * 2, lightV, lightU);

		assertEquals(3, vanilla.getVertexCount());
		ByteBuffer written = vanilla.getBuffer();
		for (int vertex = 0; vertex < 3; vertex++) {
			int start = vertex * base;
			int mismatch = ParticleVertexWriter.firstMismatch(written, start, direct, start, 0, base);
			assertEquals(-1, mismatch, () -> "vertex byte " + start + ", element byte " + mismatch
				+ "; offsets=(" + offsetX + "," + offsetY + "," + offsetZ + "), xyz=(" + x + "," + y + "," + z
				+ "), uv=(" + u + "," + v + "), rgba=(" + red + "," + green + "," + blue + "," + alpha
				+ "), light=(" + lightU + "," + lightV + ")");
		}
	}
}
