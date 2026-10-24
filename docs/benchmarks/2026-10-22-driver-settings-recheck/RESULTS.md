# Session 32 follow-up: driver settings recheck

2026-10-22, America/Los_Angeles. **The completed launch no longer exhibits sustained NVIDIA-origin worker CPU.
Fresh-launch repeatability remains unresolved because the second attempt failed its quiet preflight.**

The user disabled global Threaded optimization (previously enabled) and selected maximum-performance GPU power
management. These are user-reported settings; the NVIDIA connector confirms driver 616.92 but cannot read effective
global/program 3D profiles. This tests the combined settings, not the isolated causal effect of threading. User GPU
settings were left as selected. CPU power was temporarily High performance and restored afterward.

## Completed terrain launch

The unchanged frozen Java 25 build completed every original terrain phase with the environmental monitor enabled.
All environmental and source/artifact/world/config integrity checks passed. All phases are client-heavy; no earlier
low-client-CPU/high-native-worker state appeared in their aggregate metrics.

| Phase | Frame mean ms | Client CPU/frame ms | Process CPU/frame ms | Render / swap ms |
|---|---:|---:|---:|---:|
| reference-pre | 0.61038 | 0.5870 | 0.63376 | 0.4059 / 0.1388 |
| steady | 0.65424 | 0.6086 | 0.65064 | 0.4416 / 0.1445 |
| pan | 1.00147 | 0.9623 | 1.02550 | 0.7749 / 0.1524 |
| sort | 1.05066 | 1.0137 | 1.22402 | 0.8217 / 0.1531 |
| fly | 1.32576 | 1.2951 | 2.34633 | 1.0566 / 0.1798 |
| reference-post | 0.63343 | 0.6146 | 0.66313 | 0.4259 / 0.1426 |

Five-second native snapshots show **0 measured NVIDIA-origin thread cores in all static/reference and pan/sort
windows**, and approximately 0.00089 cores during flight. Existing NVIDIA-origin threads retain essentially flat
CPU counters after startup. This differs markedly from the previous fast state's approximately 0.82–0.84 cores.
Counters cover 15 of 20 seconds in each reference, 55 of 60 in steady, and 25–35 seconds in moving phases. New or
short-lived threads can be missed; a start-module address is not a native instruction-pointer trace.

This strengthens the driver-work-distribution explanation and supports the settings taking effect. It does not
prove Threaded optimization alone caused every historical difference: GPU power settings and diagnostic overhead
also changed, and the old effective profile was not captured. No speedup or regression is claimed against old runs.

Timing still varies: steady five-second means range **0.6318–0.6681 ms**. The initial reference has 1,374 visible
chunks, while steady/final reference have 1,293, as seen previously; initial/final means are not a same-workload pair.
Whole-launch readable background averages 0.413 cores, with pan/flight near the existing 0.5-core phase limit.
Foreground and critical telemetry are complete; all sampled client processors are P-cores. GPU clock averages
1,817 MHz. Unattributed busy-system-CPU flags remain unresolved, including about 1.98 cores in flight.

## Second attempt and budget

The second attempt did **not start Minecraft**: it could not obtain ten consecutive quiet readings within the fixed
60-second maximum. Its 60 readable samples average **0.672 background cores**, range 0.357–2.831; the longest run
at or below 0.5 cores was five samples. Named contributors over that minute include:

| Process | Core-seconds |
|---|---:|
| ChatGPT.exe | 27.72 |
| codex-computer-use.exe | 5.86 |
| iCUE.exe | 1.39 |
| zen.exe | 0.62 |

These are measured contributors to the failed quiet window, not explanations for all timing noise in the completed
launch. No process was closed, no threshold changed, and no failed slot was retried. The declared stop rule ended
the series after **6 min 32 s**, with **one actual Minecraft launch** and one rejected preflight, within the 15-minute
budget. Original CPU power plan restored, no client or runner lock remains, and foreground automation is stopped.

## Overhead, verification and next step

JFR was omitted. Sparse native collection measured approximately **0.0087–0.0125 CPU cores** across phase windows,
versus 0.070–0.086 in the prior one-second collector. Environmental monitor self CPU averaged **0.01318 cores**.
Neither quantity measures the causal frame-time effect of instrumentation. Six focused tooling tests pass; no
production code or rendering defaults changed, so the full Java/Python suites were not repeated this follow-up.
The actual client executable is the explicit Temurin 25.0.4.1 runtime.

All **113 prior archive files and 402 prior raw files** checked retain their hashes. The aa31w2 frozen source,
artifacts, world, shared configuration, JDK and source ZIP still verify. Earlier evidence and failed verdicts remain
unchanged. No commit, push, calibration or full A/A pilot ran.

**Next:** repeat this same two-launch recheck during an actually quiet interval, keeping the chosen GPU settings
fixed. Prefer minimal Codex/ChatGPT activity during the quiet wait and measurements. The current launch supports
removal of the native-worker state, but provides no separate-launch stability estimate. Do not start a full pilot
or claim reliable 2% detection from this result.

Evidence: [registered protocol](README.md), [settings](settings.json), [phase analysis](analysis.json),
[quiet-window analysis](quiet-analysis.json), [attempts](attempts), [runner output](runner-output.txt),
[elapsed time and restoration](state.json), [preservation](preservation-after.json), [raw hashes](raw-files.json),
[focused tests](focused-tests.txt). Raw frames and native/monitor traces remain under this checkout's `run/`.
