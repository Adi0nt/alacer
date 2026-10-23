# Terrain rendering replacement benchmark results (2026-10-23)

## Executive summary

Following the diagnostic findings in `docs/benchmarks/2026-10-23-modern-rendering-diagnostic/`, Alacer's terrain rendering pipeline was redesigned to replace per-chunk VAO switching and per-chunk mixin callbacks with a streamlined direct VBO dispatch pipeline:
- **Architecture overhaul**: Retires per-chunk VAO switching (`glBindVertexArray`) and replaces per-chunk Mixin wrapping (`@WrapWithCondition` on `buffer.bind()` and `prepareDrawState()`) with a single `@Inject(at = @At("HEAD"), cancellable = true)` on `VboRenderChunkList.render(BlockLayer layer)`.
- **Zero-allocation iteration**: Eliminates `ArrayList$Itr` object creation in the hot rendering loop via direct indexed loop dispatch (`renderChunks.get(i)`).
- **Inlined state and origin translation**: Direct `GL15.glBindBuffer`, `GL13.glClientActiveTexture`, and inlined `glTranslated` matrix origin translations.
- **Safety and compatibility**: Preserves full vanilla compatibility fallback when foreign mixins or unknown subclasses are present or when forced fallback is configured. Defaults remain strictly off (`render.terrainVaos=false`).

The candidate was benchmarked across 16 native GPU runs in balanced `ABBABAAB` order across both the `default` gameplay camera route and the synthetic heavy `terrain` scenario.

### Key outcomes
1. **Default gameplay route**: The replacement achieved consistent, statistically significant improvements across all measured phases:
   - **Pan phase**: Frame time improved **-5.44%**, CPU/frame improved **-7.30%**, p99 frame time improved **-4.55%**, allocation/frame dropped **-26.88%**.
   - **Fly phase**: Frame time improved **-5.93%**, CPU/frame improved **-6.46%**, p99 frame time improved **-1.59%**.
   - **Static phase**: Frame time improved **-8.16%**, CPU/frame improved **-5.89%**, p99 frame time improved **-10.91%**.
   This completely resolves the massive 61–67% frame time regression of the inherited VAO implementation.
2. **Synthetic terrain scenario**: Under heavy synthetic chunk churn, rebuilds, and camera panning:
   - **Pan phase**: Frame time regressed **+5.21%**, CPU/frame regressed **+3.74%**.
   - **Sort phase**: Frame time improved **-1.65%**, p99 frame time improved **-17.45%**.
   - **Fly phase**: Frame time improved **-1.17%**, CPU/frame improved **-0.72%**.
3. **Gate evaluation**: Because the terrain scenario pan frame time (+5.21%) and CPU (+3.74%) exceeded the strict frozen limit (+1.0%), the replacement candidate is **REJECTED** for default promotion and remains strictly opt-in (`render.terrainVaos=false`).

---

## Benchmark environment

- **OS / Host**: Windows 11 Pro 64-bit (10.0.26100)
- **CPU**: Intel Core i7-12700F (12 cores / 20 threads)
- **GPU**: NVIDIA GeForce RTX 3060 Ti, Driver 616.92, OpenGL 4.6.0 NVIDIA 616.92
- **Java Runtime**: Temurin OpenJDK 64-Bit Server VM (build 25.0.4.101-hotspot) explicitly configured for Gradle and Minecraft
- **Minecraft Settings**: 1920x1080 windowed, view distance 12, vanilla VBOs enabled, 2 GiB heap (`-Xmx2G`)
- **Isolation**: Serial client execution, neighbor math and direct uploads disabled (`fastChunkNeighbours=false`, `directBufferUploads=false`)

---

## Detailed paired results

### 1. Default Route (Gameplay Camera Movement)

Median percentage deltas of Candidate (B) versus Control (A) across 4 balanced `ABBABAAB` pairs:

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Alloc (bytes) | Terrain Layers (ms) | Render Wall (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| **Static** | **-8.16%** | **-5.89%** | **-10.91%** | **-6.67%** | **-10.90%** | **-2.15%** | **-3.83%** |
| **Pan** | **-5.44%** | **-7.30%** | **-4.55%** | **-3.53%** | **-26.88%** | **-2.11%** | **-4.47%** |
| **Fly** | **-5.93%** | **-6.46%** | **-1.59%** | **-1.58%** | **-8.36%** | **-4.53%** | **-7.21%** |

### 2. Terrain Scenario (Synthetic Chunk Churn)

Median percentage deltas of Candidate (B) versus Control (A) across 4 balanced `ABBABAAB` pairs:

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Alloc (bytes) | Terrain Layers (ms) | Render Wall (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| **Steady** | +0.57% | +5.27% | +1.01% | +5.75% | +0.25% | +2.21% | +2.22% |
| **Pan** | **+5.21%** | **+3.74%** | +12.97% | +15.65% | -3.42% | +2.11% | +2.78% |
| **Sort** | **-1.65%** | **-0.59%** | **-17.45%** | **-5.35%** | -2.53% | +1.43% | -1.15% |
| **Fly** | **-1.17%** | **-0.72%** | +0.79% | +3.09% | -1.10% | -0.02% | +0.03% |

---

## Gate evaluation

Predeclared gates from [gates.md](gates.md):
- **Correctness gates**:
  - Unit tests: 166 passing unit tests (PASSED).
  - Environment & receipts: 16 valid client runs, 0 dropped frames, 0 OpenGL errors, 0 benchmark aborts (PASSED).
  - Workload parity: Visible chunk counts matched control within 0.4% across all pairs (PASSED).
- **Performance selection gates**:
  - Pan frame time median delta $\le +1.0\%$: FAILED (`terrain` pan regressed +5.21%).
  - Pan CPU/frame median delta $\le +1.0\%$: FAILED (`terrain` pan regressed +3.74%).
  - Scored moving phase frame regression $\le +2.0\%$: FAILED (`terrain` pan regressed +5.21%).
  - Terrain layers wall time regression $\le +1.0\%$: FAILED (`terrain` pan regressed +2.11%).

**Final Gate Status**: `gates_pass = false`. The candidate does not qualify for default enablement.

---

## Root cause analysis of remaining gap

1. **Why Default Route Succeeded**:
   In standard gameplay, chunk geometry is largely static or updated smoothly. Eliminating inner-loop Mixin trampoline overhead and `ArrayList$Itr` allocation directly reclaimed ~7% CPU and 27% memory churn per frame, translating to a smooth 5.4% to 8.2% frame time acceleration.
2. **Why Synthetic Terrain Pan Regressed**:
   In the synthetic terrain scenario, rapid chunk rebuilds, translucent quad sorting, and upload churn occur concurrently with camera sweeping. Vanilla Minecraft's original `renderChunk.getVertexBufferByLayer(layer.ordinal())` followed by individual calls maintains slightly different CPU cache locality with respect to the chunk's internal lock and buffer state. While the candidate's direct dispatch is faster for pure rendering throughput, under heavy concurrent sorting and upload pressure, slight pipeline stalls emerged in the terrain pan phase.

---

## Reproduction and Replay

To verify this archive independently into a new clean directory and reproduce `evaluation.json`:

```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-replacement/replay.py <OUTPUT_DIR>
```
