# Default-option qualification v2 (frozen before resumed launches)

The user authorized fixing the crash and continuing on 2026-10-28. The original
protocol, crash and stopped results remain intact in the parent archive. This is
a fresh campaign on the repaired source recorded in freeze.json. No original
observation is pooled into it.

Standard mode, original release baseline `941405bf0a2afbb18785f8dd36d1d285dc37c633`, tag
`v1.0.0` retained, working branch `codex/1.0-qualification`. No default changes.
Standard evidence cannot support a release decision or a small-improvement claim.

Runtime: explicit Temurin 25.0.4.1 for Gradle/compiler/tests/client; bytecode 25.
Every client uses `./gradlew runClient -Pwindow=1920x1080 -Pxmx=2G`.
Benchmarks add `-Palacer.benchmark=true -Palacer.benchmark.label=<label>`.
Build defaults plus explicit JVM overrides; seeded `alacer-benchmark` (1789),
view distance 12, no chests, no mobs except the model-transform scenario (200).
Verification and detail profiling off for performance; unlimited FPS, normal
JVM ergonomics. Default phase lengths/timeScale=1; normal runner settling.
Fresh default config for the manual smoke; preserve and restore original config/options.

Before performance: clean build and Python tests with no game running; one manual
default smoke (title/world/F7/F8/F9/video settings), one plain `q2-smoke` benchmark,
then one verifier launch per option below. Worker verification uses chunkEdit,
VBO=true and chunkLatency=true. Model verification uses 200 entities. Mipmap
verification uses vanilla cutout blending (build default 0). Record comparison
counts as well as mismatch counts; no output is not proof of exercised checks.
Any verifier difference stops work on that option: no performance launches for it.

| Performance labels | Option | Design / measured phases |
|---|---|---|
| q2-cameraHysteresis-1, -2 | render.cameraHysteresis | toggle ABBA, BAAB; static and pan |
| q2-vectorFrustum-1, -2 | render.vectorFrustum | toggle ABBA, BAAB; static and pan |
| q2-flatOcclusion-1, -2 | render.flatOcclusion | toggle ABBA, BAAB; static and pan |
| q2-fastModelTransforms-1, -2 | render.fastModelTransforms | toggle ABBA, BAAB, entities=200; static and pan |
| q2-fastTerrainPipeline-1, -2 | render.fastTerrainPipeline | toggle ABBA, BAAB; static and pan, after runtime safety review and adding both-path warmup |
| q2-fastWorkerAo-1 through -4 | render.fastWorkerAo | separate A B B A, chunkEdit=true, vbo=true, profiler.chunkLatency=true; still and edit plus chunk latency |
| q2-fastMipmaps-1 through -4 | render.fastMipmaps | separate A B B A; startup resource loading and one F3+T reload if UI interaction succeeds; frame times not a mipmap performance measure |

A=option off, B=option on (shipped default). Toggle launches use
`-Palacer.benchmark.toggle=render.<option>`, second adds
`-Palacer.benchmark.toggleReverse=true`. Separate launches use explicit
`-Palacer.render.<option>=false/true/true/false`.
Other shipped options remain at build defaults. The terrain implementation reads
the volatile flag per layer on the client thread, rebuilds layer queues at SOLID,
restores GL state before return, and shares compiled chunk storage with control;
no startup-selected hooks depend on this flag. Both-path warmup must be enabled
before its performance runs. If review/verification invalidates this, do not run it.

Budget: at most two performance launches per in-process option, four for worker AO
and mipmaps (user explicitly approved this exception on 2026-10-28), plus nine fresh
smoke/correctness launches (q2-manual, q2-smoke and q2-verify-<option>). Failed/interrupted attempts count; no automatic retries.
Overall hard deadline: four hours after the first resumed manual smoke launch; no launch
without time to finish (reserve 12 minutes). Stop the campaign on functional
failure or a large unexplained timing-state change; retain all failed evidence.
Verifier differences stop the affected option only, as explicitly requested.

Performance gates, each launch and every measured phase: B median frame time,
p99 frame time and client CPU/frame must each be <=103% of A. Record whether
each improves by >=3%. Require both toggle launches; a sign reversal between
orders is inconclusive even if thresholds pass. For separate worker launches,
report all four observations, compare pairs A1/B2 and A4/B3 and pooled A/B
descriptively; require all pair/phase gates and record chunk build/upload latency
means and tails. Mipmap startup/reload resource durations are reported for all
four launches and both pairs (no frame-time gate substituted); absent precise
resource timing means unavailable, requiring Extended work, not a pass.

Reuse release-pacing/analyze.py for in-process CSV analysis. Environmental monitor
uses existing aa_monitor/aa_platform, 1-second sampling and GPU telemetry; require
a 10-second pre-launch window with mean background CPU <=1 core, up to 120 seconds.
No quiet-window success means no launch; report missing telemetry and environmental
contamination, never exclude an observation after seeing performance. No affinity
override; record observed affinity, foreground, power/clock and background state.
No builds, tests or offline analysis while any game is running. Archive summaries,
launch commands/status, verifier lines, environment and results. Raw CSV/telemetry
remain under ignored run/alacer/benchmarks; preserve all existing archives.

## v2 implementation and resource timing details

Repair all four dirty-set redirects by slicing setupRender from the dispatcher
clear() scheduling boundary, requiring exactly one selected call per redirect.
A bytecode regression test checks the real mapped vanilla method and annotation
slices. fastTerrainPipeline is added to warmsBothPaths before freezing this source.
All code and artifact hashes and the exact source commit are in freeze.json.

Mipmap performance launches add benchmark.resourceTiming=true and benchmark.exit=false.
The opt-in nanoTime timer records atlas loadAndStitch at startup and reloadResources
at startup/reload. The first startup reloadResources occurs before atlas listener
registration, so it is NOT total texture startup time. Primary startup metric is
atlas loadAndStitch wall time; F3+T measures full reloadResources plus the nested
atlas duration. After the normal benchmark completes, send one F3+T, wait for its
completion, then close the client. These launches do not judge frame-time results.
A missing reload is explicitly untested; no retry. Timing includes image decoding,
stitching and uploads as well as mipmaps; it does not isolate mipmap algorithm CPU.
Any >=3% resource-time benefit is descriptive Standard evidence only.

Verifiers use one complete affected-scene launch each. A difference excludes only
that option from performance, and all difference lines/counts are retained. No
automatic repairs or repeats during this frozen campaign. Functional failure
stops the campaign. Verification without a success counter is qualified by source
coverage and cannot be described as a counted pass.
