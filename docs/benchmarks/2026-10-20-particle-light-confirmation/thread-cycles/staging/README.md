# Thread-cycle attribution: staging only

These files are outside the source tree. Do not integrate, compile, smoke, launch,
or change build/Gradle files until Main confirms the frozen light profiles have
finished. Nothing here establishes build, runtime, allocation, or performance
success. No third-party implementation was used.

## API and lifetime

`ThreadCycles.open()` reads `alacer.profiler.particleThreadCycles` (default false).
The staged diagnostics only create it inside a client render-thread `Session`
when that property **and** existing `ParticleDrawDiagnostics.ENABLED` are true.
Thus the additional property alone does nothing in a normal client or benchmark
without particle draw diagnostics. The existing benchmark/profiler gates remain.
Disabled cycle probes are behind the static property gate, with no native helper
initialization, arena, library lookup, or reads.

Each enabled session owns one confined arena, one reusable eight-byte output
buffer, a cached query method handle and a cached current-thread pseudo handle.
There is no explicit allocation on a successful `read()`; actual JVM allocation
must still be checked after integration. Initialization/probing precedes capture.
`GetCurrentThread() -> ADDRESS`; `QueryThreadCycleTime(ADDRESS, ADDRESS) -> JAVA_INT`
maps the Windows 32-bit BOOL correctly. No native lookup occurs on unsupported
OS/architectures, virtual threads, or without enabled native access. The selected
64-bit Windows architectures are AMD64/x86_64 and AArch64/arm64; linker/native
initialization failures still produce unavailable status.

- `read()`: cumulative raw cycles, or `-1` unavailable. Never nanoseconds.
- `available()`: usable now on this owner thread; false after failure or close.
- `supported()`: initial native probe succeeded; remains true on later failure.
- `status()`: `disabled`, `unsupported_platform`, `unsupported_virtual_thread`,
  `native_access_disabled`, `null_thread_handle`, `ready`, `query_failed`,
  `unsigned_range_exceeded`, `non_monotonic`, `native_failure:<class>`, or `closed`.
- `close()`: owner-only, idempotent; closes the confined arena and clears native
  references. A foreign-thread read returns unavailable without poisoning the
  owner; a foreign-thread close throws before touching resources. The pseudo
  handle is never passed to `CloseHandle`.

Failures disable cycle reads for the session without substituting a clock or
changing CPU/wall/allocation availability. Fatal VM errors are rethrown. The
unsigned upper half of ULONG64 and regressions are rejected rather than becoming
negative counts. No per-read error string or result object is created on success.
`finish()` snapshots metadata and closes the helper in `finally`, including a GL
cleanup failure. Normal file-write retries use detached totals and metadata.

## Schema 2 additions

Existing CPU, allocation, wall, GPU, sample, residual, pool and phase fields remain.
The 128-pair asynchronous GPU query logic is unchanged: availability-only polling,
no wait/flush, issue-phase ownership and deletion at finish.

Each phase in JSON and each CSV row gains:

| Field | Meaning |
| --- | --- |
| `draw_thread_cycles` | Sum of valid draw cycle deltas |
| `draw_thread_cycle_samples` | Draws contributing to that sum |
| `pass_thread_cycles` | Sum of valid outer-pass cycle deltas |
| `pass_thread_cycle_samples` | Passes contributing to that sum |

JSON metadata adds `thread_cycles_enabled`, `thread_cycles_supported`,
`thread_cycles_available`, `thread_cycles_status`, `thread_cycles_unit=raw_cycles`,
and `thread_cycles_source=QueryThreadCycleTime(GetCurrentThread)`.
CSV appends the four measurements and the four enabled/supported/available/status
metadata columns, preserving the original columns and their order. Availability
and status describe the final pre-close snapshot, not post-close usability.
`0` cycles with `0` samples is missing data, not a zero-cost measurement. Only
nonnegative, monotonic endpoint pairs whose sum fits a signed long contribute;
sum overflow omits the sample. Compare sample counts with draws/passes.

