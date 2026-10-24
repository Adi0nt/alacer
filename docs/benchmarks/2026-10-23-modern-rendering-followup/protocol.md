# Bounded VAO regression follow-up

Human authorization: "Ok start testing so antigravity can continue where you leave off".
Starting branch: claude/magical-planck-7t9612; source HEAD:
95cceb6b1e7a513cf94ed8a206abc49d04813a8f. The earlier testing hold is lifted.

This is attribution only. The original modern-rendering archive, selection
thresholds, source, compiled classes, configuration and base world remain frozen.
There is no fix, option selection, default change, PR, or release in this stage.
All work takes place in C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer.
The separate primary main checkout is outside the experiment.

## Conditions and fixed budget

Native Windows, explicit Temurin Java 25.0.4.101 for the Gradle JVM and the
existing Java 25 game toolchain, 1920x1080, distance 12, vanilla VBOs, 2 GiB.
The expected device is i7-12700F / RTX 3060 Ti / NVIDIA 616.92. The archived
ordinary static/pan/fly route and fresh base-world copies are retained. All
three phases are reported. JFR, screenshots, detailed probes and verification
are off. Existing vanilla/default optimizations remain at their archived values.
No tests, builds, analysis or second client may run concurrently with a client.
runClient may execute its existing up-to-date prerequisite tasks; frozen compiled
class hashes must still match after every launch. No shipping code is edited.

Three conditions, with neighbor math and named uploads always off:

- A: terrainVaos=false, forceFallback=false; default startup has no GL hooks.
- V: terrainVaos=true, forceFallback=false; active VAO path.
- F: terrainVaos=true, forceFallback=true; hooks installed, original GL path.

Stage 1: A V V A (two adjacent balanced A/V pairs), maximum four launches.
Only proceed to stage 2 if all launch validations pass, each pair's pan visible
chunk mean differs by at most 1%, and one common metric (pan frame mean OR
CPU/frame) regresses by at least 20% in BOTH pairs. This is a diagnostic routing
rule for reproducing the previously observed large gap; it does not replace
any original selection gate or establish detection power for small effects.
Otherwise stop and hand off the inconclusive/changed result.

Stage 2, conditional: F V V F (two adjacent balanced F/V pairs), maximum four
additional launches. Report every result regardless of direction. No automatic
third stage, profiling capture, replacement launch, or unbounded retry.
Each launch has a 240-second timeout and the experiment stops at first failure.
Preserve failed/partial attempts and logs. Choose a new explicitly documented
attempt identifier for any later resumption; never overwrite this one.

## Validation and interpretation

Before/after every launch, check the inherited 240-entry performance manifest
and 279-entry selection manifest, all files in the original archive, and these
protocol/runner/analyzer bytes. Check Java/backend/device/settings, all phase
config values against the archived control (except terrainVaos), zero dropped
frames, absent verification/JFR, and expected VAO activity / zero named uploads.
Retain exact argv, JVM path, timestamps, source identity, full summary and phase
JSON, lossless gzipped CSV, original-file hashes and full logs.

The F comparison separates baseline hooks/fallback behavior from active-path
Java checks plus query, binding and downstream draw effects. It cannot isolate
native glBindVertexArray latency, and its early return can change JIT behavior.
Terrain section timing includes filtering and drawing, and is not GPU timing.
Visible chunk counts are a coarse workload check, not exact draw/vertex identity.
Existing cumulative GL counters do not supply per-phase submitted geometry.
No new draw instrumentation is introduced in these ordinary timing launches.

Report frame mean, CPU/frame, p99, slowest 1% mean, allocation/frame, visibility,
visible chunks, four terrain layers, upload, render, swap and process CPU.
Use paired changes and absolute values, retaining order effects and all phases.
Two pairs are limited attribution evidence, not performance selection or driver
causality. The earlier query-bypass diagnostic remains retired; no private LWJGL
state access or replacement guard is pursued here. Preserve correctness evidence
as historical evidence; these runs do not rerun the intrusive correctness suite.

Handoff must state completed launches, validity, limitations, process state,
repository state, exact continuation commands and the next discriminating step.
