package net.adiont.alacer.render;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

import net.adiont.alacer.profiler.JsonWriter;

/**
 * Attribution-only unlit particle pass/draw totals. No draw records or per-draw allocation.
 * Call phase(name) at capture arming, phase(null) at stop and finish(directory) before exit,
 * all on the client render thread. A non-null phase after finish starts a fresh session.
 * GL objects are created only at the first captured pass and destroyed at finish.
 */
public final class ParticleDrawDiagnostics {

	/** Command-line-only gate; must match AlacerMixinPlugin.particleDrawDiagnosticsEnabled(). */
	public static final boolean ENABLED = Boolean.getBoolean("alacer.profiler.particleDrawDiagnostics")
		&& Boolean.getBoolean("alacer.benchmark")
		&& Boolean.parseBoolean(System.getProperty("alacer.profiler.enabled", "true"));
	private static final boolean THREAD_CYCLES_ENABLED = ENABLED
		&& Boolean.getBoolean("alacer.profiler.particleThreadCycles");
	private static final int LIMIT = 128;
	private static Session session;

	private ParticleDrawDiagnostics() { }

	/** Null stops capture without discarding pending queries or their issuance phases. */
	public static void phase(String name) {
		if (!ENABLED) { return; }
		if (name != null && (session == null || session.finished)) { session = new Session(); }
		if (session == null || session.finished) { return; }
		session.checkThread();
		if (session.passDepth != 0) { throw new IllegalStateException("Particle diagnostic phase changed during render"); }
		session.current = null;
		if (name == null) { return; }
		for (int i = 0; i < session.phaseCount; i++) {
			if (session.phases[i].name.equals(name)) { session.current = session.phases[i]; return; }
		}
		if (session.phaseCount == LIMIT) { throw new IllegalStateException("Particle diagnostic exceeds 128 phases"); }
		session.current = new Totals(name);
		session.phases[session.phaseCount++] = session.current;
	}

	public static void beginPass() {
		if (!ENABLED || session == null || session.finished) { return; }
		Session s = session;
		s.checkThread();
		if (s.passDepth++ != 0) { return; } // Do not nest probes or overwrite active clocks.
		if (s.current != null) { s.initGl(); }
		s.poll(); // Polling and lazy GL initialization are outside both timed regions.
		s.pass = s.current;
		if (s.pass == null) { return; }
		if (THREAD_CYCLES_ENABLED) { s.passCycles = s.cycles(); }
		s.passAllocation = s.allocation();
		s.passCpu = s.cpu();
		s.passWall = System.nanoTime();
	}

	public static void endPass() {
		if (!ENABLED || session == null || session.finished) { return; }
		Session s = session;
		s.checkThread();
		if (s.passDepth == 0 || --s.passDepth != 0 || s.pass == null) { return; }
		long wall = System.nanoTime() - s.passWall;
		long cpu = s.cpu();
		long allocation = s.allocation();
		long cycles = THREAD_CYCLES_ENABLED ? s.cycles() : -1;
		Totals p = s.pass;
		p.passes++;
		p.passWall += wall;
		if (THREAD_CYCLES_ENABLED && s.passCycles >= 0 && cycles >= s.passCycles) {
			long delta = cycles - s.passCycles;
			if (p.passThreadCycles <= Long.MAX_VALUE - delta) {
				p.passThreadCycles += delta; p.passThreadCycleSamples++;
			}
		}
		if (s.passCpu >= 0 && cpu >= 0) { p.passCpu += cpu - s.passCpu; p.passCpuSamples++; }
		if (s.passAllocation >= 0 && allocation >= 0) {
			p.passAllocation += allocation - s.passAllocation; p.passAllocationSamples++;
		}
		s.pass = null;
	}

	public static void beginDraw() {
		if (!ENABLED || session == null || session.finished) { return; }
		Session s = session;
		if (s.passDepth != 1 || s.pass == null || s.drawDepth++ != 0) { return; }
		s.draw = s.pass;
		s.drawSlot = -1;
		if (s.gpuSupported) {
			for (int i = 0; i < LIMIT; i++) {
				if (s.owners[i] == null) {
					s.drawSlot = i;
					s.owners[i] = s.draw; // Capture phase at issue, never at result collection.
					s.draw.issued++;
					s.draw.outstanding++;
					ARBTimerQuery.glQueryCounter(s.queries[2 * i], ARBTimerQuery.GL_TIMESTAMP);
					break;
				}
			}
		}
		if (s.drawSlot < 0) { s.draw.dropped++; }
		if (THREAD_CYCLES_ENABLED) { s.drawCycles = s.cycles(); }
		s.drawAllocation = s.allocation();
		s.drawCpu = s.cpu();
		s.drawWall = System.nanoTime();
	}

