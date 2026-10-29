# 1.0 default qualification: stopped on verifier crash

The user subsequently authorized repair and continuation. See the separate
[repaired campaign](resume-1/README.md): the redirect crash was fixed, 21 fresh
launches completed, three options failed correctness gates, and the remaining
performance results failed gates or were inconclusive. This original stopped
attempt remains preserved below.

**The campaign stopped on its first verifier launch. No option passed correctness
qualification, and no A/B performance launch occurred.** The frozen
[protocol](PROTOCOL.md) requires stopping on a functional failure. No retries,
remaining verifier launches, performance runs, rendering fixes or default changes
were made. This is an incomplete qualification with a concrete blocking defect,
not a performance verdict. Standard mode cannot support a release decision or a
small-improvement claim even if a later campaign completes its gates.

Archive date follows the requested directory name; execution was **2026-10-28,
17:30–17:38 America/Los_Angeles** (2026-10-29 UTC).

## Build and setup evidence

Source and tag: `941405bf0a2afbb18785f8dd36d1d285dc37c633`, `v1.0.0`.
Work branch: `codex/1.0-qualification`; main and tag unchanged, no push.
Windows 11 build 26300, Intel i7-12700F (12 cores / 20 logical processors),
NVIDIA RTX 3060 Ti, driver 616.92, OpenGL 4.6.0. Temurin **25.0.4.1+1-LTS**
explicitly selected for Gradle, compiler, tests and client; classfile major 69.
1920x1080, 2 GiB heap, G1, compact headers off by JVM default, VBOs on,
view distance 12, fancy graphics, maximum smooth lighting, mipmaps 4, vsync off.
Benchmark world seed 1789, no benchmark-spawned mobs/chests.

The baseline command `./gradlew clean build javaRuntimeInfo` succeeded in 48 s:
**352 tests, 0 failures/errors/skips**. `cd tools; python -m pytest -q tests`
passed **119 tests, 2 skipped, 9 subtests** in 129.51 s. Both completed before
the first client. Compiler/deprecation, Gradle and OneDrive-location warnings are
retained in [the build log](build-evidence/baseline-build.log); per-suite JUnit XML
is retained in `build-evidence/`. These checks do not establish native rendering
correctness or performance. No builds/tests/offline analysis ran beside a client.

Built remapped JAR SHA-256:
`98e06d881a4535770820bec12d9882ff0b2bfe6809966a40eb348225bb733eab`.
ZIP inspection confirmed `COPYING_alacer`, `COPYING.LESSER_alacer` and classfile
major 69. **The prescribed `runClient` task loads development classes/resources;
it does not load that remapped JAR.** The final packaging edit built successfully,
but the actual distributable JAR remains unlaunched/unverified.

The persisted config enabled modernPipeline/terrainShader and pacing experiments
and changed visual settings. It was backed up under ignored `build/qualification/`,
temporarily moved aside for the manual default-config smoke, and restored after
exit. Original `options.txt` was backed up and restored too. Benchmarks explicitly
report `build-defaults-and-jvm-overrides`, ignoring persisted Alacer config.
Final config/options/base-level hashes are in [results.json](results.json).
The manual smoke used disposable `alacer-benchmark-run`, leaving the base world
available for fresh benchmark copies. No other application was closed or changed
by this task; the user quieted background applications after preflight detected
Roblox Studio load.

## Commands and every client launch

