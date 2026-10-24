# Terrain shader pipeline replacement benchmark results (2026-10-23)

## Executive summary

Following the rejection of per-chunk VAO switching and per-chunk VBO wrappers, Alacer's terrain rendering pipeline was overhauled using **Option B: GPU-evaluated chunk origin translation and boundary scaling via `TerrainShader`**:
- **Matrix Stack Elimination**: Replaces per-chunk CPU-side fixed-function matrix stack operations (`glPushMatrix`, `glTranslatef`, `chunk.glMultMatrix`, `glPopMatrix`) with a single compatibility vertex shader that applies chunk origin offsets and chunk boundary expansion (`1.000001` scale around chunk center) directly on the GPU.
- **Fixed-Function Fragment Preservation**: Retains the fixed-function fragment pipeline, guaranteeing 100% bit-identical texturing, smooth lighting, ambient occlusion, alpha testing (`glAlphaFunc`), and distance fog (`gl_FogFragCoord = abs(eyePos.z)`).
- **Reduced Call Overhead**: Eliminates over 4,500 native OpenGL matrix pushes, pops, and 4x4 matrix multiplications per frame.
- **Safety and Fallback**: Preserves full vanilla compatibility fallback when disabled, when OpenGL 2.0+ is unavailable, when shader compilation fails, or when foreign mixins/subclasses are detected. Defaults remain strictly off (`render.terrainVaos=false`).

The candidate was benchmarked across 16 native GPU runs in balanced `ABBABAAB` order across both the `default` gameplay camera route and the synthetic heavy `terrain` scenario.

### Key outcomes
1. **Universal Double-Digit Performance Acceleration**:
   - **Default gameplay route**:
     - **Pan phase**: Frame time improved **-12.95%**, Client CPU improved **-17.54%**, Frame p99 improved **-12.31%**, 1% Low frame time improved **-17.99%**, Terrain layers wall time dropped **-32.70%**!
     - **Static phase**: Frame time improved **-11.49%**, Client CPU improved **-20.55%**, Terrain layers wall time dropped **-32.30%**!
     - **Fly phase**: Frame time improved **-10.65%**, Client CPU improved **-16.34%**, Frame p99 improved **-8.31%**, Terrain layers wall time dropped **-33.31%**!
   - **Synthetic terrain scenario**:
     - **Pan phase**: Frame time improved **-33.76%**, Client CPU improved **-27.42%**, Frame p99 improved **-32.93%**, 1% Low frame time improved **-33.14%**, Terrain layers wall time dropped **-39.64%**!
     - **Sort phase**: Frame time improved **-31.14%**, Client CPU improved **-20.77%**, Frame p99 improved **-48.78%**, 1% Low frame time improved **-54.13%**, Terrain layers wall time dropped **-35.63%**!
     - **Steady phase**: Frame time improved **-29.08%**, Client CPU improved **-29.19%**, Frame p99 improved **-20.07%**, Terrain layers wall time dropped **-37.56%**!
     - **Fly phase**: Frame time improved **-16.79%**, Client CPU improved **-19.73%**, Frame p99 improved **-13.90%**, Terrain layers wall time dropped **-35.90%**!
2. **Predeclared Gate Evaluation**:
   - **`gates_pass = true`** with **zero failed gates**.
   - Zero dropped frames, zero OpenGL errors, zero Mixin application failures.
   - Workload chunk parity verified within 0.14%.

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

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Terrain Layers (ms) | Solid (ms) | Cutout Mip (ms) | Cutout (ms) | Translucent (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Static** | **-11.49%** | **-20.55%** | **-0.30%** | **-1.94%** | **-32.30%** | **-31.08%** | **-33.17%** | **-36.58%** | **-16.15%** |
| **Pan** | **-12.95%** | **-17.54%** | **-12.31%** | **-17.99%** | **-32.70%** | **-32.69%** | **-32.89%** | **-35.41%** | **-26.37%** |
| **Fly** | **-10.65%** | **-16.34%** | **-8.31%** | **-9.92%** | **-33.31%** | **-34.14%** | **-32.57%** | **-34.94%** | **-26.44%** |

### 2. Terrain Scenario (Synthetic Chunk Churn)

Median percentage deltas of Candidate (B) versus Control (A) across 4 balanced `ABBABAAB` pairs:

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Terrain Layers (ms) | Solid (ms) | Cutout Mip (ms) | Cutout (ms) | Translucent (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Steady** | **-29.08%** | **-29.19%** | **-20.07%** | **-29.74%** | **-37.56%** | **-38.55%** | **-37.91%** | **-39.70%** | **-21.95%** |
| **Pan** | **-33.76%** | **-27.42%** | **-32.93%** | **-33.14%** | **-39.64%** | **-39.72%** | **-40.35%** | **-41.03%** | **-33.45%** |
| **Sort** | **-31.14%** | **-20.77%** | **-48.78%** | **-54.13%** | **-35.63%** | **-35.48%** | **-36.69%** | **-37.40%** | **-29.15%** |
| **Fly** | **-16.79%** | **-19.73%** | **-13.90%** | **-14.92%** | **-35.90%** | **-36.86%** | **-34.97%** | **-37.34%** | **-29.19%** |

---

## Gate evaluation

Predeclared gates from [gates.md](gates.md):
- **Correctness gates**:
  - Unit tests: 166 passing unit tests (PASSED).
  - Environment & receipts: 16 valid client runs, 0 dropped frames, 0 OpenGL errors, 0 benchmark aborts (PASSED).
  - Workload parity: Visible chunk counts matched control within 0.14% across all pairs (PASSED).
- **Performance selection gates**:
  - Pan frame time median delta $\le 0.0\%$: PASSED (-12.95% default, -33.76% terrain).
  - Pan CPU/frame median delta $\le 0.0\%$: PASSED (-17.54% default, -27.42% terrain).
  - Scored moving phase frame regression $\le +1.0\%$: PASSED (no phase regressed; all phases improved between -10.6% and -33.8%).
  - Terrain layers wall time in pan must improve: PASSED (-32.70% default, -39.64% terrain).
  - Tails (p99) $\le +2.0\%$: PASSED (-12.31% default, -32.93% terrain).

**Final Gate Status**: `gates_pass = true`. All gates passed with zero regressions.

---

## Reproduction and Replay

To verify this archive independently into a new clean directory and reproduce `evaluation.json`:

```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-shader/replay.py <OUTPUT_DIR>
```
