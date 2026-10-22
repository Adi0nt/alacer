# Session 30 gate series: `render.fastCameraSectionFaces` on the reference hardware

Selects or closes session 28b's camera-section open-faces candidate (`docs/ARCHITECTURE.md`, "Session 28b: camera-section
open faces"). It is verified (unit tests; 3,400 container answers, 0 mismatches) but has never been measured. This file,
the runner (`tools/run_section_faces_reference.py`), `gates.py`, the sources and the compiled artifacts are frozen
together by `run_section_faces_reference.py freeze` (hash manifest plus sources ZIP in `manifest.json`, session 27's
method; the reference checkout is untracked and shared) before any gate launch. Two smoke launches precede the freeze
and are attempts, not evidence. Every attempt is kept. No earlier session is rescored, replaced or pooled.

Written on October 22, 2026 in a Linux container, before any reference-hardware measurement of this option. The
thresholds below come from the mechanism (next section) and from earlier sessions' gates, not from data on this option.

## What the option changes, and where a difference can appear

With optimization 1 on, the chunk visibility search needs the camera section's open faces. Vanilla answers by flooding
the section's 4,096 cells with a linked queue, copying `Direction.values()` per visited cell (session 26: 20-29% of
fly-phase client allocation in the container, 0.7-1.1% of fly Java samples). Optimization 1 caches that answer per eye
block, so vanilla's fill runs once each time the camera enters another block. The option replaces the fill with bit sets
and a fixed stack, and reuses the answer while the camera stays in the same connected pocket of an unchanged section;
its faces are vanilla's (verify mode compares every answer). It adds no hooks; "off" is the same build with the option
false.

Consequences for measurement, stated before measuring:

- **Only phases where the camera translates can differ** ("moving": d0/d200 `fly`, terrain `sort` (12-block orbit) and
  `fly`, chunk-flight `fresh-flight` at 20 blocks/s). In static, pan, steady and still phases the eye block never
  changes, so the option does nothing there ("still" phases); they guard against regressions only.
- **The work is per block crossing, not per frame.** At 600-1,800 FPS only a few percent of frames enter another block,
  so the saving is concentrated in those frames. Its share of mean per-frame allocation and time is expected to be far
  smaller than session 26's container shares (which were taken at about 20 FPS). The visibility search's own section
  (`terrain_setup`) is where a saving must show first; tails are as relevant as means.
- The option bypasses optimization 1's per-block cache and replaces it with pocket reuse, so a regression is possible
  in principle (a re-read of 4,096 cells whenever the camera leaves the pocket or the section is dirtied). The still and
  moving no-regression gates cover this.

## Environment (stated before measuring)

Windows 11, i7-12700F, RTX 3060 Ti, Adoptium 25.0.4.1 at `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`,
1920x1080, 2 GiB heap, view distance 12, vsync off, frame limit off, display lists (`benchmark.vbo=false`), the seed-1789
base world `run/saves/alacer-benchmark` (hashes in the manifest; every launch plays a fresh copy), `run/config/alacer.properties`
unchanged (ignored by benchmark launches; hash checked).

## Launches (serial; no JFR; no builds or analysis during the series)

Every launch passes all options explicitly as sessions 28-29 did: the nine shipped defaults on; every experiment off,
including `render.reuseShadowPositions`; every `.verify`, profiler-detail and benchmark-diagnostic option off; plus
`render.fastCameraSectionFaces` and its `.verify`. "Off" is `render.fastCameraSectionFaces=false`; "on" is `true`.
Scenes: `d0`/`d200` (default static/pan/fly, 0/200 mobs, time scale 2), `terrain` (terrain backend scenario, time
scale 2; its two warmups are recorded, never judged), `near`/`high` (session 27's `benchmark.chunkFlight`, time
scale 1: stationary warmup and still reference, then one 40 s fresh flight).

| Group | Labels | Scene | Settings |
|---|---|---|---|
| smoke (pre-freeze) | `sf30-smoke-verify`, `sf30-smoke-near` | d200; near | on + `.verify`, screenshots with fixed pose, time scale 0.2; on + `.verify`, time scale 0.1 |
| verify | `sf30-verify-d200`, `sf30-verify-terrain`, `sf30-verify-near`, `sf30-verify-high` | each | on + `.verify` |
| screens | `sf30-shot-off-1`, `sf30-shot-on`, `sf30-shot-off-2` | d200, `benchmark.screenshots=true`, `benchmark.screenshotEndPose=true` | off / on / off, time scale 1 |
| startup | `sf30-st-<scene>-<n>-<off\|on>`, n = 1..4, scene = d0, d200, terrain, near, high | each | four pairs per scene, in the order (off, on), (on, off), (on, off), (off, on); pair n of every scene runs before pair n+1 of any scene |

