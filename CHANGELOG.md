# Changelog

## Unreleased qualification follow-up

- At the maintainer's direction, default off `render.cameraHysteresis`,
  `render.flatOcclusion`, `render.fastTerrainPipeline`, `render.fastWorkerAo` and
  `render.fastModelTransforms`. Remove them from the `render.defaults` benchmark
  preset and the Video Settings menu; explicit config/toggle opt-ins remain.
  The first three failed native verifier checks; the latter two failed frame/CPU
  gates in the [Java 25 qualification](docs/benchmarks/2026-10-29-defaults-qualification/resume-1/README.md).
- Vector frustum and mipmaps retain their defaults pending Extended work. The
  Standard campaign does not support a release-performance claim. Historical
  evidence, release tag and the completed campaign's frozen inputs are preserved.

## 1.0.0 (2026-10-28)

First release. Client-only, for Minecraft 1.8.9 on Ornithe Gen 2 with Fabric Loader 0.20.0+,
OSL 0.22.2 and Java 25. Licensed under LGPL-3.0-only.

### Optimizations on by default

Each one can be switched back to vanilla's code path in `config/alacer.properties` or under
**Options -> Video Settings -> Performance**, and most have a `.verify` mode that runs vanilla
alongside it and logs any difference.

- Terrain: allocation-free chunk visibility search with vectorized frustum tests and packed
  occlusion flags, exact-input visibility reuse, single-pass terrain layer submission, faster
  chunk packet unpacking and cached block states for chunk-builder ambient occlusion.
- Entities: cached world-chunk and renderer lookups, early distance/frustum culling, empty-section
  skipping, allocation-free lighting and shadow queries, skipped zero model translations.
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
  options and Python comparison tools; new `alacer.benchmark.fpsLimit` exercises the frame limiter.

### Opt-in experiments (off by default)

Modern OpenGL pipeline (terrain shader, VAOs, named buffer uploads, multi-draw indirect, GPU
culling and translucent sorting), exact chunk frustum test, camera-section face reuse, chunk
worker feeding and time floor, particle light-entry/light reuse/movement/box/entity-query
experiments, shadow position reuse, model display-list batching, and the frame pacing/swap
options `render.fastDisplaySync`, `render.fastSwapBuffers` and `render.suppressThreadYield`.

### Changes since the last development build

- `render.fastDisplaySync`, `render.fastSwapBuffers` and `render.suppressThreadYield` now default
  to off and are no longer in the Video Settings menu or the `render.defaults` benchmark preset.
  They had no archived measurement; see the
  [1.0.0 frame pacing check](docs/benchmarks/2026-10-28-release-pacing/README.md).
- GPU timer queries for frame pacing now run only while `render.fastDisplaySync` is on. Previously
  every frame issued them on OpenGL 3.3+ drivers even with the option off.
- Added license texts (`COPYING`, `COPYING.LESSER`, also inside the jar), mod authors, contact
  links and the `license` field to `fabric.mod.json`.
- README: release status, player installation steps, every configuration option documented.

### Known limitations

- Performance numbers are from one Windows/NVIDIA machine. Several default-on options from the
  2026-10-24/25 roadmap work (`render.cameraHysteresis`, `render.fastWorkerAo`,
  `render.fastTerrainPipeline`, `render.vectorFrustum`, `render.flatOcclusion`,
  `render.fastMipmaps`, `render.fastModelTransforms`) have correctness checks but no archived
  Java 25 A/B performance qualification. `render.fastModelTransforms` skips the same zero
  translations as the separate `render.skipZeroTranslates` experiment, which measured no benefit.
- External launchers must pass `--enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector`.
- Build-time plugins (Fabric Loom, Ploceus) are `1.18-SNAPSHOT` versions; this build resolved 1.18.4.
