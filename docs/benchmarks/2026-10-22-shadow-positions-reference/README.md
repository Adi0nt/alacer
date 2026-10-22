# Session 29: `render.reuseShadowPositions` on the reference hardware (results)

**Verdict: closed. `render.reuseShadowPositions` stays off by default and outside `render.defaults`.** Under the
frozen outcome rule in [gates.md](gates.md) any failed condition closes the candidate. The allocation target passed
clearly, as did `.verify`, gate 7 and the static and pan screenshots. The fly screenshot, gates 2-6, the startup
frame-mean condition and the non-selecting scenes' frame-mean condition failed. The candidate stayed exact:
335,264,645 positions compared with vanilla's, 0 mismatched.

The series ran on October 22, 2026, from 01:40 to about 03:32 local (PDT), in the shared reference checkout. Two
user-approved amendments refroze it (three freezes in all). 26 launches are judged: 3 verify, 3 screenshot, 4
in-process A/B and 16 startup. Every attempt is kept, including the externally ended, the rejected and the accepted
but unjudged ones. `gates.py` produced [gates.json](gates.json), which holds the judged conditions, the reported
medians and every launch's raw metrics. This README was written afterwards, in session 31, from those files; nothing
in them was changed or rescored.

## Environment

Windows 11, i7-12700F, RTX 3060 Ti (`NVIDIA GeForce RTX 3060 Ti/PCIe/SSE2`, OpenGL 4.6.0 NVIDIA 616.92 in every
launch), Eclipse Adoptium 25.0.4.1 at `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot` for Gradle, tests
and the client (the runner checked the actual `java.exe` and its `-Dalacer.*` values from `Win32_Process`). Window
1920x1080, 2 GiB heap, view distance 12, vsync off, display lists. The seed-1789 base world
`run/saves/alacer-benchmark` (34 hashed files) and `run/config/alacer.properties` were unchanged across all three
freezes. Every launch played a fresh copy.

## Freezes and amendments

| Freeze | Frozen (UTC) | Sources ZIP | Changed from the previous freeze | Launches run under it |
|---|---|---|---|---|
| 1 (`freeze-1.json`) | 2026-10-22T08:43:46Z | `sources-e337ca1723d15205.zip` | (after the two smoke launches) | `spr29-verify-d200-1` (ended externally) |
| 2 (`freeze-2.json`) | 2026-10-22T08:55:29Z | `sources-44da61fd7f3fdf70.zip` | runner, gates.md, gates.py (amendment 1: `-r2` verify label) | 21 accepted, 1 rejected |
| 3 (`manifest.json`) | 2026-10-22T10:08:22Z | `sources-19846e6f433fa9a0.zip` | `BenchmarkRunner.java` and its class, runner, gates.md, gates.py (amendment 2) | 6 accepted |

- **Smoke** (before freeze 1, attempts only): `spr29-smoke-verify` (on with `.verify` and fixed-pose screenshots, time
  scale 0.2; 9,862,350 positions compared, 0 mismatched; 67 s) and `spr29-smoke-ab` (toggle A/B; 78 s). Both passed.
- **Amendment 1.** `spr29-verify-d200-1`, the first launch under freeze 1, started at 01:44 local. It was ended from
  outside the runner at about 01:49, just after the client logged `recording 'fly'`. No Java or Python process
  remained, and the attempt file still reads `running` with no exit code or record. The cause was not established.
  Its periodic verification lines (153,000,000 compared, 0 mismatched) are not counted. With the user's approval the
  launch was repeated as `spr29-verify-d200-1-r2` under freeze 2. Nothing else changed.
