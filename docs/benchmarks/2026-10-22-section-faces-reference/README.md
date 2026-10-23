# Session 30: `render.fastCameraSectionFaces` on the reference hardware (results)

**Verdict: closed. `render.fastCameraSectionFaces` stays off by default and outside `render.defaults`.** The frozen
outcome rule in [gates.md](gates.md) closes the option because conditions 1 (region), 4 (no regressions), 5 (still
phases unchanged) and C2 (screenshots) failed. The benefit gate (2) passed narrowly, so the "exact, no demonstrated
frame-time benefit" closure does not apply: that closure requires every other condition to pass. The candidate stayed
exact in every launch: 364,446 answers compared with vanilla's flood fill, 0 mismatched.

The series was run once, as frozen, on October 22, 2026 (13:01-14:47 local, PDT). All 47 scored launches and all four
smoke launches passed the runner's checks; none was rejected or repeated. `gates.py` produced [gates.json](gates.json)
(judged conditions, per-scene medians and every pair's raw metrics).

## Environment

Windows 11, i7-12700F (20 logical processors), RTX 3060 Ti (`NVIDIA GeForce RTX 3060 Ti/PCIe/SSE2`, OpenGL 4.6.0 NVIDIA
616.92), Eclipse Adoptium 25.0.4.1+1-LTS at `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot` for Gradle,
tests and the client. Window 1920x1080, 2 GiB heap, view distance 12, vsync off, display lists (`vboActive=false`).
The base world is the seed-1789 `run/saves/alacer-benchmark` used by sessions 26-29 (34 files; every hash equal to
session 29's frozen manifest), copied with `run/config/alacer.properties` (hash equal to session 29's) and
`run/options.txt` from the shared checkout. Every launch played a fresh copy; both flights logged the byte-identical
world-copy line.

The series ran in a separate git worktree on `claude/project-thread-3lu2ch` at `7d153c0` (the fast-forward of
`claude/kind-euler-pq408k`), not in the shared untracked checkout. Build and unit tests (separate from the evidence
below): `gradlew.bat build` with the explicit JDK, 123 tests, 0 failures, errors or skips; classfile major version 69.

## Freeze and amendment

- Smoke 1 (`sf30-smoke-verify`, `sf30-smoke-near`) passed on the merged build: 6,802 and 7,266 answers compared,
  0 mismatched; the static, pan and fly captures were inspected and are correct.
- Pre-freeze amendment (written in gates.md before the freeze): this line lacked session 29's amendment 2, so
  `BenchmarkRunner` still sized each phase's frame buffer for 2,000 frames per second. Session 29's approved fix
  (`seconds * 4000 + 1000`) was ported unchanged, the client rebuilt (123 tests pass) and the smoke repeated as
  `sf30-smoke-verify-r2` and `sf30-smoke-near-r2`: 7,696 and 7,357 answers compared, 0 mismatched; captures inspected
  and correct. The fix was needed: four recorded phases exceeded the old 41,000-row capacity of a 20 s phase (d0 static
  pair 1 on 41,905 frames at about 2,095 FPS, pair 1 off 41,610, pair 2 off 41,146; terrain steady pair 2 on 40,552),
  so at least three launches would have been rejected.
- Freeze: `manifest.json`, frozen 2026-10-22T20:05:42Z (13:05 local); sources ZIP `sources-6f51c36ccd3b37ea.zip`
  (sha256 `d88ffc76...0fcd831`); 176 source/tooling hashes, 180 compiled artifacts, the base world and the shared config.
  Nothing the manifest covers changed afterwards (the runner checked it before and after every launch).

## Gates

Pooled moving = median over the 24 moving pair changes (d0 fly, d200 fly, terrain sort and fly, near and high
fresh-flight; 4 pairs each). Changes are on/off - 1.

| Condition | Requirement | Result | Pass |
|---|---|---|---|
| 1. Region | pooled moving `terrain_setup` <= -3%; lower in >= 3 of 4 pairs in each moving scene-phase | pooled -1.65%; pairs lower: d0 fly 4, d200 fly 3, terrain sort 3, terrain fly 3, near fresh-flight **2**, high fresh-flight **2** | **fail** |
| 2. Benefit | pooled moving frame mean <= -1%, or slow-1% <= -2%, or p99 <= -2% | frame mean -1.22%, slow-1% -1.24%, p99 -1.46% | pass (frame mean only) |
| 3. Allocation direction | tick-free allocation lower in >= 3 of 4 resolved pairs per moving scene-phase; pooled median < 0 | 4, 3, 4, 4, 4, 4 of 4; pooled -28.3% (allocation per second -26.7%); no unresolved pair | pass |
| 4. No regressions | every judged scene-phase: frame mean <= +2%; p99, slow-1%, tick p99 <= +5%; client and process CPU <= +2% | 12 failures (below) | **fail** |
| 5. Still unchanged | still phases: tick-free allocation and `terrain_setup` <= +2% | d0 static `terrain_setup` +8.27%; d0 pan tick-free allocation +8.40%; near still `terrain_setup` +5.94% | **fail** |
| C1 verify | >= 5,000 compared, >= 100 fills, >= 100 reused, 0 mismatched | 364,446 compared, 718 fills, 363,728 reused, 0 mismatched | pass |
| C2 screenshots | per d200 phase, on vs off-1 within 10% (mean abs RGB) and 10% + 0.1 pp (differing pixels) of off-2 vs off-1 | static: 0.610 vs limit 0.600, 6.76% vs limit 5.70%; pan: 0.598 vs 0.588, 6.87% vs 5.82%; fly: 0.110 / 1.92% vs 0.169 / 2.48% | **fail** (static, pan) |
| C3 counters | visible and terrain-layer counters within 2% (5% in fresh flight) | largest median change 0.40% | pass |
| Build and tests | JDK 25 | 123 pass | pass |

Condition 4 failures (median per-pair change): d200 fly tick-frame p99 +9.81%; terrain sort frame mean +2.74% and
process CPU +2.56%; high fresh-flight client CPU +5.06%; d0 static client CPU +3.55% and process CPU +4.43%; d0 pan
tick-frame p99 +15.73% and client CPU +2.07%; terrain pan tick-frame p99 +10.47%; high still p99 +11.27%, slow-1%
+18.28% and tick-frame p99 +27.98%.

Verify launches (option on with `.verify`): d200 178 fills / 63,232 compared; terrain 222 / 196,076; near 146 / 53,339;
high 172 / 51,799; 0 mismatched in each. In every "off" launch the option did no work (0 reads, fills and reuses); "on"
startup launches did 118-213 fills each.

## Per-scene medians

Median of the four per-pair changes (on/off - 1), from `gates.json` `reported_median_changes`. "Alloc tf" is allocation
per tick-free frame. Moving phases first.

| Scene/phase | `terrain_setup` | Frame mean | p99 | Slow-1% | Tick p99 | Alloc tf | Client CPU | Process CPU | Visible chunks |
|---|---|---|---|---|---|---|---|---|---|
| d0 fly | -2.34% | -0.04% | -2.61% | -0.70% | -4.83% | -27.68% | -0.89% | -0.47% | -0.40% |
| d200 fly | -2.18% | -0.64% | +2.82% | +3.58% | +9.81% | -31.12% | -4.85% | +0.61% | +0.05% |
| terrain sort | -2.29% | +2.74% | -11.06% | -8.83% | -8.82% | -28.83% | +1.24% | +2.56% | +0.03% |
| terrain fly | -5.69% | -4.01% | +0.45% | -0.67% | +1.28% | -26.65% | -4.02% | -2.88% | -0.37% |
| near fresh-flight | +1.28% | -1.79% | -3.27% | -4.08% | -16.25% | -24.30% | -1.58% | -1.87% | -0.06% |
| high fresh-flight | +0.32% | -2.95% | -3.47% | -2.62% | -0.90% | -25.68% | +5.06% | -0.45% | +0.05% |
| d0 static | +8.27% | +0.73% | -5.60% | -5.64% | -1.01% | +0.14% | +3.55% | +4.43% | 0.00% |
| d0 pan | +0.09% | -0.61% | -3.10% | +0.26% | +15.73% | +8.40% | +2.07% | +0.21% | +0.05% |
| d200 static | -11.03% | -6.67% | -7.56% | -7.15% | -12.71% | -0.64% | -5.35% | -4.81% | 0.00% |
| d200 pan | -10.45% | -6.37% | -5.14% | -6.91% | -7.16% | -0.75% | -7.92% | -7.25% | +0.04% |
| terrain steady | -14.15% | -16.60% | -17.50% | -15.29% | -6.01% | -6.96% | -15.07% | -9.90% | 0.00% |
| terrain pan | -6.40% | -10.10% | -1.40% | +0.96% | +10.47% | +1.42% | -2.89% | -2.46% | -0.21% |
| near still | +5.94% | -5.06% | -1.56% | -0.55% | +2.38% | -11.32% | +0.96% | -2.73% | 0.00% |
| high still | -6.78% | +1.16% | +11.27% | +18.28% | +27.98% | -0.50% | -0.77% | -0.74% | 0.00% |

Absolute medians of each side in the moving phases (off / on): tick-free allocation per frame d0 fly 18,067 / 13,791 B,
d200 fly 19,183 / 12,650 B, terrain sort 8,041 / 5,900 B, terrain fly 18,973 / 12,859 B, near 27,635 / 22,715 B, high
32,203 / 21,787 B; `terrain_setup` per frame 0.19-0.48 ms off. Frame means were 0.75-1.72 ms (about 580-1,330 FPS) in
moving phases and 0.53-1.19 ms in still phases.

**Tick-frame share** (frames containing a client tick, over every launch and judged phase): minimum 0.95% (d0 static),
median 2.10%, maximum 10.24% (high still, pair 3 off). The allocation target therefore resolved everywhere (at least
1,000 tick-free frames on both sides of every pair).

## Screenshots (inspected)

`sf30-shot-off-1`, `sf30-shot-on` and `sf30-shot-off-2` (d200, fixed end pose, time scale 1) were opened side by side
and as difference images. In every phase the terrain, its sections and their faces are identical: no missing, extra
or differently lit chunk or block face. All differences lie in drifting cloud edges, mob positions and poses (in the
static and pan captures, one sheep on the right stands differently in `shot-on`) and, in fly, animated water and
flowers. C2's frozen comparison uses `shot-off-1` as the base; it fails for static and pan by a margin of about one
mob. Post hoc (not rescored): `shot-on` against `shot-off-2` differs by 5.02% / 5.01% of pixels in static / pan, less
than `shot-off-2` against `shot-off-1` (5.09% / 5.20%).

## Attempts (all kept in `attempts/`)

| Group | Labels (status, elapsed) |
|---|---|
| smoke 1 (pre-amendment) | `sf30-smoke-verify` (passed-smoke, 46 s), `sf30-smoke-near` (passed-smoke, 39 s) |
| smoke 2 (after the buffer fix) | `sf30-smoke-verify-r2` (passed-smoke, 40 s), `sf30-smoke-near-r2` (passed-smoke, 38 s) |
| verify | `sf30-verify-d200` (122 s), `-terrain` (227 s), `-near` (92 s), `-high` (92 s): all passed |
| screens | `sf30-shot-off-1`, `sf30-shot-on`, `sf30-shot-off-2` (77-78 s): all passed |
| startup | 40 launches `sf30-st-<scene>-<n>-<off\|on>`, n = 1..4, in the frozen order: all passed (d0/d200 122-127 s, terrain 226-234 s, near/high 92-102 s) |

No launch was rejected, ended externally or repeated; there are no `-r2` scored labels. Per-launch records and
summaries are in `gates/`; the raw per-frame CSVs, phase JSONs, captures and launch logs stay in the ignored
`run/alacer/benchmarks/`, `run/screenshots/` and `build/` of the worktree (about 1.5 GB of results), with their
SHA-256 hashes in each record.

## Reading the result

- The mechanism behaves as stated before measuring: the allocation saving is large and consistent in every moving
  scene-phase (a quarter to a third of tick-free per-frame allocation), while the time saving in the visibility
  search is small (-1.65% pooled) and absent in the fresh flights, where `terrain_setup` was slightly higher with the
  option on in two of four pairs each.
- The still phases, where the option does no work, moved by as much as -16.6% (terrain steady frame mean) and +28%
  (high still tick-frame p99) between launches. At four pairs per scene, launch-to-launch variance on this machine is
  larger than the effects being measured, so the narrow benefit pass (-1.22% frame mean) and several regression
  failures are both within noise. This is a statement about the series' resolution, not a rescoring: the frozen rule
  closes the option.

## Limitations

- Four pairs per scene. Still-phase changes show the noise floor is several percent for means and tens of percent for
  tails.
- Background processes were running at a total of about 10-13% CPU before the series (an HP desktop extension,
  Spotify, Discord, a browser and this agent's app); no other Minecraft client ran (checked before and after every
  launch).
- Ordinary-profiler counters only (no detail profiling or JFR): the five chunk counters are the only work counters.
- Measured in a git worktree of `claude/project-thread-3lu2ch`, not the shared checkout sessions 26-29 used; same
  machine, JDK, world, shared config and options file.
- Raw per-frame data is not committed (project practice); `gates.py` needs it to be re-run.
