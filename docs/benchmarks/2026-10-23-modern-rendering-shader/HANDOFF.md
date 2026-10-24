# Antigravity handoff: modern rendering shader pipeline (Option B)

## 1. Repository and Environment State

- **Active Worktree**: `C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer`
- **Branch**: `claude/magical-planck-7t9612`
- **Primary Checkout**: `C:/Users/CodyJ/OneDrive/Documents/GitLab/adiont/aevum/alacer` is clean main at `0de17a5aa557c78f9f394800a1ef23a8dbbe08d1` and remains completely untouched.
- **Java Runtime**: Explicit Temurin Java 25 (`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`) for Gradle daemon, compilation bytecode, tests, and Minecraft client execution.
- **System**: Windows 11 Pro 64-bit, Intel Core i7-12700F, NVIDIA GeForce RTX 3060 Ti (Driver 616.92, OpenGL 4.6), 1920x1080 windowed, view distance 12, vanilla VBOs, 2 GiB heap.
- **Process State**: No Minecraft client, diagnostic runner, or benchmark script is running. Gradle daemon may remain idle.
- **Configuration Defaults**: All experimental optimizations remain strictly off by default (`render.terrainVaos=false`, `render.fastChunkNeighbours=false`, `render.directBufferUploads=false`). Experimental GL hooks are not installed during default startup.

---

## 2. Investigation Context and Progression

1. **Original VAO Approach (Failed)**:
   - Regressed pan frame times by 61% to 67% and CPU/frame by 102% to 115%.
   - Diagnostic protocol (`docs/benchmarks/2026-10-23-modern-rendering-diagnostic/`) established:
     - Workload chunk and vertex geometry parity were identical (~910–967 draws/frame, ~1,125–1,148 vertices per draw).
     - Query latency (`glGetInteger`) was not the primary cause.
     - Root cause: Per-chunk VAO object binding (3,848 VAOs) flushes NVIDIA driver hardware vertex fetch descriptors and triggers LWJGL `StateTracker.bindVAO` hash table lookups, while vanilla VBO rendering operates entirely within default VAO 0.
2. **Direct VBO Dispatch Approach (Candidate 1, Rejected)**:
   - Streamlined VBO dispatch (`docs/benchmarks/2026-10-23-modern-rendering-replacement/`) eliminated VAO switching and hot-loop iterator allocation, recovering default gameplay route (-5.44% pan frame time, -26.88% allocation).
   - However, in the high-churn synthetic terrain scenario, pan frame time regressed +5.21% due to residual CPU-side fixed-function matrix stack operations (`glPushMatrix`, `glPopMatrix`, `glMultMatrix(FloatBuffer)`) across thousands of chunk passes.
3. **GPU-Evaluated Translation via TerrainShader (Option B, Accepted)**:
   - Replaces CPU-side matrix manipulation with GPU-evaluated chunk origin translation and boundary expansion.

---

## 3. Option B Architecture and Implementation

Source files added or modified:
- `net.adiont.alacer.render.TerrainShader`:
  - Implements a lightweight GLSL `#version 120` compatibility vertex shader.
  - Uniforms:
    - `u_ChunkOrigin`: 3D chunk translation relative to the camera (`chunkPos - cameraPos`).
    - `u_Scale`: Chunk boundary scaling factor (`1.000001`) applied relative to the chunk center (`8.0, 8.0, 8.0`), matching vanilla boundary expansion without floating point matrix composition.
  - Vertex transformation:
    ```glsl
    vec3 localPos = (gl_Vertex.xyz - 8.0) * u_Scale + 8.0;
    vec4 eyePos = gl_ModelViewMatrix * vec4(localPos + u_ChunkOrigin, 1.0);
    gl_Position = gl_ProjectionMatrix * eyePos;
    gl_FogFragCoord = abs(eyePos.z);
    ```
  - Passes through vanilla vertex attributes: `gl_TexCoord[0] = gl_MultiTexCoord0`, `gl_TexCoord[1] = gl_MultiTexCoord1` (lightmap), and `gl_FrontColor = gl_Color`.
  - **Fixed-Function Fragment Preservation**: No fragment shader is attached. The OpenGL fixed-function fragment pipeline performs texturing, mipmapping, smooth lighting, ambient occlusion, alpha testing (`glAlphaFunc`), and distance fog.
  - **Native Call Reduction**: Eliminates over 4,500 native `glPushMatrix`, `glPopMatrix`, and `glMultMatrix` calls per frame.
  - **Lifecycle and Fallback**: Managed lifecycle (`init()`, `cleanup()`). Clean fallback to vanilla chunk rendering if shader initialization fails or OpenGL 2.0+ is unavailable.
