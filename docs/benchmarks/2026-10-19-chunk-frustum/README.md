# Session 12: chunk frustum test (roadmap item 1)

Continues the interrupted plane-test implementation. The prototype had the plane test,
config, counters and search hooks, but its `FrustumCullerAccessor` was missing from the mixin
configuration and it had no focused tests or live selection evidence. This session registers
the accessor, tests against Minecraft's actual `FrustumData.contains`, completes verification
logging, and uses the existing benchmark's warmed A/B/B/A protocol for this option.

## Exactness and scope

`render.fastChunkFrustum` only changes the frustum predicate in Alacer's existing chunk
visibility search. Vanilla rejects an AABB when every corner of a plane evaluates to a value
that is not strictly positive. The candidate evaluates the corner with the greatest value:
choose the larger coordinate when a coefficient is nonnegative, the smaller otherwise. It
uses the same camera subtraction, widened float coefficients, operation order and strict
`> 0.0` comparison as vanilla. Coordinates are sorted per axis, including reversed boxes.

This is exact for finite float coefficients and bounded translated doubles: the sum of the
six coordinate magnitudes must be at most `2^800`, which prevents product or partial-sum
overflow. Multiplication and addition are monotone here; the selected corner's computed
value is at least every other corner's. Unsafe inputs call vanilla. Only the exact vanilla
culler over `FrustumData` or `Frustum` is eligible. Subclasses, unknown cullers and detected
foreign mixins to any of the three frustum classes use the original predicate. No renderer,
occlusion rules, traversal order, terrain drawing, entity culling or GL state is replaced.

## Correctness evidence

`./gradlew.bat test` passes the five new frustum cases and the existing suite. The new cases
include 1,000,000 random chunk-box comparisons against the game's actual method, another
100,000 comparisons across finite float/double exponents and signed zero, strict boundary
contacts and their adjacent representable values, camera offsets, reversed/degenerate boxes,
malformed/nonfinite planes, unsafe coordinates and subclass fallback. These run on the
Gradle JVM; the following diagnostics run in the actual Java 8 game client.

Two full static/pan/fly diagnostics with 200 mobs, 1080p, distance 12 and VBO off:

- `fr-verify-boxes`: **17,668,936 box tests, zero differences, zero unsafe-input fallbacks**.
  Uses vanilla's answer while checking the candidate for each tested box.
- `fr-verify-traversal`: candidate predicate enabled, box verification off, full traversal
  verification on. **At least 11,000 searches, zero differences with stable compiled data**.
  Nine comparisons had compiled data change concurrently; five of those differed and are
  inconclusive. The last periodic log is at 11,000; this is not an exact final search count.
- The traversal diagnostic also replays each search's boxes through both predicates, in
  alternating order with one timer per batch. Pan: vanilla **0.0423 ms/frame**, candidate
  **0.0412** (about 1,300 tests/frame). Fly: **0.0391 → 0.0360 ms/frame** (about 1,108 tests).
  This shows only a small isolated saving. Replay adds work and changes JIT/cache behavior;
  it is attribution evidence, excluded from performance selection.

`verification.json` and `runs/*verify*.summary.json` archive the diagnostics. Rebuild with:

```powershell
python docs/benchmarks/2026-10-19-chunk-frustum/verify.py
```

Raw diagnostics and selection CSVs remain in ignored `run/alacer/benchmarks/`; complete
launch output is in `build/<label>.log`. The legacy dev client still logs its existing
missing Realms/Twitch/console-appender messages; these are distinct from rendering failures.

## Performance protocol

Six interleaved launches: `fr-{0,200}-r{1,2,3}`. A calls the vanilla culler, B uses the plane
test. Every launch warms A and B for static (5 seconds each), records static A/B/B/A (5 seconds
each), warms both through a complete pan (7.5 seconds each), and records pan A/B/B/A
(7.5 seconds each). The setup frame is discarded and each segment's actual config is saved.
Static is a control: it almost never rebuilds the visibility list. Fly generates/builds chunks
and is covered by correctness diagnostics, not repeated as an A/B comparison.

