# Java 25 migration and fresh baselines

This archive separates the Java 25 migration from all retained Java 8 results. Java 25 is
selected independently for Gradle, javac/tests and Minecraft, with `--release 25`/classfile 69
and Mixin `JAVA_25`. No dependency version upgrade, renderer rewrite, optimization default
change or backend forcing is part of the migration. See [compatibility and setup](../../JAVA25.md).

## Audit and compatibility

The existing Gradle 9.7.1, resolved Loom 1.18.4, Ploceus 1.18.1, Fabric Loader 0.20.0,
Mixin 0.17.5+mixin.0.8.7, ASM 9.11, Ornithe Gen 2 mappings/OSL and LWJGL
2.9.4+legacyfabric.15 were retained. First a short Java 25 game run completed using the
original classfile-52 output (`java25-audit-release8.log`); then the target was raised and
the build/remapped jar plus actual client were checked (`java25-build.log`,
`java25-bytecode-smoke.log`). The remapped jar contains Alacer classfile 69 exclusively.
All 47 existing tests and two new real JVM allocation/heap/GC tests pass.

The game grants native access to unnamed modules, and JFR starts directly with
`-XX:StartFlightRecording`. The only existing production Java source edit adds runtime/VM
build and allocation-availability fields to environment reports. Every renderer, mixin,
optimization, benchmark runner and profiler hot path is unchanged. Build/resource metadata
and project documentation are updated; the new root `AGENTS.md` explicitly allows justified
large independently developed subsystem replacements.

## Collection protocol

