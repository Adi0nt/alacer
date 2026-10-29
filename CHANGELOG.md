# Changelog

## 1.0.0 (2026-10-28)

First release. Client-only, for Minecraft 1.8.9 on Ornithe Gen 2 with Fabric Loader 0.20.0+,
OSL 0.22.2 and Java 25. Licensed under LGPL-3.0-only.

### Optimizations on by default

Each one can be switched back to vanilla's code path in `config/alacer.properties` or under
**Options -> Video Settings -> Performance**, and most have a `.verify` mode that runs vanilla
alongside it and logs any difference.

- Terrain: allocation-free chunk visibility search with vectorized frustum tests, faster chunk
  packet unpacking.
- Entities: cached world-chunk and renderer lookups, early distance/frustum culling, empty-section
  skipping, allocation-free lighting and shadow queries.
- Particles: cached light queries, single-pass dead-particle removal, direct vertex writes.
- Client: throttled per-frame GL error checks, CPU-side camera and chunk matrices instead of
  `glGet` reads, cached fancy cloud geometry, allocation-free crosshair ray traces and random
  display ticks, lock-free sound thread wake-ups, lookup-table mipmap generation.

### Features

- In-game profiler with bounded per-frame history: overlay (F7), performance graph (F8) and
  JSON/CSV profile export (F9), all rebindable under Controls -> Alacer.
- Categorized Video Settings menu with search, staged Apply/Cancel, presets, per-feature
  graphics details, camera settings, a hold-to-zoom key and an Advanced page.
- Scripted, seeded benchmark (`-Palacer.benchmark=true`) with in-process A/B toggles, stability
  options and Python comparison tools. `alacer.benchmark.fpsLimit` exercises the frame limiter and
  `alacer.benchmark.resourceTiming` logs atlas and resource-reload durations.

### Opt-in experiments (off by default)

Not in the Video Settings menu or the `render.defaults` benchmark preset; enable them in the
config file only for investigation.

- Failed release qualification, see the
  [Java 25 defaults qualification](docs/benchmarks/2026-10-29-defaults-qualification/resume-1/README.md):
  `render.cameraHysteresis`, `render.flatOcclusion` and `render.fastTerrainPipeline` (verifier
  differences from vanilla); `render.fastWorkerAo` and `render.fastModelTransforms` (frame-tail and
  CPU gate failures).
- Failed the [frame pacing check](docs/benchmarks/2026-10-28-release-pacing/README.md):
  `render.fastDisplaySync`, `render.fastSwapBuffers` and `render.suppressThreadYield`.
- Earlier experiments: modern OpenGL pipeline (terrain shader, named buffer uploads,
  multi-draw indirect), exact chunk frustum test,
  camera-section face reuse, chunk worker feeding and time floor, particle light-entry/light
  reuse/movement/box/entity-query experiments, shadow position reuse, zero-translate skipping and
  model display-list batching.

### Fixes in release preparation

- Resident terrain VBOs now bind their arena page and draw from their actual vertex offset
  when the shader/MDI path falls back to fixed-function rendering. Previously, enabling MDI
  with forced modern-GL fallback drew from an empty individual buffer and crashed in native
  `glDrawArrays`. Replacement uploads also release old slices before returning to ordinary
  storage. Enabling MDI alone or selecting its benchmark toggle also installs the required
  buffer/lifecycle hooks. MDI remains an opt-in experiment; see the
  [v1 hardening evidence](docs/benchmarks/2026-10-29-v1-hardening/README.md).
- Alacer's dirty-chunk set redirects in `WorldRenderer.setupRender` also intercepted vanilla's
  direction-set operations and cast them to `RenderChunk`, a `ClassCastException` whenever
  vanilla's own visibility search ran (observed with `render.cameraHysteresis.verify`; by code
  reading also with `render.fastChunkVisibility` off). They now apply
  only to the scheduling block; a bytecode-level regression test checks the selected calls.
- GPU timer queries for frame pacing run only while `render.fastDisplaySync` is on. Before, every
  frame issued them on OpenGL 3.3+ drivers even with the option off.
- The terrain pipeline and mipmap verifiers now report counts, and `render.fastTerrainPipeline`
  can be benchmark-toggled in process with both paths warmed.

### Known limitations

- All measurements come from one Windows 11 / NVIDIA RTX 3060 Ti machine and Standard-mode
  campaigns, which cannot support small-improvement claims. `render.vectorFrustum` and
  `render.fastMipmaps` stay on with exact verifiers but inconclusive performance results.
- The [muted native follow-up](docs/benchmarks/2026-10-29-remaining-features/README.md)
  exercised the actual remapped JAR in an isolated, manually assembled Loader installation
  and twelve scheduled calls to the resource-reload entrypoint. An external installer profile
  and physical F3+T delivery remain unverified.
- GPU compute culling and translucent sorting are unintegrated helpers with no production
  callers. The sorting helper runs on the CPU; these are not active opt-in GPU features.
- External launchers must pass `--enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector`.
- Build-time plugins (Fabric Loom, Ploceus) are `1.18-SNAPSHOT` versions; this build resolved 1.18.4.
