# Repaired 1.0 qualification: correctness exclusions and failed performance gates

The user authorized repairing the crash and continuing on 2026-10-28. The broad
dirty-set redirect crash is fixed and the repaired client completed **21 fresh
launches without a crash or retry**: two smoke launches, seven verifiers, and
twelve performance launches. All defaults remain unchanged. Main and the
`v1.0.0` tag still point to release commit `941405b`; nothing was pushed.

**No option earns a release-performance approval from this campaign.** Camera
hysteresis, flat occlusion and terrain pipeline produced verifier differences
and received no performance launches. Vector frustum, model transforms and
worker AO fail their predeclared frame/CPU gates. Mipmap startup times change
sign between pairs. Standard mode cannot support a release decision or a
small-improvement claim.

The original stopped attempt, protocol and results remain in the parent archive.
This campaign uses the separately frozen [v2 protocol](PROTOCOL.md) and
[source/artifact/world/config manifest](freeze.json), without pooling original
observations. Measured source is local commit
`5dbead083e71b47485dde0fa74e91485a94d39a7`; later archive-only edits do not change it.
Execution was **2026-10-28, 17:51–18:33 America/Los_Angeles**. The directory's
2026-10-29 date follows the original request (also the UTC execution date).

## Repair and measurement support

`WorldRendererMixin` formerly redirected every `Set.add`, `contains` and `addAll`
in `setupRender`, although vanilla also uses direction sets there. Its chunk-only
casts/epoch logic intercepted those direction operations during verification.
All four dirty-set redirects, including reusable set creation, are now sliced
from `ChunkRenderDispatcher.clear()` to the end of the scheduling block. Each
requires and permits exactly one selected call. Existing set reuse and dirty
epochs are retained. Vanilla direction sets now execute their original calls.

`DirtyChunkRedirectTest` reads the actual mapped Minecraft bytecode and compiled
mixin annotations without initializing Minecraft. It verifies that each slice
selects one dirty-set call and excludes the direction-set operations. The first
test attempt exposed an array-annotation parsing error in the new test itself;
that parser was corrected before the full passing builds and any resumed client.

Runtime review found `fastTerrainPipeline` reads a volatile flag per layer on the
client thread, rebuilds its layer queues at SOLID, shares compiled chunk buffers
with control, restores GL state and has no flag-dependent startup hook selection.
It was added to `BenchmarkRunner.warmsBothPaths()` before the source freeze.
Its later correctness differences excluded it from timing, so this campaign did
not exercise the new terrain toggle warmup in a performance run.

Two previously silent verifiers now publish counts: mipmap sprites/pixels and
terrain layer counts. Counters execute only during verification. Opt-in
`alacer.benchmark.resourceTiming=true` uses `nanoTime` around atlas
`loadAndStitch` and Minecraft `reloadResources`, logging duration at return.
Timing includes the whole named method, not just mipmap arithmetic. These hooks
do not change optimization defaults or apply alternative rendering logic.

## Build, runtime and smoke evidence

Clean build with the redirect fix/regression test: **353 tests, all passed**.
After adding verifier counters/resource timing, `build javaRuntimeInfo` reran all
353 tests and passed. Python tooling: **119 passed, 2 skipped, 9 subtests passed**
in 94.02 s. Build logs and per-suite XML are in [build-evidence/](build-evidence/).
Build/test evidence is separate from the real-client observations below. No
build, test or offline analysis ran beside a game client.

Temurin **25.0.4.1+1-LTS** explicitly selected for Gradle, compiler, tests and
client, bytecode major 69, G1, JVM-default compact headers off. Windows 11 build
26300, i7-12700F (12 cores/20 logical processors), RTX 3060 Ti, NVIDIA driver
616.92, GL 4.6.0. 1920x1080, 2 GiB heap, vsync off, unlimited benchmark FPS,
VBO on, distance 12, fancy graphics, maximum smooth lighting, mipmaps 4, default
cutout blending. Seed-1789 base world, fresh run copies, no benchmark-spawned
mobs/chests except 200 mobs in model-transform launches. Natural entity counts
varied; model runs reported 209 versus 210 client entities.

Frozen remapped-JAR SHA-256:
`43f866de47eda1fca81d1fa71723aba918178a25ccb2ba79b7d18a31659ea69b`.
The prescribed `runClient` task loads development classes/resources, **not the
remapped distributable**. A production installation of that JAR remains untested.
No release tag was moved or recreated.

