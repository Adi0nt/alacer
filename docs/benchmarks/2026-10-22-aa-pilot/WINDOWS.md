# Session 31 Windows reference validation

Validation date: 2026-10-22 (America/Los_Angeles). Working branch: `claude/serene-sagan-j5czir`, starting at
`c3fb96d`. Validation uses a separate managed worktree; the original dirty `main` checkout is preserved.

## Machine and prerequisites

- Windows 11 build 26200, Intel i7-12700F, 12 physical cores / 20 logical processors.
- Windows reports efficiency class 1 on processors 0-15 (eight SMT P-cores), class 0 on 16-19 (four E-cores), one group.
- NVIDIA GeForce RTX 3060 Ti, driver 616.92; actual client exports NVIDIA OpenGL 4.6.
- Explicit Gradle/client JDK: `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`, Temurin 25.0.4.1+1-LTS.
  Java bytecode target remains 25. Python 3.13.6, numpy 2.5.2.
- Copied the existing benchmark base world and shared configuration into the isolated worktree without editing the
  originals. The compressed NBT `RandomSeed` field is 1789. Scored launches will hash both inputs.
- The initial `check` correctly refused the custom Ultimate Performance plan
  `6926823e-1958-4e8c-a849-b485e20e7181`. Validation temporarily selects the protocol's High performance plan.
  No threshold or rendering default has changed.

## Corrections before freezing

- Quiet-window expiry rejects the attempt before spawning a client. Only consecutive fresh readings with finite,
  nonnegative CPU values can establish quiet; missing, stale, duplicate or gapped samples cannot pass.
- Windows process enumeration uses explicit pointer-width signatures and raises on enumeration failures instead of
  interpreting them as an empty process list. Unreadable Java command lines cannot establish a no-client preflight;
  the monitor retries temporary command-line failures instead of caching them permanently.
- Standalone monitor checks return failure for missing critical telemetry or an observed competing client.
- Synthetic Python runner/parser tests no longer construct the Linux-only `/proc` implementation on Windows.
  Separate tests exercise real Windows process identity, topology, CPU, foreground, power, memory, priority,
  keep-awake and child-process affinity, plus Windows API fault injection.
- Failed smoke launches stop the command. Environmental smoke failures remain explicitly recorded and prevent
  freezing. `prepare` now stops for inspection; freezing is an explicit subsequent stage.
- Added a Computer Use foreground helper for one activation per newly launched benchmark window; it does not
  reclaim focus if the machine is used during a phase. The helper and batch wrapper are now frozen with the tooling.

## Attempts retained so far (smoke only)

| Attempt | Functional outcome | Environmental outcome |
|---|---|---|
| `aa31-smoke-d0` | failed: Gradle daemon disappeared during static | incomplete; no result record |
| `aa31-smoke-d0-r2` | all 13 phases completed, runner checks passed | rejected: foreground 0% in every phase; fly and repeat-06/07 exceeded 0.5 background cores |
| `aa31-smoke-terrain` | all 8 phases completed, runner checks passed | began unfocused; acceptance details in attempt file |

The initial hang is unexplained. Windows event 1002 records a Java application hang; the Gradle daemon (PID 38024)
disappeared and its client exited. A late `jcmd` did not produce a dump. The fresh d0 retry answered two thread dumps
and completed without recurrence. This does not establish the cause of the first hang.

The d0 retry recorded no dropped frames, no unknown processor samples and no E-core frames. Mob positions stayed
fixed in every still phase; static had a 7.5-degree pose change (the declared rule gates displacement, not turns).
Monitor self CPU averaged 12.24 ms/sample, 1.224% of one core, including startup. Several phases flagged more than
one unattributed busy core; these are not attributed to a particular process or driver without further evidence.
The first freeze attempt was refused because no environmentally valid d0 smoke existed. No manifest was written.

