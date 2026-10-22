package net.adiont.alacer.render;

import java.nio.ByteBuffer;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.ParticleBufferAccessor;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;

/**
 * Particle vertex writing (session 20, {@code render.fastParticleVertices}).
 *
 * <p>Every drawn particle emits one quad: four vertices of four elements each (position, texture
 * coordinates, color, light map coordinates), sixteen {@code BufferBuilder} calls per particle per
 * frame. For each element {@code BufferBuilder} reads the element's offset from the format's
 * {@code List<Integer>} (a bounds check and an unboxing), switches on the element's type, writes,
 * then {@code nextElement} advances the element index modulo the element count, fetches the next
 * element from the format's list and skips padding. In the particle scenario this bookkeeping was
 * 39-53% of the client thread's samples inside the particle pass, more than the light queries.
 *
 * <p>With the option on, the element calls inside {@code Particle.render} (which every
 * {@code super.render} caller reaches: smoke, flame, redstone, explosion, cloud, crit, ...) and
 * {@code BlockParticle.render} write each element directly with the puts of vanilla's branch for
 * that element type, at the element's fixed offset ({@link ParticleVertexWriter}), when:
 *
 * <ul>
 * <li>the buffer's format is the {@code DefaultVertexFormat.PARTICLE} object and that object's
 * layout was confirmed at the start of the particle pass (vanilla's {@code VertexFormat} is
 * mutable);</li>
 * <li>the buffer's element index is 0 at the vertex call, so the vertex starts at its first
 * element, and the buffer is not {@code uncolored} (whose color call writes nothing and does not
 * advance);</li>
 * <li>the call is on the client thread captured at startup.</li>
 * </ul>
 *
 * <p>The element index stays 0 through the four writes; vanilla's ends the vertex at 0 as well,
 * since the format has four elements and no padding. {@code nextVertex} (vertex count and buffer
 * growth) is vanilla's call. When a vertex call does not qualify, it and the rest of the vertex
 * take vanilla's methods: vanilla's vertex call leaves the element index at 1, which the other
 * three calls check. Particle order, each particle's render method (sizes, texture coordinates,
 * alpha, its light query), the pass, its state changes and the draw are vanilla's. Nothing is kept
 * between frames but the layout flag; no GL objects.
 *
 * <p>Verification ({@code render.fastParticleVertices.verify}) lets vanilla write every element of
 * qualifying vertices, computes the candidate's bytes for the same call into a scratch buffer of
 * the same byte order, and compares them with the bytes vanilla wrote (only NaN-to-NaN encoding
 * differences in FLOAT lanes are ignored). It also checks that every
 * verified vertex runs through all four elements in order.
 */
public final class ParticleVertices {

	private static final long SUMMARY_EVERY = 250_000_000L;
	private static final int BYTES = ParticleVertexWriter.VERTEX_BYTES;

	private static Thread owner;
	/** {@code DefaultVertexFormat.PARTICLE} had the expected layout at the start of the latest pass. */
	private static boolean layout;
	private static boolean layoutWarned;

	// Verification state; client thread only.
	private static final ByteBuffer EXPECTED = ByteBuffer.allocate(BYTES);
	private static int verifyVertex = -1;
	private static int verifyNext;
	private static long compared;
	private static long vertices;
	private static long incomplete;
	private static long fallbacks;
	private static long mismatches;
	private static long passes;
	private static long layoutRejected;

	private ParticleVertices() {
	}

	/** Called on the client thread at startup; only that thread writes particle vertices directly. */
	public static void init() {
		owner = Thread.currentThread();
	}

	/** Start of the particle pass: confirm the particle format's layout once per frame. */
	public static void beginPass() {
		if (Thread.currentThread() != owner) {
			return;
		}
		layout = ParticleVertexWriter.particleLayout(DefaultVertexFormat.PARTICLE);
		if (!layout && !layoutWarned) {
			layoutWarned = true;
			Alacer.LOGGER.warn("render.fastParticleVertices: the particle vertex format has been changed ({}); vanilla writes particle vertices",
				DefaultVertexFormat.PARTICLE);
		}
		if (Alacer.get().config().verifyFastParticleVertices) {
			passes++;
			if (!layout) {
				layoutRejected++;
			}
			if (verifyVertex >= 0) {
				incomplete++;
				verifyVertex = -1;
			}
		}
	}

	/** Replaces {@code buffer.vertex(x, y, z)}. */
	public static BufferBuilder position(BufferBuilder buffer, double x, double y, double z) {
		AlacerConfig config = Alacer.get().config();
		if (config.fastParticleVertices && Thread.currentThread() == owner) {
			ParticleBufferAccessor state = (ParticleBufferAccessor) buffer;
			if (layout && state.alacer$particleFormat() == DefaultVertexFormat.PARTICLE && state.alacer$particleElementIndex() == 0
				&& !state.alacer$particleUncolored()) {
				int vertex = state.alacer$particleVertexCount() * BYTES;
				if (config.verifyFastParticleVertices) {
					return verifyPosition(buffer, state, vertex, x, y, z);
				}
				ParticleVertexWriter.position(state.alacer$particleBytes(), vertex, x, y, z,
					state.alacer$particleOffsetX(), state.alacer$particleOffsetY(), state.alacer$particleOffsetZ());
				return buffer;
			}
			if (config.verifyFastParticleVertices) {
				fallbacks++;
			}
		}
		return buffer.vertex(x, y, z);
	}