The fresh default-config manual smoke reached title and world, showed the F7
overlay and F8 graph, wrote the F9 8,192-frame profile
`run/alacer/profiles/profile-20261028-175200.json`, and opened Video Settings.
It exited normally. The subsequent plain `q2-smoke` benchmark completed static,
pan and fly. The original persisted config/options were restored after manual
smoke and again at campaign end; all benchmark summaries identify build defaults
plus explicit JVM overrides. Every archived phase has zero dropped frames.

Logs still contain the earlier Log4j Queue/missing ServerGuiConsole-appender,
Twitch initialization and missing RealmsNotificationsScreen errors. These did
not prevent the observed smoke actions or complete benchmark launches. No client
crash, mixin-application failure or OpenGL error was found in the resumed runs.
Verifier mismatches below remain correctness failures. Full logs are in
[logs/](logs/); archived copies strip trailing whitespace, while original raw
logs remain in ignored `run/alacer/benchmarks/qualification-r2-telemetry/`.

## Correctness results

| Option | Exercised comparisons | Differences | Performance eligible |
|---|---:|---:|---|
| cameraHysteresis | Last periodic checkpoint: 26,000 searches | **3,454 stable-input**, plus 329 differing comparisons among 609 with changing compiled inputs | No |
| vectorFrustum | 89,165,331 boxes; 0 outside exact limit | 0 | Yes |
| flatOcclusion | 80,007,343 mask hits reported | **3** | No |
| fastModelTransforms | 700 sampled zero translations | 0 raw matrix-bit changes | Yes |
| fastTerrainPipeline | 339,423 layer counts | **290** | No |
| fastWorkerAo | 81,931,321 block and 10,798,628 light queries | 0 | Yes |
| fastMipmaps | 886 sprites / 87,550 pixels | 0 | Yes |

The camera verifier publishes periodic checkpoints rather than an exact final
total: **at least** 3,454 stable differences occurred. One warning compares
vanilla 1,719 chunks with Alacer 1,951 chunks, first difference at index 530.
This verifier checks the complete candidate traversal/retained result against
vanilla, with other shipped options active. This campaign does not attribute
all differences uniquely to hysteresis or isolate its interaction with the
other visibility options. It does establish failure of the requested zero-
difference prerequisite. Compiled-input changes are reported separately and
are not silently counted as stable mismatches.

Flat occlusion reports flat=false versus vanilla=true for face pairs 4→0, 4→3,
4→5. Terrain warnings include all three Solid/Mipped Cutout/Cutout layers; for
example 430 versus 428 Solid chunks. No repair/retry was made for these verifier
findings under the frozen protocol, and **six planned performance launches were
excluded**. Defaults were not disabled to hide differences in other runs.

[verification.json](verification.json) retains counts/eligibility. Each verifier's
`*-verifier-lines.txt` under `launches/` keeps every emitted mismatch/difference
line, including checkpoints and final summaries. Camera/flat warnings are rate
limited by their existing verifiers, so warning-line counts are not total mismatch
counts. Terrain/mipmap native counts are now explicit.

## In-process performance gates

B=option on relative to A=off. The same copied release-pacing [analyze.py](analyze.py)
combines per-frame CSVs and weights client CPU/frame by frame count. Both static
and pan are judged in both launches. Every median/p99/client-CPU change must be
<=+3%; record >=3% improvements separately. Bold values fail a gate.

| Option | Launch | Phase | Median change | p99 change | Client CPU/frame change | All three gates |
|---|---|---|---:|---:|---:|---|
| vectorFrustum | 1 ABBA | static | -4.17% | -7.82% | **+6.88%** | Fail |
| | | pan | -3.99% | -13.64% | -17.56% | Pass |
| | 2 BAAB | static | +1.32% | -0.27% | -12.52% | Pass |
| | | pan | -0.19% | -8.36% | -6.97% | Pass |
| fastModelTransforms | 1 ABBA | static | +2.20% | **+4.37%** | **+6.30%** | Fail |
| | | pan | -2.64% | -2.93% | -3.45% | Pass |
| | 2 BAAB | static | +0.50% | **+5.04%** | **+7.17%** | Fail |
| | | pan | +1.59% | +1.01% | -8.02% | Pass |

Vector's static median changes sign and its static CPU changes from a regression
to a large improvement. Model pan median/p99 also change sign. These results are
**inconclusive with respect to an option benefit**, not passing effects to average
away. Vector's first pan phase improves all three metrics by >=3%, but that does
not satisfy the across-launch/phase rule. Model's pan CPU improves by >=3% in both
launches, while static p99/CPU fail both. No positive whole-client claim follows.

[inprocess.json](inprocess.json) retains the unmodified analyzer output;
[results.json](results.json) adds every gate, >=3% condition and sign reversal.

## Worker-AO separate-launch comparison

