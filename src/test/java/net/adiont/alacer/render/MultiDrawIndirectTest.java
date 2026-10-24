package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiDrawIndirectTest {

	@BeforeEach
	void setUp() {
		MultiDrawIndirect.clear();
	}

	@Test
	void commandLayoutMatchesOpenGl43Specification() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
		MultiDrawIndirect.packCommand(buffer, 400, 1, 100, 5);
		buffer.flip();

		// Check offsets: 0: count, 4: instanceCount, 8: first, 12: baseInstance
		assertEquals(400, buffer.getInt(0), "Offset 0 must be vertex count");
		assertEquals(1, buffer.getInt(4), "Offset 4 must be instanceCount (1)");
		assertEquals(100, buffer.getInt(8), "Offset 8 must be first vertex offset");
		assertEquals(5, buffer.getInt(12), "Offset 12 must be baseInstance (origin record)");
	}

	@Test
	void originPackingComputesExactCameraRelativeDeltas() {
		FloatBuffer originBuffer = ByteBuffer.allocateDirect(3 * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

		double cameraX = 100.5;
		double cameraY = 64.0;
		double cameraZ = -200.25;

		MultiDrawIndirect.packOrigin(originBuffer, 116, 64, -192, cameraX, cameraY, cameraZ);
		MultiDrawIndirect.packOrigin(originBuffer, 132, 80, -176, cameraX, cameraY, cameraZ);
		originBuffer.flip();

		assertEquals(15.5f, originBuffer.get(0), 1e-5f);
		assertEquals(0.0f, originBuffer.get(1), 1e-5f);
		assertEquals(8.25f, originBuffer.get(2), 1e-5f);

		assertEquals(31.5f, originBuffer.get(3), 1e-5f);
		assertEquals(16.0f, originBuffer.get(4), 1e-5f);
		assertEquals(24.25f, originBuffer.get(5), 1e-5f);
	}

	@Test
	void verificationRejectsMalformedCommands() {
		// Valid command
		assertTrue(MultiDrawIndirect.validateCommand(40, 1, 0, 0));

		// Non-quad vertex count (must be multiple of 4)
		assertFalse(MultiDrawIndirect.validateCommand(42, 1, 0, 0));

		// Zero or negative vertex count
		assertFalse(MultiDrawIndirect.validateCommand(0, 1, 0, 0));
		assertFalse(MultiDrawIndirect.validateCommand(-4, 1, 0, 0));

		// Non-1 instance count
		assertFalse(MultiDrawIndirect.validateCommand(40, 0, 0, 0));
		assertFalse(MultiDrawIndirect.validateCommand(40, 2, 0, 0));

		// Negative first vertex
		assertFalse(MultiDrawIndirect.validateCommand(40, 1, -1, 0));

		// Negative base instance
		assertFalse(MultiDrawIndirect.validateCommand(40, 1, 0, -1));
	}

	@Test
	void verificationRejectsNonFiniteOrigins() {
		assertTrue(MultiDrawIndirect.validateOrigin(1.0f, 2.0f, 3.0f));
		assertFalse(MultiDrawIndirect.validateOrigin(Float.NaN, 0.0f, 0.0f));
		assertFalse(MultiDrawIndirect.validateOrigin(0.0f, Float.POSITIVE_INFINITY, 0.0f));
		assertFalse(MultiDrawIndirect.validateOrigin(0.0f, 0.0f, Float.NEGATIVE_INFINITY));
	}

	@Test
	void clearResetsCounters() {
		MultiDrawIndirect.recordCommand(100);
		assertTrue(MultiDrawIndirect.commandsGenerated() > 0);

		MultiDrawIndirect.clear();
		assertEquals(0, MultiDrawIndirect.commandsGenerated());
		assertEquals(0, MultiDrawIndirect.batchesSubmitted());
		assertEquals(0, MultiDrawIndirect.verifiedCommands());
		assertEquals(0, MultiDrawIndirect.verifyMismatches());
	}
}