Other six defaults on, both older experiments off, entity detail/shadow timing/frustum replay
and all verification off. Same fixed-seed copied world, natural animals retained, 0 or 200
added NoAI mobs, fancy graphics/clouds, vsync off, FPS option 260 (unlimited in this client),
1080p, distance 12, VBO off, 2G heap, Temurin 8u492, i7-12700F / RTX 3060 Ti / NVIDIA 616.92.
Driver settings were left alone. CPU utilization does not establish the driver's worker mode.

```powershell
& docs/benchmarks/2026-10-19-chunk-frustum/run.ps1
python docs/benchmarks/2026-10-19-chunk-frustum/analyze.py
```

The analyzer checks source hashes, the complete phase set, actual per-phase toggles/settings,
full durations, zero dropped frames, setup-frame exclusion, raw frame-clock totals, zero
intrusive timer columns, active pan frustum counters, and successful launch logs. Summaries
are archived under `runs/`; `metrics.json` and `comparison.md` are rebuilt from the raw CSVs.

## Outcome and default decision

All six full-duration launches pass the data/config/log checks. Medians and ranges below are
over three launches, comparing pooled B segments against pooled A segments within each JVM:

| Added mobs | Phase | Median FPS change | Range | B faster than pooled A / A2 |
|---|---|---:|---:|---:|
| 0 | static | +2.26% | -3.13 to +8.62% | 2/3 / 2/3 |
| 0 | pan | -1.01% | -3.88 to +0.68% | 1/3 / 1/3 |
| 200 | static | +3.29% | -1.86 to +7.69% | 2/3 / 2/3 |
| 200 | pan | +0.58% | -4.47 to +15.84% | 2/3 / 2/3 |

Static almost never runs the frustum test, so its similar or larger fluctuations cannot be
credited to the predicate. Pan visibility time was lower in 4/6 comparisons against pooled
A, but only 3/6 against A2. Median visibility savings were about 0.010 ms/frame at either
population, much smaller than A1/A2 drift in several launches; the isolated replay saved just
0.001-0.003 ms/frame. There is no dependable end-to-end improvement. Individual sections,
client CPU, allocation and tails are retained in `metrics.json`; do not select a best segment
or infer a driver mode from these varying timings.

**Decision: `render.fastChunkFrustum=false` by default.** Retain the exact, verified predicate
as an opt-in experiment. `render.defaults` keeps it off in both A and B, preserving the six
previously selected optimizations. Do not prioritize further frustum arithmetic changes from
this evidence; chunk-build responsiveness is the next roadmap item.

The interrupted prototype defaulted this unmeasured option to true. The selection series
explicitly switched it false/true, independent of that default. After selection, only the
config default, preset, option description and default assertion changed; the predicate,
search and benchmark hot paths stayed identical. `implementation.json` keeps both the
measured `selection_sha256` and final `sha256` maps and names this difference. A final build
passes **39 tests, zero failures/errors/skips**. The abbreviated `fr-final-defaults-smoke`
launch checks the final combined preset; it is excluded from all performance tables.

Final validation commands (archive the existing run with the same label before rerunning):

```powershell
.\gradlew.bat test build --console=plain
.\gradlew.bat runClient '--console=plain' '-Palacer.benchmark=true' '-Palacer.benchmark.label=fr-final-defaults-smoke' '-Palacer.benchmark.entities=0' '-Palacer.benchmark.toggle=render.defaults' '-Palacer.benchmark.timeScale=0.1' '-Palacer.profiler.entityDetail=false' '-Palacer.profiler.frustumTiming=false' '-Palacer.profiler.hud=false' '-Palacer.benchmark.vbo=false' '-Pwindow=1920x1080' '-Pxmx=2G'
python docs/benchmarks/2026-10-19-chunk-frustum/verify.py
```

The preset smoke completed all twelve segments, keeping the frustum and older experiments
off in both paths and switching precisely the six selected defaults. No rendering errors were
found. It does not provide performance evidence.

Limits: one machine/settings/world, two added populations, both paths in an instrumented
client. Synthetic fallback tests and the exact-class/mixin guards are not arbitrary-mod
compatibility certification. No framebuffer identity claim, no driver-mode inference, and no
unmodified-client comparison. Nothing is committed, pushed, tagged or released.