- **Amendment 2.** Under freeze 2, `spr29-st-d0-2-absent` was rejected by the runner's zero-dropped-frames check. Its
  static phase reached about 2,255 FPS and overflowed the 41,000-row buffer, dropping 4,102 frames. With the user's
  approval, `BenchmarkRunner` now sizes the buffer for 4,000 FPS (`seconds * 4000 + 1000`, allocated before
  recording, so recorded frames and allocation are unaffected), and the series was refrozen (freeze 3). The whole d0-2
  pair was repeated as `spr29-st-d0-2-on-r2` then `spr29-st-d0-2-absent-r2`, followed by the four terrain launches.
  The accepted `spr29-st-d0-2-on` (freeze 2) is kept but not judged. gates.md's amendment heading says "22 accepted
  launches under freeze 2"; the attempt files show 21 (3 verify, 3 screens, 4 ab and the first 11 startup launches).
  The frozen text is left as it is.

## Gates

Selecting scene d200. "In-process" is the median over the four `spr29-ab` launches of B/A - 1, with A and B each
pooled over two segments. "Startup" is the median over the startup pairs of on/absent - 1. All values are from
`gates.json` (`judged`, `reported`; gate 5 from the medians of
`startup[scene][pair][phase].change.alloc_per_tick_free_frame`).

| Condition | Requirement | Result | Pass |
|---|---|---|---|
| 1. Target, d200 static | tick-free allocation lower in >= 3 of 4 in-process launches; in-process and startup medians <= -5% and >= 9,855 B saved | 4 of 4; in-process -37.73% (19,968 B), startup -38.36% (20,279 B); no unresolved combination | pass |
| 1. Target, d200 pan | as above, >= 10,737 B | 4 of 4; in-process -38.47% (21,446 B), startup -37.35% (21,233 B) | pass |
| 2. Frame mean | >= 2% saving in one d200 in-process phase; no d200 combination above +1% | saving met (in-process static -2.54%); regressions: in-process pan **+2.35%**, startup fly **+6.63%** | **fail** |
| 3. Tails | p95, p99, tick-frame p99 <= +5%; slow-1% not higher (d200) | in-process pan slow-1% **+2.96%**; startup fly p95 **+7.09%**, slow-1% **+5.07%** | **fail** |
| 4. CPU | client CPU not higher; process CPU <= +2% (d200) | in-process pan client **+3.50%**, process **+2.60%**; startup fly client **+8.13%**, process **+8.34%** | **fail** |
| 5. Allocation no-op limit | tick-free allocation <= +2% in every scene and phase | startup d200 fly **+9.03%**, d0 pan **+2.07%**, d0 fly **+2.29%** | **fail** |
| 6. Counters | within 2% everywhere | startup d0 fly `visible_chunks` **-2.39%** (every other counter within 2%) | **fail** |
| 7. Drift | if median abs(A2/A1 - 1) > 10%, B below A1 and A2 in >= 3 of 4 | static 9.59% (`ab-2` +30.6% and `ab-3` +15.4% flagged), B below both in 4 of 4; pan 2.85% | pass |
| Startup d200 | median frame mean lower | static -1.74%, pan -10.55%, fly **+6.63%** | **fail** |
| Non-selecting | d0, terrain frame mean <= +1% | d0 static **+3.33%**, pan **+16.07%**, fly **+24.42%**; terrain +0.68 / -0.31 / +0.03 / -0.17% | **fail** |
| `.verify` | >= 10,000,000 compared, 0 mismatched | 335,264,645 compared (`d200-1-r2` 159,983,004, `d200-2` 151,472,464, `walk` 23,809,177), 0 mismatched | pass |
| Screenshots | per phase, on vs absent-1 within 10% (mean abs RGB) and 10% + 0.1 pp (differing pixels) of absent-2 vs absent-1 | static 0.460 / 5.89% vs 1.564 / 9.56%; pan 0.458 / 5.87% vs 1.580 / 9.57%; fly **0.0415 / 1.74%** vs 0.0172 / 0.081% | **fail** (fly) |
| Build and tests | JDK 25 | 105 tests passed at preparation (container, run plan); the reference build's count is not recorded | not judged by `gates.py` |

`judged.correctness_pass` is false (the fly screenshot) and `judged.pass` is false.

## In-process medians (d200, `spr29-ab-1` .. `-4`)

