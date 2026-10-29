# Alacer

Client-side performance mod for Minecraft 1.8.9, built on
[Ornithe](https://ornithemc.net/) Gen 2 with Fabric Loader. Every optimization is written from
scratch and has to justify itself with measurements taken by the mod's own profiler and
benchmark.

Status: **1.0.0**, the first release. It ships the default-on optimizations listed under
[Configuration](#configuration), the in-game profiler and overlay, the expanded Video Settings
menu, and the scripted benchmark. Not every default has an archived Java 25 performance
qualification; [CHANGELOG.md](CHANGELOG.md) lists the known limitations. Everything else stays opt-in, including the modern
OpenGL pipeline, the chunk frustum test, particle light reuse, model display-list batching and the
2026-10-25 frame pacing/swap options (see the
[1.0.0 frame pacing check](docs/benchmarks/2026-10-28-release-pacing/README.md)). Every
optimization can be switched back to vanilla's code path, and most have a `.verify` mode that runs
vanilla alongside it and logs any difference.

Measured results, including the ideas that did not pay off, are in
[docs/BENCHMARKS.md](docs/BENCHMARKS.md) and the archives under `docs/benchmarks/`. See
[docs/ROADMAP.md](docs/ROADMAP.md) for the performance roadmap,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design notes and
[CHANGELOG.md](CHANGELOG.md) for release notes.

Release qualification turned off camera visibility reuse, flat occlusion, the direct terrain
pipeline, worker AO caching and model zero-translation skipping: they are config-only experiments,
outside the Video Settings menu and the shipped-default benchmark preset. See the
[qualification report](docs/benchmarks/2026-10-29-defaults-qualification/resume-1/README.md).
A config file written by a development build keeps its saved values; set these five keys to
`false` in it.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.8.9 (client only) |
| Loader | Fabric Loader ≥ 0.20.0 via the Ornithe installer, Gen 2 intermediary |
| Libraries | OSL `entrypoints`, `lifecycle-events`, `keybinds` (and `core`), from OSL 0.22.2 |
| Java (game) | Java 25; primary development and benchmark runtime |
| Java (Gradle) | Installed Eclipse Temurin JDK 25, selected by `gradle/gradle-daemon-jvm.properties`; Gradle 9.7.1 |
| Java (compiler/tests) | Eclipse Temurin JDK 25 toolchain, `--release 25` (classfile 69), tests on Java 25 |

## Installing

1. Install Minecraft 1.8.9 with Fabric Loader 0.20.0 or later through the
   [Ornithe installer](https://ornithemc.net/) (Gen 2 intermediary).
2. Put OSL 0.22.2 (`core`, `entrypoints`, `lifecycle-events`, `keybinds`) and
   `alacer-1.0.0+mc1.8.9.jar` in the instance's `mods` folder.
3. Run the game on Java 25. Launchers other than the development client must add
   `--enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector` to the JVM arguments.

Alacer is client-only and does not need to be installed on servers. Settings are written to
`config/alacer.properties` on first launch and are also available under
**Options -> Video Settings**.

## Building

```bash
./gradlew build
```

The mod jar is written to `build/libs/alacer-<version>+mc1.8.9.jar`. Unit tests run as part of
`build` (`./gradlew test` to run only them).

Install Eclipse Temurin JDK 25 before building. Gradle finds installed JDKs automatically; otherwise point
`org.gradle.java.installations.paths` at one. The Gradle daemon, compiler/test toolchain and
Minecraft launcher each select Java 25 independently. `JAVA_HOME` selects the wrapper's
bootstrap JVM; `./gradlew --version` reports both launcher and daemon JVMs. Run
`./gradlew javaRuntimeInfo` to see the selected installations and bytecode target. No automatic JDK
download is configured. The mod now requires Java 25; historical Java 8 benchmark results
remain separate references. See [Java 25 compatibility](docs/JAVA25.md).

## Running the development client

On Windows, double-click `run-client.bat`. It runs from the project directory and keeps
the window open on errors. Command-line options are forwarded, for example:

```bat
run-client.bat -Pwindow=1920x1080 -Pxmx=2G
```

```bash
./gradlew runClient
```

The development launcher grants `--enable-native-access=ALL-UNNAMED` for LWJGL 2's native
libraries. External launchers using Java 25 also need that argument and
`--add-modules=jdk.incubator.vector` for the Vector API implementation.

`-PcompactObjectHeaders=true` opts the test and development-client JVMs into Java 25's
compact object headers. `false` explicitly selects the control layout; without the property,
the JVM uses its own default. The option is experimental for Alacer and is not promoted to
the default by the short compatibility check. Profile and benchmark exports include the
effective JVM layout flags so comparisons can check what actually ran.

Useful project properties (all optional):

| Property | Effect |
|---|---|
| `-Pwindow=1920x1080` | Initial window size. |
| `-Pxmx=2G` | Maximum heap for the game. |
| `-Pjfr=name` | Record a Java Flight Recorder profile to `run/name.jfr`. |
| `-PjfrSettings=path` | Optional JFR settings file, resolved from the game directory (`run/`). Default `profile`. |
| `-PjfrDebugNonSafepoints=true` | With `-Pjfr`, also pass `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints` so inlined-frame lines are recorded between safepoints. Attribution launches only (session 25). |
| `-PjfrTlab=16k` | With `-Pjfr`, fix the TLAB size (`-XX:TLABSize=16k -XX:-ResizeTLAB`) so allocation samples, which are taken at TLAB refills, reach a high-allocating thread more often. Allocation attribution launches only (session 28). |
| `-Palacer.<option>=<value>` | Override any config option for this launch (see below). |

## In-game profiler

The profiler is on by default and costs a few `System.nanoTime()` calls per frame. It keeps a
bounded history (8192 frames by default) of:

- frame time (start of one frame to the start of the next, including the frame limiter);
- client-thread wall time spent ticking, rendering, in terrain setup and the visibility search within it,
  uploading built chunks, entity rendering (split into prepare, global, outline, entity loop
  and block-entity phases), vanilla's GL error checks, the camera and frustum setup (where
  vanilla reads matrices back from OpenGL), render chunk matrix reads, and the buffer swap (time
  blocked on the GPU, the driver or vsync);
- with `profiler.entityDetail=true`, per-call timing inside the entity loop (world-chunk
  lookups, visibility checks, entity and block-entity draws) and operation counts;
- with `profiler.shadowTiming=true`, entity shadow rendering as one section, and shadows drawn;
- the particle manager's tick and its draw calls, and particles ticked and rendered per frame;
  with `profiler.particleDetail=true`, particle updates, their movement, dead-particle removal
  and light queries individually;
- with `profiler.chunkDetail=true`, separate dirty/build/upload queues, observed dirty-set ages,
  client and worker builds, buffer waits, upload latency and GL display-list compilation;
- bytes allocated by the client thread per frame (HotSpot per-thread counters);
- visible chunks and pending entries (dirty chunks + queued builds + per-layer upload jobs;
  this is not a count of unique chunks and excludes running builds);
- client tick durations, heap usage and GC counts and time (sampled once per second).

Keys (rebindable under Controls → Alacer):

| Key | Action |
|---|---|
| F7 | Toggle the overlay: FPS, 1%/0.1% lows, frame-time percentiles, CPU breakdown, allocation, heap, GC. Covers the last 5 seconds. |
| F8 | Toggle the performance graph; Shift+F8 freezes or resumes its history. |
| Unbound (Zoom) | Hold for the selected magnification; bind under Controls -> Alacer. |
| F9 | Export the full history to `alacer/profiles/profile-<time>.json` (summary) and `.csv` (one row per frame). |

The [F3 overlay and graph](docs/F3-OVERLAY.md) use Minecraft's font and debug backgrounds.
With the graph visible, F4 changes its mode, Ctrl+F4 changes its scale, and Shift+F4
freezes the history. Compact layouts reserve space above the graph for world details.

## Video settings

Open **Options -> Video Settings** for categorized controls, global search,
contextual help and staged Apply/Cancel. The menu uses Minecraft's native widgets
and formatting colors. Minecraft 1.8.9's single Graphics option is split into
per-feature Details controls: leaves, weather, rain splashes and the vignette
each default to **Follow Graphics**, so the base Graphics option keeps today's
behaviour until a feature is overridden. Details also adds Stars (Off/On),
Enchantment glint (Off/On) and Dropped items (Fast/Fancy); each defaults to
vanilla's appearance. New choices are saved to `config/alacer.properties` as
`quality.*` options, are recorded in profile and benchmark exports, and use their
defaults during benchmark launches. Existing optimization defaults stay unchanged.

The menu also offers entity and block-entity draw distances, biome blend, a
particle limit, anisotropic filtering, distance fog, sun and moon, per-group
texture animations, FOV effects and damage tilt, camera settings (bobbing strength, distortion effects,
third-person distance and collision, FOV transitions, and a zoom factor for a Zoom key that is unbound by
default), and an **Advanced** page with
the chunk update budget, chunk builder threads (experimental, restart) and the
performance overlay. **Presets...** stages Performance, Vanilla or Enhanced
appearance choices; Apply is still required. Every new default is vanilla's
behaviour, stored as `quality.*`/`advanced.*` options.
See [the settings guide and validation](docs/VIDEO_SETTINGS.md).

Graphics also includes fog start, cloud height, sky visibility, star brightness
and weather opacity. These controls can soften the horizon, move clouds or tune
night/weather effects while keeping Minecraft's default appearance until edited.
The menu retains native Minecraft buttons and sliders with a wider layout and
clear selected-category and pending-change indicators.

## Configuration

Performance percentages below describe the historical Java 8 measurements unless explicitly
labelled Java 25. They explain the existing choices; migration preserves those defaults.

`config/alacer.properties` is created on first launch. Every option can also be overridden for
one launch with `-Dalacer.<option>=<value>` (or `-Palacer.<option>=<value>` with Gradle).

Benchmark launches (`-Palacer.benchmark=true`) use the defaults from the running build plus
explicit JVM overrides. They ignore and never rewrite `config/alacer.properties`, including
when selecting startup profiling hooks. This prevents saved values from an older measured
build from silently disabling optimizations or enabling intrusive timers. Exported environments
record `alacer.configSource`; inspect each phase's `config` for the options actually measured.
Normal launches continue to use saved settings, so an existing explicit `false` remains off.

| Option | Default | Description |
|---|---|---|
| `profiler.enabled` | `true` | Collect measurements. |
| `profiler.history` | `8192` | Frames kept in memory (256 to 1048576). |
| `profiler.logInterval` | `0` | Seconds between performance summaries in the log; 0 = off. |
| `profiler.entityDetail` | `false` | Time individual calls inside entity rendering and count the OpenGL calls made while drawing entities (per-frame counters plus a per-entity-class table in the log for each benchmark phase). Adds measurable overhead; the extra hooks are only installed when this is on at startup. |
| `profiler.entityStages` | `true` | With `entityDetail`, additionally time renderer setup, model preparation/animation/drawing, layers, overlays, name-tag checks and shadows (including per-block world queries). Inclusive sections overlap; use for attribution, not FPS claims. Set false at startup to measure the additional instrumentation's overhead. |
| `profiler.shadowTiming` | `false` | Time all of entity shadow rendering as one section (`shadows`) and count shadows drawn: two `nanoTime` calls per shadow. Its hook is only installed when this is on at startup. |
| `profiler.frustumTiming` | `false` | Replay each search's tested boxes through the vanilla and candidate frustum predicates, alternating order and timing each batch. Adds work; for attribution only, excluded from warmed A/B selection. |
| `profiler.chunkDetail` | `false` | Chunk pipeline and backend submission attribution, including per-layer submitted chunks, worker completion totals and sampled dirty-set ages. Hooks are installed only at startup; intrusive, excluded from selection. Worker/latency/age columns are not client render work. See sessions 13–14. |
| `profiler.chunkLatency` | `false` | Visible chunk-update latency: from the end of the first terrain setup that finds a chunk dirty and visible until a completed rebuild is published (on whichever thread publishes it), plus a count of never-built chunks in view. Benchmark summaries add `update_latency_p50/p90/p99/max_ms`. Measurement only; its hooks are installed only when this is on at startup. See the [2026-10-23 chunk scheduling archive](docs/benchmarks/2026-10-23-chunk-scheduling/README.md). |
| `profiler.particleDetail` | `false` | Time each particle's update, its movement, the removal of dead particles and the light query each particle makes while drawn, and count removed particles: two `nanoTime` calls per particle and operation. Intrusive, for attribution only; its hooks are only installed when this is on at startup. See session 15. |
| `profiler.particleLightRepeats` | `false` | Count, per unlit particle render pass, the particle light queries, their distinct (x, y, z, blockLight) arguments and positions, and those equal to the previous query (`particle_light_*` counters, final log line). Read once at first use and folded away when off; two hash-set inserts per query when on, so attribution only. Startup only. |
| `profiler.hud` | `false` | Show the overlay at startup. |
| `profiler.graph` | `false` | Show the performance graph at startup; also toggled by its Controls binding (F8 by default). |
| `profiler.graphMode` | `1` | Graph startup mode: 0 frame milliseconds, 1 subsystem milliseconds, 2 subsystem percentages, 3 top subsystem detail. |
| `benchmark.chunkEdit` | `false` | JVM-only benchmark scenario selector (not read from the config file): use with `benchmark=true`, an explicit `benchmark.vbo=false` or `true`, and `profiler.chunkLatency=true` for the 16-cell edit-burst responsiveness scenario described below. |
| `quality.viewBobbing` | `100` | Bobbing strength, 0-100% in steps of 5, while vanilla View bobbing is on. Immediate; 100 returns vanilla's values untouched. |
| `quality.distortion` | `100` | Nausea/portal view stretch, 0-100% in steps of 5. Immediate; 0 removes the stretch, 100 preserves vanilla. |
| `quality.thirdPersonDistance` | `4` | Third-person camera distance, 1-16 blocks. Immediate; 4 preserves vanilla. |
| `quality.cameraCollision` | `1` | Third-person block collision: 0 Off, 1 On (vanilla). Immediate; Off permits the camera behind blocks. |
| `quality.fovTransition` | `1` | FOV approach rate: 0 Instant, 1 Vanilla, 2 Smooth, 3 Very smooth. Immediate; independent of FOV effects strength. |
| `quality.zoom` | `2` | Hold-to-zoom magnification: 0=2x, 1=3x, 2=4x, 3=6x, 4=8x. Bind `key.alacer.zoom` under Controls -> Alacer; unbound by default. Changes the world FOV; the hand projection is unchanged. |
| `render.fastChunkVisibility` | `true` | Use Alacer's chunk visibility search. |
| `render.fastChunkVisibility.verify` | `false` | Debug: run vanilla's and Alacer's searches side by side and log differences. Slower. |
| `render.fastChunkNeighbours` | `false` | Experimental exact culling math: precompute six neighbor addresses per chunk-storage slot instead of repeating division and modulo on each traversal edge. Keeps the existing traversal, frustum predicate and draw order. Requires Alacer visibility; guarded fallback for unsupported storage/camera inputs. Outside `render.defaults`. |
| `render.fastChunkNeighbours.verify` | `false` | Debug: compare every precomputed address with the original world-coordinate calculation. Use with `render.fastChunkNeighbours=true`; whole-search verification remains separate. |
| `render.terrainShader` | `false` | Modern OpenGL terrain vertex shader pipeline evaluating origin translation and chunk boundary expansion directly on the GPU, eliminating 4,500+ matrix operations per frame. Replaced the failed terrain VAO experiment; includes legacy `render.terrainVaos` alias. Startup hooks, capability/compatibility fallback; outside `render.defaults`. |
| `render.terrainVaos` | `false` | Legacy alias for `render.terrainShader`. |
| `render.directBufferUploads` | `false` | Experimental OpenGL 4.5 buffer creation/named-data uploads, retaining vanilla's upload bytes, usage and final ARRAY_BUFFER=0. Startup hooks, capability/compatibility fallback; outside `render.defaults`. |
| `render.modernGl.verify` | `false` | Debug: exercises supported VAO/named-upload paths, checks all four terrain pointer layouts and reads back every uploaded byte. Intrusive; excluded from performance selection. |
| `render.fastChunkFrustum` | `false` | Experimental: in Alacer's chunk search, test one corner per frustum plane with vanilla's exact arithmetic/boundary rules. Unknown/modified cullers and unsafe inputs use vanilla. No dependable measured gain; see session 12. |
| `render.fastChunkFrustum.verify` | `false` | Debug: compare every eligible chunk predicate with vanilla, return vanilla's answer and log differences. Use with the Alacer chunk search enabled. |
| `render.fastCameraSectionFaces` | `false` | Experimental (session 28b, not yet measured; reference series prepared as session 30): in Alacer's chunk search, find the camera section's open faces with an allocation-free flood fill instead of vanilla's (which copies `Direction.values()` per visited block), reading the section's blocks with vanilla's own calls and reusing the answer while the camera stays in the same connected pocket of an unchanged, populated section. Same faces as vanilla; only matters when the camera moves between blocks. Runtime-toggleable; not in `render.defaults`. |
| `render.fastCameraSectionFaces.verify` | `false` | Debug: also run vanilla's camera-section flood fill for every answer, use its result and log any difference (`Camera section faces final:` in the benchmark log). Use with the Alacer chunk search enabled. |
| `render.fastEntityChunkLookup` | `true` | Reuse each render chunk's world-chunk reference when gathering entities. |
| `render.fastEntityChunkLookup.verify` | `false` | Debug: also do vanilla's lookup for every cached one and log differences. |
| `render.fastEntityPipeline` | `true` | Streamline entity and block entity iteration by stamping active chunk sections and iterating list instances directly. Eliminates 95%+ of entity chunk loop CPU overhead across empty visible chunks while preserving exact vanilla rendering order and behavior. |
| `render.fastEntityPipeline.verify` | `false` | Debug: also evaluate vanilla entity traversal and verify identical rendered entity counts and sequence. |
| `render.throttleGlErrorChecks` | `true` | Check for OpenGL errors about once per second instead of twice per frame. Errors are still logged, with a note that they may come from any frame since the previous check; after the first error, checks return to every frame. Saves about 2-3% of frame time when the NVIDIA driver runs threaded; no measurable effect otherwise (see BENCHMARKS.md). |
| `render.throttleGlErrorChecks.verify` | `false` | Debug: deliberately raise a harmless OpenGL error between checks and log whether it is reported. |
| `render.avoidMatrixReads` | `true` | Answer the world render's camera, frustum and viewport reads, and render chunk matrix reads, from CPU-side copies instead of `glGet` calls that wait for the driver. The real values are compared about once per second; after a difference (something changed the matrices without `GlStateManager`), reads return to OpenGL for the session. With `render.throttleGlErrorChecks` also on, about 35% less frame time when the NVIDIA driver runs threaded; no measurable effect otherwise (see BENCHMARKS.md). |
| `render.avoidMatrixReads.verify` | `false` | Debug: also read the real values every time, use them, and log any difference. |
| `render.skipZeroTranslates` | `false` | Skip the translations by (0, 0, 0) that every entity model part issues twice, about 20% of the OpenGL calls of drawing entities. Exact, but no measurable frame-time effect in the benchmark, so off by default (see BENCHMARKS.md). |
| `render.skipZeroTranslates.verify` | `false` | Debug: issue the translations anyway and check about once per second that they leave OpenGL's matrix unchanged. |
| `render.cacheClouds` | `true` | Draw fancy cloud geometry from a display list, rebuilt when the integer cloud cell, height band, color, cloud mode or anaglyph pass changes. Keeps both vanilla passes and moves the geometry with the fractional cell offset. |
| `render.cacheClouds.verify` | `false` | Debug: draw vanilla clouds and compare every vertex with the translated cache, allowing float rounding in positions and requiring exact texture coordinates, colors and normals. |
| `render.fastEntityShadows` | `true` | Answer entity shadows' block and light queries with chunks from a small reference cache instead of a chunk hash lookup per query (three to five per block under each nearby mob's shadow). Block states and light are still read on every query, so results are exactly vanilla's. With 200 mobs nearby, about 0.05-0.1 ms less client-thread time per frame and 19-22 KB less allocation; frame time improves when the client thread limits it (see BENCHMARKS.md). |
| `render.fastEntityShadows.verify` | `false` | Debug: also run vanilla's shadow queries, use their results and log any difference. |
| `render.fastParticleLight` | `true` | Answer the light query every particle makes each frame while drawn with chunks from a small reference cache instead of chunk hash lookups, and without copying neighbor positions for blocks such as tall grass and slabs. Block states and light are still read on every query, so results are exactly vanilla's. Java 25, about 3,300 particles: median 20-23% less frame time (25-31% at about 5,100) and about half the allocation (see BENCHMARKS.md session 15). |
| `render.fastParticleLight.verify` | `false` | Debug: also run vanilla's particle light query, use its result and log any difference. |
| `render.fastParticleRemoval` | `true` | Remove dead particles each tick in one pass by network id instead of vanilla's `removeAll`, which compares every live particle with every dead one. Same particles removed, same order, same list; classes overriding `equals`/`hashCode` take vanilla's call. Java 25, particle scenario: about 0.75-1.5 ms less in every frame that runs a client tick (median tick-frame mean 16-24% lower); frame means change by a few percent at most (see BENCHMARKS.md session 16). |
| `render.fastParticleRemoval.verify` | `false` | Debug: also run vanilla's dead-particle removal on the real list, compare the survivors and log any difference. |
| `render.fastParticleVertices` | `true` | Write each particle vertex's position, texture, color and light bytes directly at their fixed offsets in the particle vertex format, with vanilla's own buffer puts, instead of `BufferBuilder`'s per-element format lookups. Same bytes in the same buffer; every particle's render code, light query, order and draw are vanilla's. Applies to `Particle.render` (and every subclass calling `super.render`) and block-break particles. Hooks are installed only when on at startup (or verified or benchmark-toggled). Java 25, particle scenario: median frame time 20-33% lower, particle pass 25-37% lower (see BENCHMARKS.md session 20). |
| `render.fastParticleVertices.verify` | `false` | Debug: let vanilla write particle vertices, compare the direct writes' bytes with them and log any difference. |
| `render.fastParticleLightEntry` | `false` | Experimental: answer the loaded-chunk check before each particle's light query with vanilla's client-world range test, and reuse one position instead of copying it when `render.fastParticleLight` answers the light query. Same results; allocation per frame drops, but the measured time saving was not dependable (session 21: all four A/B groups failed their gates). Hooks are installed only when on at startup (or verified or benchmark-toggled). Not in `render.defaults`. |
| `render.fastParticleLightEntry.verify` | `false` | Debug: keep vanilla's position copy, also run vanilla's chunk check, use its result and log any difference. Enable the option as well. |
| `render.reuseParticleLight` | `false` | Experimental: within one unlit particle render pass, answer `render.fastParticleLight`'s light queries for a block position already answered in that pass from that answer (the client world cannot change during the pass; same results). Verified exactly, but failed one of four measured groups and one startup comparison (session 22); off by default and not in `render.defaults`. Its hooks are installed only when this, its verification or a benchmark toggle of it is on at startup. Runtime-toggleable. |
| `render.reuseParticleLight.verify` | `false` | Debug: also run vanilla's light query for every reused or computed answer, use its result and log any difference. Enable the option as well. |
| `render.fastParticleMovement` | `false` | Experimental: fetch block states for particle movement and collision gathering through the existing chunk-reference cache. Collision loops, shapes, callbacks and movement calculations remain vanilla. Startup hooks are absent unless this option, its verification, or its benchmark toggle is requested. See the session 17 archive. |
| `render.fastParticleMovement.verify` | `false` | Debug: compare each cached movement block-state read with vanilla, returning vanilla's state. Enable the movement option as well. |
| `render.feedChunkWorkers` | `false` | Experimental: after vanilla's per-frame chunk deadline stops queueing rebuilds, keep queueing visible dirty chunks (same order and calls) until the builder threads have about one frame of work; uploads keep vanilla's deadline. No measurable effect in a software-rendering container, where uploads rather than idle workers were the limit (2026-10-23); unmeasured on the reference hardware. Runtime-toggleable; not in `render.defaults`. |
| `render.chunkTimeFloor` | `false` | Experimental: when vanilla's chunk deadline leaves less than 1/32 of the previous frame (at most 4.2 ms), allow that much for uploading built chunks and queueing rebuilds. On llvmpipe it cut edit-update latency p90 by 76% but failed its frozen gates on flight frame cost (+20% mean) and visible holes (+12%) (2026-10-23); unmeasured on the reference hardware. Runtime-toggleable; not in `render.defaults`. |
| `render.batchModelLists` | `false` | Experimental: combine face draws inside eligible vanilla model-part display lists. |
| `render.batchModelLists.verify` | `false` | Debug: compare native feedback from vanilla and alternate lists at compilation; reject differing lists. |
| `render.fastChunkUnpack` | `true` | Vectorized block state unpacking and linear block counter scan during chunk packet handling. |
| `render.fastChunkUnpack.verify` | `false` | Debug: compare fast chunk unpacking and block counters with vanilla's scalar logic. |
| `render.fastTerrainPipeline` | `false` | Experimental direct terrain layer submission and filtering; off after layer-count verifier differences. |
| `render.fastTerrainPipeline.verify` | `false` | Debug: compare fast terrain layer rendered chunk counts with vanilla. |
| `render.fastEntityLighting` | `true` | Answer entity light and brightness queries without BlockPos allocations or chunk hash lookups. |
| `render.fastEntityLighting.verify` | `false` | Debug: also run vanilla's entity light queries, use their results and log any difference. |
| `render.fastEntityRendererLookup` | `true` | Cache entity renderer lookups to eliminate repeated map searches in entity rendering. |
| `render.fastEntityRendererLookup.verify` | `false` | Debug: also run vanilla's renderer map query, use its result and log any difference. |
| `render.fastEntityCulling` | `true` | Evaluate entity distance and frustum before entity state preparation and renderer invocation. |
| `render.fastEntityCulling.verify` | `false` | Debug: also run vanilla's shouldRender check, use its result and log any difference. |
| `render.fastRayTrace` | `true` | Trace crosshair and entity rays without intermediate Vec3d or BlockPos allocations. |
| `render.fastRayTrace.verify` | `false` | Debug: also run vanilla's ray trace, use its result and log any difference. |
| `render.fastRandomDisplayTicks` | `true` | Reduce client ambient tick position and lookup allocations, preserving vanilla per-tick Random lifetime. |
| `render.fastRandomDisplayTicks.verify` | `false` | Debug: also verify fast display tick block queries against vanilla, logging differences. |
| `render.fastSoundThread` | `true` | Wake paulscode sound threads via lock-free parking instead of Thread.sleep and thread interruption. |
| `render.fastSoundThread.verify` | `false` | Debug: verify sound thread wake timing, lifecycle and interruption handling against expected bounds. |
| `render.cameraHysteresis` | `false` | Experimental exact-input visibility reuse; off pending diagnosis of traversal verifier differences. |
| `render.cameraHysteresis.verify` | `false` | Debug: compare retained visibility against a fresh vanilla search and use vanilla's result. |
| `render.flatOcclusion` | `false` | Experimental packed section connectivity flags; off after native verifier differences. |
| `render.flatOcclusion.verify` | `false` | Debug: also test chunk occlusion via vanilla CompiledChunk.isVisible and log differences. |
| `render.vectorFrustum` | `true` | Vectorized frustum testing of chunk bounding boxes against the 6 frustum planes. |
| `render.vectorFrustum.verify` | `false` | Debug: also run vanilla frustum culler and log differences. |
| `render.fastWorkerAo` | `false` | Experimental worker block-state/light caching; off after frame-tail and CPU gate failures. |
| `render.fastWorkerAo.verify` | `false` | Debug: also verify worker block state queries against vanilla chunk lookups. |
| `render.fastModelTransforms` | `false` | Experimental zero-translation skipping; off after static p99/CPU gate failures. No hierarchy flattening or push/pop removal. |
| `render.fastModelTransforms.verify` | `false` | Debug: retain vanilla translations and sample raw matrix bits before/after zero translations. |
| `render.fastMipmaps` | `true` | Fast LUT-based gamma mipmap generation and parallel sprite downsampling. |
| `render.fastMipmaps.verify` | `false` | Debug: compare fast mipmap results against vanilla downsampling and log differences. |
| `render.reuseParticleBoxes` | `false` | Experimental: reuse immutable zero-offset particle movement boxes with bit-exact safe coordinates. Off by default. |
| `render.reuseParticleBoxes.verify` | `false` | Debug: compare particle movement Box coordinates against vanilla using raw double bits. |
| `render.scalarParticleEntityQuery` | `false` | Experimental: scalar particle collision nearby-entity query bounds. Off by default. |
| `render.scalarParticleEntityQuery.verify` | `false` | Debug: compare scalar bounds and ordered entity query results with vanilla. |
| `render.reuseShadowPositions` | `false` | Experimental: pass one reused position instead of a new BlockPos to each entity shadow's block-below query (same query and answer). Off by default; its hook is installed only when on at startup (or verified, or benchmark-toggled). |
| `render.reuseShadowPositions.verify` | `false` | Debug: keep vanilla's new position, compare the reused one's coordinates and the block state answered for each, and log any difference. |
| `render.modernPipeline` | `false` | Modern OpenGL rendering pipeline (TerrainShader, VAO attribute binding, and precomputed chunk adjacency). |
| `render.multiDrawIndirect` | `false` | Experimental OpenGL 4.3 Multi-Draw Indirect submission over shared resident mesh storage. |
| `render.multiDrawIndirect.verify` | `false` | Debug: validate indirect draw command structs, quads, and origin records against reference direct draws. |
| `render.fastDisplaySync` | `false` | Experimental: high-precision frame limiter and pacer using LockSupport.parkNanos and Thread.onSpinWait, with GPU timer-query margins. Not yet qualified against vanilla's Display.sync; off by default. See the 1.0.0 frame pacing check. |
| `render.fastDisplaySync.verify` | `false` | Debug: verify frame limiter sleep durations and pacing error bounds. |
| `render.fastSwapBuffers` | `false` | Experimental: early 3D pipeline flush to overlap CPU HUD/GUI rendering with GPU draw execution. Not yet qualified; off by default. See the 1.0.0 frame pacing check. |
| `render.fastSwapBuffers.verify` | `false` | Debug: verify pipeline flush execution and fence status. |
| `render.suppressThreadYield` | `false` | Experimental: skip vanilla's unconditional client-thread yield after the buffer swap. Not yet qualified; off by default. See the 1.0.0 frame pacing check. |

The experimental `render.batchModelLists` option (default `false`) combines the face draws
inside eligible vanilla model parts' display lists. Animation, transforms, child parts and
layers still use vanilla's render methods. Custom part/box/polygon subclasses use vanilla.
`render.batchModelLists.verify=true` compares native OpenGL feedback from both lists at
compilation, including transformed geometry, lit color, texture coordinates and final current
normal/texture coordinates; a difference rejects that alternate list. It does not prove
framebuffer identity or compatibility with arbitrary injections into list compilation.

Its hooks are installed only when the option or verification is enabled at startup, or when
`benchmark.toggle=render.batchModelLists` requests them. The ordinary disabled default adds
no model/upload hooks. In-process A/B switches between the retained vanilla and alternate
lists without recompiling during the measured segments.

## Benchmarking

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=mytest -Pwindow=1920x1080 -Pxmx=2G
```

This plays a fresh copy of a world called `alacer-benchmark` with a fixed seed, freezes time
and weather, switches to spectator mode and replays three scripted camera paths, each recorded
frame by frame:

| Phase | Duration | What it stresses |
|---|---|---|
| `static` | 10 s | Camera still at ground level: steady-state rendering. |
| `pan` | 15 s | Camera turning in place: visibility and culling work every frame. |
| `fly` | 20 s | Camera moving at 20 blocks/s: chunk loading, building and uploading. |

Results go to `run/alacer/benchmarks/<timestamp>-<label>/` (`summary.json`, plus a JSON summary
and raw CSV per phase) and the game exits. The first run generates the base world (**discard its
results**); every later run copies it to `alacer-benchmark-run`, so nothing a run changes
carries over. Additional options: `alacer.benchmark.entities` (motionless mobs placed around the
camera), `alacer.benchmark.chests`, `alacer.benchmark.viewDistance` (default 12),
`alacer.benchmark.vbo` (true/false; default leaves the option alone), `alacer.benchmark.seed`,
`alacer.benchmark.timeScale` (scales phase durations), `alacer.benchmark.fpsLimit` (10-260; default unlimited, which never calls the frame limiter), `alacer.benchmark.exit`,
`alacer.benchmark.screenshots` (saves `screenshots/alacer-<label>-<phase>.png` at the end of each
phase; with `alacer.benchmark.screenshotEndPose=true` it first renders one unrecorded frame at the
phase's exact end pose, so pan captures have the same yaw at any frame rate), and `alacer.benchmark.timeout` (seconds, default 600, 0 = off: if the benchmark has not
finished by then, the game exits with code 3, so a scripted series of launches cannot hang).

To compare an option on and off, prefer **in-process A/B**: with
`-Palacer.benchmark.toggle=<runtime-toggleable option>` the static and pan phases run as
segments A, B, B, A in one launch, with the option off for A and on for B. Separate launches can
land in different graphics-driver threading modes, which changes the cost of OpenGL calls far
more than a small optimization does (see BENCHMARKS.md).

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.toggle=render.fastEntityChunkLookup -Palacer.benchmark.entities=200 -Pwindow=1920x1080 -Pxmx=2G
python tools/compare_benchmarks.py --toggle run/alacer/benchmarks/*-ab
```

For the **Phase 10 combined comparison**, use `-Palacer.benchmark.toggle=render.defaults`.
A uses vanilla render paths for all default optimizations; B enables the shipped defaults.
All experiments remain off, including `render.fastChunkFrustum`. Phase 10 measured the first five; since session 11 the preset
also switches `render.fastEntityShadows`, since session 15 `render.fastParticleLight`, since session 16 `render.fastParticleRemoval`, and since session 20 `render.fastParticleVertices`. The runner warms both paths in static and pan,
settles after each switch, excludes the recorder's setup frame and exports each phase's actual
config. Detail and
verification must be off. This measures both paths with the same profiler installed; it is
not a separate unmodified-client launch. On the test machine, B was faster than A in all nine
launches, static and pan; median FPS gains were +15.7% to +68.9% depending on mob count and
view. See [Phase 10 evidence](docs/benchmarks/2026-10-19-phase10-vanilla/README.md).

To attribute what entity shadows cost, `-Palacer.benchmark.toggle=benchmark.disableEntityShadows`
turns the game's own Entity Shadows option off in B (this changes the image; it is not a config
option), best with `-Palacer.profiler.shadowTiming=true`. It and `render.fastEntityShadows` use
the Phase 10 protocol (both paths warmed; detail and verification must be off). For shadow
correctness, `-Palacer.benchmark.shadowDiagnostic=true` with
`-Palacer.render.fastEntityShadows.verify=true` keeps the mobs' AI and keeps changing blocks
inside their shadows; it is not a performance comparison. See
[session 11](docs/benchmarks/2026-10-19-shadows/README.md).

