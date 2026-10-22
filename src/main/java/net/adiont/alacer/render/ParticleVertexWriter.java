package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;

/**
 * The bytes vanilla's {@code BufferBuilder} writes for each element of the particle vertex format
 * (pure, unit tested against a real {@code BufferBuilder}; NaN encodings are compared by value).
 *
 * <p>For every element, {@code BufferBuilder} looks up the element's offset in the format's
 * {@code List<Integer>}, switches on the element's type, writes it, then advances to the next
 * element through the format's element list. For the particle format the offsets and types are
 * fixed, so each method here performs exactly the primitive {@code ByteBuffer} puts of the branch
 * vanilla takes for that element type, at the element's constant offset, with vanilla's casts and
 * order. The puts are absolute, like vanilla's, so the buffer's position, limit and byte order
 * apply the same way.
 */
public final class ParticleVertexWriter {

	/** Bytes per vertex of {@code DefaultVertexFormat.PARTICLE}. */
	public static final int VERTEX_BYTES = 28;
	static final int UV0 = 12;
	static final int COLOR = 20;
	static final int LIGHT = 24;

	private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

	private ParticleVertexWriter() {
	}

	/** {@code vertex(x, y, z)}: POSITION, FLOAT x3, with the buffer's offset added in double. */
	public static void position(ByteBuffer buffer, int vertex, double x, double y, double z, double offsetX, double offsetY, double offsetZ) {
		buffer.putFloat(vertex, (float)(x + offsetX));
		buffer.putFloat(vertex + 4, (float)(y + offsetY));
		buffer.putFloat(vertex + 8, (float)(z + offsetZ));
	}

	/** {@code texture(u, v)} with doubles: UV0, FLOAT x2. */
	public static void texture(ByteBuffer buffer, int vertex, double u, double v) {
		buffer.putFloat(vertex + UV0, (float)u);
		buffer.putFloat(vertex + UV0 + 4, (float)v);
	}

	/** {@code color(r, g, b, a)} with floats: scaled and truncated to int, then UBYTE x4 by native order. */
	public static void color(ByteBuffer buffer, int vertex, float red, float green, float blue, float alpha) {
		int r = (int)(red * 255.0F);
		int g = (int)(green * 255.0F);
		int b = (int)(blue * 255.0F);
		int a = (int)(alpha * 255.0F);
		int i = vertex + COLOR;
		if (NATIVE_LITTLE_ENDIAN) {
			buffer.put(i, (byte)r);
			buffer.put(i + 1, (byte)g);
			buffer.put(i + 2, (byte)b);
			buffer.put(i + 3, (byte)a);
		} else {
			buffer.put(i, (byte)a);
			buffer.put(i + 1, (byte)b);
			buffer.put(i + 2, (byte)g);
			buffer.put(i + 3, (byte)r);
		}
	}

	/** {@code texture(u, v)} with ints (the light map coordinates): UV1, SHORT x2, v first. */
	public static void light(ByteBuffer buffer, int vertex, int u, int v) {
		buffer.putShort(vertex + LIGHT, (short)v);
		buffer.putShort(vertex + LIGHT + 2, (short)u);
	}

	/**
	 * First differing byte of an element (or whole vertex), relative to the vertex, or -1.
	 * Used only by verification and tests, never by the direct writing path. The buffers must
	 * have the same byte order and the range must contain complete particle-format elements.
	 *
	 * <p>Only two NaNs in the same FLOAT lane compare equal despite different bits. Java does
	 * not specify the NaN sign/payload selected by arithmetic or casts; even identical position
	 * expressions can produce different NaN bits at different compilation tiers. All other bits,
	 * including signed zero, infinity, color bytes and light shorts, must match exactly.
	 */
	static int firstMismatch(ByteBuffer expected, int expectedVertex, ByteBuffer actual, int actualVertex, int offset, int length) {
		for (int i = offset; i < offset + length; i++) {
			if (expected.get(expectedVertex + i) != actual.get(actualVertex + i)) {
				int lane = i & -Float.BYTES;
				if (i < COLOR && Float.isNaN(expected.getFloat(expectedVertex + lane)) && Float.isNaN(actual.getFloat(actualVertex + lane))) {
					i = lane + Float.BYTES - 1;
					continue;
				}
				return i;
			}
		}
		return -1;
	}

	/**
	 * True if the format has exactly the particle layout these methods assume: position FLOAT x3
	 * at 0, UV0 FLOAT x2 at 12, color UBYTE x4 at 20, UV1 SHORT x2 at 24, 28 bytes, no padding.
	 */
	public static boolean particleLayout(VertexFormat format) {
		return format.getElementCount() == 4 && format.getVertexSize() == VERTEX_BYTES
			&& element(format, 0, 0, VertexFormatElement.Usage.POSITION, VertexFormatElement.Type.FLOAT, 0, 3)
			&& element(format, 1, UV0, VertexFormatElement.Usage.UV, VertexFormatElement.Type.FLOAT, 0, 2)
			&& element(format, 2, COLOR, VertexFormatElement.Usage.COLOR, VertexFormatElement.Type.UBYTE, 0, 4)
			&& element(format, 3, LIGHT, VertexFormatElement.Usage.UV, VertexFormatElement.Type.SHORT, 1, 2);
	}

	private static boolean element(VertexFormat format, int index, int offset, VertexFormatElement.Usage usage,
		VertexFormatElement.Type type, int uvIndex, int count) {
		VertexFormatElement element = format.getElement(index);
		return element != null && format.getOffset(index) == offset && element.getUsage() == usage
			&& element.getType() == type && element.getIndex() == uvIndex && element.getCount() == count;
	}
}
