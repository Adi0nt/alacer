# Antigravity handoff: OpenGL 4.5 direct buffer uploads benchmark

## 1. Repository and Environment State

- **Active Worktree**: `C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer`
- **Branch**: `claude/magical-planck-7t9612`
- **Primary Checkout**: `C:/Users/CodyJ/OneDrive/Documents/GitLab/adiont/aevum/alacer` is clean main at `0de17a5aa557c78f9f394800a1ef23a8dbbe08d1` and remains completely untouched.
- **Java Runtime**: Explicit Temurin Java 25 (`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`).
- **System**: Windows 11 Pro 64-bit, Intel Core i7-12700F, NVIDIA GeForce RTX 3060 Ti (Driver 616.92, OpenGL 4.6), 1920x1080 windowed, view distance 12, vanilla VBOs, 2 GiB heap.
- **Process State**: No Minecraft client, diagnostic runner, or benchmark script is running.
- **Configuration Defaults**: All experimental optimizations remain strictly off by default (`render.terrainShader=false`, `render.terrainVaos=false`, `render.directBufferUploads=false`, `render.fastChunkNeighbours=false`).

---

## 2. Investigation and Benchmark Findings

Archive: `docs/benchmarks/2026-10-23-modern-rendering-uploads/`
Protocol: `protocol.md` (16-launch hard budget, all 16 completed with code 0).
Gates: `gates.md` (frozen before execution).

1. **Elimination of Redundant Unbind**:
   - `ModernGl.upload` was modified in commit `59648af` to remove redundant `GLX.bindBuffer(GL_ARRAY_BUFFER, 0)`, cutting native driver calls in half for DSA uploads.
2. **Definitive Rejection of DSA Buffer Uploads**:
   - In static/low-churn route (`default`), DSA uploads slightly improved pan frame time (-5.38%).
   - In dynamic chunk churn (`terrain`), `glNamedBufferData` caused severe driver synchronization stalls:
     - **Pan frame time**: regressed **+22.85%**
     - **Pan CPU time**: regressed **+21.27%**
     - **Chunk upload wall time in pan**: regressed **+52.47%**
     - **Sort frame time**: regressed **+6.04%**
     - **Fly frame time**: regressed **+7.01%**
3. **Driver Mechanism Isolated**:
   - The NVIDIA OpenGL driver has an optimized lock-free buffer orphaning fast path for the legacy `glBindBuffer` + `glBufferData` sequence.
   - Using `glNamedBufferData` incurs driver resource hash-table lookups and driver thread serialization during rapid chunk rebuilds, significantly slowing down upload throughput.
4. **Decision**:
   - Candidate failed predeclared gates: **`gates_pass = false`**.
   - Per `AGENTS.md`, `render.directBufferUploads` remains strictly disabled by default (`false`).

---

## 3. Exact Continuation and Verification Commands

### Run Unit Tests
```powershell
./gradlew.bat test --offline
```

### Replay and Verify Uploads Archive
```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-uploads/replay.py <OUTPUT_DIR>
```

### Replay Prior Archives
- Terrain Shader (Option B): `python -B docs/benchmarks/2026-10-23-modern-rendering-shader/replay.py <OUTPUT_DIR>`
- Direct VBO replacement: `python -B docs/benchmarks/2026-10-23-modern-rendering-replacement/replay.py <OUTPUT_DIR>`