Cycle brackets surround the original host allocation/CPU/wall probes; draw cycle
brackets exclude the GPU timestamp calls. Pass cycles include nested draw probes
and query issuance. Existing host clock endpoints/order remain; cycle reads sit
outside their respective intervals, although a pass still contains its draw
probes. Instrumentation perturbs execution. Do not add pass and draw cycles, infer
GPU time, subtract cycles from wall ns, or convert cycles to time. Sleeping can
still consume some cycles; the smoke asserts no loop/sleep ratio.

## Main integration after the freeze

1. Check the current diagnostic SHA-256 against `attribution-manifest.json`.
   If it changed, rebase these small additions onto the current full file.
2. Copy `ThreadCycles.java` and the staged `ParticleDrawDiagnostics.java` into
   `src/main/java/net/adiont/alacer/render/` only after Main clears the freeze.
   Keep `ThreadCyclesSmoke.java` separate from product sources.
3. Build with explicit Java 25 Gradle/compiler/client JVMs; run the standalone
   smoke, then client smoke for diagnostics disabled, cycles disabled, and cycles
   enabled. Confirm default controls produce no native initialization, enabled
   captures produce valid sample counts, finish/retry works, and missing native
   support preserves existing exports. Verify actual probe allocation separately.
4. Enable cycles only alongside `-Dalacer.profiler.particleDrawDiagnostics=true`,
   `-Dalacer.benchmark=true`, and profiler enabled. The client JVM needs
   `--enable-native-access=ALL-UNNAMED` (already present in the current launcher
   Gradle configuration; verify the actual launched JVM). Use a new attribution
   run/output directory and its own manifest; do not append to frozen runs or
   replace their manifests. Do not change product defaults.

Deferred standalone commands (NOT executed; choose an installed Java 25 JDK):

```powershell
$java25 = 'C:\path\to\jdk-25'
$stage = Join-Path (Get-Location) 'docs/benchmarks/2026-10-20-particle-light-confirmation/thread-cycles/staging'
$smokeOut = Join-Path $env:TEMP ('alacer-thread-cycles-' + [guid]::NewGuid())
& "$java25\bin\javac.exe" --release 25 -d $smokeOut "$stage\ThreadCycles.java" "$stage\ThreadCyclesSmoke.java"
# Disabled path: no native access required.
& "$java25\bin\java.exe" -cp $smokeOut net.adiont.alacer.render.ThreadCyclesSmoke
# Windows enabled path: require success, monotonic CPU-loop/sleep reads and owner close.
& "$java25\bin\java.exe" --enable-native-access=ALL-UNNAMED -Dalacer.profiler.particleThreadCycles=true -cp $smokeOut net.adiont.alacer.render.ThreadCyclesSmoke --expect-ready
# Enabled property without native access: unavailable, no native lookup.
& "$java25\bin\java.exe" -Dalacer.profiler.particleThreadCycles=true -cp $smokeOut net.adiont.alacer.render.ThreadCyclesSmoke
```

The staged manifest records the source snapshot and unrun state. Main must create
a separate actual attribution manifest after integration with exact source/JAR
hashes, JVM/version/arguments, scenario, gates, CPU/OS, capability/status, output
paths, and build/smoke/client evidence. Staging is not performance evidence.

## Primary references

- [Microsoft QueryThreadCycleTime](https://learn.microsoft.com/en-us/windows/win32/api/realtimeapiset/nf-realtimeapiset-querythreadcycletime):
  raw user/kernel thread cycles, BOOL success, Kernel32.dll, and the explicit
  prohibition on converting cycles to elapsed time because CPU timers vary.
- [Microsoft GetCurrentThread](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getcurrentthread):
  current-thread pseudo handle and no close requirement.
- [Java 25 Linker](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html),
  [SymbolLookup](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/SymbolLookup.html),
  [Arena](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Arena.html):
  ABI layouts/downcalls and arena-bound library/buffer lifetime and confinement.