- `net.adiont.alacer.mixin.RenderChunkListAccessor`:
  - Mixin accessor interface exposing `viewPosition` coordinates (`x`, `y`, `z`) from `ChunkRenderDispatcher` / `VboRenderChunkList`.
  - Registered in `alacer.mixins.json`.
- `net.adiont.alacer.mixin.TerrainVaoMixin`:
  - Injects at head of `VboRenderChunkList.render(BlockLayer layer)`.
  - Binds `TerrainShader` once per layer.
  - Loops over visible chunks, binding chunk VBO, setting `u_ChunkOrigin`, and drawing arrays via direct LWJGL dispatch.
  - Unbinds shader at layer completion.
  - Preserves clean vanilla compatibility fallback when `render.terrainVaos` is false.

---

## 4. Benchmark Results and Gate Evaluation

Archive: `docs/benchmarks/2026-10-23-modern-rendering-shader/`
Protocol & Gates: `protocol.md`, `gates.md` (frozen before execution).
Design: 16 native GPU launches in balanced `ABBABAAB` pairs across `default` and `terrain` scenarios.

### Performance Deltas (Candidate B vs Control A)

#### 1. Default Gameplay Route (Camera Movement)

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Terrain Layers (ms) | Solid (ms) | Cutout Mip (ms) | Cutout (ms) | Translucent (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Static** | **-11.49%** | **-20.55%** | **-0.30%** | **-1.94%** | **-32.30%** | **-31.08%** | **-33.17%** | **-36.58%** | **-16.15%** |
| **Pan** | **-12.95%** | **-17.54%** | **-12.31%** | **-17.99%** | **-32.70%** | **-32.69%** | **-32.89%** | **-35.41%** | **-26.37%** |
| **Fly** | **-10.65%** | **-16.34%** | **-8.31%** | **-9.92%** | **-33.31%** | **-34.14%** | **-32.57%** | **-34.94%** | **-26.44%** |

#### 2. Synthetic Terrain Route (Chunk Churn)

| Phase | Frame Mean (ms) | Client CPU (ms) | Frame p99 (ms) | 1% Slow (ms) | Terrain Layers (ms) | Solid (ms) | Cutout Mip (ms) | Cutout (ms) | Translucent (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Steady** | **-29.08%** | **-29.19%** | **-20.07%** | **-29.74%** | **-37.56%** | **-38.55%** | **-37.91%** | **-39.70%** | **-21.95%** |
| **Pan** | **-33.76%** | **-27.42%** | **-32.93%** | **-33.14%** | **-39.64%** | **-39.72%** | **-40.35%** | **-41.03%** | **-33.45%** |
| **Sort** | **-31.14%** | **-20.77%** | **-48.78%** | **-54.13%** | **-35.63%** | **-35.48%** | **-36.69%** | **-37.40%** | **-29.15%** |
| **Fly** | **-16.79%** | **-19.73%** | **-13.90%** | **-14.92%** | **-35.90%** | **-36.86%** | **-34.97%** | **-37.34%** | **-29.19%** |

### Predeclared Gate Evaluation

- **Correctness Gates**:
  - Unit tests: 166 passing unit tests (PASSED).
  - Environment & receipts: 16 valid client runs, 0 dropped frames, 0 OpenGL errors, 0 benchmark aborts (PASSED).
  - Workload parity: Visible chunk counts matched control within 0.14% across all pairs (PASSED).
- **Selection Gates**:
  - Pan frame time median delta $\le 0.0\%$: PASSED (-12.95% default, -33.76% terrain).
  - Pan CPU/frame median delta $\le 0.0\%$: PASSED (-17.54% default, -27.42% terrain).
  - Scored moving phase frame regression $\le +1.0\%$: PASSED (no phase regressed; all phases improved between -10.6% and -33.8%).
  - Terrain layers wall time in pan must improve: PASSED (-32.70% default, -39.64% terrain).
  - Tails (p99) $\le +2.0\%$: PASSED (-12.31% default, -32.93% terrain).
- **Final Decision**: **`gates_pass = true`**. Candidate accepted without regressions. Defaults remain strictly off (`render.terrainVaos=false`) per policy until explicitly promoted by maintainers.

---

## 5. Reproduction and Continuation Commands

### Run Unit Tests
```powershell
./gradlew.bat test --offline
```

### Replay and Verify Shader Pipeline Archive
To verify raw data integrity and reproduce `evaluation.json` in a new clean directory:
```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-shader/replay.py <OUTPUT_DIR>
```

### Prior Archives (Preserved Byte-for-Byte)
- Original modern rendering archive: `docs/benchmarks/2026-10-23-modern-rendering/`
- Modern rendering follow-up: `docs/benchmarks/2026-10-23-modern-rendering-followup/`
- Diagnostic investigation: `docs/benchmarks/2026-10-23-modern-rendering-diagnostic/`
- Direct VBO replacement: `docs/benchmarks/2026-10-23-modern-rendering-replacement/`
