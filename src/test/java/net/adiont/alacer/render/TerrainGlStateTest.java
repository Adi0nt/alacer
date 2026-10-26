package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TerrainGlStateTest {
	private static final class Driver implements TerrainGlState.Driver {
		int program = 7, vao = 11, buffer = 13;
		boolean failVao, failBufferRestore;
		public int program() { return program; }
		public int vao() { return vao; }
		public int arrayBuffer() { return buffer; }
		public void program(int id) { program = id; }
		public void vao(int id) {
			if (failVao && id == 29) throw new IllegalStateException("bind failed");
			vao = id;
		}
		public void arrayBuffer(int id) {
			if (failBufferRestore) throw new IllegalStateException("buffer restore failed");
			buffer = id;
		}
	}

	@Test void restoresNonzeroIncomingBindingsAfterDrawFailure() {
		Driver driver = new Driver();
		TerrainGlState state = new TerrainGlState(driver);
		assertThrows(IllegalArgumentException.class, () -> {
			state.begin(23, 29, true);
			try {
				assertEquals(23, driver.program);
				assertEquals(29, driver.vao);
				driver.buffer = 31;
				throw new IllegalArgumentException("draw failed");
			} finally { state.end(); }
		});
		assertEquals(7, driver.program);
		assertEquals(11, driver.vao);
		assertEquals(13, driver.buffer);
		state.end();
	}

	@Test void failedBeginRollsBackBindingsAndCanBeRetried() {
		Driver driver = new Driver();
		TerrainGlState state = new TerrainGlState(driver);
		driver.failVao = true;
		assertThrows(IllegalStateException.class, () -> state.begin(23, 29, true));
		assertEquals(7, driver.program);
		assertEquals(11, driver.vao);
		driver.failVao = false;
		state.begin(23, 29, true);
		state.end();
		assertEquals(7, driver.program);
	}

	@Test void classicPathLeavesVaoUntouchedWithoutVaoSupport() {
		Driver driver = new Driver();
		TerrainGlState state = new TerrainGlState(driver);
		state.begin(23, 0, false);
		assertEquals(11, driver.vao);
		state.end();
		assertEquals(11, driver.vao);
		assertEquals(7, driver.program);
	}

	@Test void laterBindingsRestoreEvenIfBufferRestoreThrows() {
		Driver driver = new Driver();
		TerrainGlState state = new TerrainGlState(driver);
		state.begin(23, 29, true);
		driver.failBufferRestore = true;
		assertThrows(IllegalStateException.class, state::end);
		assertEquals(11, driver.vao);
		assertEquals(7, driver.program);
	}
}
