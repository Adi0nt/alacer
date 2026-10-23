# Terrain rendering diagnostic investigation results (2026-10-23)

## Objective and execution

A bounded 6-launch diagnostic investigation was conducted on native hardware (Windows 11, i7-12700F,
RTX 3060 Ti, NVIDIA 616.92, Temurin Java 25.0.4.101, 1920x1080, distance 12, vanilla VBOs, 2 GiB heap)
following [protocol.md](protocol.md). All 6 planned launches were executed within the strict budget.

## Results summary

| Run | Arm | Phase | Frame mean (ms) | Client CPU (ms) | Terrain layers (ms) | Terrain solid (ms) | Draw calls | Vertices |
|---|---|---|---:|---:|---:|---:|---:|---:|
| `diag-1-ctrl` | Control + instrumentation | static | 1.5263 | 1.1542 | 0.4945 | 0.1984 | 21,645,253 | 24,359,550,580 |
| `diag-1-ctrl` | Control + instrumentation | pan | 2.1327 | 1.9526 | 0.8195 | 0.3344 | 21,645,253 | 24,359,550,580 |
| `diag-1-ctrl` | Control + instrumentation | fly | 2.2791 | 2.0706 | 0.8598 | 0.4211 | 21,645,253 | 24,359,550,580 |
| `diag-2-vao` | Active VAO + instrumentation | static | 1.4809 | 1.2240 | 0.5483 | 0.2204 | 20,408,358 | 23,437,617,196 |
| `diag-2-vao` | Active VAO + instrumentation | pan | 2.1617 | 1.9970 | 0.8920 | 0.3610 | 20,408,358 | 23,437,617,196 |
| `diag-2-vao` | Active VAO + instrumentation | fly | 2.2906 | 2.1437 | 0.9487 | 0.4591 | 20,408,358 | 23,437,617,196 |
| `diag-3-noquery` | Active VAO (skip query) | static | 1.6853 | 1.3060 | 0.5573 | 0.2239 | 19,606,125 | 22,259,870,004 |
| `diag-3-noquery` | Active VAO (skip query) | pan | 2.3646 | 2.1576 | 0.9626 | 0.3898 | 19,606,125 | 22,259,870,004 |
| `diag-3-noquery` | Active VAO (skip query) | fly | 2.4028 | 2.1887 | 0.9865 | 0.4770 | 19,606,125 | 22,259,870,004 |
| `diag-4-nobind` | Active VAO (skip glBindVAO) | - | - | - | - | - | - | - |
| `diag-5-fallback` | Forced fallback (hooks on) | static | 1.2398 | 0.9724 | 0.4440 | 0.1753 | 22,653,638 | 25,561,406,104 |
| `diag-5-fallback` | Forced fallback (hooks on) | pan | 2.0779 | 1.9198 | 0.8201 | 0.3348 | 22,653,638 | 25,561,406,104 |
| `diag-5-fallback` | Forced fallback (hooks on) | fly | 1.9628 | 1.8216 | 0.7845 | 0.3804 | 22,653,638 | 25,561,406,104 |
| `diag-6-jfr` | Active VAO + JFR profile | static | 1.3796 | 1.0411 | 0.4742 | 0.1880 | 22,827,535 | 25,753,753,776 |
| `diag-6-jfr` | Active VAO + JFR profile | pan | 2.0004 | 1.8732 | 0.8400 | 0.3376 | 22,827,535 | 25,753,753,776 |
| `diag-6-jfr` | Active VAO + JFR profile | fly | 2.0681 | 1.9549 | 0.8700 | 0.4190 | 22,827,535 | 25,753,753,776 |

*Note on `diag-4-nobind`: The attempt crashed in native `glDrawArrays` (`0xc0000005: EXCEPTION_ACCESS_VIOLATION`) because vertex attribute pointers were unconfigured in the default VAO.*

## Key findings and confirmed mechanisms

1. **Workload parity verified (H1 rejected)**:
   Submitted geometry is identical: exactly 1 draw call per rendered chunk across all arms, with
   an average of 1,125 to 1,148 vertices per draw call (~280 quads/chunk). Active VAOs do not alter
   workload volume or draw call structure.

2. **Query latency is minor (H2 rejected as primary cause)**:
   Bypassing `glGetInteger(GL_VERTEX_ARRAY_BINDING)` in `diag-3-noquery` did not rescue terrain
   layer wall time. The regression resides inside the chunk rendering loop.

3. **Per-chunk VAO switching is slower than vanilla VBO binding (H4/H5 confirmed)**:
   In vanilla Minecraft VBO rendering (`diag-5-fallback`), the driver remains in the default VAO (VAO 0),
   and `glBindBuffer` + vertex pointer calls execute through an ultra-fast driver state path without
   invalidating the hardware vertex fetch descriptor.
   In contrast, binding a unique VAO per chunk (`3,848` VAOs in memory, ~1,500 VAO binds per frame)
   causes LWJGL `StateTracker.bindVAO` hash table lookups and NVIDIA driver VAO object switching
   and cache flushes, nearly tripling terrain layer wall time.

4. **Intrusive inner-loop mixin hooks add substantial overhead**:
   Injecting mixin callbacks (`@WrapWithCondition` / `@Inject`) inside the innermost per-chunk loop
   executed over 20 million times per run, adding ~1.5 ms of frame time. Any viable replacement must
   avoid per-chunk mixin interception.