All four launches use chunkEdit, VBO and chunkLatency; order A1/B2/B3/A4.
`tools/compare_benchmarks.py` produced [pair 1](worker-A1-B2.md),
[pair 2](worker-A4-B3.md) and [pooled descriptive output](worker-pooled.md).
The pairs retain launch order; pooled values do not override either pair's gates.

| Pair | Phase | Median change | p99 change | Client CPU/frame change | All three gates |
|---|---|---:|---:|---:|---|
| A1 / B2 | still | -0.33% | -3.14% | -8.66% | Pass |
| | edit | +1.65% | **+36.87%** | **+3.49%** | Fail |
| A4 / B3 | still | +2.19% | **+3.21%** | **+7.92%** | Fail |
| | edit | +0.82% | +2.05% | -5.00% | Pass |
| pooled (descriptive) | still | +0.80% | -0.82% | -0.76% | Pass |
| | edit | +1.25% | **+21.48%** | -0.87% | Fail |

These are each launch's edit-phase visible-update latencies (ms):

| Launch | Option | Samples | Mean | Median | p99 | Max |
|---|---|---:|---:|---:|---:|---:|
| A1 | off | 563 | 4.2613 | 4.0272 | 8.1876 | 9.3050 |
| B2 | on | 562 | 5.0283 | 4.5417 | 15.1160 | 15.7988 |
| B3 | on | 567 | 4.8687 | 4.5033 | 18.0409 | 19.4595 |
| A4 | off | 564 | 4.6913 | 4.4247 | 11.4413 | 12.1511 |

No latency-buffer overflow occurred. Both candidate edit observations have higher
mean and p99 update latency than their adjacent controls. These are **end-to-end
dirty-to-visible update durations**, covering scheduling/build/upload, not an
isolated worker AO execution timer. Detailed worker-build/upload latency fields
are unavailable/zero with detail profiling off and are **not interpreted as zero
cost**. The existing lightweight chunk-upload-per-frame mean and other available
means remain in the comparison output/summary; they do not replace missing
isolated stage latency. This limitation prevents the full requested stage-level
attribution, and no extra launches were spent to change instrumentation.

## Mipmap startup observations

Each launch enabled the opt-in resource timer, retained normal benchmark phases,
and stayed open for the attempted UI reload/normal close. Frame times are not a
mipmap performance measure and are not judged for this option.

| Launch | Option | Startup atlas loadAndStitch wall time |
|---|---|---:|
| A1 | off | 254.2024 ms |
| B2 | on | 281.5777 ms |
| B3 | on | 226.6411 ms |
| A4 | off | 274.6729 ms |

A1/B2 is **+10.77% slower** with the option on; A4/B3 is **-17.49% faster**.
The sign flips: inconclusive. Startup atlas timing includes decode/stitch/upload
and mipmap work, not total application startup or isolated mipmap CPU.

The keyboard tool's requested `F3+t` in A1 opened debug/chat rather than holding
F3 while pressing T. No second atlas load or full reload duration was emitted.
The remaining reloads were not attempted after that tool limitation. **F3+T
resource reload behavior/timing remains untested**; there is no synthetic reload
estimate and no additional launch/retry. The initial startup `reloadResources`
times (16.23, 17.05, 20.14, 16.54 ms) precede atlas listener registration and are
not labeled total texture/resource startup time. Missing reload measurements
remain null in the machine-readable results.

## Environment, limitations and recommendations

Existing aa_monitor/aa_platform sampled at one second with NVIDIA telemetry.
Every benchmark passed the 10-second <=1-core mean pre-launch quiet requirement;
waits were 11.00–13.02 s. No monitor exceptions occurred. No affinity override.
Every source/JAR/base-world/config/options/protocol hash was rechecked at the end
and matched. Full source hashes cover 331 files; base-world hashes cover 34 files.
The frozen protocol SHA-256 is
`483f9211436f6c04ce585e66880bea2cdf0cc183e39efcc8536491c1fcba68f2`.
Local Git attributes preserve the exact hashed protocol/manifest bytes across
Git line-ending normalization; neither was edited after the freeze.

**All scored frame/worker runs had foreground-client fraction 0.** Foreground
state was monitored but not controlled, and no foreground performance acceptance
was established. Other applications remained active; whole-attempt background
CPU means were roughly 0.88–1.10 cores for scored frame/worker runs. Startup/
preflight samples are included in those aggregates. This campaign cannot be
presented as isolated, foreground gameplay or Extended environmental acceptance.
Per-launch telemetry includes availability, GPU clocks/P-states, power plan,
CPU clocks, background processes, affinity, foreground and monitor self overhead.
Do not read whole-attempt means as per-phase environmental gates.