| Phase | Alloc tf | Saving | Frame mean | p95 | p99 | Slow-1% | Tick p99 | Client CPU | Process CPU | Visible chunks |
|---|---|---|---|---|---|---|---|---|---|---|
| static | -37.73% | 19,968 B | -2.54% | -0.14% | -5.67% | -2.17% | -0.24% | -2.45% | -6.82% | 0.00% |
| pan | -38.47% | 21,446 B | +2.35% | +4.33% | +4.48% | +2.96% | +0.48% | +3.50% | +2.60% | -0.11% |

Per launch, tick-free allocation fell by 37.0-37.8% (static) and 38.3-38.6% (pan) in all four. Frame mean changed by
+2.2, -9.1, -7.3 and +23.7% (static) and -2.2, +18.9, -2.9 and +6.9% (pan). Medians of the absolute values: tick-free
allocation per frame 52,923 B (A) and 32,956 B (B) static, 55,772 B and 34,307 B pan; frame mean 1.188 / 1.267 ms
static and 1.405 / 1.506 ms pan.

## Startup medians

On/absent - 1, median over pairs (4 in d200, 2 in d0 and terrain). "Alloc tf" is allocation per tick-free frame
(gate 5).

| Scene/phase | Alloc tf | Frame mean | p95 | p99 | Slow-1% | Tick p99 | Client CPU | Process CPU | Visible chunks |
|---|---|---|---|---|---|---|---|---|---|
| d200 static | -38.36% | -1.74% | -3.89% | -1.69% | -2.22% | -13.62% | -2.49% | -3.29% | 0.00% |
| d200 pan | -37.35% | -10.55% | -9.66% | -11.80% | -14.44% | -10.53% | -12.50% | -11.71% | -0.19% |
| d200 fly | +9.03% | +6.63% | +7.09% | +4.88% | +5.07% | -0.41% | +8.13% | +8.34% | +0.24% |
| d0 static | +0.15% | +3.33% | +8.56% | +8.72% | +2.00% | +26.63% | +2.72% | +4.16% | 0.00% |
| d0 pan | +2.07% | +16.07% | +23.67% | +19.47% | +19.35% | +17.56% | +13.41% | +17.26% | -0.22% |
| d0 fly | +2.29% | +24.42% | +22.54% | +16.36% | -4.82% | +22.21% | +21.08% | +13.83% | -2.39% |
| terrain steady | -20.70% | +0.68% | +4.75% | +4.90% | +9.85% | +7.67% | -2.96% | -2.49% | 0.00% |
| terrain pan | -15.34% | -0.31% | +2.53% | +5.75% | +5.89% | +11.54% | -1.22% | -1.96% | +0.06% |
| terrain sort | -12.89% | +0.03% | -0.03% | -0.55% | -0.60% | -6.11% | -0.79% | -0.40% | -0.04% |
| terrain fly | -6.82% | -0.17% | +2.22% | +1.03% | -0.34% | -5.76% | +0.59% | +0.22% | -0.18% |

Medians of each side (absent / on): tick-free allocation per frame d200 static 52,862 / 32,583 B, pan 57,022 /
35,707 B, fly 22,932 / 24,322 B. Frame mean d200 1.087 / 1.068 ms (static), 1.647 / 1.512 (pan), 1.382 / 1.448 (fly).
d0 0.595 / 0.614, 0.783 / 0.908 and 1.156 / 1.416 ms. Terrain 0.532-1.085 ms on both sides.

Per pair, the d200 fly changes run in step. Tick-free allocation changed by +24.6, -5.8, +23.8 and -10.7%, and frame
mean by +22.2, -9.0, +26.8 and -12.4%. In fly the camera crosses blocks continuously, so per-crossing allocation is
spread over more or fewer frames as the frame rate changes. The fly allocation gate therefore follows frame time and
does not isolate the shadow path. The d0 pair 2 changes (+29.4% pan, +52.4% fly) come from the repeated pair, whose
"on" launch ran slowly in every phase. Session 31's [noise-floor analysis](../2026-10-22-noise-floor/README.md) puts
that launch at +15.2% in its still phases; that analysis is reported separately and does not rescore this series.

