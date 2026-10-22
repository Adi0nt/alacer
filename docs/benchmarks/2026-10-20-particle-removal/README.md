# Particle removal: dead-particle `removeAll` (session 16)

Roadmap item 1 after session 15. All launches are Java 25 (Temurin 25.0.4.1, G1) on the same
machine and settings as [the particle archive](../2026-10-20-particles/README.md): 1920x1080, render
distance 12, fancy graphics and clouds, smooth lighting, VBOs off, vsync off, unlimited FPS, the
seed-1789 copied world, the dedicated particle scenario (`benchmark.particles=true`) at
`particleScale` 1 (about 3,300 particles drawn, 3,500 ticked) and 2 (about 5,130 drawn, 5,400
ticked, the 4,000-per-list cap engaged). NVIDIA driver settings unchanged. Nothing here is pooled
with Java 8 data or with session 15's numbers.

## The cost

Every client tick, `ParticleManager.tickParticles(List)` ticks each particle of a list, collects
the removed ones in a new `ArrayList` and calls `particles.removeAll(dead)`. On Java 25
`ArrayList.removeAll` is `batchRemove`, which asks `dead.contains(live)` for each live particle,
that is `live.equals(dead[i])` for every pair until a match: about 3,500 x 210 `Entity.equals`
calls per tick at scale 1. It runs in the frame that ticks.

Intrusive detail launches (`pr-dflt-detail-*`, `profiler.particleDetail=true`, shipped defaults,
no toggle; per frame that ran a client tick):

| Scale | Phase | Removal, vanilla | Removal, `render.fastParticleRemoval` | Particle tick, vanilla -> option | Removed per tick |
|---|---|---:|---:|---:|---:|
| 1 | static | 0.871 ms | 0.091 ms | 2.630 -> 1.912 ms | 210 |
| 1 | pan | 0.886 ms | 0.098 ms | 2.679 -> 1.933 ms | 211 |
| 2 | static | 1.587 ms | 0.123 ms | 4.570 -> 2.631 ms | 273 |
| 2 | pan | 1.494 ms | 0.141 ms | 4.228 -> 2.823 ms | 274 |

The timers add two `nanoTime` calls per particle update, move and light query (detail values are
inflated and the launches are separate JVMs); the light-off detail pair (`pr-detail-*`) agrees:
0.84-0.85 -> 0.08 ms at scale 1, 1.39 -> 0.12-0.13 ms at scale 2. Movement (`Entity.move`, 1.25-2.3
ms per tick) is untouched and is now the largest particle tick cost.

## Optimization 8: `render.fastParticleRemoval`