For the chunk-frustum investigation, `-Palacer.benchmark.toggle=render.fastChunkFrustum`
also warms both paths and requires detail, verification and replay timing off. Box verification
uses `-Palacer.render.fastChunkFrustum.verify=true`; to compare the complete candidate traversal,
instead use `-Palacer.render.fastChunkFrustum=true -Palacer.render.fastChunkVisibility.verify=true`
with frustum verification off. See [session 12 evidence](docs/benchmarks/2026-10-19-chunk-frustum/README.md).

For the **chunk-edit scenario**, add `-Palacer.benchmark.chunkEdit=true` (best with
`-Palacer.profiler.chunkLatency=true`). From the `benchmark.chunkFlight=near` pose (24 blocks above the
anchor, facing east) it records an unscored `warmup-pan` (10 s), `still` (10 s) and `edit` (30 s), in which one
block in each of 16 render chunks ahead of the camera is placed or removed every second (28 bursts), on a schedule
that follows phase time so a slower scheduler builds a backlog. Each launch plays a fresh, byte-identical world
copy; compare separate launches in alternating order. See the
[2026-10-23 chunk scheduling archive](docs/benchmarks/2026-10-23-chunk-scheduling/README.md).

For the dedicated **particle scenario**, add `-Palacer.benchmark.particles=true`. Instead of the
usual paths it records `static` (10 s) and `pan` (15 s) at the anchor, without added mobs, while 16
stationary emitters around the camera emit a fixed, seeded schedule every client tick: one stone
block break (64 particles), 48 smoke, 32 flame, 48 redstone dust, 16 small and 2 large explosion
particles, about 3,300 particles alive. `-Palacer.benchmark.particleScale=2` (1-8) multiplies the
schedule; at 2 vanilla's 4,000-per-list cap is reached. The Particles option is set to All. It
works with in-process A/B (`render.fastParticleLight`, `render.fastParticleRemoval`, `render.fastParticleVertices`,
`render.fastParticleLightEntry`, `render.reuseParticleLight`, or
`benchmark.disableParticles`, which stops the schedule and clears the particles in B to attribute
their cost; all use the Phase 10 protocol). For particle light verification,
`-Palacer.benchmark.particleDiagnostic=true` with `-Palacer.render.fastParticleLight.verify=true` adds
particles above and below the build height and keeps changing the blocks under the emitters; it
is not a performance comparison. See [session 15](docs/benchmarks/2026-10-20-particles/README.md).
For removal verification, `-Palacer.benchmark.particleRemovalDiagnostic=true` with
`-Palacer.render.fastParticleRemoval.verify=true` adds probe particles that change layers while
ticking, add particles to a full list while it ticks, share a network id with a live particle,
appear twice, or override `equals`; it is not a performance comparison either. Removal shows in the
frames that tick, so compare tick-frame means and tails, not only frame means. See
[session 16](docs/benchmarks/2026-10-20-particle-removal/README.md). For vertex verification,
`-Palacer.render.fastParticleVertices.verify=true` (best with both particle diagnostics) compares
every element vanilla writes with the direct bytes. The in-process A/B runs vanilla through the
installed hooks; to compare with hooks absent, launch separately with
`-Palacer.render.fastParticleVertices=false` (no toggle). See
[session 20](docs/benchmarks/2026-10-20-particle-vertices/README.md) and
`tools/run_particle_vertices.py`. The light-entry experiment works the same way
(`-Palacer.render.fastParticleLightEntry=true -Palacer.render.fastParticleLightEntry.verify=true`;
[session 21](docs/benchmarks/2026-10-20-particle-light-entry/README.md),
`tools/run_particle_light_entry.py`), and so does per-pass light reuse
(`-Palacer.render.reuseParticleLight=true -Palacer.render.reuseParticleLight.verify=true`;
[session 22](docs/benchmarks/2026-10-20-particle-light-reuse/README.md),
`tools/run_particle_light_reuse.py`); `-Palacer.profiler.particleLightRepeats=true` counts the light
queries and distinct positions per pass (attribution only). The run directory's
`run/config/alacer.properties` keeps every option's last file value, which overrides a changed
default; pass options explicitly in benchmark launches.