Test machine: Windows 11 x64, Temurin 25.0.4.1+1-LTS, i7-12700F (20 logical processors),
RTX 3060 Ti, NVIDIA 616.92, OpenGL 4.6. Window 1920x1080, heap limit 2G, G1 default GC,
view distance 12, fancy graphics/clouds, max smooth lighting, mipmaps 4, vsync off and
unlimited FPS (`maxFps=260` is vanilla's unlimited setting). No driver settings were changed.

All launches use the existing seed-1789 base world and its copied-world protocol. Every
recorded run must report `benchmark.generationRun=false`, nonempty phases, one discarded
setup frame per phase, zero dropped frames and positive allocation/client/process CPU
measurements. Raw CSVs remain in `run/alacer/benchmarks/`; `runs/*.record.json` stores exact
commands, local source paths, file hashes and verification log lines. Summaries and full
launch logs are archived here. Diagnostic time scaling is explicit in each command.

The seven diagnostic launches are excluded from all performance controls:

- `j25-verify-dl` and `j25-verify-vbo`: 200 mobs, visibility and entity chunk lookup checks,
  matrices, frustum boxes, zero translations, cloud geometry, shadows and native model-list
  feedback, under each terrain backend. Static/pan/fly durations are scaled by 0.5.
- `j25-frustum-traversal`: candidate frustum enabled, box verification off, complete traversal
  compared with vanilla; scale 0.5. Changing compiled inputs remain separately inconclusive.
- `j25-gl-error`: deliberate harmless GL error injection/reporting, scale 0.2. Its GL ERROR
  messages are expected. Other runs reject GL errors.
- `j25-cloud-sweep`: all ten existing cloud views, positive/negative wrapping, height bands,
  rain/dusk, anaglyph, flat/disabled/fancy, geometry verification and screenshots; scale 0.3.
- `j25-shadow-diagnostic`: moving mobs and changing shadowed blocks, compared with vanilla;
  scale 0.5. It is a correctness test rather than fixed-population performance evidence.
- `j25-jfr-detail`: full static/pan/fly durations with chunk/entity/shadow detail and JFR.
  JFR recording and management counters validate instrumentation; intrusive measurements do
  not enter selection. The local recording is `run/java25-detail.jfr`.

The ten performance controls use full existing durations, with verification, entity/chunk
detail and replay timing off:

- Six `j25-defaults-e{0,200,1000}-r{1,2}` launches: the existing warmed `render.defaults`
  A/B/B/A protocol measures static and pan. A disables the six shipped optimizations; B
  enables them. Frustum, zero-translate and model-list experiments remain off. The first
  repetition increases density, the second decreases it; each density has two independent
  launches. Both variants have the same ordinary profiler installed.
- Four `j25-backend-{dl,vbo}-r{1,2}` launches in DL/VBO/VBO/DL order: the existing dedicated
  364-block stained-glass scenario and discarded pan/sort warmups precede steady, pan, sort
  and one fresh flight. Both backends retain the same six enabled optimizations. Each phase
  checks backend option, GLX state and actual render-chunk class; vanilla reload restores the
  original backend on exit. Two pairs establish fresh baselines; they do not satisfy the
  historical four-pair selection protocol by themselves.

## Results and evidence

**Completed:** 49/49 tests, 17/17 suite launches and both additional migration smoke
launches pass (19 client launches total). The new batch launcher also passes a forwarded
`--dry-run` from outside the project directory. Every suite phase has positive allocation
and CPU measurements, zero dropped frames and one excluded setup frame.

The six-default A/B comparison lowers mean frame time in all 12 launch/phase comparisons.
Median reductions by population are static **47.8% / 17.5% / 17.5%** and pan
**33.3% / 28.3% / 15.6%** for 0/200/1,000 mobs. This compares Alacer defaults with vanilla
paths within Java 25, not Java 25 with Java 8. Launch drift remains visible: the two
1,000-mob pan changes are -4.8% and -26.5%. Zero-mob static allocation/frame falls while
allocation/second rises 36.2%; faster rendering does not imply a lower allocation rate.

VBO lowers mean frame time in all eight paired backend/phase comparisons: median changes
are **-39.5% / -58.6% / -56.5% / -52.7%** for steady/pan/sort/fly. Client CPU/frame falls
65.5–67.5%, but allocation/second rises **11.2–100.9%**, and steady allocation/frame rises
7.2%. These two pairs establish new throughput baselines, not a universal smoothness or
responsiveness decision. In particular, they do not replace the historical four-pair gates.

Rendering diagnostics report zero mismatches across **15,158,899 frustum boxes**,
**37,047,130 shadow queries** (including changing-block/moving-mob diagnostics),
**532,885,504 cloud vertices**, and **192 native model-list feedback comparisons**.
Matrix, zero-translation, entity lookup and stable-input visibility checks also report no
differences; changing compiled inputs remain inconclusive. All **32 injected GL errors**
were reported with zero missed. The 77-second JFR recording contains **3,660 execution
samples**, **13,840 allocation samples**, **73 GC events**, heap summaries and thread CPU
and allocation statistics. Detail/JFR/verification results remain excluded from selection.

The CSV-derived per-run and paired results are in [measurements.md](measurements.md) and
`metrics.json`. `verification.json` records tests, artifact hashes/class versions, diagnostic
checks and JFR event counts. `preservation.json` records the original file identities,
unchanged Java 8 archives, base world, config and game options. Build/runtime audit logs and
resolved dependencies are retained alongside them.
The existing comparison CLI also succeeds in both modes; its pooled reports are retained
as [defaults at 200 mobs](comparison-defaults-e200.md) and
[terrain backends](comparison-backends.md). Paired launch effects, absolute values and
drift remain separately visible in `metrics.json`. Allocation units follow the existing
tool: KB = 1,024 bytes and MB = 1,048,576 bytes. Percentage changes with a zero baseline
are undefined and omitted; absolute slow-frame counts/rates are retained.

Screenshots from the optimized bytecode smoke, both verification backends and cloud sweep
are retained under `run/screenshots/alacer-j25-*.png`; inspected-image identities and scope
are recorded in `images.json`. They cover terrain, entities, cloud height bands and dusk/rain.
They are visual checks, not an exhaustive pixel-identity proof. Native model feedback also
checks transformed geometry, lit color, texture coordinates and final GL state.

## Interpretation and limitations

These controls establish new Java 25 references while preserving the existing choices.
No optimization is selected or rejected automatically, and no backend is forced. Use the
raw per-run means, CPU, allocation/frame, allocation/second, tails and maxima together;
an FPS gain does not establish lower allocation rate or better rare-stall behavior.
With two launches per condition and one machine, repeated agreement remains scenario-specific.

The Java 8 archives used Parallel GC and classfile 52; this series uses G1 and classfile 69.
Comparing historical means does not isolate the causal effect of the JVM version. Preserve
both series separately and do not pool CSVs across them. Java 8 archives and their conclusions
remain exactly as recorded, including the then-unverified Java 25 status in historical files.

Known optional Realms/Twitch/logging errors also occur in prior Java 8 logs; operation of
those optional features is not claimed. Netty uses its direct-buffer compatibility fallback.
OpenAL starts, but audible output is not listening-tested. This validates the Loom development
client on this Windows/NVIDIA system; production installer, other OS/natives, multiplayer
and arbitrary third-party mod stacks need separate live checks. See [JAVA25.md](../../JAVA25.md).

## Reproduce

```sh
./gradlew build
python tools/run_java25_validation.py diagnostics
python tools/run_java25_validation.py controls
python docs/benchmarks/2026-10-20-java25/analyze.py
python docs/benchmarks/2026-10-20-java25/verify.py
```

The collection tool skips successful archived labels. The final preservation check refers
to the migration's original local `build/java25-*-before.json` snapshots and test/build
outputs; it is an audit of this migration checkout, not a fixture that regenerates Java 8
evidence. Nothing was committed, pushed or published.
