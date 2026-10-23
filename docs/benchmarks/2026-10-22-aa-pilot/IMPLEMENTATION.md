# Session 31: A/A stability infrastructure, implementation and validation report

This report covers the original implementation and container validation. Current Windows validation is tracked in
[WINDOWS.md](WINDOWS.md). The original report below is not a measurement: the pilot protocol is
[README.md](README.md) with [protocol.json](protocol.json), and it has not yet run on the reference machine. Every
launch described here ran in a Linux cloud container with Mesa llvmpipe (software GL, about 20 FPS); those launches
tested the tooling and judge nothing about frame-time stability.

## Deliverables

| # | Deliverable | Where |
|---|---|---|
| 1 | Windows A/A runner: plan, smoke, freeze, calibrate, pilot (resumable), overhead, evaluate, status | `tools/aa_pilot.py`, `tools/aa_rules.py`; `tools/aa_pilot_windows.bat` (check, prepare, run, sitting N) |
| 2 | Environmental monitor, standard library only, availability per source, self-cost measured | `tools/aa_monitor.py`, `tools/aa_platform.py` (Windows `ctypes` probes; Linux `/proc` probes for development) |
| 3 | Reference-phase validation against an independent calibration; whole-pair rejection and retries | `BenchmarkRunner` + `StabilityOptions` (reference phases), `aa_rules.build_calibration`, `validate_environment`, `pair_identity`; `Runner.decide_pair` |
| 4 | Per-scene statistical evaluator and report | `tools/aa_stats.py`, `tools/aa_report.py` -> `pilot.json`, `pilot.md` |
| 5 | Automated tests | 7 new Java tests (`StabilityOptionsTest`, `ProcessorSamplerTest`); 80 Python tests under `tools/tests/` |
| 6 | Configuration and execution on the reference hardware | [README.md](README.md), "Running it on the reference machine"; root README, "Benchmarking" |
| 7 | Proposed frozen Session 31 pilot protocol | [README.md](README.md), [protocol.json](protocol.json) (frozen on the reference machine by `freeze`) |
| 8 | This report | `IMPLEMENTATION.md` |

## What changed in the client (all opt-in; defaults and old scenarios unchanged)

`StabilityOptions` parses new `alacer.benchmark.*` properties; malformed values are errors, never defaults. With none of
them set, `BenchmarkRunner` builds exactly the old phase list (the scenario code moved unchanged into
`addScenarioPhases`), writes the same summary fields, and adds nothing to the environment. With them:

- `reference=<s>` / `referencePost=true`: `reference-pre` and `reference-post` phases at the default static view;
  in a toggle run they record with the option off.
- `phaseSeconds=name:s,...`: literal recorded lengths for named scenario phases; unknown names are errors.
- `repeatSegments=<n>`, `repeatSegmentSeconds`: identical in-process segments `repeat-NN` at the reference view.
- `toggleBlocks=<n>`, `toggleSegmentSeconds`: with `benchmark.toggle`, static segments in ABBA/BAAB blocks
  (`static-NN-a1/b1/b2/a2`), balanced for linear drift; pan segments are not repeated.
- `processorSampling=true`: `ProcessorSampler` calls `GetCurrentProcessorNumberEx` (Windows) or `sched_getcpu` (Linux)
  through the Java 25 foreign-function API once per recorded frame on the client thread; per-phase histograms go to
  `summary.json` (`client_processor_frames`, `client_processor_unknown_frames`). Unavailable means -1, never 0.
- `mobPoseCheck=true`: mob positions and body/head yaw at the first and last recorded frame of each phase
  (`mob_pose` per phase).
- `freezeMobs=true`: NoAI on every loaded mob before each phase settles.

No render path, option default, mixin or `render.defaults` member changed. No third-party code was used.

## Design decisions worth knowing

- **Monitoring in the runner, not the client.** One thread, once per second, Windows APIs through `ctypes`; no
  PowerShell or WMI per sample (sessions 26-30 started a PowerShell `Get-CimInstance` every second during launches,
  itself a background load; the new runner reads the client's command line once, through
  `NtQueryInformationProcess`, with PowerShell only as a fallback).
