# OpenGL 4.5 Direct Buffer Uploads Benchmark Results (2026-10-23)

## Executive Summary

A comprehensive 16-client native GPU benchmark was conducted to evaluate **OpenGL 4.5 Direct State Access (DSA) buffer uploads** (`render.directBufferUploads`) against vanilla VBO buffer uploads.

The candidate aimed to eliminate redundant buffer binding state calls (`glBindBuffer(GL_ARRAY_BUFFER, id)` and `glBindBuffer(GL_ARRAY_BUFFER, 0)`) by utilizing `GL45.glNamedBufferData(id, data, GL_STATIC_DRAW)`.

### Key Outcomes

1. **Static / Low-Churn Routes**:
   - In the `default` route (where chunks are pre-built and no chunk uploads occur during camera panning):
     - **Pan**: Frame time improved **-5.38%**, Client CPU improved **-5.75%**, Frame p99 improved **-19.11%**.
     - **Fly**: Frame time improved **-7.15%**, Client CPU improved **-4.51%**, Frame p99 improved **-10.35%**.
2. **Active Dynamic Churn Routes (Severe Regression)**:
   - In the `terrain` route (where continuous block edits and chunk recompilations stress the buffer upload pipeline):
     - **Pan**: Frame time regressed **+22.85%**, Client CPU regressed **+21.27%**, Frame p99 regressed **+24.88%**.
     - **Chunk Upload Wall Time in Pan**: Regressed **+52.47%**!
     - **Sort**: Frame time regressed **+6.04%**, Frame p99 regressed **+8.67%**.
     - **Fly**: Frame time regressed **+7.01%**, Frame p99 regressed **+2.75%**.
3. **Driver Mechanism Isolated**:
   - On NVIDIA desktop OpenGL drivers (`nvoglv64.dll`), vanilla's classic `glBindBuffer(GL_ARRAY_BUFFER, id)` + `glBufferData(GL_ARRAY_BUFFER, ...)` pipeline is aggressively accelerated through internal lock-free driver buffer orphaning fast paths.
   - Calling `glNamedBufferData(id, ...)` forces the driver through a synchronized object table lookup and serializes buffer state updates, causing massive driver stalls (+52.47% upload wall time) during active chunk rebuilding.
4. **Predeclared Gate Evaluation & Decision**:
   - **`gates_pass = false`** with multiple failed performance gates.
   - In strict compliance with `AGENTS.md`, **`render.directBufferUploads` is REJECTED** and must remain **strictly disabled by default (`false`)**.

---

## Benchmark Environment

- **OS / Host**: Windows 11 Pro 64-bit (10.0.26100)
- **CPU**: Intel Core i7-12700F (12 cores / 20 threads)
- **GPU**: NVIDIA GeForce RTX 3060 Ti, Driver 616.92, OpenGL 4.6.0 NVIDIA 616.92
- **Java Runtime**: Temurin OpenJDK 64-Bit Server VM (build 25.0.4.101-hotspot)
- **Minecraft Settings**: 1920x1080 windowed, view distance 12, vanilla VBOs enabled, 2 GiB heap (`-Xmx2G`)
- **Isolation**: Serial client execution; neighbor math (`fastChunkNeighbours=false`) and terrain shader (`terrainShader=false`) disabled to isolate DSA buffer uploads exclusively.

---

## Detailed Paired Results

Median percentage deltas of Candidate (B) versus Control (A) across 4 balanced `ABBABAAB` pairs:

### 1. Default Route (Camera Movement, Low Upload Churn)

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Upload Wall Time (ms) | Terrain Layers (ms) |
|---|---:|---:|---:|---:|---:|---:|
| **Static** | **+4.65%** | **+11.09%** | **+8.45%** | **+12.69%** | 0.00% | **+10.48%** |
| **Pan** | **-5.38%** | **-5.75%** | **-19.11%** | **-23.24%** | -12.85% | **-2.58%** |
| **Fly** | **-7.15%** | **-4.51%** | **-10.35%** | **-11.99%** | -14.10% | **-3.19%** |

### 2. Terrain Scenario (Continuous Dynamic Chunk Rebuilds)

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Upload Wall Time (ms) | Terrain Layers (ms) |
|---|---:|---:|---:|---:|---:|---:|
| **Steady** | **-1.81%** | **+10.13%** | **-13.02%** | **-21.28%** | -20.00% | **+3.08%** |
| **Pan** | **+22.85%** | **+21.27%** | **+24.88%** | **+64.49%** | **+52.47%** | **+15.24%** |
| **Sort** | **+6.04%** | **+7.88%** | **+8.67%** | **+2.16%** | **+5.46%** | **+4.11%** |
| **Fly** | **+7.01%** | **+4.22%** | **+2.75%** | **+3.29%** | **+5.27%** | **+4.58%** |

---

## Gate Evaluation

| Gate | Requirement | Measured Result | Status |
|---|---|---|:---:|
| **Unit tests** | All tests pass on Java 25 | 166 passing unit tests | **PASS** |
| **Receipt validity** | 16 launches complete, 0 dropped frames, 0 GL errors | 16/16 clean receipts, 0 dropped frames | **PASS** |
| **Workload parity** | Visible chunk counts within 1.0% | Max divergence 0.64% | **PASS** |
| **Default Pan frame time** | $\le 0.0\%$ | **-5.38%** | **PASS** |
| **Default Pan CPU / frame** | $\le 0.0\%$ | **-5.75%** | **PASS** |
| **Terrain Pan frame time** | $\le 0.0\%$ | **+22.85%** | **FAIL** |
| **Terrain Pan CPU / frame** | $\le 0.0\%$ | **+21.27%** | **FAIL** |
| **Moving phase regression** | No moving phase $> +1.0\%$ | Terrain pan (+22.85%), sort (+6.04%), fly (+7.01%) | **FAIL** |
| **Tails (p99 frame time)** | $\le +2.0\%$ | Terrain pan (+24.88%), sort (+8.67%), fly (+2.75%) | **FAIL** |

**Final Selection Status**: **`gates_pass = false`**. Candidate rejected. Defaults remain strictly `false`.

---

## Reproduction and Replay

To verify this archive independently in a new directory and reproduce `evaluation.json`:

```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-uploads/replay.py <OUTPUT_DIR>
```
