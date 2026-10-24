# Phase 3.1: Windows reference scheduling protocol (prepared, not measured)

This is a new protocol. Do not modify or pool the October 23 Linux/llvmpipe archives.
The audit at `91efebe` found a world-load crash in `RenderChunkRegionMixin` and
correctness/verification gaps. No scored series is valid until those are repaired,
reviewed and tested. The runner cannot freeze without an unchanged, passing native
smoke. Repaired sources require a fresh smoke/archive. No default promotion is automatic.

Recovery follow-up: the native world-load failure and frustum counterexample have been
repaired locally. A combined unscored 200-mob smoke completed with zero stable visibility,
worker-cache, vector, raycast, renderer-policy, lighting or occlusion mismatches. The
model verifier samples matrices; ambient verification checks queries rather than full
effect-stream identity, and no listening-based sound certification is implied. The
core series below evaluates scheduling viability on this fixed repaired baseline. It
cannot certify those independent subsystems or promote a default. Retain the audit's
before evidence and all interrupted/failed attempts.

## Runtime and controls

Windows / RTX 3060 Ti, Java 25 explicitly selected with `--jdk` (default: installed
Adoptium `jdk-25.0.4.101-hotspot`), 1920x1080, 2 GiB heap, distance 12, display lists,
VSync off, FPS limit 260 (vanilla unlimited value), fancy graphics, smooth lighting 2,
mipmaps 4, clouds 2. Restrict Gradle toolchain discovery to the selected JDK and check
the actual client executable. Gradle's daemon criteria independently require Java 25;
capture `gradlew --version` before preparation. `--release 25` remains in build.gradle.

Preserve shared config, options and the existing seed-1789 base world. Benchmark mode
ignores persisted Alacer config. Each launch copies the base world; chunk scenes also
emit a byte-identical-copy receipt. The benchmark already uses a single disposable
world name: **multiple clients means multiple serial launches, never concurrent games**.
Prebuild all runtime outputs, including remapped runtime classes, before smoke/freeze.
Preparation launches are unscored and retained. No compilation or analysis while a
scored client runs. No JFR, verification hooks, chunkDetail or GPU-query diagnostics
in scored launches. Ordinary profiling and chunkLatency are on in every arm.

The smoke exports the complete effective configuration; freeze captures it and the
runner explicitly forwards its render/quality/advanced/profiler values to scored
launches. Only the named scheduling option changes, with the other scheduling option
off. Verification flags are off for scoring. Source, tooling, protocol, compiled
artifacts, JDK executable/release, shared files and base world hashes must stay fixed.
The source ZIP is immutable. The GPU/driver/JVM and quality receipts must match smoke.

## Bounded suites

`core` is **16 launches**: edit and near flight, eight per scene; only the floor is
scored. A = both options off; B = `render.chunkTimeFloor=true`. Order in each scene:
ABBABAAB, four adjacent A/B pairs, normalize every pair as B/A-1.

`full` is **80 launches**, selected before freeze: those 16 plus floor high-flight,
default and terrain controls (24), then five eight-launch scenes for
`render.feedChunkWorkers` (40). The two candidate options are independent contrasts;
no combined arm. The 16-launch Linux study cannot simultaneously provide three arms
and all these added controls. Core results cannot select either default.

Durations (seconds, scale 1):

| Scene | Phases |
| --- | --- |
| edit | warmup-pan 10, still 10, edit 30 (28 bursts, 16 edits each) |
| near/high | warmup-pan 10, still 10, fresh-flight 40 (20 blocks/s) |
| default | static 10, pan 15, fly 20 |
| terrain | warmup-pan 15, warmup-sort 16, steady 10, pan 15, sort 16, fly 20 |

Warmups and setup frames are excluded. Smoke is near/A at scale 0.1 and is never
scored. Unique archive-derived labels, logs, reservation/final attempt receipts and
raw result hashes are retained. Stop at the first invalid launch; no automatic retry,
resume, changed threshold, or extension. An interrupted/failed series remains
incomplete. An amended series uses a new archive, fresh smoke and freeze.

## Gates

All records must complete, use the actual display-list backend, publish updates in
chunk scenes, have positive allocation/CPU counters, zero dropped frames, zero
latency overflow, fixed phase times/settings and zero client/Mixin/GL errors.
Reject a generation run or a build during launch. Missing metrics fail evaluation.

Responsiveness keeps the original targets: edit latency p90 improves in 4/4 pairs,
median <= -25%; near and high flight p90 improve in >=3/4, median <= -10%.
Visible uncompiled chunk mean median change <= +5%; when the control is zero,
the candidate must also be zero. Still frame-mean change stays within +/-5%.

The general roadmap gates are stricter than the historical software-renderer frame
allowances and take precedence here: each scored active/control phase has median
frame mean <= +1%, p99 and slow-1% mean <= +2%, and CPU/frame <= 0%.
Slow-1% mean is `1000 / one_percent_low_fps`, not the p99. Allocation, process CPU,
upload section time, latency counts and uncompiled chunks remain available in raw
receipts; inclusive worker timers are not summed into frame CPU.

Passing timing gates is only viability evidence. Default enablement also requires
separate fixed-end-pose visual comparisons, no missing/incorrect geometry, strict
candidate verification, a declared visual noise floor and hardware stability
calibration. This runner does not establish 2% detection power or certify visual
identity. None of these gates is waived by a passing unit build.

## Commands (PowerShell: quote dotted Gradle options)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat test classes --console=plain
python -B tools/run_chunk_scheduling_reference.py plan --suite core
python -B tools/run_chunk_scheduling_reference.py plan --suite full
# After repairing the audited blockers and preparing runtime outputs:
python -B tools/run_chunk_scheduling_reference.py smoke --archive build/csr-reference-01
python -B tools/run_chunk_scheduling_reference.py freeze --archive build/csr-reference-01 --suite core
python -B tools/run_chunk_scheduling_reference.py measure --archive build/csr-reference-01
python -B tools/run_chunk_scheduling_reference.py evaluate --archive build/csr-reference-01
```

Use a different new archive for another smoke. Preserve the result directory outside
`build/` before running Gradle clean. These commands prepare/run exactly the frozen
suite; this audit did not run the scored 16- or 80-client series.