For cloud correctness diagnostics, `-Palacer.benchmark.cloudSweep=true` replaces the usual
paths with views below, through and above the cloud layer, positive/negative texture wrapping,
rain at dusk, anaglyph, flat/disabled clouds and a return to fancy. Use it with
`-Palacer.render.cacheClouds.verify=true` at the usual view distance of 12 (a smaller distance
can leave the initial spawn outside the loaded area). This generates chunks and is not a
performance comparison. Screenshots
can be saved with `-Palacer.benchmark.screenshots=true` (also useful with verification off to
inspect the cached drawing).

For the dedicated **display-list versus VBO investigation**, use
`-Palacer.benchmark.backendScenario=true -Palacer.benchmark.vbo=false` (display lists) or
`true` (vanilla VBOs). This creates four overlapping white stained-glass walls in the per-run
world copy, records discarded rotation/orbit warmups, then `steady` (10 s), `pan` (15 s),
`sort` (16 s) and one fresh `fly` (20 s). It requires an explicit backend and rejects other
scenario toggles/populations. The backend is selected before world load, checked against
GLX and actual render-chunk storage in every phase, and the prior VBO option is restored
through vanilla reload after export, before automatic exit. No backend is forced in ordinary
play. `profiler.chunkDetail=true`
adds opt-in terrain submission/chunk counts to the existing upload/build/sort diagnostics;
leave it off for selection. See the [Session 14 protocol and evidence](docs/benchmarks/2026-10-19-display-lists-vbo/README.md).

