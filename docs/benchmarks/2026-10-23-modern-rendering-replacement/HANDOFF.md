# Antigravity handoff: modern rendering diagnostic and replacement

## 1. Repository and Environment State

- **Active Worktree**: `C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer`
- **Branch**: `claude/magical-planck-7t9612`
- **Primary Checkout**: `C:/Users/CodyJ/OneDrive/Documents/GitLab/adiont/aevum/alacer` is clean main at `0de17a5aa557c78f9f394800a1ef23a8dbbe08d1` and remains completely untouched.
- **Java Runtime**: Explicit Temurin Java 25 (`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`) for Gradle daemon, compilation bytecode, tests, and Minecraft client execution.
- **System**: Windows 11 Pro 64-bit, Intel Core i7-12700F, NVIDIA GeForce RTX 3060 Ti (Driver 616.92, OpenGL 4.6), 1920x1080 windowed, view distance 12, vanilla VBOs, 2 GiB heap.
- **Process State**: No Minecraft client, diagnostic runner, or benchmark script is running. Gradle daemon may remain idle.
- **Configuration Defaults**: All experimental optimizations remain strictly off by default (`render.terrainVaos=false`, `render.fastChunkNeighbours=false`, `render.directBufferUploads=false`). Experimental GL hooks are not installed during default startup.

---

## 2. Investigation and Diagnostic Findings

Archive: `docs/benchmarks/2026-10-23-modern-rendering-diagnostic/`
Protocol: `protocol.md` (6-launch hard budget, all 6 completed).

1. **Workload Parity Verified**:
   - Instrumented draw call and vertex counters proved that geometry workload is identical between vanilla VBO and active VAO paths (1 draw call per rendered chunk, ~910–967 draws/frame, ~1,125–1,148 vertices per draw).
   - Active VAOs do not alter draw call volume, geometry, or quad counts.
2. **Query Latency Is Not the Primary Regression Cause**:
   - Bypassing `glGetInteger(GL_VERTEX_ARRAY_BINDING)` in `diag-3-noquery` improved static slightly but failed to recover moving camera terrain wall time.
3. **Root Cause of VAO Slowdown on NVIDIA Drivers**:
   - Vanilla Minecraft VBO rendering uses the default VAO (VAO 0), issuing `glBindBuffer` and setting vertex attribute pointers. Under NVIDIA OpenGL drivers (`nvoglv64.dll`), staying within VAO 0 follows an ultra-fast driver path without invalidating internal hardware vertex fetch descriptors.
   - Binding a distinct VAO per chunk (3,848 VAO objects in memory, ~1,500 VAO binds per frame) causes LWJGL2 `StateTracker.bindVAO` hash table lookups and NVIDIA driver VAO object state revalidation and hardware descriptor pipeline flushes, nearly tripling terrain layer wall time.
4. **Inner-Loop Mixin Overhead**:
   - Per-chunk Mixin wrapping (`@WrapWithCondition` on `buffer.bind()` and `prepareDrawState()`) inside the chunk loop fired over 20 million times per session, introducing substantial JVM method invocation overhead.

---

## 3. Replacement Implementation

Source changes:
- `net.adiont.alacer.render.ModernGl`:
  - Retires per-chunk VAO switching in favor of a streamlined direct VBO dispatch pipeline (`alacer$renderPipeline`).
  - Added fast-path active flag check: `ModernGl.isVaoPipelineActive()` evaluates once at layer start.
  - If inactive or compatibility fallback is triggered, cleanly executes the vanilla chunk list loop.
- `net.adiont.alacer.mixin.TerrainVaoMixin`:
  - Replaced inner-loop `@WrapWithCondition` calls with a single `@Inject(at = @At("HEAD"), cancellable = true)` in `VboRenderChunkList.render(BlockLayer layer)`.
  - Implemented direct indexed loop dispatch (`renderChunks.get(i)`), eliminating `ArrayList$Itr` object creation in the hot render loop.
  - Hoisted `BlockLayer.ordinal()` lookups outside the chunk loop.
  - Inlined matrix translation math (`glTranslated(x - viewX, y - viewY, z - viewZ)`).
  - Direct LWJGL dispatch (`GL15.glBindBuffer`, `GL11.glDrawArrays`).
- `net.adiont.alacer.render.ModernVertexBuffer` & `ModernVertexBufferMixin`:
  - Added `int alacer$vertexCount()` accessor to retrieve chunk vertex count directly without repeated buffer size calculations.

---

## 4. Benchmark Results and Gate Evaluation

Archive: `docs/benchmarks/2026-10-23-modern-rendering-replacement/`
Gates: `gates.md` (frozen before execution).
Design: 16 native GPU launches in balanced `ABBABAAB` pairs across `default` (camera movement) and `terrain` (synthetic chunk churn) scenarios.

### Performance Deltas (Candidate B vs Control A)

- **Default Gameplay Route**:
  - **Static**: Frame time **-8.16%**, CPU **-5.89%**, p99 **-10.91%**, Alloc **-10.90%**, Terrain layers **-2.15%**.
  - **Pan**: Frame time **-5.44%**, CPU **-7.30%**, p99 **-4.55%**, Alloc **-26.88%**, Terrain layers **-2.11%**.
  - **Fly**: Frame time **-5.93%**, CPU **-6.46%**, p99 **-1.59%**, Alloc **-8.36%**, Terrain layers **-4.53%**.
  *Completely overcomes the earlier 61–67% VAO regression, delivering solid frame time and CPU gains with a massive 27% reduction in memory allocation during camera panning.*
- **Synthetic Terrain Route**:
  - **Pan**: Frame time **+5.21%**, CPU **+3.74%**, p99 **+12.97%**, Terrain layers **+2.11%**.
  - **Sort**: Frame time **-1.65%**, p99 **-17.45%**.
  - **Fly**: Frame time **-1.17%**, CPU **-0.72%**.
  - **Steady**: Frame time **+0.57%**, CPU **+5.27%**.

### Gate Evaluation
- **Correctness Gates**: All 166 unit tests passed. 16 valid client runs, 0 dropped frames, 0 OpenGL errors, 0 benchmark aborts. Workload parity preserved. (PASSED)
- **Selection Gates**: Failed due to terrain scenario pan frame time regressing +5.21% (> 1.0% gate) and CPU regressing +3.74% (> 1.0% gate).
- **Decision**: In strict adherence to `AGENTS.md` and project rules, defaults remain OFF (`render.terrainVaos=false`). Startup remains free of experimental GL hooks.

---

## 5. Exact Continuation and Verification Commands

### Run Unit Tests
```powershell
./gradlew.bat test --offline
```

### Replay Diagnostic Archive
```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-diagnostic/run_diagnostic.py --help
# (Diagnostic archive raw files and logs are verified in raw-manifest.json and evaluation.json)
```

### Replay Replacement Archive
To verify raw data integrity and reproduce `evaluation.json` in a new clean directory:
```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-replacement/replay.py <OUTPUT_DIR>
```