The user authorized an approximately four-hour idle run after validation passes. New smoke attempts with foreground
control must pass the unchanged rules before freezing, calibration and the pilot. Until then, frame-time noise,
confidence intervals, MDE80 and power for 2% remain unresolved. Raw logs, telemetry and frames stay in the isolated
worktree under `build/` and `run/`; attempts and compact records are retained here. No historical session is rescored.

## Corrected Windows smoke and pre-freeze amendments

The user explicitly authorized closing Roblox, Zen Browser and Discord; none remained running for the qualifying
smoke. The subsequent ten-second Windows check measured 0.251 background cores on average and no probe errors.

The initially proposed asynchronous focus helper could not call Computer Use outside an active tool execution;
its failures are retained in the helper log. It was stopped. The corrected helper exposes `service(45000)`, called
inside active Computer Use executions, and successfully activated each qualifying smoke before its first reference
phase. It never reclaims focus mid-launch. Codex remains resident for this assisted operation, with its CPU included
in the unchanged background limit; this is explicitly documented in the protocol before freezing.

The focused NoAI-only d0 smoke (`aa31-smoke-d0-r3`) still moved one mob 0.26 blocks and failed the unchanged 0.25
gate. Inspection of the mapped Java 25 runtime shows `MobEntity.tick` calling `LivingEntity.tick` and body control
running separately from the NoAI flag. NoAI is therefore not a complete physical or pose freeze. The benchmark-only
`BenchmarkMobFreezeMixin` now suppresses mob ticks and clears velocity on client and integrated server, including
newly loaded mobs. The plugin omits it unless both benchmark and freezeMobs are explicitly enabled; normal clients
and old benchmarks incur no hook. This changes opt-in simulation cost equally on both A/A sides; historical results
are retained, never rescored. No performance threshold changed.

| Qualifying smoke | Phases | Environmental rejections | Still movement / turn | Processor unknown / E-core frames | Critical telemetry / coverage | Monitor self CPU |
|---|---:|---:|---|---|---|---|
| `aa31-smoke-d0-r4` | 13 | 0 | 0 blocks / 0 degrees | 0 / 0 | 100% / >=100% | 1.185% of one core |
| `aa31-smoke-terrain-r3` | 8 | 0 | 0 blocks / 0 degrees | 0 / 0 | 100% / >=100% | 1.165% of one core |

Both qualifying smokes used the expected NVIDIA renderer, Java 25 executable, 1920x1080 window, display-list
backend, explicit options, unchanged copied base world and shared configuration, fixed High performance plan,
and constant P-core mask 0-15. No other Minecraft client was observed. Every phase completed its declared length,
discarded exactly one setup frame and dropped no frames. Processor histograms answered every recorded frame.
Terrain recorded 323,504 processor samples, all on P-cores. Reference frame-time, client/process CPU and epoch
values were present. Absolute critical-monitor coverage and availability passed for every phase. Several phases
still flag more than one unattributed busy core; the source is unproven and the original flag-only policy remains.
Monitor self CPU is measured; its frame-time effect has not been isolated in an overhead experiment.

At this point Windows integration and smoke are PASS. Calibration and the full pilot have not yet started; noise,
confidence intervals and ability to detect 2% remain unresolved until the scored series completes. The first hang
has not recurred in subsequent completed launches, but its cause is still unknown.

## Original aa31 calibration: terminal failure retained

The frozen original series completed calibration with 7 accepted launches (3 d0, 4 terrain), 3 environmental
rejections and 1 interrupted attempt. The first d0 slot exhausted its three attempts: two foreground/background
rejections, then the user's temporary request to finish validation only interrupted its final attempt. The user
subsequently restored authorization for the idle scored run; resumption retained that interruption and exhausted
slot, rather than resetting or reusing it. A later d0 slot rejected repeat-07 at 0.66 background cores and passed
its whole-launch retry. The predefined minimum of three valid calibrations per scene was met.

