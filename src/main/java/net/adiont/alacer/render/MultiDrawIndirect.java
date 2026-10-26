package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.entity.Entity;

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

	private static ByteBuffer commandBuffer;
	private static ByteBuffer originByteBuffer;
	private static FloatBuffer originFloatBuffer;
	private static int commandVboId;
	private static int originVboId;
	private static int currentCapacity = 0;

	private MultiDrawIndirect() {}

	private static void ensureCapacity(int needed) {
		if (needed <= currentCapacity && commandBuffer != null) {
			return;
		}
		int cap = Math.max(4096, Integer.highestOneBit(needed) << 1);
		commandBuffer = BufferUtils.createByteBuffer(cap * COMMAND_SIZE_BYTES);
		originByteBuffer = BufferUtils.createByteBuffer(cap * ORIGIN_SIZE_BYTES);
		originFloatBuffer = originByteBuffer.asFloatBuffer();
		currentCapacity = cap;

		if (commandVboId == 0) {
			commandVboId = GL15.glGenBuffers();
		}
		if (originVboId == 0) {
			originVboId = GL15.glGenBuffers();
		}
	}

	public static void cleanup() {
		if (commandVboId != 0) {
			GL15.glDeleteBuffers(commandVboId);
			commandVboId = 0;
		}
		if (originVboId != 0) {
			GL15.glDeleteBuffers(originVboId);
			originVboId = 0;
		}
		commandBuffer = null;
		originByteBuffer = null;
		originFloatBuffer = null;
		currentCapacity = 0;
	}

	public static int renderLayer(BlockLayer layer, TerrainLayerBatch.LayerQueue queue, Entity camera, double tickDelta) {
		int queueSize = queue.size();
		if (queueSize == 0) {
			return 0;
		}
		ensureCapacity(queueSize);

		double camX = camera.lastX + (camera.x - camera.lastX) * tickDelta;
		double camY = camera.lastY + (camera.y - camera.lastY) * tickDelta;
		double camZ = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;

		boolean isTranslucent = (layer == BlockLayer.TRANSLUCENT);
		FastRenderChunk[] chunks = queue.array();
		int layerOrdinal = layer.ordinal();
		boolean verify = isVerifyEnabled();

		int totalRendered = 0;
		int i = isTranslucent ? queueSize - 1 : 0;
		int step = isTranslucent ? -1 : 1;
		int count = queueSize;

		while (count > 0) {
			FastRenderChunk firstChunk = null;
			int firstBufferId = 0;
			int firstVCount = 0;
			int firstVertex = 0;

			while (count > 0) {
				FastRenderChunk c = chunks[i];
				i += step;
				count--;

				VertexBuffer vb = c.alacer$getBuffer(layerOrdinal);
				if (vb instanceof ModernVertexBuffer mvb) {
					int vc = mvb.alacer$vertexCount();
					if (vc > 0) {
						ResidentMeshArena.Slice slice = mvb.alacer$slice();
						firstChunk = c;
						firstBufferId = slice != null ? slice.glBufferId : mvb.alacer$bufferId();
						firstVertex = slice != null ? slice.firstVertex : 0;
						firstVCount = vc;
						break;
					}
				}
			}

			if (firstChunk == null) {
				break;
			}

			commandBuffer.clear();
			originFloatBuffer.clear();
			originByteBuffer.clear();

			packCommand(commandBuffer, firstVCount, 1, firstVertex, 0);
			packOrigin(originFloatBuffer, firstChunk.alacer$originX(), firstChunk.alacer$originY(), firstChunk.alacer$originZ(), camX, camY, camZ);
			int batchSize = 1;
			int batchVertices = firstVCount;

			if (verify) {
				recordVerification(validateCommand(firstVCount, 1, firstVertex, 0)
					&& validateOrigin(originFloatBuffer.get(0), originFloatBuffer.get(1), originFloatBuffer.get(2)));
			}

			while (count > 0) {
				int nextIdx = i;
				FastRenderChunk c = chunks[nextIdx];
				VertexBuffer vb = c.alacer$getBuffer(layerOrdinal);
				if (!(vb instanceof ModernVertexBuffer mvb)) {
					i += step;
					count--;
					continue;
				}
				int vc = mvb.alacer$vertexCount();
				if (vc <= 0) {
					i += step;
					count--;
					continue;
				}
				ResidentMeshArena.Slice slice = mvb.alacer$slice();
				int bufId = slice != null ? slice.glBufferId : mvb.alacer$bufferId();
				if (bufId != firstBufferId) {
					break;
				}

				i += step;
				count--;
				int fv = slice != null ? slice.firstVertex : 0;
				packCommand(commandBuffer, vc, 1, fv, batchSize);
				packOrigin(originFloatBuffer, c.alacer$originX(), c.alacer$originY(), c.alacer$originZ(), camX, camY, camZ);

				if (verify) {
					recordVerification(validateCommand(vc, 1, fv, batchSize)
						&& validateOrigin(originFloatBuffer.get(batchSize * 3), originFloatBuffer.get(batchSize * 3 + 1), originFloatBuffer.get(batchSize * 3 + 2)));
				}

				batchSize++;
				batchVertices += vc;
			}

			originByteBuffer.position(0).limit(batchSize * ORIGIN_SIZE_BYTES);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, originVboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, originByteBuffer, GL15.GL_STREAM_DRAW);
			ARBVertexAttribBinding.glBindVertexBuffer(1, originVboId, 0L, ORIGIN_SIZE_BYTES);

			commandBuffer.flip();
			GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, commandVboId);
			GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL15.GL_STREAM_DRAW);

			ARBVertexAttribBinding.glBindVertexBuffer(0, firstBufferId, 0L, ResidentMeshArena.VERTEX_STRIDE);
			TerrainShader.setInstancedOrigin(true);

			GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, 0L, batchSize, COMMAND_SIZE_BYTES);

			TerrainShader.setInstancedOrigin(false);
			recordBatch();
			recordCommand(batchVertices);
			totalRendered += batchSize;
		}

		int dummy = TerrainShader.dummyOriginVbo();
		if (dummy != 0) {
			ARBVertexAttribBinding.glBindVertexBuffer(1, dummy, 0L, 12);
		} else {
			ARBVertexAttribBinding.glBindVertexBuffer(1, 0, 0L, 0);
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		return totalRendered;
	}

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
