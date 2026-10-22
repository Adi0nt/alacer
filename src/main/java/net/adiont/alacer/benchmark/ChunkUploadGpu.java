package net.adiont.alacer.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;
import net.adiont.alacer.profiler.JsonWriter;

/** Sparse attribution only: timestamps OUTSIDE list compilation, around the upload drain. No flush/wait. */
public final class ChunkUploadGpu {
	public static final boolean ENABLED = Boolean.getBoolean("alacer.benchmark.chunkUploadGpu")
		&& Boolean.getBoolean("alacer.benchmark") && Boolean.getBoolean("alacer.profiler.chunkDetail");
	private static final int SLOTS = 64, ROWS = 8192, STRIDE = 32;
	private static Session session;
	private ChunkUploadGpu() { }

	public static void phase(String name) {
		if (!ENABLED) { return; }
		if (session == null && name != null) { session = new Session(); }
		if (session == null) { return; }
		session.check();
		session.phase = -1;
		if (name != null) {
			for (int i = 0; i < ChunkFlightScenario.NAMES.length; i++) {
				if (name.equals(ChunkFlightScenario.NAMES[i])) { session.phase = i; }
			}
			if (session.phase < 0) { throw new IllegalArgumentException("Unknown chunk GPU phase"); }
		}
	}

	public static void begin() {
		if (!ENABLED || session == null) { return; }
		Session s = session;
		s.check();
		s.active = -1;
		if (s.phase < 0 || (++s.calls % STRIDE) != 0) { return; }
		s.init(); s.poll();
		if (!s.supported || s.rows == ROWS) { s.dropped++; return; }
		for (int i = 0; i < SLOTS; i++) {
			if (s.owners[i] >= 0) { continue; }
			int row = s.rows++;
			s.owners[i] = row; s.active = i;
			s.data[0][row] = s.phase;
			s.data[3][row] = -1;
			ARBTimerQuery.glQueryCounter(s.queries[2*i], ARBTimerQuery.GL_TIMESTAMP);
			s.data[1][row] = System.nanoTime();
			return;
		}
		s.dropped++;
	}

	public static void end(boolean nonempty) {
		if (!ENABLED || session == null || session.active < 0) { return; }
		Session s = session;
		s.check();
		int i = s.active, row = s.owners[i];
		s.data[2][row] = System.nanoTime() - s.data[1][row];
		s.data[4][row] = nonempty ? 1 : 0;
		ARBTimerQuery.glQueryCounter(s.queries[2*i+1], ARBTimerQuery.GL_TIMESTAMP);
		s.active = -1;
	}

	public static void finish(Path dir) throws IOException {
		if (!ENABLED || session == null) { return; }
		Session s = session; s.check(); s.poll();
		int completed = 0;
		StringBuilder csv = new StringBuilder("phase,start_ns,host_ns,command_stream_ns,nonempty\n");
		for (int r = 0; r < s.rows; r++) {
			if (s.data[3][r] >= 0) { completed++; }
			csv.append(ChunkFlightScenario.NAMES[(int)s.data[0][r]]);
			for (int c = 1; c < 5; c++) { csv.append(',').append(s.data[c][r]); }
			csv.append('\n');
		}
		Files.writeString(dir.resolve("chunk-upload-gpu.csv"), csv, StandardCharsets.UTF_8);
		JsonWriter json = new JsonWriter().beginObject().field("supported", s.supported)
			.field("timestamp_bits", s.bits).field("stride", STRIDE).field("slots", SLOTS)
			.field("drain_calls", s.calls).field("issued", s.rows).field("completed", completed)
			.field("outstanding", s.rows-completed).field("dropped", s.dropped)
			.field("scope", "runTasksUntil; includes empty drains; only available query pairs read; no flush or wait; command-stream elapsed includes host emission gaps, not GPU busy time");
		Files.writeString(dir.resolve("chunk-upload-gpu.json"), json.endObject()+"\n", StandardCharsets.UTF_8);
		for (int q : s.queries) { if (q != 0) { GL15.glDeleteQueries(q); } }
		session = null;
	}

	private static final class Session {
		final Thread owner = Thread.currentThread();
		final int[] queries = new int[2*SLOTS], owners = new int[SLOTS];
		final long[][] data = new long[5][ROWS];
		int phase = -1, active = -1, rows, bits, dropped;
		long calls;
		boolean initialized, supported;
		ContextCapabilities context;
		Session() { java.util.Arrays.fill(this.owners, -1); }
		void check() {
			if (Thread.currentThread() != this.owner) { throw new IllegalStateException("Chunk GPU probe requires client thread"); }
			if (this.context != null && this.context != GLContext.getCapabilities()) { throw new IllegalStateException("Chunk GPU context changed"); }
		}
		void init() {
			if (this.initialized) { return; }
			this.initialized = true; this.context = GLContext.getCapabilities();
			if (!this.context.OpenGL33 && !this.context.GL_ARB_timer_query) { return; }
			this.bits = GL15.glGetQueryi(ARBTimerQuery.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS);
			if (this.bits < 1 || this.bits > 64) { return; }
			for (int i = 0; i < this.queries.length; i++) { this.queries[i] = GL15.glGenQueries(); }
			this.supported = true;
		}
		void poll() {
			if (!this.supported) { return; }
			check();
			for (int i = 0; i < SLOTS; i++) {
				int row = this.owners[i];
				if (row < 0 || GL15.glGetQueryObjecti(this.queries[2*i+1], GL15.GL_QUERY_RESULT_AVAILABLE) == 0
					|| GL15.glGetQueryObjecti(this.queries[2*i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) { continue; }
				long elapsed = ARBTimerQuery.glGetQueryObjectui64(this.queries[2*i+1], GL15.GL_QUERY_RESULT)
					- ARBTimerQuery.glGetQueryObjectui64(this.queries[2*i], GL15.GL_QUERY_RESULT);
				if (this.bits < 64) { elapsed &= -1L >>> (64-this.bits); }
				this.data[3][row] = elapsed; this.owners[i] = -1;
			}
		}
	}
}
