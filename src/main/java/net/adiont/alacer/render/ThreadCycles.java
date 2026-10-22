package net.adiont.alacer.render;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Session-owned Windows raw thread-cycle counter. Never a time source. */
public final class ThreadCycles implements AutoCloseable {

	public static final String PROPERTY = "alacer.profiler.particleThreadCycles";
	private final Thread owner = Thread.currentThread();
	private Arena confinedArena;
	private MethodHandle query;
	private MemorySegment threadHandle, buffer;
	private long previous = -1;
	private boolean supported, ready, closed;
	private String status = "disabled";

	/** Only call from an enabled diagnostic Session (or the standalone smoke). */
	public static ThreadCycles open() { return new ThreadCycles(); }

	private ThreadCycles() {
		if (!Boolean.getBoolean(PROPERTY)) { return; }
		String os = System.getProperty("os.name", "");
		String arch = System.getProperty("os.arch", "");
		// Do not initialize the linker or load any native library on other platforms.
		if (!os.startsWith("Windows") || !(arch.equalsIgnoreCase("amd64")
			|| arch.equalsIgnoreCase("x86_64") || arch.equalsIgnoreCase("aarch64")
			|| arch.equalsIgnoreCase("arm64"))) { this.status = "unsupported_platform"; return; }
		if (this.owner.isVirtual()) { this.status = "unsupported_virtual_thread"; return; }
		if (!ThreadCycles.class.getModule().isNativeAccessEnabled()) {
			this.status = "native_access_disabled"; return;
		}
		try {
			this.confinedArena = Arena.ofConfined();
			SymbolLookup kernel = SymbolLookup.libraryLookup("Kernel32.dll", this.confinedArena);
			Linker linker = Linker.nativeLinker();
			// Win32 BOOL is a 32-bit integer, not the C bool/JAVA_BOOLEAN layout.
			this.query = linker.downcallHandle(kernel.findOrThrow("QueryThreadCycleTime"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			MethodHandle current = linker.downcallHandle(kernel.findOrThrow("GetCurrentThread"),
				FunctionDescriptor.of(ValueLayout.ADDRESS));
			this.threadHandle = (MemorySegment) current.invokeExact();
			if (this.threadHandle.equals(MemorySegment.NULL)) {
				this.status = "null_thread_handle"; return;
			}
			this.buffer = this.confinedArena.allocate(ValueLayout.JAVA_LONG);
			this.ready = true;
			this.status = "ready";
			this.supported = read() >= 0; // Probe before any render clocks start.
		} catch (Throwable failure) {
			this.ready = false;
			this.status = "native_failure:" + failure.getClass().getSimpleName();
			rethrowFatal(failure);
		} finally {
			if (!this.ready) { release(); }
		}
	}

	/** Raw cumulative cycles, or -1 if unavailable. No allocation on successful reads. */
	public long read() {
		if (!available()) { return -1; }
		try {
			int ok = (int) this.query.invokeExact(this.threadHandle, this.buffer);
			if (ok == 0) { this.ready = false; this.status = "query_failed"; return -1; }
			long cycles = this.buffer.get(ValueLayout.JAVA_LONG, 0);
			// Reject the unsigned upper half and regressions; never fabricate a delta.
			if (cycles < 0 || cycles < this.previous) {
				this.ready = false;
				this.status = cycles < 0 ? "unsigned_range_exceeded" : "non_monotonic";
				return -1;
			}
			this.previous = cycles;
			return cycles;
		} catch (Throwable failure) {
			this.ready = false;
			this.status = "native_failure:" + failure.getClass().getSimpleName();
			rethrowFatal(failure);
			return -1;
		}
	}

	/** Capability means the initial native probe succeeded; status records later failure. */
	public boolean supported() { return this.supported; }
	public boolean available() { return Thread.currentThread() == this.owner && this.ready && !this.closed; }
	public String status() { return this.closed && this.status.equals("ready") ? "closed" : this.status; }

	/** Owner-only and idempotent. The GetCurrentThread pseudo handle needs no CloseHandle. */
	@Override
	public void close() {
		if (Thread.currentThread() != this.owner) { throw new IllegalStateException("ThreadCycles requires its owner thread"); }
		if (this.closed) { return; }
		release();
		this.closed = true;
	}

	private void release() {
		if (this.confinedArena != null) { this.confinedArena.close(); this.confinedArena = null; }
		this.query = null;
		this.threadHandle = null;
		this.buffer = null;
	}

	private static void rethrowFatal(Throwable failure) {
		if (failure instanceof VirtualMachineError error) { throw error; }
		if (failure instanceof ThreadDeath death) { throw death; }
	}
}