Commands below ran from repository root unless indicated. Java environment for
Gradle/client: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`;
the baseline also prepended its `bin` directory to PATH. Python was
`C:\Python313\python.exe`. `launch.py` sets JAVA_HOME explicitly and invokes
`cmd.exe /d /c gradlew.bat`; the complete argv, actual client executable, JVM
command line, affinity, timestamps and hardware are in each launch JSON.

| Command | Result |
|---|---|
| `git switch -c codex/1.0-qualification` | Created branch at release commit |
| `./gradlew clean build javaRuntimeInfo` | Passed; 352 JUnit tests, all four Java runtimes explicit |
| `cd tools; python -m pytest -q tests` | 119 passed, 2 skipped, 9 subtests passed |
| `python docs/benchmarks/2026-10-29-defaults-qualification/launch.py manual` | One non-benchmark default smoke, normal exit 0 |
| `python docs/benchmarks/2026-10-29-defaults-qualification/launch.py q-smoke` | One plain benchmark, normal exit 0, complete summary |
| `python docs/benchmarks/2026-10-29-defaults-qualification/verify_all.py` | Invoked camera verifier once, stopped at its nonzero exit; no remaining launches |
| `python docs/benchmarks/2026-10-29-defaults-qualification/assemble.py` | Recreated stopped-campaign results, 3 launches, 0 performance launches |
| `python -m py_compile <archive>/launch.py <archive>/verify_all.py <archive>/assemble.py <archive>/analyze.py` | Syntax validation; no clients launched |
| `git diff --check` and staged equivalent | Passed |

Every game command starts `./gradlew runClient -Pwindow=1920x1080 -Pxmx=2G`:

| Launch / local time | Additional properties | Result / artifact |
|---|---|---|
| manual, 17:32:34–17:34:19 | none | Title and world rendered; F7/F8/F9 and Video Settings observed; exit 0; [metadata](launches/manual.json) |
| q-smoke, 17:36:25–17:37:45 | `-Palacer.benchmark=true -Palacer.benchmark.label=q-smoke` | Static 16,713, pan 17,407, fly 22,896 frames, 0 dropped in every phase; [summary](runs/20261028-173744-q-smoke-vis_on-ecl_on-ent0/summary.json) |
| q-verify-cameraHysteresis, 17:38:16–17:38:27 | `-Palacer.benchmark=true -Palacer.benchmark.label=q-verify-cameraHysteresis -Palacer.render.cameraHysteresis.verify=true` | Client exit -1, Gradle exit 1; crash at 17:38:26; no summary; [metadata](launches/q-verify-cameraHysteresis.json) |

All three use release source without Java/build-script changes. `launch.py` and
`verify_all.py` received reporting-only changes after the stop: propagate a failed
client as a nonzero script exit and extract verifier lines before aborting. They
were not used to retry any launch. The original retained logs/statuses are the
evidence. The manual UI used the computer-use skill: title screen, Singleplayer,
selected `alacer-benchmark-run`, Play, F7, F8, F9, Escape, Options, Video Settings,
then Alt+F4. A transient capture/input issue was recovered by reselecting and
activating the Minecraft window before continuing.

## Smoke outcome and log errors

Manual smoke: all requested UI actions succeeded. F7 visibly showed the overlay;
F8 showed the graph; F9 wrote 8,192 frames to
`run/alacer/profiles/profile-20261028-173356.json` (chat and export log confirmed).
Video Settings rendered normally. The plain benchmark completed all three phases.

Smoke logs are **not error-free**. Launcher stdout reports Log4j Queue
`CLASS_NOT_FOUND` and missing `ServerGuiConsole` appender; Minecraft reports
`Couldn't initialize twitch stream` and missing
`com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen` on title/world
transitions. Both smoke clients continued. No mixin-application error, OpenGL
error or smoke crash was found. These errors are reported without assuming they
are harmless in a distributable installation. Shutdown includes Paul Lamb sound
library warnings. Full launcher and Minecraft logs are in [logs/](logs/).
Archived text copies have trailing spaces removed for Git whitespace validation;
original raw logs and the crash report remain in the ignored run directory.

## Correctness stop and source finding

The first verifier crashed while entering the copied world, before recorded
phases, final comparison counters or `summary.json`:

```text
java.lang.ClassCastException: class net.minecraft.util.math.Direction cannot be cast to class net.minecraft.client.render.world.RenderChunk
  at WorldRenderer.redirect$zed000$alacer$fastDirtyAdd(WorldRenderer.java:6134)
  at WorldRenderer.setupRender(WorldRenderer.java:819)
```

See [the crash report](logs/crash-2026-10-28_17.38.26-client.txt) and
[complete launcher output](logs/q-verify-cameraHysteresis.log).

`src/main/java/net/adiont/alacer/mixin/WorldRendererMixin.java:109` redirects
**every** `Set.add(Object)` in `setupRender`, then unconditionally casts the object
to `RenderChunk` at line 114. The local mapped Minecraft sources also contain
`renderChunkInfo3.culling.add(direction3)` in that method (mapped source line 739),
as well as `dirtyChunks.add(renderChunk3)` (765). Thus the redirect also intercepts
the vanilla search's direction-set insertion. Camera verification deliberately
executes that vanilla search: `ChunkVisibility.beforeVanillaSearch` prepares its
candidate and leaves vanilla's view-changed branch active. This explains the
observed cast failure. It is a shared redirect defect exposed by verification;
the crash alone does not establish a retained-visibility mismatch or prove the
camera option's default rendering is wrong. No repair/retest was attempted under
the campaign's stop rule.

| Option | Verifier | Emitted difference lines | Total mismatches / comparisons |
|---|---|---:|---|
| cameraHysteresis | Crashed during world entry | 0 | **Unavailable**, no final counters; not a zero-mismatch pass |
| fastWorkerAo | Not run after campaign stop | N/A | Unavailable |
| fastTerrainPipeline | Not run after campaign stop | N/A | Unavailable |
| vectorFrustum | Not run after campaign stop | N/A | Unavailable |
| flatOcclusion | Not run after campaign stop | N/A | Unavailable |
| fastMipmaps | Not run after campaign stop | N/A | Unavailable |
| fastModelTransforms | Not run after campaign stop | N/A | Unavailable |

[The extracted verifier-line file](launches/q-verify-cameraHysteresis-verifier-lines.txt)
contains no mismatch/difference messages. Zero emitted lines before a crash
provide no correctness certification. No other verifier had a launch budget spent.

## Performance gates and recommendations

There were **zero performance launches**. No median/p99/client-CPU non-regression
gate or >=3% improvement condition was evaluated; ABBA/BAAB order sensitivity is
unknown. The plain smoke run is not an A/B control or evidence for any option.