	public static void endDraw() {
		if (!ENABLED || session == null || session.finished) { return; }
		Session s = session;
		if (s.passDepth != 1 || s.pass == null || s.drawDepth == 0 || --s.drawDepth != 0) { return; }
		long wall = System.nanoTime() - s.drawWall;
		long cpu = s.cpu();
		long allocation = s.allocation();
		long cycles = THREAD_CYCLES_ENABLED ? s.cycles() : -1;
		if (s.drawSlot >= 0) {
			ARBTimerQuery.glQueryCounter(s.queries[2 * s.drawSlot + 1], ARBTimerQuery.GL_TIMESTAMP);
		}
		Totals p = s.draw;
		p.draws++;
		p.drawWall += wall;
		if (THREAD_CYCLES_ENABLED && s.drawCycles >= 0 && cycles >= s.drawCycles) {
			long delta = cycles - s.drawCycles;
			if (p.drawThreadCycles <= Long.MAX_VALUE - delta) {
				p.drawThreadCycles += delta; p.drawThreadCycleSamples++;
			}
		}
		if (s.drawCpu >= 0 && cpu >= 0) { p.drawCpu += cpu - s.drawCpu; p.drawCpuSamples++; }
		if (s.drawAllocation >= 0 && allocation >= 0) {
			p.drawAllocation += allocation - s.drawAllocation; p.drawAllocationSamples++;
		}
		s.draw = null;
		s.drawSlot = -1;
	}