Design and lifecycle: [ARCHITECTURE.md](../../ARCHITECTURE.md#optimization-8-dead-particle-removal).
`Entity.equals` is true exactly for another entity with the same `networkId`, and `Entity.hashCode`
returns it. For classes that inherit both from `Entity` (every vanilla particle; checked once per
class with a `ClassValue`), removal is "remove every particle whose id is a dead particle's id".
A `@Redirect` of that `removeAll` call hands both lists to `KeyedRemoval`, which builds a reused
open-addressing set of the dead ids, checks every element, then moves survivors down with `set`
and removes the tail from the end, so the list object, survivor order, backing array, size and
modification count end exactly as `batchRemove` leaves them. Equal-id particles and duplicates are
removed as `removeAll` removes them. The ticking loop is vanilla's, so particles added during the
tick (including the cap's `remove(0)` evictions) and layer moves (`setAlpha` -> `changeLayer`)
behave as before.

Vanilla's call runs when the option is off, off the client thread, when either list is not exactly
`java.util.ArrayList`, or when any element is null or of a class overriding `equals` or
`hashCode` (the list is untouched before that decision). The fast path allocates nothing (the
id set grows only when a larger dead list appears); vanilla's dead `ArrayList` is still created by
vanilla. Runtime-toggleable; client thread only.

**Verification** (`verification.md`): `render.fastParticleRemoval.verify` runs the fast path on a copy
and vanilla on the real list, and compares survivors by identity, count and return value.
`pr-verify-1` (scale 1): 7,200 removals, 165,751 particles removed, 0 mismatches. `pr-verify-2`
(scale 2 with `benchmark.particleRemovalDiagnostic`): 7,176 removals, 189,156 particles removed,
80 vanilla fallbacks (the `equals`-overriding probe), 0 mismatches, with 32,894 layer moves during
ticking (3,119 on the dying tick), 43,960 additions to a full list while it ticked, 787 deaths with
a live equal-id twin and 797 duplicated probes. Unit tests compare against `ArrayList.removeAll`
including the modification count.

## Measured series

Warmed in-process A/B/B/A (`benchmark.toggle=render.fastParticleRemoval`, added to
`BenchmarkRunner.warmsBothPaths`; both paths warmed in static and pan; detail and verification
off). Eight launches per series, scale 1 and 2 interleaved by round with the scale order
alternating (`r1`: 1, 2; `r2`: 2, 1; ...). The source manifest (`implementation.json`) was frozen
before the first measured launch; every launch passes `analyze.py`'s checks (full durations, one
discarded setup frame, no dropped frames, configuration per phase, particle counts, zero detail
columns, no GL errors, crashes, mismatches or fallbacks, identical emitters/anchor/JVM/GPU/mods).

**Primary series `pr-dflt-*`** (all seven shipped defaults on in A and B, including
`render.fastParticleLight`). Tick frames are frames that ran a client tick: about 200 per variant
in static (pooled 2 x 5 s) and 300 in pan (2 x 7.5 s), so tick-frame p99 is about the second or
third slowest frame and the maximum is one frame.

| Scale | Phase | Tick-frame mean reduction, median (range) | Tick-frame ms saved, median (range) | Particle tick saved per tick frame | Frame mean reduction, median (range) | B faster (frame mean) |
|---|---|---:|---:|---:|---:|---:|
| 1 | static | 24.3% (14.5 to 24.5%) | 1.10 (0.59 to 1.29) | 0.93 (0.74 to 0.97) ms | 9.8% (-1.2 to 14.1%) | 3/4 |
| 1 | pan | 15.9% (11.5 to 21.6%) | 0.76 (0.54 to 0.97) | 0.79 (0.75 to 0.83) ms | 0.4% (-3.7 to 6.3%) | 2/4 |
| 2 | static | 23.8% (20.7 to 27.2%) | 1.50 (1.23 to 1.72) | 1.37 (1.20 to 1.47) ms | 5.7% (-0.9 to 12.5%) | 3/4 |
| 2 | pan | 21.6% (18.6 to 27.5%) | 1.41 (1.19 to 1.93) | 1.29 (1.20 to 1.51) ms | 3.8% (-0.1 to 14.9%) | 3/4 |

Launches where B is lower (of 4 per cell; static/pan at scale 1, then scale 2):

| | Tick-frame mean | Tick-frame p95 | Tick-frame p99 | Tick-frame max | All-frame p99 | Non-tick-frame mean | Client CPU |
|---|---:|---:|---:|---:|---:|---:|---:|
| Scale 1 | 4/4, 4/4 | 4/4, 3/4 | 2/4, 4/4 | 4/4, 4/4 | 4/4, 4/4 | 3/4, 2/4 | 3/4, 2/4 |
| Scale 2 | 4/4, 4/4 | 4/4, 4/4 | 4/4, 4/4 | 4/4, 4/4 | 4/4, 4/4 | 2/4, 2/4 | 4/4, 3/4 |

1% low FPS was higher in 15/16 pairs. The tick-frame saving matches the detail runs' removal cost;
frames without a tick are unchanged within noise (as expected: the option does nothing there).

**Frame means are noisy and should not be read as the effect size.** Only about one frame in 20-30
runs a tick, so the expected mean saving is roughly the tick-frame saving divided by that ratio,
about 0.03-0.07 ms (2-5%); the per-launch frame-mean changes range from +3.7% to -14.9%, and
launch-to-launch frame means moved by up to 45% (static A 0.92-1.34 ms at scale 1), so the larger
medians above include noise. Allocation per tick frame is unchanged (for example 2,073.7 -> 2,070.7
KB in `pr-dflt-1-r1` static); per-frame allocation moved by -22 to +2.5 KB, following frame rate (the
tick's allocation is spread over more or fewer frames), not the removal itself.

**Secondary series `pr-opt-*`** (run first; `render.fastParticleLight` was off in A and B because the
shared `run/config/alacer.properties` still held session 15's measured-build value `false`, and file
values override defaults). A valid A/B on a heavier frame: particle tick saved per tick frame
0.55-0.92 ms (scale 1) and 1.23-1.39 ms (scale 2) in every launch; tick-frame mean lower in 15/16
pairs. The exception, `pr-opt-1-r3` pan, still saved 0.55 ms of particle tick, but its B segments'
particle pass rose 0.21 ms and its non-tick frames 0.40 ms with the same particle counts, so its
frame mean was 25% higher in B; no cause is assigned. All-frame p99 lower in 15/16. Frame means:
-25.2% to +11.1% at scale 1 pan, the noisiest cell; 6/8 lower at scale 2.

**Decision: `render.fastParticleRemoval=true` by default**, and the `render.defaults` preset now
switches eight optimizations. It is exact (verified, including every edge case above), allocates
nothing, removes about 0.8-1.5 ms from every frame that ticks in this scenario, and lowered
tick-frame mean, maximum and all-frame p99 in every primary pair. Only `AlacerConfig.java` and its
test changed after the measured series (`final-default.diff`); `pr-final-defaults` is a short smoke
run of the final build through the preset. Not a claim for scenes with few particles (the cost is
O(live x dead)) or other hardware.

## Files

- `analyze.py`: rebuilds `comparison.md`, `metrics.json` and `selection.json` from the raw CSVs in
  `run/alacer/benchmarks/` and the launch logs in `build/`, validating every launch; checks that
  the current sources match `final_source_sha256`. Copies each summary to `runs/`.
- `implementation.json`: frozen measured build (every file under `src/` plus the Gradle files) and
  final build hashes.
- `final-default.diff`: the only change after measurement.
- `verification.md`: the correctness runs and probe case counts.

Smoke runs (`pr-smoke-detail`, `pr-smoke-ab`, `pr-smoke-verify`, `pr-verify-r1`, `pr-final-defaults`;
earlier builds or `timeScale` 0.3) checked hooks and harness only and are not used.

## Limits

One machine, one settings profile, one world area and a fixed particle mix. Tick-frame p99 and
maximum rest on 200-300 tick frames per variant. Both paths run in the same instrumented client
with the redirect present in A. Particle internals use vanilla's unseeded randomness (ticked and
removed counts match within 1% between variants). The verification runs had
`render.fastParticleLight` off, which does not touch removal. A mod that also `@Redirect`s this
`removeAll` call conflicts; injections around it compose (the detail timer does). Lists replaced by
another mod with a non-`ArrayList` type take vanilla's call. No driver settings were changed and no
driver mode is inferred from CPU use.

## Reproduce

```sh
./gradlew build
# optimization A/B (scale 1; add -Palacer.benchmark.particleScale=2 for the dense launches)
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pr-dflt-1-r1 -Palacer.benchmark.toggle=render.fastParticleRemoval -Palacer.render.fastParticleLight=true -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# intrusive attribution (repeat with fastParticleRemoval=true)
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pr-dflt-detail-off-1 -Palacer.profiler.particleDetail=true -Palacer.render.fastParticleLight=true -Palacer.render.fastParticleRemoval=false -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# verification (scale 2 with the removal probes)
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pr-verify-2 -Palacer.benchmark.particleScale=2 -Palacer.benchmark.particleRemovalDiagnostic=true -Palacer.render.fastParticleRemoval=true -Palacer.render.fastParticleRemoval.verify=true -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
python docs/benchmarks/2026-10-20-particle-removal/analyze.py
```

Every launch also passed `-Palacer.profiler.entityDetail=false -Palacer.profiler.hud=false
-Palacer.render.batchModelLists=false -Palacer.render.skipZeroTranslates=false
-Palacer.render.fastChunkFrustum=false` (the defaults) and logged to `build/<label>.log`. Launches
ran serially, with a check that no other game client was running. Pass
`-Palacer.render.fastParticleLight=true` explicitly (or fix `run/config/alacer.properties`) when the
run directory's config file may hold an old value. Nothing was committed or pushed.