Other limitations: natural entity-count variation; other default options with
known verifier differences remained active in both variants; no native visual
A/B comparison; no production-JAR installation; no reload timing; no isolated
worker-stage timings; no Extended stability/power/repeatability campaign. The
common baseline's correctness failures limit attribution and render certification
for every measured option, even where its own verifier reports zero differences.

| Option | Recommendation to maintainer | Evidence / prerequisite |
|---|---|---|
| cameraHysteresis | **Turn off pending diagnosis** | >=3,454 stable traversal differences under its verifier; interaction/cause not isolated to hysteresis alone |
| flatOcclusion | **Turn off pending repair** | 3 native verifier differences |
| fastTerrainPipeline | **Turn off pending repair** | 290 layer-count differences |
| vectorFrustum | **Needs Extended campaign** | Zero verifier mismatches; static CPU gate failure and order-sensitive static results |
| fastModelTransforms | **Turn off pending further evidence** | Static p99/CPU fail both orders; exact overlap with skipZeroTranslates |
| fastWorkerAo | **Turn off pending further evidence** | Gate failures and higher edit update-latency tails in both pairs |
| fastMipmaps | **Needs Extended campaign** | Zero verifier differences; startup effect changes sign; reload missing |

These are conservative recommendations, not a release decision. **No shipped
default was changed.** Correctness failures must be repaired and verified before
their performance can be judged in a future versioned campaign. The maintainer
decides any default/release change.

## Overlap, commands and reproduction

The overlap finding is unchanged: both fastModelTransforms and skipZeroTranslates
use `ModelPartMixin` → `ZeroTranslates.shouldTranslate` to skip the same zero
`GlStateManager.translatef`/GL translation calls in `render`, `renderForceTransform`
and `transform`, including zero offset, negated offset and pivot translations.
Signed zeros qualify; nonzero translations remain. With both enabled, the fast
model branch takes precedence and adds only its counter. Neither removes matrix
push/pop, rotation, scale, display-list draws, external translations or hierarchy
recursion. Verification shares the same sampled raw-matrix-bit check. Neither
option was merged or deleted; historical Java 8 evidence remains unchanged.

[LAUNCHES.md](LAUNCHES.md) lists **every actual game command**, local start/end,
exit status, quiet wait and environment observations. Individual launch JSONs
also retain the actual Java executable/JVM command, source/artifact identity and
full monitor results. Twenty run summaries are archived under `runs/`; raw CSVs
stay in ignored `run/alacer/benchmarks/`. The first stopped campaign remains
separate. There were no client launch retries.

Other execution commands/results:

| Command | Result |
|---|---|
| `JAVA_HOME=<Temurin25>; ./gradlew test --tests net.adiont.alacer.render.DirtyChunkRedirectTest` | Initial new-test annotation parser failed; corrected before full builds |
| `JAVA_HOME=<Temurin25>; ./gradlew clean build javaRuntimeInfo` | 353 tests passed, Java 25 explicit |
| `JAVA_HOME=<Temurin25>; ./gradlew build javaRuntimeInfo` after timer additions | Passed |
| Same after verifier counter additions | Final build passed, 353 tests |
| `cd tools; python -m pytest -q tests` | 119 passed, 2 skipped, 9 subtests passed |
| `python <archive>/launch.py q2-manual` | Manual smoke passed |
| `python <archive>/launch.py q2-smoke` | Plain smoke completed |
| `python <archive>/verify_all.py` | Seven completed verifiers, three options excluded by findings |
| `python <archive>/verification.py` | Retained all difference lines and explicit eligibility |
| `python <archive>/performance.py` | Eight completed frame/worker launches |
| `python <archive>/launch.py q2-fastMipmaps-{1,2,3,4} ...` | Four completed resource launches; exact arguments in LAUNCHES.md |
| `python <archive>/score.py` | Reused copied analyzer and compare_benchmarks; all values/gates in results.json |
| `python <archive>/finalize.py` | Validated counts, no dropped frames, frozen hashes, defaults/main/tag and restored config/options |
| `python -m py_compile` with the six explicit helper paths; Git diff checks | Passed before final archive commit |

The clean/final builds' source and test results are retained. The targeted-test
failure's tool output was observed in-session; its temporary build directory was
replaced by the subsequent clean build. This affects only the initial test-parser
failure, not the passing final XML or native evidence.

To regenerate after clients exit, run `verification.py`, `score.py`, then
`finalize.py` from repository root. CSV-dependent scoring requires the ignored
raw run directories named in the retained metadata. The current built artifact
and frozen shared files are required for final integrity assertions. The launch
scripts refuse reused labels; these scripts do not authorize another campaign.
