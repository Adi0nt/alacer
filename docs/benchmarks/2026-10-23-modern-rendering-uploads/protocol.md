# Protocol: OpenGL 4.5 direct buffer uploads selection benchmark (2026-10-23)

## 1. Objective and Hypotheses

Evaluate the performance and correctness of **OpenGL 4.5 Direct State Access (DSA) buffer uploads** (`render.directBufferUploads`) against vanilla VBO buffer uploads.

- **Hypothesis 1**: Vanilla `VertexBuffer.upload` issues two binding state changes per upload (`glBindBuffer(GL_ARRAY_BUFFER, id)` and `glBindBuffer(GL_ARRAY_BUFFER, 0)`), plus `glBufferData`.
- **Hypothesis 2**: OpenGL 4.5 `glNamedBufferData` updates buffer memory directly without modifying the bound buffer, eliminating 2 native driver state calls per chunk upload.
- **Hypothesis 3**: The prior +19.46% pan regression reported in single-run ablation testing (`mg-ablation-directBufferUploads`) was an artifact of unrepeated run variance in a phase where zero chunk uploads occurred.
- **Hypothesis 4**: In a rigorous, balanced 16-launch series across both `default` and high-churn `terrain` routes, streamlined DSA uploads will exhibit neutral to improved frame times and CPU utilization with zero dropped frames or GL errors.

---

## 2. Benchmark Design

- **Runtime**: Explicit Temurin Java 25 (`25.0.4.101-hotspot`) for Gradle daemon, compiler, and Minecraft launcher.
- **System**: Intel Core i7-12700F, NVIDIA GeForce RTX 3060 Ti, NVIDIA 616.92, Windows 11 Pro 64-bit, 1920x1080 windowed, view distance 12, vanilla VBOs, 2 GiB heap.
- **Isolation**: Serial execution; neighbor math (`fastChunkNeighbours=false`) and terrain shader (`terrainShader=false`) disabled to isolate DSA buffer uploads exclusively.
- **Launch Matrix**: 16 native GPU client runs:
  - 8 runs in `default` route: balanced `ABBABAAB` sequence (4 Control A, 4 Candidate B).
  - 8 runs in `terrain` route: balanced `ABBABAAB` sequence (4 Control A, 4 Candidate B).
  - Total: 8 paired comparisons across static, pan, fly, steady, and sort phases.

---

## 3. Launch Configurations

- **Control (A)**:
  `-Palacer.render.directBufferUploads=false -Palacer.render.fastChunkNeighbours=false -Palacer.render.terrainShader=false -Palacer.render.terrainVaos=false`
- **Candidate (B)**:
  `-Palacer.render.directBufferUploads=true -Palacer.render.fastChunkNeighbours=false -Palacer.render.terrainShader=false -Palacer.render.terrainVaos=false`

Sequence:
- Run 1: Control (A)
- Run 2: Candidate (B)
- Run 3: Candidate (B)
- Run 4: Control (A)
- Run 5: Candidate (B)
- Run 6: Control (A)
- Run 7: Control (A)
- Run 8: Candidate (B)

Pairs:
- Pair 1: Run 1 (A) vs Run 2 (B)
- Pair 2: Run 4 (A) vs Run 3 (B)
- Pair 3: Run 6 (A) vs Run 5 (B)
- Pair 4: Run 7 (A) vs Run 8 (B)

---

## 4. Stop Rules

1. Any launch exiting with a non-zero code immediately stops the series.
2. Any run reporting dropped frames (`frames_dropped > 0`) or OpenGL errors halts the series.
3. Any divergence in visible chunk counts $> 1.0\%$ halts the series.