Early rejection windows identify ChatGPT as their largest named background CPU consumer. After the user minimized
the host and left the game in front, subsequent launches passed except for that retained d0 background disturbance.
No unrelated app was closed beyond the approved Roblox, Zen Browser and Discord processes.

The immutable `calibration.json` nevertheless says FAIL, solely: "calibration launches ran with different
configurations". No reference-spread gate failed. `pilot.json` / `pilot.md` were generated with the original frozen
evaluator: no passing calibration, no pilot pairs, stability and 2% power unresolved, validity invalidated. That
verdict and all original protocol, manifest, source ZIP, attempts and records remain untouched.

This revealed a validator defect: `build_record` hashes a phase's full exported configuration. Terrain includes
extra scene settings, including backend identity, which d0 does not export. The old calibration incorrectly
required a single hash across *different scenes*, even though it already used per-scene reference/backend
baselines. The correction requires one hash within each scene, and every pilot launch must match its own scene's
baseline. It still rejects within-scene drift, another scene's hash, and missing scene baseline data. Four new
regressions failed before the correction and passed afterward. No numeric performance or environmental threshold
changed, and the original failed calibration is never rescored.

A fresh series, [aa31w2](windows2/README.md), records the scope amendment before scoring and uses new labels,
fresh smoke, a new freeze, fresh calibration and the same eight A/A pairs per scene. An automated guard compares
both protocol files and ensures that only series metadata and the explicit configuration-scope declaration differ.
The batch wrapper prints and defaults to the new protocol; `ALACER_AA_PROTOCOL` can explicitly override it.

Fresh `prepare` stopped before any new client launch when a synthetic attempt's atomic JSON replacement received
`PermissionError: [WinError 5] Access is denied`. Its log is retained. Windows metadata replacement now retries up
to six times with at most 0.75 seconds total backoff; a permanently denied replacement still raises, leaves the old
target intact and retains the complete pending temporary JSON. Tests cover transient recovery, permanent failure
and the absence of retry on non-Windows platforms. The original frozen series is unaffected.


## Fresh aa31w2 preparation passed

The Java 25 build passes 131 tests. The complete Python suite discovers 107 tests: 105 pass and two
Linux-only tests skip. Fresh d0 and terrain smoke both pass every environmental rule;
[windows2/validation.json](windows2/validation.json) records coverage, processor counts, stillness and
checksums of the retained setup/test logs. No client source or compiled artifact changed for the
configuration-scope correction. Fresh calibration and the full pilot are pending the new freeze.


## Fresh aa31w2: terminal calibration failure

The new freeze (`6b75fc5`, after fixes `39a0a5a`) completed 12 calibration launches: 7 accepted
(3 d0, 4 terrain), 5 rejected for background CPU. d0 slot 4 exhausted all retries; terrain slot 4
passed on its third attempt. Every attempt is preserved. Calibration FAILS the unchanged 5% rule:
terrain/reference-pre robust frame-mean spread is **12.7093%**. This measured failure is distinct
from the original aa31 validator defect. No source/tooling changed during these measurements.

The runner stopped before any pilot pair. Full pilot: NOT RUN; 3% paired stability, paired confidence
intervals and 2% power: UNRESOLVED. The monitor correctly rejected observed disturbances, but
the system is not yet qualified for 1–2% A/B claims. See the complete [outcome](windows2/RESULTS.md)
for scenario medians and descriptive variation, every acceptance/rejection, CPU attribution and limits.

Across all 126 calibration phases, critical telemetry and foreground were 100%, all 4,328,653 processor
samples were known P-core samples, and there were no monitor errors. All frozen hashes and 308 raw
result-file hashes were verified after scoring. The client and runner exited, foreground automation
stopped, and the original Ultimate Performance plan was restored. Raw data remain locally preserved.
A new separately frozen series requires diagnosis of the terrain timing groups and fresh calibration;
this failed archive is never rescored. No rendering defaults or candidate selections changed.
