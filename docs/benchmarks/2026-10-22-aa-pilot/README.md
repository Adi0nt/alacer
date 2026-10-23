# Session 31: A/A pilot protocol and stability infrastructure

**Status: Windows integration and corrected smoke passed; calibration and pilot pending.** Reference-hardware smoke findings and retained
rejections are recorded in [WINDOWS.md](WINDOWS.md). Smoke runs are not measurements of the machine's noise floor.
The proposed series is [protocol.json](protocol.json) with this text; it is
frozen by `tools/aa_pilot.py freeze` on the reference machine (hash manifest and sources ZIP, session 27's method)
before its first calibration launch. Only container tooling tests have run so far (section "Validation so far"); they
used software GL and judge nothing.

This is infrastructure, not an optimization: both sides of every pair are the same build, JVM, options, world copy
and machine settings, with every experiment absent or off. No render option is toggled to make an A/B comparison, no
default changes, and no earlier session is rescored. It implements the session 31 proposal in
[ARCHITECTURE.md](../../ARCHITECTURE.md) ("Measuring 1-2% effects") after the
[noise-floor analysis](../2026-10-22-noise-floor/README.md), which found that session 30's spread came mostly from a
20-minute block in which whole launches ran 58-373% slower.

## Question and decision rule

Can the reference machine (Windows 11, i7-12700F, RTX 3060 Ti, Java 25, 1920x1080) run back-to-back launches whose
frame times agree closely enough that a future series could resolve a 1-2% effect? The pilot answers three separate
questions, and the report keeps them separate:

1. **Stability gate (predeclared):** in every required scene-phase, the robust per-pair spread of frame mean,
   S = 1.4826 x median |ln(B / A)| reported as e^S - 1, is **at most 3%**, with all **8 pairs accepted** per scene.
   Required: d0 `static`, `pan`, `fly`; terrain `steady`, `pan`, `sort`, `fly`. If any slot cannot be accepted within
   its retries, the gate is *unresolved*, not passed.
2. **Power for 2%:** the smallest true change that the median of **16 pairs** (the planned size of a future series per
   scene) detects with 80% power at a two-sided 5% level (MDE80), from the pilot's own pairs, is at most 2% in every
   required scene-phase. Passing the gate does not imply this: a 2.9% spread passes the gate and still needs about 26
   pairs for 2% (normal model). A pooled figure over scenes is reported separately and labelled "pooled only".
3. **Validity:** whether disturbances or other issues undermine the conclusion: more than 25% of pair attempts
   rejected, any slot that exhausted its retries, an incomplete pilot, no passing calibration, a time trend in the pair
   changes (Spearman permutation p < 0.01), or |change| rising with an environmental covariate (p < 0.01).

If the gate fails, the remedy is the machine state, not the gate (the session 31 proposal): find the disturbance in
the monitoring data, fix it, and run a new series under a new name. A passing pilot permits planning a candidate
series; it selects nothing.

## Series design

Stages are separate commands and run in this order; each refuses to run out of order.

| Stage | Command | What it does |
|---|---|---|
| plan | `aa_pilot.py plan` | Prints labels, order, phase lengths and expected time. No launch. |
| smoke | `aa_pilot.py smoke` | One launch per pilot scene with the full protocol. Shapes tooling; attempts, never evidence. |
| freeze | `aa_pilot.py freeze` | Manifest: sources, tooling, this README and protocol.json, compiled artifacts, base world, shared config, JDK binaries; sources ZIP. Refuses if frozen or if a smoke has not passed per scene. |
| calibrate | `aa_pilot.py calibrate` | 4 launches each of d0 and terrain, interleaved; writes `calibration.json` once. |
| pilot | `aa_pilot.py pilot` | 8 A/A pairs each of d0 and terrain. Resumable. |
| overhead | `aa_pilot.py overhead` | Optional, exploratory: 2 d0 pairs with the monitor on/off. Not A/A and never pooled. |
| evaluate | `aa_pilot.py evaluate` | `pilot.json` and `pilot.md` from the archive only. Refuses while a runner holds the lock. |

**Scenes.** The runner supports d0, d200, terrain, near and high (sessions 26-30's scenes). The pilot runs d0 (default
static/pan/fly path, 0 placed mobs, time scale 2) and terrain (terrain backend scenario, time scale 2; its two warmups
are recorded, never judged). Every launch passes every render, verification, profiler and benchmark option explicitly
(sessions 28-30's lists plus `render.reuseShadowPositions` and `render.fastCameraSectionFaces`, all off), with
display lists (`benchmark.vbo=false`), view distance 12, a 2 GiB heap and a fresh copy of the seed-1789 base world.

**Phases (opt-in options added in this session; old scenarios unchanged).**

| Scene | Recorded phases (seconds) | Launch, about |
|---|---|---|
| d0 | `reference-pre` 20, `static` **60**, `pan` 30, `fly` 40, `repeat-01`..`repeat-08` 8 each, `reference-post` 20 | 5.5 min |
| terrain | `reference-pre` 20, `warmup-pan` 30, `warmup-sort` 32, `steady` **60**, `pan` 30, `sort` 32, `fly` 40, `reference-post` 20 | 5.8 min |

- The **reference phases** record the default static view (anchor, ground + 1, yaw 45, pitch 5) before and after the
  scored phases, identically on both sides. `reference-post` follows the flight, so its chunks reload first (the usual
  settle waits until no chunk build has been pending for 2 s).
- Still phases are **60 s** (`benchmark.phaseSeconds`), so a tick-frame p99 rests on 12 tick frames instead of 4. The
  tick-frame mean and p90 are reported beside it.
- The eight **repeat segments** in d0 are identical in-process segments at the reference view (no option changes),
  scored as ABBA blocks to compare in-process with separate-launch stability.
- **Stable entities:** the "0 mob" world is not mob-free: the generated world holds about 90-100 loaded animals with AI,
  about 6-9 of them near the anchor, and they turn and walk during still phases (container smoke: heads turned up to
  148 degrees within one phase). `benchmark.freezeMobs` sets NoAI on every loaded mob before each phase settles (the
  benchmark's own placed mobs are NoAI already), and `benchmark.mobPoseCheck` records each phase's largest mob
  movement and turn.
  **Windows amendment before scoring:** NoAI alone still allowed a mob to move 0.26 blocks during a focused static
  phase. `BenchmarkMobFreezeMixin`, applied only with both benchmark and freezeMobs enabled, now suppresses mob ticks
  on client and integrated server, including newly loaded mobs, and clears velocity. NoAI remains set at phase
  boundaries. This changes this opt-in benchmark control, including server simulation cost; it changes no normal
  rendering default. Both A/A sides and their new calibration use the corrected control. The 0.25-block gate is unchanged.
- `benchmark.processorSampling` records, every recorded frame, the logical processor the client thread is on
  (`GetCurrentProcessorNumberEx` through the Java 25 foreign-function API).

**Order.** Pairs run serially, both launches of a pair back to back. Pair n of d0, then pair n of terrain, before pair
n + 1 of either. Nominal sides alternate per pair in the pattern (A, B), (B, A), (B, A), (A, B), repeated; A and B differ
only in their label. Labels: `aa31-pl-<scene>-<n>-<a|b>`, retries `-r2`, `-r3`; calibration `aa31-cal-<scene>-<i>`.
Expected time without retries: about 3.8 hours (calibration about 45 minutes, pilot about 3.0 hours), including
the pre-launch quiet window; the same estimate was within 10% of the container launches' durations.

**Identity of the two sides.** Before a pair is accepted the runner checks: both launches passed; launch options
identical except the label (hash); the observed launcher command lines identical except the label; the same launcher
executable (the protocol's JDK, `-Xmx2G`, no JFR flags, every `-Dalacer.*` value); identical recorded phase
configurations; identical exported environment except volatile keys (wall-clock epoch, settle time, the label, and the
order in which the loader lists mods, which varies between identical launches in session 30's archived summaries); the
same manifest, compiled artifacts, JDK hashes, hardware snapshot, expected affinity and phase list; and no more than
420 s between the end of the first and the start of the second launch.

**Retained attempts and resumption.** Every attempt keeps its file under `attempts/` with its status (`passed`,
`failed-checks`, `rejected-env`, `interrupted`, `error`) and reasons; `records/` keeps each completed launch's metrics,
monitoring summaries and a 5 s time series; `pairs/` keeps every pair decision. Labels, logs, result folders and
records are never reused or overwritten, and the runner refuses any archive folder that holds a different
`protocol.json` (so no historical archive can be written to). After an interruption, `pilot` marks launch attempts
left `preflight` or `running` as `interrupted`, decides any undecided pair (rejected unless both launches passed and
the identity checks hold, recorded as `decided_on_resume`), and continues; accepted pairs are never repeated and an
incomplete pair is never completed or reused. A runner lock prevents two runners on one archive.

## Monitoring

`tools/aa_monitor.py` samples once per second in a thread of the runner (standard library only; Windows APIs through
`ctypes`). Raw samples go to `run/alacer/stability/<series>/<label>.jsonl` (ignored, hashed in the attempt); the record
keeps per-phase summaries and a 5 s series. Every source carries a status per sample (`ok`, `unavailable`, `error`) with
a reason, and summaries count how often each answered; a missing value is `None`, never zero.

| Source | How | Critical |
|---|---|---|
| CPU: system busy, client process, harness (runner, Gradle, nvidia-smi), every other process, top 8 by name | `GetSystemTimes`, Toolhelp32 + `GetProcessTimes` per process (keyed by PID and start time) | yes |
| Client-thread CPU per frame, process CPU per frame | `ThreadMXBean` / `OperatingSystemMXBean` inside the client, per phase (existing) | (metrics) |
| Client-thread processor, per frame | Java `GetCurrentProcessorNumberEx`; core class from `GetLogicalProcessorInformationEx` efficiency classes (never from processor numbers) | yes |
| Effective CPU clock, overall and per core class | PDH `\Processor Information(*)\% Processor Performance` x `Processor Frequency` | flag only |
| Foreground application | `GetForegroundWindow` -> owning process's executable name only (no window titles or contents) | yes |
| Power plan | `PowerGetActiveScheme` | yes |
| Client affinity and priority | `GetProcessAffinityMask`, `GetPriorityClass` | yes |
| GPU utilization, graphics/SM/memory clocks, P-state, temperature, power, clock-event reasons | one `nvidia-smi --query-gpu ... -lms 1000` process | flag only |
| GPU backend | exported `gl.*`, `vboActive`, `backend.renderChunkClass` | yes (must match calibration) |
| Available memory | `GlobalMemoryStatusEx` | flag only |
| Mob movement and turns per phase | client-side positions at phase start and end | yes (still phases) |
| Monitor's own cost | thread CPU time per sample; nvidia-smi counted as harness | reported |

The monitor costs about 3-4 ms of CPU per sample in the container (0.3-0.4% of one core); on Windows it is measured in
every launch (`monitor_self_cores`), and the optional overhead stage bounds its effect on frame time coarsely. Windows
thread CPU clocks advance in about 15.6 ms steps, so per-sample self-cost is quantized; its mean over a launch is not.

**Affinity policy `p-cores`.** When the client process appears (within about a second of its start, well before the
world loads), the runner sets its process affinity to the logical processors of the highest efficiency class and
checks that it took. `SetProcessAffinityMask` constrains every thread, including ones created earlier. The policy
refuses to run if the topology is unavailable, not hybrid, or spans more than one processor group. Both sides and the
calibration get the same mask. Verification: the affinity is sampled every second, and the client thread's processor
is sampled every frame; a frame on an efficiency core rejects the launch.

## Acceptance rules (predeclared)

A launch is rejected, and its whole pair with it, if any rule fails. Rules read only the reference phases, the
monitoring data and the runner checks, never the scored phases' frame times, so the scored A/A data cannot tune their
own acceptance.

| Rule | Limit |
|---|---|
| Runner checks | as sessions 29-30: no other client before, during or after; launcher verified; sources, tooling, artifacts, world, config and JDK equal to the manifest before and after; Gradle compiled nothing; exported environment (Java 25.0.4.1 Adoptium, RTX 3060 Ti renderer, window, display lists, view distance, vsync, mobs, flags); phase list and lengths; zero dropped frames; one discarded setup frame; experiments off and verification off in every phase config; no GL errors, mixin failures, aborts, timeouts or non-zero mismatch lines |
| Reference frame mean (`reference-pre` and `reference-post`) | within +/-5% of the calibration median of the same scene and phase |
| Reference client and process CPU per frame | within +/- clamp(3 x calibration robust spread, 5%, 15%) of the calibration median |
| Background CPU (other processes) in every recorded phase | mean <= 0.5 cores and 5 s rolling peak <= 1.5 cores |
| Pre-launch quiet window | 10 s of consecutive fresh, finite CPU readings, mean <= 0.5 cores; a gap or unavailable reading resets the window; expiry at 300 s rejects without starting a client |
| Foreground | the client in front in >= 95% of samples of every phase |
| Power plan | one plan for the whole launch, High performance (`8c5e7fda-...`), equal to calibration's |
| Affinity | constant and equal to the P-core mask; no client-thread frame on an efficiency core |
| GPU backend and platform identity | equal to calibration (`gl.vendor`, `gl.renderer`, `gl.version`, `vboActive`, backend class, OS, CPU, Java) |
| Configuration | phase configuration hash equal to calibration's |
| Critical telemetry | CPU, power plan, affinity, foreground and processor sampling answered in >= 90% of each phase's samples; monitor coverage >= 90% of expected samples |
| Still scene | in still phases, no mob moved more than 0.25 blocks and the mob count did not change |

Flags (reported and correlated, never rejections): P-core effective clock more than 10% from calibration's median,
missing GPU telemetry, available memory below 2 GiB, more than 1 core of busy CPU attributed to no readable process.
The clock and GPU sources are flags because their reliability on this machine has not been verified yet.

**Calibration.** Four launches each of d0 and terrain (full launches, same options), interleaved, run before the pilot
and checked against the absolute rules above. A failed calibration launch is repeated under `-r2`, `-r3`. Calibration
passes if each scene has at least 3 valid launches, the robust spread of their reference frame means is at most 5%,
their GPU backend, power plan and configuration are identical. It is written once to `calibration.json`; a failed
calibration stops the series (`pilot` refuses to start), and redoing it needs a new series name.

**Retries.** A rejected pair is repeated whole at the end of the schedule under new labels, at most 3 attempts per pair
slot. If the first launch of a pair is rejected, the second is not run. One side is never discarded or replaced to
improve a result: statistics use accepted pairs only, and the evaluator re-checks that both launches of every
accepted pair passed and are identical.

## Statistics (`tools/aa_stats.py`, `tools/aa_report.py`)

Per scene, phase and metric (frame mean, p95, p99, slow-1% mean, tick-frame mean, p90 and p99, client and process CPU
per frame, allocation per tick-free frame, `terrain_setup`), from accepted pairs in time order, with d = ln(B / A):

- per-pair percentages and log ratios; median and mean; robust spread around zero (the gate), MAD around the median,
  RMS and largest |d|; outliers by modified z-score > 3.5, flagged and never removed;
- time structure: lag-1 autocorrelation, effective n = n (1 - r1) / (1 + r1) for r1 > 0, and a rank trend test;
- environmental correlations of |d| (gate metric): background CPU, its difference between sides, the reference
  phase's deviation, clock and GPU-clock differences, efficiency-core share;
- the observed median's bootstrap 95% interval and the gate spread's bootstrap interval;
- null resolution R95 and MDE80 at the pilot's n and at 16 pairs, from two models: sign-flip resampling of the observed
  |d| (keeps the tails; cannot exceed the largest observed) and normal with the robust spread; the larger MDE is used,
  inflated by sqrt(n / n_eff) when pairs are correlated; pairs needed for 1%, 2% and 5%;
- pooled over scenes (labelled pooled): whole pairs are resampled within each scene, keeping their phases together,
  and the planning model counts independent pairs per scene, not per phase;
- in-process against separate-launch stability: the spread of d0's repeat-segment ABBA blocks (2 x 8 s against 2 x 8 s
  in one launch) beside the spread of `reference-pre` pairs (20 s each, separate launches); lengths differ, so only
  orders of magnitude compare.

Scenes and phases are never assumed to share a variance. A resampled interval from 8 pairs is a planning estimate, not
proof of measurement accuracy; with 8 pairs the gate's spread estimate itself is uncertain, and its interval is
reported beside the gate.

## Outputs

`pilot.json`: protocol hash, manifest, calibration, every launch's status and reasons, every pair decision, slot
states, monitoring summaries per launch and phase, 5 s time series, per-scene-phase results, pooled results, in-process
comparison, exploratory overhead, and the three-part verdict. `pilot.md`: the verdict, gate rows with their
intervals and MDE80, per-scene-phase tables, pair decisions and monitoring availability. Raw CSVs, logs and monitor
samples stay in the ignored `run/` and `build/` folders, hashed in the records.

## Running it on the reference machine

Prerequisites: the shared checkout's base world `run/saves/alacer-benchmark` (hash-checked by the freeze), Adoptium
25.0.4.1 at the protocol's path, Python 3.9 or newer (numpy only for `evaluate`), the NVIDIA driver's `nvidia-smi` on
`PATH` (optional; GPU telemetry is a flag source), and the High performance power plan
(`powercfg /setactive SCHEME_MIN`). The batch file checks and never changes machine settings.

```bat
tools\aa_pilot_windows.bat check      & rem schedule, a 10 s monitor reading of this machine, status
tools\aa_pilot_windows.bat prepare    & rem attended: build + tests, smoke per scene; stops for inspection
python tools\aa_pilot.py freeze --protocol docs\benchmarks\2026-10-22-aa-pilot\protocol.json
tools\aa_pilot_windows.bat run        & rem unattended (about 3.8 h): calibrate, pilot, evaluate; rerun resumes
tools\aa_pilot_windows.bat sitting 6  & rem or: calibrate if needed, then at most 6 pairs per sitting
```

Before standalone `run`: close browsers, chat, music and the agent app; keep the game window in front (the runner keeps the
display on and the machine awake while a stage runs); do not build, analyse or edit the checkout until the stage ends
(the manifest check rejects any change, as it did in the container test below). After `run`, read `pilot.md`, inspect
rejected attempts and their monitoring data, then write the results section of this README.

For the assisted reference run, the user authorized an idle sitting and closing Roblox, Zen Browser and Discord;
those processes were stopped before the qualifying smoke. The Codex host remains resident to operate the startup
focus helper. Its CPU is still counted as background, and every quantitative CPU, foreground and telemetry gate
remains unchanged. This operator amendment is recorded before freezing and before any scored launch.

`prepare` does not freeze automatically. Inspect every smoke record and its `environment_rejections` first;
`freeze` refuses a scene without environmentally valid smoke evidence. A failed smoke launch returns a nonzero
exit code. The Windows monitor check also fails on missing critical probes, rather than printing an empty success.

For this attended Windows validation, `tools/aa_foreground_windows.mjs` runs through the Computer Use `node_repl`
and its imported `@oai/sky` client. Its `service(45000)` method must be awaited inside each active tool call; background
Computer Use calls have no execution context and are unsupported. It watches attempt-file changes and activates the uniquely identified Minecraft
window once per newly observed client, before the reference phase. It never reclaims focus during a launch; the
unchanged 95% foreground rule still rejects subsequent interference. Activation events are preserved under
`run/alacer/stability/aa31/foreground-helper.jsonl`. The helper source is included in the freeze. Its file watcher
is idle during recording; UI enumeration is performed only while waiting for a new client window.

## Validation so far (container tooling tests; not evidence about the reference machine)

All in a Linux cloud container (4 vCPU Xeon, Mesa llvmpipe, about 20 FPS), where frame times judge nothing.

- Build and tests on JDK 25 (Temurin 25.0.5): 130 Java tests, 0 failures (123 before this session). 80 Python tests
  (`python -m unittest discover -s tools/tests -p "test_aa_*.py"`), 0 failures: statistics (including reproducing the
  noise-floor README's 16.7% robust spread from session 30's archived still pairs), schedule and labels, resume after
  interruption and after a hard crash, retry limits, prevention of one-sided rejection, tampered acceptances,
  validation rules (each one triggered), calibration bands, pair identity on synthetic and archived data, missing
  telemetry, processor topology parsing (including a machine that numbers efficiency cores first), result-folder and
  log parsing.
- A live smoke launch per change (`aa31c-smoke-d0`, `aa31c2-smoke-d0`, d0, time scale 0.25): every runner check passed;
  reference, 6 s override, repeat and post-reference phases recorded as planned; `sched_getcpu` sampling answered every
  frame; monitor coverage 1.0 in every phase; launcher detected and verified; `freezeMobs` froze 101 mobs and mob turns
  in still phases fell from up to 148 degrees to 0.
- The first container series (`aa31c2`) was frozen, then tooling was edited during its first calibration launch. The
  runner rejected that launch ("source_sha256 changed during the launch"), refused every retry against the manifest,
  failed the calibration and refused to start the pilot. Its scratch archive is kept in `build/`.
- A complete container series, `aa31c4` ([container-tooling-test.md](container-tooling-test.md), its generated
  report; raw archive in the ignored `build/aa31c4-container-series/`): smoke passed; freeze; calibration 2 of 2
  launches passed; pilot pair 1 accepted; pair 2's second launch was interrupted with SIGINT 40 s in (the attempt was
  marked `interrupted`, the client was killed, exit -9), the runner was restarted, the pair was rejected on resume
  and repeated whole as `aa31c4-pl-d0-2-r2`, which was accepted; `evaluate` wrote `pilot.json` and `pilot.md`. Launches
  took 97-118 s against a planned 108 s. The monitor used 3.9 ms of CPU per second (0.39% of one core). Power plan,
  foreground and clock telemetry are unavailable on Linux and were reported as flags, never as values. The numbers in
  that report come from 2 pairs under software GL and say nothing about any machine's stability.

## Limitations and what remains to verify on the reference machine

- The Windows probes (Toolhelp32, PDH, `NtQueryInformationProcess` command lines, `GetLogicalProcessorInformationEx`,
  `SetProcessAffinityMask`, foreground, power plan, `GetCurrentProcessorNumberEx`) are implemented and their parsers are
  unit-tested on synthetic buffers, but none has run on Windows yet. `tools\aa_pilot_windows.bat check` and the smoke
  stage are the first real test; any failure there is an amendment before the freeze.
- The P-core policy has not been verified on the i7-12700F; Windows must report two efficiency classes there (it does
  for Alder Lake according to its documentation). If the smoke shows otherwise, the protocol must be amended to
  `"policy": "none"` before the freeze, and the E-core share then becomes a reported quantity, not a rule.
- `% Processor Performance` and nvidia-smi clocks are flags until their behaviour on this machine is known.
- Busy CPU that no readable process accounts for (System, protected processes, interrupts and DPCs, which include the
  graphics driver's work for the client) is reported and flagged, not gated.
- The reference view differs between scenes (terrain's glass walls), so each scene has its own calibration.
- Eight pairs pin a spread only roughly; the gate reads the point estimate as predeclared and reports its interval.
- The limits (0.5 cores background, +/-5% reference) are proposals from the session 31 analysis, not measured on this
  machine; if calibration shows them impossible to meet on an idle machine, amend before the pilot, not after.