The independent culling-math/modern-OpenGL trials use this same retained scene
and the ordinary route on Java 25. Each option stays off by default. See the
[mechanisms, gates and native-GPU results](docs/benchmarks/2026-10-23-modern-rendering/README.md).
`-Palacer.benchmark.toggle=render.modernPipeline` toggles their combination for
diagnostics; it is not a saved preset. `-Palacer.modernGl.forceFallback=true`
forces the original GL path for compatibility checks.

On the measured Java 8 profile, VBO reduced paired mean frame time by 51–61%, but rare fly
maxima, allocation per second and one queue-wait proxy worsened. This is a throughput
preference with limits; no backend or production render default is changed. Both Java 8
backend baselines are retained. Fresh Java 25 results and compatibility gates are recorded
separately in [the Java 25 migration archive](docs/benchmarks/2026-10-20-java25/README.md).

Separate launches can be compared too; alternate the variants (A, B, B, A) and pool them. The
directory name ends with the label and the main toggles, for example `mytest-vis_on-ecl_on-ent200`:

```bash
python tools/compare_benchmarks.py run/alacer/benchmarks/*-a1-* run/alacer/benchmarks/*-a2-* -- run/alacer/benchmarks/*-b1-* run/alacer/benchmarks/*-b2-*
```

**Benchmark stability (session 31, all opt-in; without them every scenario is unchanged).**
`alacer.benchmark.reference=<s>` adds an identical `reference-pre` phase at the static view before the
scenario (`referencePost=true` adds `reference-post` after it); `phaseSeconds=static:60,steady:100`
sets recorded lengths of named phases (literal seconds, not scaled); `repeatSegments=<n>` with
`repeatSegmentSeconds` adds identical in-process segments; `toggleBlocks=<n>` with
`toggleSegmentSeconds` turns a toggle run's static segments into short ABBA/BAAB blocks;
`processorSampling=true` records the client thread's logical processor per frame;
`mobPoseCheck=true` records mob movement per phase; `freezeMobs=true` sets NoAI on every loaded mob,
including the world's own animals. `tools/aa_pilot.py` runs a monitored A/A pilot (identical builds
on both sides) with calibration, predeclared rejection rules, resumable retries and a statistical
report; the protocol and the Windows commands are in
[the session 31 A/A pilot archive](docs/benchmarks/2026-10-22-aa-pilot/README.md).

## License

Alacer is licensed under the GNU Lesser General Public License, version 3 only
(`LGPL-3.0-only`). See [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING), which holds the GNU
GPL v3 that the LGPL builds on. [THIRD_PARTY.md](THIRD_PARTY.md) lists the dependencies and their
licenses.