- **Harness versus background.** The runner's process tree, the Gradle daemon and nvidia-smi are "harness"; the client
  is identified by its command line; everything else is background. Busy CPU that no readable process accounts for is
  reported separately and only flagged (it includes the graphics driver's DPCs for the client itself).
- **Core type from the operating system.** P/E classes come from `GetLogicalProcessorInformationEx` efficiency classes;
  a test covers a machine that numbers efficiency cores first.
- **Rules read only reference phases and monitoring.** A test checks that a launch whose scored phases are 30% slower
  but whose reference is normal passes: the A/A data cannot tune their own acceptance.
- **Pairs, not sides.** If the first launch of a pair is rejected the second is not run; rejected pairs are repeated
  whole under new labels; the evaluator uses accepted pairs only and re-checks both sides and their identity.
- **Pooled power counts scenes, not phases.** The first pooled model treated 7 scene-phases x 16 pairs as 112
  independent pairs; a test showed it reporting "pooled only" power where it should not, because phases of one launch
  share its machine state. The pooled bootstrap now resamples whole pairs and the planning model counts pairs per scene.
- **`mods` order.** Session 30's archived summaries list the same mods in different orders across identical launches
  (loader hash order); a strict environment comparison would have rejected identical pairs. The comparison sorts them.

## Validation in the container (tooling only; software GL)

- **Build and Java tests**, JDK 25 (Temurin 25.0.5, `-Porg.gradle.java.installations.paths`, a Maven mirror init script
  because Maven Central rate-limits the container): `BUILD SUCCESSFUL`, 130 tests, 0 failures (123 before).
- **Python tests**: `python3 -m unittest discover -s tools/tests -p "test_aa_*.py"`: 80 tests, 0 failures. They use
  synthetic data and session 29/30 archive files (read only): statistics, schedule, labels, resume, crash and interrupt
  handling, retry limits, one-sided rejection, tampered acceptance, every validation rule, calibration, pair identity,
  missing telemetry, topology parsing, result-folder and log parsing, and live Linux probes.
- **Live launches** (d0, time scale 0.25, 854x480): see README.md, "Validation so far".

Container launches, all under software GL (tooling only):

| Run | What it showed |
|---|---|
| `aa31c-smoke-d0` | first real launch: every runner check passed; reference, override, repeat and post-reference phases recorded as planned; processor sampling and monitor coverage complete; found the world's own animals turning up to 148 degrees in still phases, and the mob-count rule firing wrongly in a moving phase (both fixed: `freezeMobs`; mob rules on still phases only) |
| `aa31c2-smoke-d0` | `freezeMobs` froze 101 mobs; mob turns 0 in every still phase |
| `aa31c2` series | tooling edited after the freeze during calibration: the launch was rejected ("changed during the launch"), every retry refused against the manifest, calibration failed, the pilot refused to start |
| `aa31c3` | stopped with SIGINT during its smoke (again after a tooling edit): attempt `interrupted`, client killed, freeze refused without a passing smoke; calibration then wasted retries on "not frozen", so `calibrate` and `pilot` now refuse up front |
| `aa31c4` series | complete: smoke, freeze, calibration (2/2), pilot with a live SIGINT in pair 2's second launch, resume (pair rejected, repeated as `-r2`, accepted), evaluation ([container-tooling-test.md](container-tooling-test.md)); launches 97-118 s against 108 s planned; monitor 0.39% of one core |

All scratch archives stay in the ignored `build/` folder; none is evidence.

## Not verified, and the conservative policy for each

| Item | Status | Policy until verified |
|---|---|---|
| Windows probes (processes, CPU times, command lines, topology, PDH clocks, foreground, power plan, affinity, keep-awake) | written against the documented APIs; parsers unit-tested on synthetic buffers; never run on Windows | `check` and `smoke` on the reference machine first; a failing critical probe rejects launches (never passes them) |
| P-core affinity on the i7-12700F | not run | the policy refuses to run unless the OS reports two efficiency classes in one group; amend to `none` before the freeze if needed |
| Effective clock (`% Processor Performance`) and nvidia-smi | not run | flags only, correlated with pair changes |
| Monitor overhead on Windows | measured per launch (self CPU), not yet observed | reported; optional overhead stage |
| Thresholds (0.5 cores, +/-5%, CPU bands) | proposals | calibration must pass them; if an idle machine cannot, amend before the pilot |
| Frame-time stability of the reference machine | unknown | this is what the pilot measures |
