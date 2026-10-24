# Session 32: focused benchmark stability investigation

Completed 2026-10-22, America/Los_Angeles. **Useful attribution obtained; exact trigger unresolved.
No calibration or A/A pilot ran. Reliable detection of a 2% improvement remains unestablished.**

## Assessment

The leading explanation is a change in **native OpenGL work distribution**, consistent with the NVIDIA driver
executing substantial work on its own thread in the fast state and mostly on the client thread in the slow state.
Confidence is high that the new traces exhibit this CPU redistribution, moderate that the same mechanism explains
the historical groups. Its trigger, the effective driver threading setting, and the exact native call paths remain
unproven. A thread's start module identifies its origin, not its current instruction pointer.

Session 31's accepted initial references all showed 1,374 visible chunks, but their render/swap means split sharply:

| Historical state | Render section | Buffer swap | Client CPU/frame | Process CPU/frame |
|---|---:|---:|---:|---:|
| Slow, terrain 1/2 initial reference | 0.4070–0.4143 ms | 0.1362–0.1364 ms | 0.5843–0.5857 ms | 0.6271–0.6308 ms |
| Fast, terrain 3/4-r3 initial reference | 0.1426–0.1456 ms | 0.3191–0.3424 ms | 0.1849–0.2029 ms | 0.6717–0.6738 ms |

The new OS thread counters independently agree with the client CPU accounting and identify where the additional
off-client CPU appears. Values below use complete one-second intervals inside each phase:

| New observation | Frame ms | Client CPU cores | NVIDIA-origin thread cores | Render / swap ms |
|---|---:|---:|---:|---:|
| Monitor on, initial reference | 0.47434 | 0.379 | 0.820 | 0.1299 / 0.3063 |
| Same launch, steady | 0.59273 | 0.962 | 0.0003 | 0.3989 / 0.1326 |
| Monitor off, initial reference | 0.61002 | 0.962 | 0 | 0.4116 / 0.1348 |
| Supplemental rejected monitor-off launch, initial reference | 0.52074 | 0.371 | 0.832 | 0.1511 / 0.3281 |
| Same supplemental launch, steady | 0.51534 | 0.380 | 0.840 | 0.1488 / 0.3273 |

The busy native thread starts inside `nvoglv64.dll` and is absent from the Java thread-name mapping. The monitored
launch changes state during `warmup-sort`: five-second render means rise from about 0.36–0.43 to 0.71–0.83 ms,
while swap falls toward 0.14 ms and the native worker becomes idle. Its final reference remains client-heavy.
The supplemental launch retains the fast signature through its final reference. Historical terrain-3 also changes
to the slow final reference; terrain-4-r3 stays fast. Longer startup waiting alone is therefore not a supported fix.

## Other hypotheses and limits

- **JIT / warmup:** the first two new initial references compile 188/198 methods and spend 0.544/0.496 compiler
  seconds, despite opposite states. Their warmup-sort compilation is also similar (4.521/4.434 seconds).
  JIT activity exists and could influence the trigger, but does not alone account for the native CPU redistribution.
  No new settling strategy is adopted. Default JFR records individual compilations only above a 1,000 ms threshold;
  cumulative compiler statistics and native compiler-thread CPU provide the supported alternative for shorter work.
- **GC:** initial-reference pauses total 9.10/5.19 ms over 20 seconds. The monitored slow final reference and
  supplemental fast final reference both have zero GC events. GC explains individual tails, not this persistent split.
- **Workload:** initial visibility matches across the original and new launches. Later static visibility varies
  between approximately 1,289 and 1,293 chunks; ambient entity counts and allocations also vary. These smaller
  differences remain relevant to small claims, although the initial split exists without a chunk-count difference.
  Optional detailed draw counters were disabled, so exact draw-call equality is not established.
- **Workers:** chunk/compiler/server activity is present in moving/warmup phases; it is not a substitute for the
  measured NVIDIA-origin CPU in the fast static view. There is no evidence of a replacement rendering workload.
- **Scheduling / frequency / power:** the monitored launch has 100% foreground, no E-core/unknown processor
  samples, the prescribed P-core mask and High performance plan. Whole-launch P-core mean is 3,335 MHz and GPU
  mean clock 1,793 MHz. As in the archive, aggregate clocks do not establish per-thread effective frequency or
  eliminate SMT, waits, DPC or protected-process effects. Kernel scheduling traces and native instruction-pointer
  stacks were not captured; unprivileged OS thread counters were the alternative used.
- **Driver / shaders:** native worker activity and render/swap movement support driver involvement. Shader/program
  compilation, driver cache behavior, adaptive threading and the effective profile were not directly measured or
  controlled. No NVIDIA setting changed. These mechanisms cannot be separated by the available evidence.
- **Background activity:** the monitored launch passes existing environmental rules (whole-launch readable
  background mean 0.168 cores). The original rejected windows still show real application interference. Protected
  processes/unattributed system CPU remain unresolved; neither observation proves the accepted historical split
  was caused by those applications. Monitor-off launches lack environmental acceptance evidence.

