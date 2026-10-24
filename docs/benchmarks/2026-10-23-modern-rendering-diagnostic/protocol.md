# Diagnostic investigation protocol: terrain VAO mechanism attribution

## Context and objective

The native follow-up runs on 95cceb6 confirmed that Alacer's active terrain VAO path
regressed pan frame time by 61-67% and CPU/frame by 110-115% across both ordinary-control
and hooks-present forced-fallback comparisons. Terrain layer wall sections nearly tripled.
The active path is implicated, but the exact mechanism—distinguishing active Java overhead,
GL binding queries, LWJGL2 StateTracker bookkeeping, downstream NVIDIA driver VAO switching,
and exact workload parity—remains unresolved.

The human lifted the testing hold. This protocol specifies a finite diagnostic investigation
to isolate the root mechanism before implementing a repair or replacement.

All work is conducted in `C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer` on branch
`claude/magical-planck-7t9612`. The separate clean main checkout remains untouched.

## Hypotheses

- **H1 (Workload divergence)**: Active VAOs inadvertently change the number of chunks rendered,
  draw calls executed, or vertices submitted compared to vanilla VBO rendering.
- **H2 (Query latency)**: `GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)` in `ModernGl.beginArrays`
  causes synchronous GPU pipeline flushes at the beginning of each terrain block layer.
- **H3 (Active Java / Mixin overhead)**: Per-chunk Java checks (`buffer.getClass()`, interface
  casting to `ModernVertexBuffer`, `alacer$vertexArray()`, context capabilities identity checks)
  in `TerrainVaoMixin.alacer$bind` dominate the CPU regression.
- **H4 (LWJGL2 StateTracker overhead)**: `GL30.glBindVertexArray` invokes `StateTracker.bindVAO`,
  performing a `FastIntMap<VAOState>` hash map query and insertion on every chunk bind.
- **H5 (NVIDIA driver VAO switching cost)**: `nglBindVertexArray` in `nvoglv64.dll` invalidates
  driver-side vertex attribute descriptors, state caches, and hardware fetch setups when switching
  between thousands of distinct VAO object names per frame.

## Tool limitations and discrimination strategy

Native Windows OS stack profiling via `wpr.exe` or `xperf.exe` was evaluated and found unavailable
due to Windows security policy (`0xc5585011: Failed to enable the policy to profile system performance`),
which restricts kernel ETW CPU sampling to elevated administrative sessions.

To rigorously separate mechanisms without native ETW, the investigation employs:
1. Workload instrumentation: exact counts of terrain chunks rendered, draw calls, and vertex count
   in both control and candidate arms.
2. Controlled diagnostic execution arms isolating specific components of the active pipeline.
3. JFR non-safepoint profiling to capture Java-thread execution distribution and native boundary calls.

## Hard launch budget (strictly <= 6 clients)

Six serial native GPU clients on Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA 616.92, Temurin Java 25.0.4.101,
1920x1080, view distance 12, vanilla VBOs, 2 GiB heap:

1. **D1 (`diag-1-ctrl`)**: Ordinary control baseline (`terrainVaos=false`, `forceFallback=false`)
   with diagnostic workload instrumentation (chunks, draw calls, vertices).
2. **D2 (`diag-2-vao`)**: Active VAO baseline (`terrainVaos=true`, `forceFallback=false`)
   with diagnostic workload instrumentation.
3. **D3 (`diag-3-noquery`)**: Active VAO with query bypassed (`terrainVaos=true`, `skipVaoQuery=true`).
4. **D4 (`diag-4-nobind`)**: Active VAO with all Java checks and tracking active, but
   `GL30.glBindVertexArray` call bypassed (`terrainVaos=true`, `diagnosticNoGlBind=true`).
5. **D5 (`diag-5-fallback`)**: Forced fallback with hooks present (`terrainVaos=true`, `forceFallback=true`).
6. **D6 (`diag-6-jfr`)**: Active VAO with JFR profiling enabled (`-Pjfr=diag-jfr`, `-PjfrDebugNonSafepoints=true`).

## Discriminating observations and stop rules

- **H1 Test**: If D1 and D2 have matching terrain draw calls and vertices (delta < 0.1%), H1 is rejected.
- **H2 Test**: D2 vs D3 determines the exact fraction of the regression caused by the GL query.
- **H3 vs H4/H5 Test**:
  - In D4, all Java checks in `alacer$bind`, interface calls, array lookup, and state tracking execute,
    but the call to `GL30.glBindVertexArray` is skipped.
  - If D4 frame time drops back to D1 baseline (~0.61 ms), H3 is definitively rejected: the regression
    is located inside `GL30.glBindVertexArray` (LWJGL bookkeeping + NVIDIA driver VAO switching).
  - If D4 frame time remains elevated (~1.0 ms), H3 is confirmed as the primary bottleneck.
- **Stop rules**:
  - Hard limit of 6 launches. Never exceed 6.
  - 240-second timeout per launch.
  - Abort immediately on non-zero exit code, client crash, or reported OpenGL error.
  - Serial execution only: no background builds, tests, or concurrent client processes.