The 40 startup launches give four alternating pairs per scene (A,B,B,A then B,A,A,B, session 28b's protocol). Scene
order within a pair index is d0, d200, terrain, near, high. Expected duration: verify about 15 minutes, screens about 8,
startup about 100.

Checks per launch (the runner rejects otherwise): no other client before or after; the actual launcher is the JDK's
`bin/java.exe` with `-Xmx2G`, no JFR and every explicit `-Dalacer.*` value (from `Win32_Process`); sources, tooling,
compiled artifacts, base world and shared config equal the manifest before and after; Gradle compiles nothing; the
exported environment (Java 25.0.4.1 Adoptium, `NVIDIA GeForce RTX 3060 Ti`, window, display lists, view distance,
vsync, mobs, chests, scenario and flight flags, allocation available, screenshot flags); for flights, the
byte-identical world-copy line; phase list, target durations, frame counts, zero dropped frames, one discarded setup
frame; each phase's recorded option state; no GL errors, mixin/injection failures, aborts, timeouts or nonzero mismatch
lines. `Camera section faces final` must report 0 mismatched in every launch, at least one comparison in verify
launches, 0 compared elsewhere, and 0 section reads, fills and reuses in "off" launches.

## Correctness gates

- **C1 verify:** over the four verify launches, at least 5,000 answers compared, at least 100 flood fills and at least
  100 reused answers, 0 mismatched.
- **C2 screenshots:** per d200 phase, `shot-on` against `shot-off-1` must not exceed `shot-off-2` against `shot-off-1` by
  more than 10% relative (mean absolute RGB) and 10% relative plus 0.1 percentage points (share of differing pixels)
  (session 29's rule); the images are also inspected.
- **C3 counters:** median per-pair change of `visible_chunks` and the four terrain-layer chunk counters within 2% in every
  scene and judged phase, except `near`/`high` `fresh-flight` (5%: what loads during a fresh flight depends on timing).
  Same faces imply the same visible set.
- Build and unit tests on JDK 25 (123 at preparation).

## Metrics (`gates.py`)

Per launch and phase, from the per-frame CSVs: allocation per tick-free frame (frames with `tick_ns == 0`), allocation
per frame and per second, frame mean, p95, p99, slow-1% mean (mean of the slowest 1% of frames), tick-frame p99,
`terrain_setup` mean per frame, client CPU per frame, process CPU per frame, counters, tick-frame share. Per scene and
phase, the median over the four pairs of on/off - 1. "Pooled moving" is the median over all 24 moving pair changes
(d0 fly, d200 fly, terrain sort, terrain fly, near fresh-flight, high fresh-flight: 6 scene-phases x 4 pairs).
Judged still phases: d0/d200 static and pan, terrain steady and pan, near/high still; warmups are never judged.
A pair with fewer than 1,000 tick-free frames on either side is unresolved for allocation.

## Pass conditions (all judged)

1. **Region (selecting):** pooled moving `terrain_setup` per frame at most -3%; and in each moving scene-phase,
   `terrain_setup` lower in at least 3 of 4 pairs.
2. **Benefit (selecting):** pooled moving frame mean at most -1%, or pooled moving slow-1% mean at most -2%, or pooled
   moving p99 at most -2%. (Region savings or allocation alone never promote: sessions 18-19, 21 and 28.)
3. **Allocation direction:** in each moving scene-phase, allocation per tick-free frame lower in at least 3 of 4
   resolved pairs, and the pooled moving median change below 0. The magnitude is reported, not gated (see the
   mechanism above).
4. **No regressions, every judged scene-phase (moving and still):** median frame mean at most +2%; p99, slow-1% mean and
   tick-frame p99 at most +5%; client CPU per frame at most +2%; process CPU per frame at most +2%.
5. **Still phases unchanged:** median allocation per tick-free frame at most +2% and median `terrain_setup` per frame at
   most +2% (the option has no work there).
6. C1, C2 and C3 above.

**Outcome rule:** if every condition passes, `render.fastCameraSectionFaces` may be promoted under these gates (a
separate, explicit decision; nothing changes defaults automatically). If 1-6 pass except 2, the option is closed as
exact but without a demonstrated frame-time benefit, and stays off. Any other failure closes it as well. Post hoc
analyses (for example crossing frames only) are reported, never rescored.