## Budget, failures and instrumentation overhead

The diagnostic clock ran **19 min 17 s**, including quiet waits, the initial failure and collector repair, below the
30-minute cap. **Four Minecraft starts: one aborted, three completed.** No d0, calibration, pilot, retry or fourth
v2 launch ran. The predeclared on/off/off/on comparison is incomplete.

The initial PowerShell collector consumed about 0.19 cores and returned zero start addresses. It was stopped;
its code, raw data and incomplete outcome are retained. Windows console Ctrl+C terminated before Python's finally;
the client exit was independently verified and the original plan explicitly restored before v2. The independently
versioned amendment uses native Windows APIs. That collector still costs **0.070–0.086 cores** in measured phases;
JFR's periodic thread adds about 0.03 client-process cores. These are attribution runs, not unperturbed benchmarks.

The third v2 client completed but failed the existing source-integrity check because the investigating agent created
this archive's README during the launch. Existing tracked source/tooling and compiled artifacts remained unchanged;
the newly added archive README is included by the existing state hash. The failure is retained, never promoted to
a passing record. Its raw measurements are explicitly supplemental. The declared functional-failure stop prevented
launch four. Final state: original Ultimate Performance plan restored, no client, no runner lock, helper stopped.

The existing environmental monitor itself averaged **0.01459 cores** (1.459% of one core; phase means
0.01172–0.01667). Monitored steady was 0.59273 ms versus 0.61184 ms in the first monitor-off launch, both
client-heavy: an apparent 3.1% reduction, **not a causal monitor effect**. Launch/state/workload differences and the
incomplete order prevent an overhead bound or significance claim. Supplemental monitor-off data also reproduce
the fast state; disabling this monitor does not remove the discrepancy. In-client profiler/processor-sampling
overhead and the frame-time effect of JFR/native tracing were not isolated.

## Changes and validation

- Added `tools/aa_diagnose.py`: read-only phase CPU distribution, render/swap, five-second raw frame bins and
  optional native-thread attribution; rejected raw data require explicit opt-in and retain their failure. No scoring
  or filtering changes. Generated analyses can be reused instead of relaunching/reparsing repeatedly.
- Added opt-in Windows thread telemetry and a bounded Session 32 driver that reuses the existing smoke launcher,
  preserves attempts, caps quiet waits, restores power and stops on failure/deadline. Exact executed v2 sources are
  retained under `diagnostic-sources/`; a post-run cleanup guard also handles failed JFR setup before a dump thread
  exists. Expensive collectors are not recommended for routine comparisons.
- Documented [Quick / Standard / Extended workflows](../../BENCHMARK_MODES.md), using existing phase lengths,
  runtime-safe toggle blocks, references and freeze/pair tooling. Targets are 2–5 / 10–30 minutes / rigorous existing
  requirements. Shortened targets are guidance, not newly validated workloads or relaxed thresholds.
- Explicit Temurin 25.0.4.1: **131 Java tests pass**. Python: **112 discovered, 110 pass, 2 Linux-only skips**,
  including real Windows thread CPU/module validation, bins retaining tails, thread identity/gaps and decimal/hex
  native ids. After the cleanup guard, all **six focused tests pass**, including partial-setup cleanup. All four
  existing cross-scene configuration regressions pass; the earlier validator defect stays fixed.
- **408 original file hashes match**, including all **308 raw result files / 1,798,590,052 bytes**. Original aa31w2
  source/tooling, artifacts, JDK, base world, shared config and source ZIP still match its frozen manifest. No Session
  31 observations or verdicts were rescored. No rendering defaults, candidate, 5%/3% limits, commits or pushes changed.

Evidence: [historical analysis](historical-analysis.json), [new analysis](diagnostic-analysis.json),
[JFR summary](jfr-analysis.json), [protocol](PROTOCOL.md), [amendment](AMENDMENT-v2.md),
[attempts](attempts), [state](diagnostic-state.json), [preservation](preservation-after.json),
[Python tests](python-tests.txt), [focused tests](focused-tests.txt), [Java tests](java-tests.txt),
[diagnostic file hashes](diagnostic-files.json). Full raw CSV/JFR/thread data remain locally preserved.

## Smallest next action

**Do not launch another full calibration yet.** First inspect the effective NVIDIA OpenGL threading profile, then
predeclare at most two terrain launches under one explicit fixed driver-threading setting, saving/restoring the
original setting. Use the existing monitor and frame timing, with only sparse native attribution if needed; keep
archive/source documents fixed before launch. This tests whether the suspected distribution state can be controlled.
It is a proposed follow-up, not performed or qualified here. If controlled references are consistent, freeze a new
protocol and run fresh calibration before any small scored claim. Otherwise use Quick/Standard for major rendering
ideas with explicit uncertainty while the small-effect gate remains unresolved. Return effort to rendering work;
do not automatically start another session, extended campaign or optimization campaign.