**Tick-frame share** (frames containing a client tick, over every judged launch and phase): minimum 1.06%, median
2.27%, maximum 4.22%. Every combination had at least 1,000 tick-free frames on both sides.

## Attempts (all kept in `attempts/`)

| Group | Labels (status, elapsed) |
|---|---|
| smoke (pre-freeze) | `spr29-smoke-verify` (passed-smoke, 67 s), `spr29-smoke-ab` (passed-smoke, 78 s) |
| verify | `spr29-verify-d200-1` (freeze 1; **ended externally**, status `running`, no record), `spr29-verify-d200-1-r2` (501 s), `spr29-verify-d200-2` (486 s), `spr29-verify-walk` (218 s): the last three passed |
| screens | `spr29-shot-absent-1` (85 s), `spr29-shot-on` (81 s), `spr29-shot-absent-2` (80 s): passed |
| ab | `spr29-ab-1` .. `-4` (201-206 s): passed |
| startup d200 | 8 launches `spr29-st-d200-<n>-<absent\|on>`, n = 1..4, pairs absent/on, on/absent, absent/on, on/absent (124-128 s): passed |
| startup d0 | `spr29-st-d0-1-absent`, `-1-on` (125-126 s): passed; `spr29-st-d0-2-on` (124 s): **accepted, not judged** (amendment 2); `spr29-st-d0-2-absent` (124 s): **rejected** (4,102 dropped frames in static); `spr29-st-d0-2-on-r2` (134 s), `spr29-st-d0-2-absent-r2` (124 s): passed |
| startup terrain | `spr29-st-terrain-1-absent`, `-1-on`, `-2-on`, `-2-absent` (228-232 s): passed |

Per-launch records and summaries are in `gates/`. The rejected and the externally ended attempts have none. The raw
per-frame CSVs, phase JSONs, captures and launch logs stay in the ignored `run/alacer/benchmarks/`, `run/screenshots/`
and `build/` of the shared checkout, with their SHA-256 hashes in each record.

## Reading the result

- The mechanism behaved as predicted: removing the per-shadow `pos.down()` allocation cut tick-free allocation per
  frame by about 38% (20-21 KB) in d200 static and pan, in every in-process launch and every startup pair.
- No frame-time benefit followed. In-process static met gate 2's 2% saving (-2.54%), but in-process pan moved +2.35%.
  The run plan expected gate 2 to decide the series, and it failed through its no-regression half.
- The d0 scene has no mob shadows, so the option has nothing to do there, yet d0 frame means moved by +3.3% to
  +24.4%. In d200, per-launch and per-pair frame-mean changes ranged from -25.8% to +26.8%. At two to four pairs,
  launch-to-launch variance is larger than any frame-time effect 20 KB less allocation could have. This is a
  statement about the series' resolution, not a rescoring: the frozen rule closes the option.
- The fly screenshot failed because the two absent captures were nearly identical (0.08% of pixels differ), so the
  allowed limit was 0.19%, and the on capture differed by 1.74%. The archive records no visual inspection of the
  captures and they are not committed, so whether the difference is mobs, clouds, water or shadows is not
  established here.

## Limitations

- Two to four pairs per scene. d0 and terrain have two pairs each, and d0 pair 2 is the repeated pair.
- Ordinary-profiler counters only (no detail profiling or JFR). With detail off, only `visible_chunks` among the work
  counters is populated.
- Two launches never completed. `spr29-verify-d200-1` was ended from outside for an unestablished reason, and
  `spr29-st-d0-2-absent` was rejected for dropped frames. Both are kept and neither is judged.
- Background load during the series is not recorded in the archive.
- Raw per-frame data and screenshots are not committed (project practice); `gates.py` needs them to be re-run.
- This README was written in session 31 from the committed files, not on the reference machine.
