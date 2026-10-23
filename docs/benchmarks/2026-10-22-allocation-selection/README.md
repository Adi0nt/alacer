# Session 28: allocation-based selection

October 22, 2026. Roadmap item 1 of "Next session (28)". **The frozen rule named one candidate,
`render.reuseShadowPositions`. It was implemented, verified exact (10.4 million comparisons, 0 mismatches) and failed
its frozen gates in this container.** It stays off by default and outside `render.defaults`. No default changed. Items
2 (parked) and 3 (closed) are unchanged. Sessions 1-27 are not rescored.

## Environment limit (read first)

All launches ran in a Linux cloud container: 4 vCPU Xeon at 2.1 GHz, Adoptium 25.0.4.1, Xvfb at 854x480, **Mesa
llvmpipe (software OpenGL)**, about 16-25 FPS against 600-1,800 FPS on the reference machine (Windows 11, i7-12700F,
RTX 3060 Ti). Frame-time, tail, CPU and swap numbers here do not represent a GPU machine and judge nothing
([protocol.md](protocol.md)). Allocation attribution, `.verify` and screenshots are meaningful, but at ~20 FPS nearly
every frame also contains a client tick, which makes whole-frame allocation per frame noisy (below). Anything favorable
here needs the same series on the user's hardware before it can matter.

## What was frozen, and when

| Commit | Content |
|---|---|
| `b517184` ([freeze.json](freeze.json)) | [protocol.md](protocol.md): attribution scenes, selection rule, gates; runner, JFR settings, classifier, decision script; `-PjfrTlab` in build.gradle |
| `4c0f58e` ([freeze-gates.json](freeze-gates.json)) | [gates.md](gates.md): candidate gate series (launch list, metrics, pass conditions), its runner and `gates.py` |
| `4f22d89`, `534287b` (`freeze-gates-2.json`, `-3.json`) | amendments 1-2 to the gate runner's acceptance checks only (gates.md); three completed launches it had wrongly rejected were repeated under `-r2` labels |

Every attempt is kept under [attempts/](attempts/): 3 pre-freeze smokes (all rejected, tooling-shaping only), 6
attribution launches (all passed), 29 gate launches (26 passed; 3 rejected by the runner bugs above).

## Attribution (six hooks-absent JFR launches)

d0, d200 and terrain, twice each in alternating order, with the nine shipped defaults on and every experiment, verify
and detail hook off (mixins absent), time scale 2. JFR allocation samples at a fixed 16 KiB TLAB (samples are taken
at TLAB refills; adaptive TLABs gave the client thread 3-11 samples/s), classified by stack into the tick path
(`Minecraft.tick`) and the per-frame path. The JFR estimate of client-thread bytes per frame was 1.000-1.005 times the
ThreadMXBean counter in every launch and phase. Only static, pan, terrain steady and terrain pan were ranked:
camera translation makes per-block-crossing work per frame scale with 1/FPS.

[decision.json](decision.json), [selection.md](selection.md):

| Set (per-frame path) | d200 static | d200 pan | Rule |
|---|---|---|---|
| `shadow.offset_pos` (`pos.down()` in `renderShadow`) | 15.5-22.1%, 19.7 KB/frame | 20.8-22.9%, 21.5 KB/frame | **named** |
| `entity_light.pos` (entity brightness `BlockPos`) | 7.9-12.8%, 10.7 KB | 10.4-11.9%, 10.9 KB | 1 combination only |
| `name_check.strip` (regex `Matcher` in `applyRotation`) | 8.6%, 8.8 KB | 8.9%, 8.7 KB | below 10% |
| `pick.block_trace` (crosshair ray march) | 1.0%, 1.0 KB | 0.8%, 0.7 KB | below (5.4% in d0 static) |

The camera-section flood fill (`getOpenChunkFaces`) is event-driven: 4.8 MB/s in fly (3.0 MB/s of it `Direction.values()`
copies), about 200-240 KB per cache miss, one miss per block entered; not rankable at this frame rate. With a still
camera, `setup_render` still allocated 20-47 KB per frame: chunk rebuilds and light updates on the client thread.

## Candidate: `render.reuseShadowPositions`

In `EntityRenderer.renderShadow` the `down()` result is consumed only by optimization 6's redirected
`getBlockState` (bytecode 305-308, never stored). For exactly `ClientWorld`, one reused `BlockPos.Mutable` at the same
coordinates replaces it (`render/ShadowPositions`, `mixin/EntityShadowPositionMixin`). The mixin is applied only when
the option, its `.verify` or its benchmark toggle is set at startup (unit test; with the option off the exported
`renderShadow` still calls `BlockPos.down`). Runtime-toggleable once applied.

## Gate results ([gates.json](gates.json), as frozen)

