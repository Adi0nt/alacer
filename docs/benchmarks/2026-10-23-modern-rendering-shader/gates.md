# Predeclared shader pipeline replacement performance and correctness gates

## Scope and Candidate

Candidate replacement for Alacer's terrain rendering pipeline (Option B):
- Replaces per-chunk fixed-function matrix stack operations (`glPushMatrix`, `glTranslatef`, `glMultMatrix`, `glPopMatrix`) with GPU-evaluated chunk origin translation and boundary scaling via `TerrainShader`.
- Uses fixed-function fragment pipeline for bit-identical texturing, lighting, alpha test, and fog.
- Defaults off; opt-in via `render.terrainVaos=true`.
- Default startup remains free of experimental GL hooks when disabled.
- Full compatibility fallback to vanilla execution when foreign mixins/subclasses are present or `forceFallback=true`.

## Correctness gates (frozen before execution)

1. **Unit tests**: 166 existing Java 25 unit tests pass with zero failures.
2. **Environment & execution**:
   - Zero dropped frames in any measured phase.
   - Zero OpenGL errors, zero Mixin application failures, zero benchmark aborts.
   - Unchanged vanilla VBO storage and lifecycle cleanup.
3. **Geometry and visual parity**:
   - Workload parity: rendered chunk count and visible chunk mean must match control within 1.0%.
   - Vertex formats (position float, color ubyte, UV0 float, UV1 short, stride 28), draw calls,
     quad primitives, and draw order remain identical to vanilla.

## Performance selection gates (frozen before execution)

Selection uses 4 adjacent balanced pairs (`ABBABAAB`) in both default and terrain backend scenarios.
Control (A) disables the feature; Candidate (B) enables it.
Evaluated on native Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA 616.92, Java 25.0.4.101, 1920x1080,
distance 12, vanilla VBOs, 2 GiB heap.

To pass selection:
1. **Pan frame time**: Median paired pan frame time must improve (delta $\le 0.0\%$) compared to control (A) in both default and terrain scenarios.
2. **Pan CPU/frame**: Median paired pan client CPU/frame must improve (delta $\le 0.0\%$) compared to control (A) in both default and terrain scenarios.
3. **No regressions in moving phases**: No scored moving phase (pan, fly, sort) median frame mean regression $> 1.0\%$.
4. **Terrain layers**: Combined terrain layers wall time in pan must improve compared to control (A).
5. **Tails and allocation**: Frame p99 must not regress $> 2.0\%$; allocation/frame must not regress $> 2.0\%$.

If any required gate fails, the candidate is rejected, retained off by default, and the result documented.