	/**
	 * Final availability-only poll, then delete even incomplete pairs on their owning GL thread.
	 * Incomplete counts describe the final poll, before deletion. No wait/flush is requested.
	 * Writes detached CSV and JSON only here. Calling again can retry a failed file write.
	 */
	public static void finish(Path directory) throws IOException {
		if (!ENABLED || session == null) { return; }
		Session s = session;
		s.checkThread();
		if (s.passDepth != 0 || s.drawDepth != 0) { throw new IllegalStateException("Particle diagnostic finish during render"); }
		if (!s.finished) {
			try {
				s.poll();
				s.deleteQueries();
			} finally {
				// Snapshot availability before closing; export retries never touch native state.
				if (s.threadCycles != null) {
					s.threadCyclesSupported = s.threadCycles.supported();
					s.threadCyclesAvailable = s.threadCycles.available();
					s.threadCyclesStatus = s.threadCycles.status();
					s.threadCycles.close();
				}
			}
			s.current = null;
			s.finished = true;
		}
		JsonWriter json = new JsonWriter().beginObject()
			.field("schema_version", 2).field("attribution_only", true)
			.field("scope", "ParticleManager.render (unlit), Tesselator.end")
			.field("query_pool_pairs", LIMIT).field("phase_limit", LIMIT)
			.field("cpu_supported", s.cpuSupported).field("allocation_supported", s.allocationBean != null)
			.field("gpu_supported", s.gpuSupported).field("gpu_support", s.gpuStatus)
			.field("timestamp_counter_bits", s.timestampBits)
			.field("thread_cycles_enabled", THREAD_CYCLES_ENABLED)
			.field("thread_cycles_supported", s.threadCyclesSupported)
			.field("thread_cycles_available", s.threadCyclesAvailable)
			.field("thread_cycles_status", s.threadCyclesStatus)
			.field("thread_cycles_unit", "raw_cycles")
			.field("thread_cycles_source", "QueryThreadCycleTime(GetCurrentThread)")
			.field("caveats", "Windows thread CPU is coarse: compare phase sums, not individual draws. "
				+ "CPU residuals are raw and may be negative; counters and wall clocks have different resolution "
				+ "and probe boundaries. Host wall minus client CPU includes waiting and descheduling, not GPU time. "
				+ "GPU timestamps include diagnostic counter sampling between markers and measure command-stream elapsed "
				+ "time, not queue latency or host wait. Pass totals include draw probes and query issuance; "
				+ "pool initialization and polling precede pass clocks. Thread cycles are raw CPU-dependent counts, "
				+ "never elapsed time or ns; no wall/cycle residual is defined. Cycle brackets surround existing host probes, "
				+ "exclude draw GPU query issuance and include nested draw probes in pass totals. Native probes perturb "
				+ "attribution; these runs must remain separate from frozen light profiles. Missing cycle endpoints or "
				+ "sum overflow omit that cycle sample; zero sums with zero samples mean unavailable, not zero cost. "
				+ "Missing CPU/allocation samples are counted; "
				+ "unsupported GPU draws count as dropped. Outstanding pairs are deleted after the final nonblocking poll.")
			.beginObject("phases");
		StringBuilder csv = new StringBuilder("phase,draws,draw_host_wall_ns,draw_client_cpu_ns,draw_allocation_bytes,"
			+ "draw_cpu_samples,draw_allocation_samples,draw_host_wall_minus_client_cpu_ns,gpu_ns,"
			+ "issued,completed,dropped,outstanding,passes,pass_wall_ns,pass_client_cpu_ns,pass_allocation_bytes,"
			+ "pass_cpu_samples,pass_allocation_samples,pass_host_wall_minus_client_cpu_ns,"
			+ "draw_thread_cycles,draw_thread_cycle_samples,pass_thread_cycles,pass_thread_cycle_samples,"
			+ "thread_cycles_enabled,thread_cycles_supported,thread_cycles_available,thread_cycles_status\n");
		for (int i = 0; i < s.phaseCount; i++) {
			Totals p = s.phases[i];
			json.beginObject(p.name).field("draws", p.draws)
				.field("draw_host_wall_ns", p.drawWall).field("draw_client_cpu_ns", p.drawCpu)
				.field("draw_allocation_bytes", p.drawAllocation).field("draw_cpu_samples", p.drawCpuSamples)
				.field("draw_allocation_samples", p.drawAllocationSamples);
			residual(json, "draw_host_wall_minus_client_cpu_ns", p.drawWall, p.drawCpu, p.drawCpuSamples == p.draws);
			json.field("gpu_ns", p.gpu).field("issued", p.issued).field("completed", p.completed)
				.field("dropped", p.dropped).field("outstanding", p.outstanding).field("passes", p.passes)
				.field("pass_wall_ns", p.passWall).field("pass_client_cpu_ns", p.passCpu)
				.field("pass_allocation_bytes", p.passAllocation).field("pass_cpu_samples", p.passCpuSamples)
				.field("pass_allocation_samples", p.passAllocationSamples);
			residual(json, "pass_host_wall_minus_client_cpu_ns", p.passWall, p.passCpu, p.passCpuSamples == p.passes);
			json.field("draw_thread_cycles", p.drawThreadCycles).field("draw_thread_cycle_samples", p.drawThreadCycleSamples)
				.field("pass_thread_cycles", p.passThreadCycles).field("pass_thread_cycle_samples", p.passThreadCycleSamples);
			json.endObject();
			csv.append('"').append(p.name.replace("\"", "\"\"")).append('"')
				.append(',').append(p.draws).append(',').append(p.drawWall).append(',').append(p.drawCpu)
				.append(',').append(p.drawAllocation).append(',').append(p.drawCpuSamples).append(',').append(p.drawAllocationSamples)
				.append(',').append(p.drawCpuSamples == p.draws ? Long.toString(p.drawWall - p.drawCpu) : "")
				.append(',').append(p.gpu).append(',').append(p.issued).append(',').append(p.completed)
				.append(',').append(p.dropped).append(',').append(p.outstanding).append(',').append(p.passes)
				.append(',').append(p.passWall).append(',').append(p.passCpu).append(',').append(p.passAllocation)
				.append(',').append(p.passCpuSamples).append(',').append(p.passAllocationSamples)
				.append(',').append(p.passCpuSamples == p.passes ? Long.toString(p.passWall - p.passCpu) : "")
				.append(',').append(p.drawThreadCycles).append(',').append(p.drawThreadCycleSamples)
				.append(',').append(p.passThreadCycles).append(',').append(p.passThreadCycleSamples)
				.append(',').append(THREAD_CYCLES_ENABLED).append(',').append(s.threadCyclesSupported)
				.append(',').append(s.threadCyclesAvailable).append(',').append(s.threadCyclesStatus).append('\n');
		}
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("particle-draw-diagnostics.csv"), csv, StandardCharsets.UTF_8);
		Files.writeString(directory.resolve("particle-draw-diagnostics.json"), json.endObject().endObject() + "\n", StandardCharsets.UTF_8);
	}

	private static void residual(JsonWriter json, String key, long wall, long cpu, boolean valid) {
		if (valid) { json.field(key, wall - cpu); } else { json.field(key, (String) null); }
	}

	private static final class Totals {
		final String name;
		long draws, drawWall, drawCpu, drawAllocation, drawCpuSamples, drawAllocationSamples;
		long drawThreadCycles, drawThreadCycleSamples, passThreadCycles, passThreadCycleSamples;
		long gpu, issued, completed, dropped, outstanding;
		long passes, passWall, passCpu, passAllocation, passCpuSamples, passAllocationSamples;
		Totals(String name) { this.name = name; }
	}

	private static final class Session {
		final Thread owner = Thread.currentThread();
		// No helper initialization or native resources at all in the default control.
		final ThreadCycles threadCycles;
		boolean threadCyclesSupported, threadCyclesAvailable;
		String threadCyclesStatus = "disabled";
		final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		final Totals[] phases = new Totals[LIMIT];
		final Totals[] owners = new Totals[LIMIT];
		final int[] queries = new int[2 * LIMIT];
		com.sun.management.ThreadMXBean allocationBean;
		boolean cpuSupported, glInitialized, gpuSupported, finished;
		String gpuStatus = "not_initialized";
		ContextCapabilities context;
		int timestampBits, phaseCount, passDepth, drawDepth, drawSlot = -1;
		Totals current, pass, draw;
		long passWall, passCpu, passAllocation, drawWall, drawCpu, drawAllocation;
		long passCycles, drawCycles;

		Session() {
			try {
				if (this.threads.isCurrentThreadCpuTimeSupported()) {
					if (!this.threads.isThreadCpuTimeEnabled()) { this.threads.setThreadCpuTimeEnabled(true); }
					this.cpuSupported = this.threads.isThreadCpuTimeEnabled();
				}
			} catch (UnsupportedOperationException | SecurityException ignored) { }
			try {
				if (this.threads instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
					if (!bean.isThreadAllocatedMemoryEnabled()) { bean.setThreadAllocatedMemoryEnabled(true); }
					if (bean.isThreadAllocatedMemoryEnabled()) { this.allocationBean = bean; }
				}
			} catch (UnsupportedOperationException | SecurityException ignored) { }
			this.threadCycles = THREAD_CYCLES_ENABLED ? ThreadCycles.open() : null;
		}

		void checkThread() {
			if (Thread.currentThread() != this.owner) { throw new IllegalStateException("Particle diagnostic requires its client render thread"); }
		}

		long cycles() { return this.threadCycles == null ? -1 : this.threadCycles.read(); }
		long cpu() { return this.cpuSupported ? this.threads.getCurrentThreadCpuTime() : -1; }
		long allocation() { return this.allocationBean == null ? -1 : this.allocationBean.getCurrentThreadAllocatedBytes(); }

		void initGl() {
			if (this.glInitialized) { return; }
			this.glInitialized = true;
			this.context = GLContext.getCapabilities();
			if (!this.context.OpenGL33 && !this.context.GL_ARB_timer_query) {
				this.gpuStatus = "requires_OpenGL33_or_GL_ARB_timer_query"; return;
			}
			// Extension presence alone is insufficient: timestamp counters may have zero bits.
			this.timestampBits = GL15.glGetQueryi(ARBTimerQuery.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS);
			if (this.timestampBits < 1 || this.timestampBits > 64) {
				this.gpuStatus = "invalid_timestamp_counter_bits"; return;
			}
			for (int i = 0; i < this.queries.length; i++) {
				this.queries[i] = GL15.glGenQueries();
				if (this.queries[i] == 0) { deleteQueries(); this.gpuStatus = "query_allocation_failed"; return; }
			}
			this.gpuSupported = true;
			this.gpuStatus = "timestamp_pairs";
		}

		void checkContext() {
			checkThread();
			if (this.context != GLContext.getCapabilities()) { throw new IllegalStateException("Particle diagnostic GL context changed before finish"); }
		}

		void poll() {
			if (!this.gpuSupported) { return; }
			checkContext();
			for (int i = 0; i < LIMIT; i++) {
				Totals p = this.owners[i];
				if (p == null) { continue; }
				int start = this.queries[2 * i], end = this.queries[2 * i + 1];
				if (GL15.glGetQueryObjecti(end, GL15.GL_QUERY_RESULT_AVAILABLE) == 0
					|| GL15.glGetQueryObjecti(start, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) { continue; }
				// Only read results after BOTH availability checks; never wait or flush.
				long delta = ARBTimerQuery.glGetQueryObjectui64(end, GL15.GL_QUERY_RESULT)
					- ARBTimerQuery.glGetQueryObjectui64(start, GL15.GL_QUERY_RESULT);
				if (this.timestampBits < 64) { delta &= -1L >>> (64 - this.timestampBits); }
				p.gpu += delta;
				p.completed++;
				p.outstanding--;
				this.owners[i] = null;
			}
		}

		void deleteQueries() {
			if (!this.glInitialized || this.context == null) { return; }
			checkContext();
			for (int i = 0; i < this.queries.length; i++) {
				if (this.queries[i] != 0) { GL15.glDeleteQueries(this.queries[i]); this.queries[i] = 0; }
			}
		}
	}
}
