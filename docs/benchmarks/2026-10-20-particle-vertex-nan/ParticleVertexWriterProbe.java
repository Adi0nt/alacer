package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** The direct writes against a real vanilla BufferBuilder's generic element path, byte for byte. */
class ParticleVertexWriterProbe {
    private static int strict, semantic, cases, edgeIteration;
    private static final java.util.Map<String, Integer> differences = new java.util.TreeMap<>();
    public static void main(String[] args) {
        int repetitions = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        System.out.println("java=" + System.getProperty("java.runtime.version") + " vm=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        ParticleVertexWriterProbe probe = new ParticleVertexWriterProbe();
        for (int r = 0; r < repetitions; r++) probe.edgeValuesMatchVanilla();
        System.out.printf("cases=%d strictMismatches=%d semanticMismatches=%d%n", cases, strict, semantic);
        System.out.println("differences=" + differences);
        if (semantic != 0) throw new AssertionError("semantic mismatch");
    }


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
			edgeIteration = n;
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
	void randomValuesMatchVanilla() {
		Random random = new Random(2026);
		for (int n = 0; n < 20000; n++) {
			compare(random.nextGaussian() * 100, random.nextGaussian(), 0.0,
				(float)(random.nextGaussian() * 64), (float)(random.nextGaussian() * 64), (float)(random.nextGaussian() * 64),
				random.nextFloat(), random.nextFloat(), random.nextFloat() * 1.5F, random.nextFloat() * 1.2F - 0.1F,
				random.nextFloat(), random.nextFloat(), random.nextInt(), random.nextInt(0x10000));
		}
	}

	/** One quad written by vanilla's chain (as Particle.render does) and by the writer, at a nonzero vertex. */
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
		byte[] expected = new byte[base * 3];
		byte[] actual = new byte[base * 3];
		for (int i = 0; i < expected.length; i++) {
			expected[i] = written.get(i);
			actual[i] = direct.get(i);
		}
		cases++;
        int different = java.util.Arrays.mismatch(expected, actual);
        if (different >= 0) {
            strict++;
            ByteBuffer e = ByteBuffer.wrap(expected).order(ByteOrder.nativeOrder());
            ByteBuffer a = ByteBuffer.wrap(actual).order(ByteOrder.nativeOrder());
            int lane = different / 4 * 4;
            String key = String.format("byte=%d expected=%08x actual=%08x", different, e.getInt(lane), a.getInt(lane));
            Integer count = differences.merge(key, 1, Integer::sum);
            if (count == 1) System.out.printf("first %s case=%d edgeIteration=%d offsetBits=(%016x,%016x,%016x) xyzBits=(%08x,%08x,%08x)%n", key, cases, edgeIteration, Double.doubleToRawLongBits(offsetX), Double.doubleToRawLongBits(offsetY), Double.doubleToRawLongBits(offsetZ), Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z));
            if (strict <= 20) System.out.printf("mismatch case=%d edgeIteration=%d byte=%d vertex=%d lane=%d expected=0x%08x actual=0x%08x offsets=(%s,%s,%s) xyz=(%s,%s,%s) uv=(%s,%s) rgba=(%s,%s,%s,%s) light=(%d,%d)%n",
                cases, edgeIteration, different, different / base, different % base / 4, e.getInt(lane), a.getInt(lane),
                Double.toHexString(offsetX), Double.toHexString(offsetY), Double.toHexString(offsetZ), Float.toHexString(x), Float.toHexString(y), Float.toHexString(z), Float.toHexString(u), Float.toHexString(v), Float.toHexString(red), Float.toHexString(green), Float.toHexString(blue), Float.toHexString(alpha), lightU, lightV);
            boolean equivalent = true;
            for (int vertex = 0; vertex < 3; vertex++) {
                for (int offsetInVertex = 0; offsetInVertex < base; offsetInVertex += 4) {
                    int at = vertex * base + offsetInVertex;
                    int eb = e.getInt(at), ab = a.getInt(at);
                    if (eb != ab && !(offsetInVertex < 20 && Float.isNaN(e.getFloat(at)) && Float.isNaN(a.getFloat(at)))) equivalent = false;
                }
            }
            if (!equivalent) semantic++;
        }
	}
}