| Option | Planned performance launches | Actual | Median / p99 / CPU gates | Recommendation and evidence |
|---|---|---:|---|---|
| cameraHysteresis | 2: ABBA, BAAB | 0 | N/A in both launches, every phase | Needs Extended campaign; first repair the verifier-blocking redirect defect |
| vectorFrustum | 2: ABBA, BAAB | 0 | N/A in both launches, every phase | Needs Extended campaign; no completed verifier or A/B evidence |
| flatOcclusion | 2: ABBA, BAAB | 0 | N/A in both launches, every phase | Needs Extended campaign; no completed verifier or A/B evidence |
| fastModelTransforms | 2: ABBA, BAAB, 200 mobs | 0 | N/A in both launches, every phase | Needs Extended campaign; exact code-path overlap established, no new native/performance proof |
| fastTerrainPipeline | 2: ABBA, BAAB | 0 | N/A in both launches, every phase | Needs Extended campaign; source appears runtime-switchable, correctness/warmup qualification unfinished |
| fastWorkerAo | 4: A, B, B, A chunk-edit | 0 | N/A in all four launches; chunk latency unmeasured | Needs Extended campaign; no worker correctness/build/upload/tail evidence |
| fastMipmaps | 4: A, B, B, A resource loads | 0 | N/A; resource duration unmeasured | Needs Extended campaign; no mipmap correctness or resource timing evidence |

These are follow-up recommendations, **not keep-on approvals or automatic
turn-off decisions**. All shipped defaults remain unchanged; the maintainer
decides. Correctness repair and a fresh versioned protocol must precede a new
campaign. Performance repetition is not authorized by this stopped protocol.

Terrain source review: the volatile flag is read on the client thread at each
layer; standard-direct rebuilds `TerrainLayerBatch` at SOLID, uses existing
compiled chunk buffers/lists and restores its GL state before returning. Mixin
selection is not conditional on this flag. That supports runtime switching, but
`BenchmarkRunner.warmsBothPaths()` still omits it. Because the campaign stopped
before performance preparation, the list/test was **not changed**, and neither
both-path warmup nor in-process timing was exercised.

Environmental evidence uses existing `aa_monitor`/`aa_platform`, 1-second samples
and NVIDIA telemetry. The manual smoke had no quiet gate; q-smoke waited 119.24 s
and camera verification 11.00 s for the required 10-second <=1-core background
window. No monitor exceptions were reported. Whole-attempt means (including
preflight/startup, not phase acceptance statistics) were 2.716, 1.631 and 1.408
background cores; foreground-client fractions were 0.667, 0.325 and 0 respectively.
Other applications remained active; this environment cannot be described as
isolated. No phase-by-phase environmental acceptance was performed after the
stop, and no performance claim depends on these observations. No affinity
override was applied. Launch JSONs retain telemetry aggregates/compact series;
raw JSONL stays under ignored `run/alacer/benchmarks/qualification-telemetry/`.

## Model-transform overlap

Both options use the **same** `ModelPartMixin` wrapper around
`GlStateManager.translatef(FFF)` in `ModelPart.render`, `renderForceTransform`
and `transform`. Both skip exactly those wrapped calls whose three arguments
compare equal to float zero (including signed zero). These include zero
`translateX/Y/Z` offsets, their negated offsets, and zero pivot translations;
the skipped GL operation is the corresponding `glTranslatef`.

`ZeroTranslates.shouldTranslate` returns true for any nonzero argument. For zero
arguments, fastModelTransforms calls `FastModelTransforms.shouldSkipZeroTranslate`
(increments its skipped counter), while skipZeroTranslates returns false directly
when fastModelTransforms is off. With both on, fastModelTransforms takes precedence;
there is no additional second set of GL calls to remove. Both verifier switches
use the same sampled native before/after raw-matrix-bit check and issue each
translation once. Neither option removes rotations, scaling, matrix push/pop,
display-list draws or translations outside those ModelPart call sites. No
hierarchy flattening occurs. Neither option was merged/deleted/modified.

The historical session-5 Java 8 result remains preserved in `docs/BENCHMARKS.md`;
it is context for the unresolved overlap, not a new Java 25 performance verdict.

## Reproduction and missing evidence

[results.json](results.json) is reconstructed with `assemble.py` from retained
launch metadata, baseline JUnit XML, smoke summary and logs, plus explicitly
transcribed manual UI/Python-test observations. The unchanged copied
[analyze.py](analyze.py) is retained for the predeclared in-process design; it was
not applied to a non-toggle smoke run. Raw smoke CSVs remain at the path recorded
in the launch metadata; only its summary is in `runs/`. The crash has no summary
to archive. `PROTOCOL.md` SHA-256 remains
`48f9a63a23fac82c17f42ff058daf6397ff666fe386ad988d08316c1c179725b`, matching all
three launch records.

Missing: every completed verifier; every A/B launch/gate; worker chunk latency;
mipmap startup and F3+T reload timing; 200-mob model verification; terrain warmup
change/test; actual remapped-JAR installation/launch; Extended stability/power
and release evidence. The stop rule, rather than an elapsed-time or launch-budget
shortage, prevented the remaining client work.
