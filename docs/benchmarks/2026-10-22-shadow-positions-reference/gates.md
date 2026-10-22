# Session 29 gate series: `render.reuseShadowPositions` on the reference hardware

Roadmap item 1 of "Next session (29)" in `docs/ARCHITECTURE.md`: confirm or close the session 28 candidate on the
reference machine. This file, the runner (`tools/run_shadow_position_reference.py`), `gates.py`, the sources and the
compiled artifacts are frozen together by `run_shadow_position_reference.py freeze` (hash manifest plus sources ZIP in
`manifest.json`; the reference checkout is untracked and shared, so the freeze is not a git commit) before any gate
launch. Two smoke launches (below) precede the freeze and are attempts, not evidence. Every attempt is kept. Sessions
1-28 are not rescored, replaced or pooled; session 28's gates.json stands as recorded.

The candidate, its gates and its thresholds are session 28's ([protocol.md](../2026-10-22-allocation-selection/protocol.md),
[gates.md](../2026-10-22-allocation-selection/gates.md)). Only the differences listed under "Changes from session 28"
are new, and each was fixed before any session 29 launch, from session 28's written failure analysis (none uses a
session 29 measurement).

## Environment (stated before measuring)

Windows 11, i7-12700F, RTX 3060 Ti (NVIDIA driver as recorded by each launch's `gl.version`), Adoptium 25.0.4.1 at
`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`, 1920x1080 window (sessions 26-27's reference size), 2 GiB
heap, view distance 12, vsync off, frame limit off, display lists (`benchmark.vbo=false`), the seed-1789 base world
`run/saves/alacer-benchmark` (hashes in the manifest), `run/config/alacer.properties` unchanged (ignored by benchmark
launches; hash checked). Sessions 26-27 measured 600-1,800 FPS here with 1.7-2.6% of frames containing a client tick.

## Changes from session 28 (fixed now)

1. **Target region and gate 5 on tick-free frames.** Allocation per frame is the client thread's `alloc_bytes` mean over
   frames with `tick_ns == 0`. Session 28 failed its whole-frame target because, at ~20 FPS, the client tick (tens of
   KB, varying between launches) sat in nearly every frame. The renderShadow hook runs only on the frame path, so
   tick-free frames contain everything it can change. Whole-frame allocation, its tick-free median and the tick-frame
   share are reported beside it. A launch/phase with fewer than 1,000 tick-free frames on either side is unresolved,
   and an unresolved selecting combination fails the target.
2. **Fixed-pose screenshots.** `benchmark.screenshotEndPose=true` (added for this series, off by default): when a phase
   ends, the benchmark renders one unrecorded frame at the phase's exact end pose and captures that frame, so pan
   captures have the same yaw at any frame rate. Session 28's pan rule failed because captures taken on the first
   frame after the phase landed a few degrees apart. Recorded frames are unchanged.
3. **Frame, tail and CPU gates are judged** (gates 2-4 and 7 and the startup frame/CPU conditions), as protocol.md
   requires on the reference hardware.
4. **New labels** (`spr29-`), Windows launcher checks and a hash-manifest freeze (session 27's method).

Thresholds are unchanged: the target needs a median saving of at least 5% and at least 9,855 bytes per frame static,
10,737 pan (half of session 28's 19,710 and 21,474 selection estimates).

## Launches (serial, fresh copied worlds; no JFR; no builds or analysis during the series)

Every launch passes all options explicitly as session 28 did (nine shipped defaults on, every experiment off, every
`.verify`, profiler detail and benchmark diagnostic off), plus `render.reuseShadowPositions` and its `.verify`. "Absent"
means `render.reuseShadowPositions=false` with no toggle (mixin not applied); "on" means `true`.

| Group | Labels | Scene | Settings |
|---|---|---|---|
| smoke (pre-freeze) | `spr29-smoke-verify`, `spr29-smoke-ab` | d200 | on + `.verify` + fixed-pose screenshots; toggle A/B; time scale 0.2 |
| verify | `spr29-verify-d200-1`, `spr29-verify-d200-2` | d200 | on + `.verify`, time scale 10 |
| verify | `spr29-verify-walk` | d200 + `benchmark.shadowDiagnostic=true` | on + `.verify`, time scale 4 |
| screens | `spr29-shot-absent-1`, `spr29-shot-on`, `spr29-shot-absent-2` | d200, `benchmark.screenshots=true`, `benchmark.screenshotEndPose=true` | absent / on / absent, time scale 1 |
| ab | `spr29-ab-1` .. `-4` | d200, `benchmark.toggle=render.reuseShadowPositions` | `toggleReverse` false, true, false, true (A1/B1/B2/A2, B1/A1/A2/B2); both paths warmed; time scale 2 |
| startup | `spr29-st-d200-<n>-<side>`, n = 1..4 | d200 | pairs in order absent/on, on/absent, absent/on, on/absent; time scale 2 |
| startup | `spr29-st-d0-<n>-<side>`, `spr29-st-terrain-<n>-<side>`, n = 1..2 | d0, terrain (non-selecting) | absent/on, on/absent; time scale 2 |

Checks per launch (the runner rejects otherwise): no other client before or after; the actual launcher is the JDK's
`bin/java.exe` with `-Xmx2G`, no JFR and every explicit `-Dalacer.*` value (from `Win32_Process`); sources, tooling,
compiled artifacts, base world and shared config equal the manifest before and after; Gradle compiles nothing
(`compileJava`/`processResources` up to date); exported environment (Java 25.0.4.1 Adoptium, `NVIDIA GeForce RTX 3060
Ti`, window, display lists, view distance, vsync, mobs, chests, scenario flags, allocation available, toggle and
reverse flags, screenshot flags); phase list, target durations, frame counts, zero dropped frames, one discarded setup
frame; each phase's recorded option state; no GL errors, mixin/injection failures, aborts, timeouts or nonzero
mismatch lines. `Shadow position reuse final` must report at least one comparison and 0 mismatched in verify launches
and 0 compared in all others. Screenshot launches must leave exactly one capture per phase.

## Correctness gates (unchanged)

- `.verify`: at least 10,000,000 positions compared over the three verify launches, 0 mismatched.
- Screenshots: per phase, `shot-on` against `shot-absent-1` must not exceed `shot-absent-2` against `shot-absent-1` by
  more than 10% relative (mean absolute RGB) and 10% relative plus 0.1 percentage points (share of differing pixels);
  the images are also inspected.
- Lifecycle: not applicable (no state kept across calls; session 28 gates.md). Build and tests on JDK 25; mixin absent
  when off (unit test).

## Performance metrics (`gates.py`)

As session 28's, per launch and phase: allocation per tick-free frame (judged), allocation per frame and per second,
frame mean, p95, p99, slow-1% mean, tick-frame p99, client CPU per frame, process CPU per frame, counters, tick-frame
share. In-process launches pool A (a1, a2) and B (b1, b2) per phase; startup pairs compare on against absent.

## Pass conditions (all judged here)

Selecting scene d200 (in-process static and pan; startup static, pan and fly), medians of per-launch changes:

1. **Target:** allocation per tick-free frame lower in at least 3 of 4 in-process launches; in-process and startup
   median savings each at least 5% and at least 9,855 (static) / 10,737 (pan) bytes per frame.
2. Frame mean saving of at least 2% in at least one d200 in-process phase; no d200 combination with a frame-mean
   regression above 1%.
3. p95, p99 and tick-frame p99 regressions at most 5%; slow-1% mean not higher.
4. Client CPU per frame not higher; process CPU per frame regression at most 2%.
5. Allocation per tick-free frame regression at most 2% in every scene and phase (d0, d200, terrain; in-process and
   startup).
6. Work counters within 2% in every scene and phase (with detail profiling off only `visible_chunks` is populated).
7. Launches with |A2/A1 - 1| frame drift over 10% are flagged; if the median absolute drift exceeds 10%, B must beat A1
   and A2 separately in the target region in at least 3 of 4 launches.

Startup pairs in d200 also need a lower median frame mean. Non-selecting scenes (d0, terrain) need no frame-mean
regression above 1%. Every judged condition must hold, together with both correctness gates, to pass.

**Outcome rule:** if everything passes, `render.reuseShadowPositions` may be promoted under these gates (a separate,
explicit decision; nothing changes defaults automatically). If anything fails, the candidate is closed and stays off
and outside `render.defaults`. Gate 2 asks for a 2% frame-mean saving from about 20 KB less allocation per frame;
failing it alone also closes the candidate. Post hoc analyses are reported, never rescored.
