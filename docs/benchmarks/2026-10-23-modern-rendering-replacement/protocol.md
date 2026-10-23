# Replacement terrain rendering pipeline protocol

## Objective

Evaluate the streamlined terrain rendering pipeline replacement against the retained ordinary
control (Arm A) on native hardware, using the same test scenarios, route, base world, and settings
as the original modern-rendering benchmark.

The original failed VAO experiment (`2026-10-23-modern-rendering`) and the bounded follow-up
(`2026-10-23-modern-rendering-followup`) remain immutable historical archives.

## Machine and runtime conditions

- OS: Native Windows 11
- CPU: Intel Core i7-12700F (20 logical processors)
- GPU: NVIDIA GeForce RTX 3060 Ti, driver 616.92, OpenGL 4.6.0
- JDK: Eclipse Adoptium Temurin 25.0.4.101-hotspot (JAVA_HOME and PATH)
- Display / Resolution: Native desktop, initial window 1920x1080
- Settings: Render distance 12, vanilla VBOs enabled, 2 GiB heap (`-Pxmx=2G`)
- World: Fresh copies of seed-1789 base world at `run/saves/alacer-benchmark`
- Unrelated experiments off: `render.fastChunkNeighbours=false`, `render.directBufferUploads=false`
- Profiler: Standard frame profiler; JFR, detailed entity probes, and intrusive verify modes off.

## Conditions and Arms

- **Arm A (Control)**: `render.terrainVaos=false`, `modernGl.forceFallback=false`.
  Default startup: no modern GL hooks installed.
- **Arm B (Candidate)**: `render.terrainVaos=true`, `modernGl.forceFallback=false`.
  Active streamlined terrain VBO rendering pipeline.

## Execution and budget

- Stage 1: Default scene (static, pan, fly). Four adjacent balanced pairs in `ABBABAAB` order (8 launches).
- Stage 2 (conditional on Stage 1 completing validly): Terrain backend scenario (`-Palacer.benchmark.backendScenario=true`).
  Four adjacent balanced pairs in `ABBABAAB` order (8 launches).
- Hard budget: at most 16 native GPU launches total across Stage 1 and Stage 2.
- Stop rules:
  - 240-second timeout per launch.
  - Abort immediately on non-zero exit code, client crash, dropped frames, or reported OpenGL error.
  - Serial execution only: no background builds, tests, or concurrent client processes.

## Verification

Before and after every launch:
- Check source and base-world SHA256 hashes against frozen manifest.
- Verify process isolation (no other Java client or launcher process active).
- Validate launch receipt, summary JSON, and phase configuration dictionary.
