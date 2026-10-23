# Candidate scope and gates (before scored launches)

Three independent opt-ins, all off by default and outside `render.defaults`:
`render.fastChunkNeighbours` (exact precomputed toroidal addresses),
`render.terrainVaos` (OpenGL 3.0 compatibility VAOs), and
`render.directBufferUploads` (OpenGL 4.5 create/named-data operations, keeping
vanilla's final ARRAY_BUFFER=0). `render.modernPipeline` is a benchmark toggle
for their combination, not a saved preset. Previous experiments remain off.
No approximate trigonometry, changed geometry, draw order, shader, occlusion
policy, persistent mapping, worker scheduling or upload-budget change.

Attribution: native RTX 3060 Ti/616.92, Java 25.0.4.1, 1920x1080, distance 12,
vanilla VBOs. Moving visibility takes 0.17-0.23 ms/frame; slot math appears in
3-6% of client samples and input-pointer setup in roughly 1-3%. This supports
testing small exact transformations, not promising a large speedup. Sample
windows use CSV frame times and the exported epoch calibration; see attribution.json.

Correctness and compatibility requirements:

1. Unit tests: at least 1,000,000 adjacency comparisons with original division /
   remainder across positive/negative coordinates, non-square grids, wrapping,
   top/bottom layers; malformed dimensions/overflow and config/startup isolation.
2. Live combined verification on VBO default/terrain and edit/reload diagnostics:
   positive adjacency, VAO-state and named-upload byte comparisons, zero errors.
   Whole visibility comparison with vanilla: zero stable-input mismatches;
   concurrent compiled-data changes are reported separately, never counted as passes.
3. Fixed-end-pose screenshots: candidate vs control differing pixels (maximum
   RGB channel delta >3) <= max(0.1%, twice the difference between two controls).
   Keep controls and attempts. This tolerates measured scene animation only;
   visual inspection must also show no missing terrain, changed blending or layout.
4. VAOs disposed with VBOs on reload/world removal; original render path on
   disabled options, forced unsupported capability, external mixins/subclasses,
   non-default incoming VAO and changed context. Never delete another context's
   numerical VAO name. These guards do not certify arbitrary mods/driver recovery.
5. Shipping build/test, no temporary diagnostic harness, and final ordinary
   default-path smoke. Existing Java 8 archives and saved user configuration untouched.

Performance selection (new sources frozen after correctness repairs and harness
removal): four adjacent balanced pairs, `ABBABAAB`, separately for default
static/pan/fly and the terrain backend scenario. Window 1920x1080, distance 12,
heap 2G, VBO=true, ordinary profiler, detailed probes/verification/JFR off.
A disables all three; B enables all three. Fresh byte-identical world copy per
launch, serial clients, no build/tests/metric analysis during a client.

Report all phase results and tails. Combination passes only if:

- Median paired visibility wall time in moving pan/sort/fly phases improves at
  least 5%, lower in >=3/4 pairs in each scene's pan.
- Median paired mean frame or client CPU/frame improves at least 1% in pan in
  both scenes, lower in >=3/4 pairs for that metric.
- No scored phase median frame mean or CPU/frame regression >2%, frame p99 or
  slowest-1% mean >5%, allocation/frame >2%, or visible chunks change >1%.
- Still controls' frame mean changes remain within +/-2%; no dropped frames,
  GL/injection errors, source/config changes, or unsupported backend.

Keep individual ablations separate from combination selection: one control and
one launch per individual option, useful for attribution only. Passing the
combination does not select an individual feature automatically. If any required
gate fails or is unresolved, retain the opt-ins off and report the result. No
default promotion during this work; broader drivers/backends still need testing.