| Gate | Result |
|---|---|
| `.verify`, >= 10 M comparisons, 0 mismatches | **pass**: 10,388,241 (4.46 M + 4.60 M d200, 1.32 M with walking mobs and cells cycling under shadows), 0 mismatched |
| Screenshots vs vanilla, per phase | static **pass**, fly **pass**, pan **fail** (on vs vanilla 16.8 mean abs RGB, 72.8% pixels; vanilla vs vanilla 9.2, 52.8%) |
| Target, d200 static | **fail**: lower in 4/4 in-process launches, but median saving 6.0 KB/frame (-3.4%) against 9.9 KB and 5% needed; startup median -17.3% |
| Target, d200 pan | pass: 3/4 lower, median 13.4 KB (-6.7%); startup median -15.1% (34.1 KB) |
| Gate 5, allocation regression <= 2% | **fail**: startup d0 static +17.8%, terrain steady +12.3%, pan +5.6%, sort +7.4% (scenes where the hook does almost nothing) |
| Gate 6, counters within 2% | pass (only `visible_chunks` is populated without detail hooks) |
| Frame, tail, CPU (gates 2-4, 7) | measured, not judged (llvmpipe). In-process medians: frame mean +1.1% static / +0.6% pan; A2/A1 drift median 3.7% / 8.2% |

**Verdict: fails; stays off.** Correctness is supported by the 10.4 M exact comparisons, but the frozen screenshot and
allocation gates failed.

## What the failures are (post hoc, reported only; nothing rescored)

- **Allocation noise, not the hook.** Whole-frame `alloc_bytes` includes the client tick in nearly every frame at
  this frame rate; per-launch A values ranged 159-191 KB static, and the no-op scenes moved -18% to +45% between
  startup launches. On frames *without* a tick ([posthoc.py](posthoc.py), [posthoc.json](posthoc.json); 1-52 frames per
  side), the in-process saving was 17.0-20.1 KB per frame in all eight launch/phase combinations (A 54.6-57.8 KB, B
  34.5-39.8 KB), matching the attribution estimate. The protocol's whole-frame target cannot resolve a 20 KB saving
  here; on a GPU machine, where most frames have no tick, it should.
- **Pan screenshots** are captured on the first frame after the phase's end; at ~16 FPS the camera turns about 3
  degrees per frame, and the "on" capture is visibly rotated against both vanilla captures
  ([screens-pan-absent1-on-absent2.png](screens-pan-absent1-on-absent2.png)). Static captures match apart from clouds
  and animation ([screens-static-absent1-on.png](screens-static-absent1-on.png)).

A later session that wants this candidate should freeze a new series on the reference hardware (where frames rarely
tick), or pre-declare a tick-free allocation target and a fixed-angle screenshot, before measuring. Do not promote it
from the post hoc numbers.

## Validation boundaries

- Build/tests: `gradlew build` on JDK 25.0.4.1: 105 tests pass (one new plugin test; config tests extended).
- Live: 32 accepted launches (6 attribution, 26 gate) with the actual launcher, `-Xmx2G` and every `-Dalacer.*` checked
  from `/proc`, frozen sources, unchanged base world, zero dropped frames, no GL errors and no nonzero mismatch lines;
  6 rejected attempts kept. Four development launches (base-world generation, a verify smoke, two class exports) are
  outside every series.
- JFR allocation samples are weighted estimates; ThreadMXBean is exact per thread but unattributed. Two attribution
  launches per scene, one world, one machine, software GL.
- No lifecycle stress was run (the change keeps no state across calls; gates.md). No third-party performance-mod code.

## Reproduction

```sh
JAVA_HOME=<jdk25> sh ./gradlew build -Porg.gradle.java.installations.paths=<jdk25>
python3 tools/run_allocation_selection.py profiles --jdk <jdk25> --init <mirror.gradle>
for s in d0 d200 terrain; do for n in 1 2; do python3 docs/benchmarks/2026-10-22-allocation-selection/classify.py \
  docs/benchmarks/2026-10-22-allocation-selection/runs/alloc28-$s-r$n.record.json \
  docs/benchmarks/2026-10-22-allocation-selection/runs/alloc28-$s-r$n.classified.json --jdk <jdk25>; done; done
python3 docs/benchmarks/2026-10-22-allocation-selection/decision.py docs/benchmarks/2026-10-22-allocation-selection/runs decision.json
python3 tools/run_shadow_position_gates.py verify|screens|ab|startup --jdk <jdk25> --init <mirror.gradle>
python3 docs/benchmarks/2026-10-22-allocation-selection/gates.py gates.json
```

Labels, logs and result folders are refused if reused; use new labels for another series. Raw CSV/JFR stay in the
ignored `run/`, logs in the ignored `build/`; records keep their hashes. `<mirror.gradle>` is only needed where Maven
Central rate-limits (this container); it rewrites Central to Google's mirror.