	/** Replaces {@code buffer.texture(u, v)} (doubles: the texture coordinates). */
	public static BufferBuilder texture(BufferBuilder buffer, double u, double v) {
		ParticleBufferAccessor state = (ParticleBufferAccessor) buffer;
		if (direct(state)) {
			ParticleVertexWriter.texture(state.alacer$particleBytes(), state.alacer$particleVertexCount() * BYTES, u, v);
			return buffer;
		}
		if (verifying(state, 1)) {
			ParticleVertexWriter.texture(expected(state), 0, u, v);
			BufferBuilder result = buffer.texture(u, v);
			compare(state, ParticleVertexWriter.UV0, 8, "texture");
			verifyNext = 2;
			return result;
		}
		return buffer.texture(u, v);
	}

	/** Replaces {@code buffer.color(r, g, b, a)} (floats). */
	public static BufferBuilder color(BufferBuilder buffer, float red, float green, float blue, float alpha) {
		ParticleBufferAccessor state = (ParticleBufferAccessor) buffer;
		if (direct(state) && !state.alacer$particleUncolored()) {
			ParticleVertexWriter.color(state.alacer$particleBytes(), state.alacer$particleVertexCount() * BYTES, red, green, blue, alpha);
			return buffer;
		}
		if (verifying(state, 2) && !state.alacer$particleUncolored()) {
			ParticleVertexWriter.color(expected(state), 0, red, green, blue, alpha);
			BufferBuilder result = buffer.color(red, green, blue, alpha);
			compare(state, ParticleVertexWriter.COLOR, 4, "color");
			verifyNext = 3;
			return result;
		}
		return buffer.color(red, green, blue, alpha);
	}

	/** Replaces {@code buffer.texture(u, v)} (ints: the light map coordinates). */
	public static BufferBuilder light(BufferBuilder buffer, int u, int v) {
		ParticleBufferAccessor state = (ParticleBufferAccessor) buffer;
		if (direct(state)) {
			ParticleVertexWriter.light(state.alacer$particleBytes(), state.alacer$particleVertexCount() * BYTES, u, v);
			return buffer;
		}
		if (verifying(state, 3)) {
			ParticleVertexWriter.light(expected(state), 0, u, v);
			BufferBuilder result = buffer.texture(u, v);
			compare(state, ParticleVertexWriter.LIGHT, 4, "light");
			verifyVertex = -1;
			vertices++;
			if (state.alacer$particleElementIndex() != 0) {
				incomplete++;
			}
			return result;
		}
		return buffer.texture(u, v);
	}

	/**
	 * A vertex written directly: only a direct vertex call leaves the particle format at element 0
	 * for the calls after it (vanilla's vertex call moves to element 1).
	 */
	private static boolean direct(ParticleBufferAccessor state) {
		return state.alacer$particleElementIndex() == 0 && state.alacer$particleFormat() == DefaultVertexFormat.PARTICLE && layout;
	}

	// --- Verification ---

	private static BufferBuilder verifyPosition(BufferBuilder buffer, ParticleBufferAccessor state, int vertex, double x, double y, double z) {
		if (verifyVertex >= 0) {
			incomplete++;
		}
		ParticleVertexWriter.position(expected(state), 0, x, y, z,
			state.alacer$particleOffsetX(), state.alacer$particleOffsetY(), state.alacer$particleOffsetZ());
		BufferBuilder result = buffer.vertex(x, y, z);
		verifyVertex = vertex;
		compare(state, 0, 12, "position");
		verifyNext = 1;
		return result;
	}

	/** Vanilla is writing element {@code element} of the vertex under verification. */
	private static boolean verifying(ParticleBufferAccessor state, int element) {
		return verifyVertex >= 0 && verifyNext == element && state.alacer$particleElementIndex() == element
			&& state.alacer$particleFormat() == DefaultVertexFormat.PARTICLE
			&& state.alacer$particleVertexCount() * BYTES == verifyVertex && Thread.currentThread() == owner;
	}

	/** The scratch vertex, in the byte order of the buffer vanilla writes to. */
	private static ByteBuffer expected(ParticleBufferAccessor state) {
		return EXPECTED.order(state.alacer$particleBytes().order());
	}

	private static void compare(ParticleBufferAccessor state, int offset, int length, String element) {
		ByteBuffer actual = state.alacer$particleBytes();
		if (ParticleVertexWriter.firstMismatch(actual, verifyVertex, EXPECTED, 0, offset, length) >= 0) {
			mismatch(element, actual, offset, length);
		}
		if (++compared % SUMMARY_EVERY == 0) {
			Alacer.LOGGER.info("Particle vertex verification: {}", verificationSummary());
		}
	}

	private static void mismatch(String element, ByteBuffer actual, int offset, int length) {
		mismatches++;
		if (mismatches <= 10) {
			StringBuilder vanilla = new StringBuilder();
			StringBuilder candidate = new StringBuilder();
			for (int i = offset; i < offset + length; i++) {
				vanilla.append(String.format("%02x", actual.get(verifyVertex + i)));
				candidate.append(String.format("%02x", EXPECTED.get(i)));
			}
			Alacer.LOGGER.warn("Particle vertex mismatch: {} bytes {} vs vanilla {} (vertex byte {})", element, candidate, vanilla, verifyVertex);
		}
	}

	public static String verificationSummary() {
		return compared + " elements compared (" + vertices + " complete vertices, " + incomplete + " incomplete, " + fallbacks
			+ " vanilla vertices, " + layoutRejected + "/" + passes + " passes with another layout), " + mismatches + " mismatched";
	}
}
