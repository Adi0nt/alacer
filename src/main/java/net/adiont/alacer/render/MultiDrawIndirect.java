package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;

/**
 * Multi-Draw Indirect (MDI) submission pipeline (Phase 6.1).
 *
 * <p>Consolidates visible chunk draw calls for each render layer into indirect draw command buffers
 * executed via {@code GL43.glMultiDrawArraysIndirect}. Replaces ~950 separate draw calls with 3-4 batch
 * submissions per frame, eliminating client-side driver draw loop overhead.
 *
 * <p>Command format matches the OpenGL 4.3 DrawArraysIndirectCommand struct (16 bytes):
 * <ul>
 *   <li>{@code count} (uint32, offset 0): vertex count for the chunk quad mesh.</li>
 *   <li>{@code instanceCount} (uint32, offset 4): 1 instance.</li>
 *   <li>{@code first} (uint32, offset 8): starting vertex index in the resident arena page.</li>
 *   <li>{@code baseInstance} (uint32, offset 12): origin record index in the instanced origin buffer.</li>
 * </ul>
 */
public final class MultiDrawIndirect {

	public static final int COMMAND_SIZE_BYTES = 16;
	public static final int ORIGIN_SIZE_BYTES = 12; // 3 floats: x, y, z

	private static final Logger LOGGER = LogManager.getLogger("Alacer/MultiDrawIndirect");

	private static final LongAdder commandsGenerated = new LongAdder();
	private static final LongAdder batchesSubmitted = new LongAdder();
	private static final LongAdder verifiedCommands = new LongAdder();
	private static final LongAdder verifyMismatches = new LongAdder();

	private MultiDrawIndirect() {}

	public static boolean isEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return false;
		}
		AlacerConfig config = alacer.config();
		return config != null && config.multiDrawIndirect;
	}

	public static boolean isVerifyEnabled() {
		Alacer alacer = Alacer.get();
		if (alacer == null) {
			return false;
		}
		AlacerConfig config = alacer.config();
		return config != null && config.verifyMultiDrawIndirect;
	}

	public static boolean isSupported() {
		try {
			return GLContext.getCapabilities().OpenGL43
				|| (GLContext.getCapabilities().GL_ARB_multi_draw_indirect && GLContext.getCapabilities().GL_ARB_base_instance);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static void packCommand(ByteBuffer buffer, int count, int instanceCount, int first, int baseInstance) {
		buffer.putInt(count);
		buffer.putInt(instanceCount);
		buffer.putInt(first);
		buffer.putInt(baseInstance);
	}

	public static void packOrigin(FloatBuffer buffer, int blockX, int blockY, int blockZ, double camX, double camY, double camZ) {
		float relX = (float) (blockX - camX);
		float relY = (float) (blockY - camY);
		float relZ = (float) (blockZ - camZ);
		buffer.put(relX);
		buffer.put(relY);
		buffer.put(relZ);
	}

	public static boolean validateCommand(int count, int instanceCount, int first, int baseInstance) {
		if (count <= 0 || (count & 3) != 0) { // must be positive multiple of 4 for quads
			return false;
		}
		if (instanceCount != 1) {
			return false;
		}
		if (first < 0) {
			return false;
		}
		if (baseInstance < 0) {
			return false;
		}
		return true;
	}

	public static boolean validateOrigin(float x, float y, float z) {
		return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
	}

	public static void recordCommand(int count) {
		commandsGenerated.increment();
	}

	public static void recordBatch() {
		batchesSubmitted.increment();
	}

	public static void recordVerification(boolean success) {
		verifiedCommands.increment();
		if (!success) {
			verifyMismatches.increment();
		}
	}

	public static void clear() {
		commandsGenerated.reset();
		batchesSubmitted.reset();
		verifiedCommands.reset();
		verifyMismatches.reset();
	}

	public static long commandsGenerated() {
		return commandsGenerated.sum();
	}

	public static long batchesSubmitted() {
		return batchesSubmitted.sum();
	}

	public static long verifiedCommands() {
		return verifiedCommands.sum();
	}

	public static long verifyMismatches() {
		return verifyMismatches.sum();
	}
}
