package net.adiont.alacer.render;

/** One reusable client-thread scope; restores bindings even when a draw/bind throws. */
final class TerrainGlState {
	interface Driver {
		int program();
		int vao();
		int arrayBuffer();
		void program(int id);
		void vao(int id);
		void arrayBuffer(int id);
	}

	private final Driver driver;
	private int program, vao, arrayBuffer;
	private boolean active, usesVaos;

	TerrainGlState(Driver driver) { this.driver = driver; }

	void begin(int program, int vao, boolean usesVaos) {
		if (this.active) throw new IllegalStateException("Nested terrain shader pass");
		this.program = this.driver.program();
		this.vao = usesVaos ? this.driver.vao() : 0;
		this.arrayBuffer = this.driver.arrayBuffer();
		this.usesVaos = usesVaos;
		this.active = true;
		try {
			this.driver.program(program);
			if (usesVaos) this.driver.vao(vao);
		} catch (RuntimeException | Error failure) {
			try { end(); } catch (RuntimeException | Error restore) { failure.addSuppressed(restore); }
			throw failure;
		}
	}

	void end() {
		if (!this.active) return;
		this.active = false;
		try { this.driver.arrayBuffer(this.arrayBuffer); }
		finally {
			try { if (this.usesVaos) this.driver.vao(this.vao); }
			finally { this.driver.program(this.program); }
		}
	}
}
