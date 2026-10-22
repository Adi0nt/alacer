# Session 28 gate series: `render.reuseShadowPositions`

The candidate named by the attribution series ([selection.md](selection.md)) is implemented behind
`render.reuseShadowPositions` (off by default, outside `render.defaults`, mixin absent unless the option, its
`.verify` or its benchmark toggle is set at startup). This series applies the gates [protocol.md](protocol.md) fixed
before the attribution. Its launch list, metrics and pass conditions are frozen here, with the sources and tooling,
before any gate launch (git commit recorded in `freeze-gates.json`). Every attempt is kept.

## Pre-freeze development launches (not evidence)

`dev28-verify-smoke` (d200, time scale 0.5, `.verify`): 575,582 positions compared, 0 mismatched.
`dev28-export-off` / `dev28-export-on` (time scale 0.2, `-Dmixin.debug.export=true`): with the option off the exported
`EntityRenderer.renderShadow` still calls `BlockPos.down` at bytecode 305 (hook absent); with it on, that call is
`redirect$...$alacer$below`. Logs under `build/`.

## Amendment 1 (before any accepted gate launch)

Two runner acceptance checks were wrong and rejected completed launches: `alloc28g-verify-d200-1` (the check for
mismatch lines also matched the periodic zero-count `Shadow position reuse verification: 1000000 positions compared, 0
mismatched` line) and `alloc28g-shot-absent-1` (the benchmark names screenshots
`alacer-<label>-vis_on-ecl_on-ent200-<phase>.png`, not `alacer-<label>-<phase>.png`). Both attempts are kept. The runner
now accepts zero-count periodic summaries and finds screenshots by `alacer-<label>-*-<phase>.png`; the two launches
are repeated as `alloc28g-verify-d200-1-r2` and `alloc28g-shot-absent-1-r2`. Launch list, metrics and pass conditions
are unchanged. The amended runner and `gates.py` (label references only) are frozen again in `freeze-gates-2.json`.

## Launches (serial, fresh copied worlds, 854x480, 2 GiB, view distance 12, display lists, no JFR)

Every launch passes all options explicitly, as the attribution did, plus `render.reuseShadowPositions` and its
`.verify`. "Absent" means `render.reuseShadowPositions=false` with no toggle (mixin not applied); "on" means `true`.

| Group | Labels | Scene | Settings |
|---|---|---|---|
| verify | `alloc28g-verify-d200-1-r2`, `alloc28g-verify-d200-2` | d200 | on + `.verify`, time scale 10 |
| verify | `alloc28g-verify-walk` | d200 + `benchmark.shadowDiagnostic=true` (mobs walk; cells under shadows cycle through glowstone, slab, air) | on + `.verify`, time scale 4 |
| screens | `alloc28g-shot-absent-1-r2`, `alloc28g-shot-on`, `alloc28g-shot-absent-2` | d200, `benchmark.screenshots=true` | absent / on / absent, time scale 1 |
| ab | `alloc28g-ab-1` .. `-4` | d200, `benchmark.toggle=render.reuseShadowPositions` | `toggleReverse` false, true, false, true (A1/B1/B2/A2, B1/A1/A2/B2); both paths warmed; time scale 2 |
| startup | `alloc28g-st-d200-<n>-<side>`, n = 1..4 | d200 | pairs in order absent/on, on/absent, absent/on, on/absent; time scale 2 |
| startup | `alloc28g-st-d0-<n>-<side>`, `alloc28g-st-terrain-<n>-<side>`, n = 1..2 | d0, terrain (non-selecting) | absent/on, on/absent; time scale 2 |

Checks per launch (as the attribution runner's, minus JFR): the actual launcher, `-Xmx2G` and every explicit
`-Dalacer.*` value from `/proc`; frozen paths equal to the freeze commit; base world unchanged; environment (Java
25.0.4.1 Adoptium, llvmpipe, window, display lists, view distance, vsync, mobs, scenario, toggle and reverse flags,
allocation available); phase list, frame counts, zero dropped frames, durations; each phase's recorded config for the
option and its `.verify`; no GL errors, mixin/injection failures, aborts, timeouts or nonzero mismatch counts. The
`Shadow position reuse final` line is required: verify launches must report at least one comparison and 0 mismatched;
every other launch must report 0 compared.

## Correctness gates

- `.verify`: at least 10,000,000 positions compared over the three verify launches (all in d200, the selecting
  scene), 0 mismatched (coordinates and the block state answered for the reused and the vanilla position).
- Screenshots: per phase, the mean absolute RGB difference and the share of differing pixels of `shot-on` against
  `shot-absent-1` must not exceed those of `shot-absent-2` against `shot-absent-1` (vanilla against vanilla: clouds,
  animated textures and the 200 mobs' idle animations already differ between launches) by more than 10% relative plus
  0.1 percentage points; the images are also inspected.
- Lifecycle: the change keeps no state across calls (one scratch position, overwritten before every use), so the
  world-replacement, reload, view-distance, backend and re-entry captures are not applicable (protocol.md: "where the
  change keeps any state across calls"); draw, GL-state and callback order are untouched (one argument object changes).
- Build and tests on JDK 25; mixin absent when off (unit test plus the export launches above).

## Performance metrics (`gates.py`, from the raw per-frame CSVs and phase JSONs)

Per launch and phase (static, pan; fly too for startup launches): allocation per frame (`alloc_bytes` mean), frame
mean, p95, p99, slow-1% mean (mean of the slowest 1% of frames, at least one), tick-frame p99 (`tick_ns > 0`), client
CPU per frame, process CPU per frame (`process_cpu_cores` times wall time over frames), allocation per second, and
counters (visible chunks, chunks per terrain layer, entities and block entities drawn, shadows drawn). In-process
launches pool the A segments (a1, a2) and the B segments (b1, b2) per phase; the change is B relative to A, and
|A2/A1 - 1| frame-mean drift is reported. Startup pairs compare on against absent within each pair.

## Pass conditions

Judged here (protocol.md: allocation, correctness and counters):

- **Target region**, d200 static and pan: allocation per frame lower in at least 3 of 4 in-process launches, and in
  the median of the four startup pairs; median saving of at least 5% and at least half the selection estimate:
  9,855 bytes per frame static, 10,737 pan (half of 19,710 and 21,474).
- **Gate 5:** no scene/phase (d0, d200, terrain; in-process and startup medians) with an allocation-per-frame regression
  above 2%.
- **Gate 6:** median work counters within 2% in every scene/phase (a counter with an A median of 0 must stay 0). With
  detail profiling off (required for these launches) only `visible_chunks` is populated; the per-layer, entity, block
  entity and shadow counters need the detail hooks and read 0 on both sides, so gate 6 effectively tests visible
  chunks. The mob count is fixed by the scene (200 NoAI mobs) and checked in the environment.

Measured and reported, not judged here (llvmpipe; protocol.md's container limit): gates 2-4 and 7 and the startup
frame/CPU conditions. Whatever the outcome, `render.reuseShadowPositions` stays off by default and outside
`render.defaults`; passing the judged gates makes it a candidate for the same series on the reference hardware.
