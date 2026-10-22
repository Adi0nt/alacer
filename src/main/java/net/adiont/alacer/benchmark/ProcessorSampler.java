package net.adiont.alacer.benchmark;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Optional;

/**
 * Which logical processor the calling thread is running on, from the operating system (opt-in benchmark
 * evidence, {@code alacer.benchmark.processorSampling}). Windows: {@code GetCurrentProcessorNumberEx}
 * (group * 64 + number); Linux: {@code sched_getcpu}. Elsewhere, or if the lookup fails, it reports itself
 * unavailable and every sample is -1: a missing answer is never shown as processor 0.
 *
 * <p>Whether a processor is a performance or an efficiency core is not decided here: logical processor
 * numbers do not identify core types, so the runner maps them with the operating system's own topology.
 */
public final class ProcessorSampler {
	/** Samples are processor indices below this, or -1. */
	public static final int MAX_PROCESSORS = 256;

	private final MethodHandle handle;
	private final boolean windows;
	private final MemorySegment number;
	private final String source;

	private ProcessorSampler(MethodHandle handle, boolean windows, MemorySegment number, String source) {
		this.handle = handle;
		this.windows = windows;
		this.number = number;
		this.source = source;
	}

	public static ProcessorSampler create() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		try {
			Linker linker = Linker.nativeLinker();
			if (os.startsWith("windows")) {
				Optional<MemorySegment> symbol = SymbolLookup.libraryLookup("kernel32", Arena.global())
					.find("GetCurrentProcessorNumberEx");
				if (symbol.isEmpty()) {
					return unavailable("GetCurrentProcessorNumberEx not found");
				}
				MethodHandle handle = linker.downcallHandle(symbol.get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
					Linker.Option.critical(false));
				// PROCESSOR_NUMBER: WORD Group, BYTE Number, BYTE Reserved.
				return new ProcessorSampler(handle, true, Arena.global().allocate(4, 2), "windows:GetCurrentProcessorNumberEx");
			}
			if (os.startsWith("linux")) {
				Optional<MemorySegment> symbol = linker.defaultLookup().find("sched_getcpu");
				if (symbol.isEmpty()) {
					return unavailable("sched_getcpu not found");
				}
				MethodHandle handle = linker.downcallHandle(symbol.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT),
					Linker.Option.critical(false));
				return new ProcessorSampler(handle, false, null, "linux:sched_getcpu");
			}
			return unavailable("unsupported operating system " + os);
		} catch (RuntimeException | LinkageError e) {
			return unavailable(e.toString());
		}
	}

	private static ProcessorSampler unavailable(String reason) {
		return new ProcessorSampler(null, false, null, "unavailable: " + reason);
	}

	public boolean available() {
		return this.handle != null;
	}

	/** "windows:...", "linux:..." or "unavailable: reason". */
	public String source() {
		return this.source;
	}

	/** The calling thread's current logical processor, or -1 if unknown. */
	public int current() {
		if (this.handle == null) {
			return -1;
		}
		try {
			int index;
			if (this.windows) {
				this.handle.invokeExact(this.number);
				index = (this.number.get(ValueLayout.JAVA_SHORT, 0) & 0xFFFF) * 64 + (this.number.get(ValueLayout.JAVA_BYTE, 2) & 0xFF);
			} else {
				index = (int) this.handle.invokeExact();
			}
			return index >= 0 && index < MAX_PROCESSORS ? index : -1;
		} catch (Throwable e) {
			return -1;
		}
	}
}
