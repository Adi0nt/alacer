# Session 21 predeclared protocol: particle light entry

Written after the fresh Java 25 attribution (le21-jfr-1/2, `profile-baseline-*.txt`) and the
implementation's two unscored smoke launches (le21-smoke-1/2, used only to confirm that the
redirects apply), before the source manifest is frozen and before any verification or measured
launch. Java 25 is explicit for Gradle, compiler/tests and launcher. Shared config SHA-256
694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31 must be unchanged (benchmark mode
ignores it). All nine shipped optimizations stay on in every launch; Sessions 17-19 experiments and
the frustum/zero-translate/model-list experiments stay off. Every render option, every `.verify` and
the profiler detail switches are passed explicitly, and each phase's exported config is validated.

## Attribution (shipped defaults, JFR, detail off, scored windows)

| Scale / phase | Frame mean | Particle pass | Pass share of client samples | Light query | Light entry (`isChunkLoaded`) | `getLightLevel` self | Pass alloc/frame |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 0.822 ms | 0.551 ms | 71.9% | 23.4% | 8.2% | 5.7% | 37 KB |
| 1 / pan | 1.652 ms | 0.822 ms | 44.9% | 19.4% | 11.5% | 9.4% | 151 KB |
| 2 / static | 1.302 ms | 0.878 ms | 68.8% | 23.8% | 7.9% | 8.7% | 91 KB |
| 2 / pan | 1.753 ms | 0.963 ms | 57.6% | 22.5% | 7.5% | 9.5% | 244 KB |

(Shares are of the pass's samples; alloc/frame is the whole frame's client-thread allocation.)
Every entry sample is `Long2ObjectHashMap.getNode <- contains <- ServerChunkCache.hasChunk <-
World.isChunkLoadedAt <- World.isChunkLoaded <- Entity.getLightLevel` (and `BlockParticle.getLightLevel`),
all inlined. The client world's chunk source is `ClientChunkCache`, whose `hasChunk` is a constant
`true` in the runtime jar (checked with javap), so the hash lookup cannot be the client's own answer:
`World.isChunkLoadedAt` is shared with the integrated server's worlds, and its compiled form inside
`getLightLevel` carries the server's receiver. Whatever the exact JIT shape, the samples are spent
in that entry. In pan, every sampled allocation inside the pass is `getLightLevel`'s `BlockPos`; in
static none is sampled (escape analysis appears to remove it in that compilation), so the
allocation effect is expected to differ by phase.

## Candidate

`render.fastParticleLightEntry` (runtime-toggleable, `.verify`, startup-gated mixins
`EntityParticleLightEntryMixin` and `BlockParticleLightEntryMixin`). For particles in the exact
`ClientWorld` class whose chunk source is exactly `ClientChunkCache`, on the client thread captured
at startup, the `World.isChunkLoaded(pos)` calls in `Entity.getLightLevel` and
`BlockParticle.getLightLevel` are answered with `World.contains` on the same coordinates (x and z in
[-30,000,000, 30,000,000), y in [0, 256)); `ClientChunkCache.hasChunk` is `true` and vanilla passes
`allowEmpty = true`, so that is vanilla's whole answer. The `new BlockPos(x, y + eyeHeight, z)` in
`Entity.getLightLevel` is replaced by one reused mutable position set to the same floored
coordinates, only when this option answers the check and optimization 7 answers the light query
(both on, neither verifying); otherwise vanilla's position is allocated. Every other entity, world
or thread takes vanilla's calls. Nothing is cached; no GL objects; no third-party performance-mod
source is used. Both hooks are `@Redirect`s (one call each per particle per frame).

## Correctness gates (before any selection)

- Unit tests: the reused position's coordinates equal `new BlockPos(x, y, z)`'s for edge doubles
  (signed zero, NaN, infinities, subnormals, integer boundaries, +-30,000,000, int overflow) and
  2,000,000 random/bit-pattern triples; the range test's boundaries; startup gating of the hooks.
- Live verification (`render.fastParticleLightEntry.verify`, option on, at scale 2 and scale 1 with
  `benchmark.particleDiagnostic` and `benchmark.particleRemovalDiagnostic`, which also emit
  particles above the build height and below y = 0): vanilla's position is kept, the reused
  position's coordinates are compared with it, vanilla's `isChunkLoaded` runs for every answered
  check and its result is used. **0 mismatches** over at least 10 million checks (some of them not
  loaded) and 10 million positions per launch; optimization 7's line must show 0 mismatches.
- Ordering/lifecycle: particle lists, order, render calls and draw are untouched; the only state is
  the owner thread and one mutable position overwritten per call, so world change, resource reload,
  render distance and backend changes do not interact. Reported limits: the reused-position path
  itself is not executed in verify mode (its two readers are this option's range test and
  optimization 7's fast path, which reads the coordinates once); other mods' `@Redirect` of the same
  calls would conflict, and another mod's injection that captures `getLightLevel`'s position local
  would see a reused object; the +-30,000,000 limits are not exercised live.

## Performance protocol

Eight fresh copied-world launches, four per particle scale 1/2, alternating scale order by round,
`benchmark.toggle=render.fastParticleLightEntry` (A off with hooks installed, B on), both paths
warmed, static and pan A1/B1/B2/A2, then eight startup launches (per scale absent/on/on/absent,
scale order 1, 2, 2, 1; `absent` = option false, no toggle, hooks not installed; `on` = option true
at startup, no toggle). Java 25, 1920x1080, display lists, view distance 12. No detail timers, JFR
or verification in scored launches; no builds or analysis competing with launches. A source-hash
manifest is frozen before the first verification launch and checked before and after every launch.
All launches and failures are retained.

Per A/B group (scale x phase), B against pooled A1+A2, gates:

1. Particle pass mean lower in at least 3 of 4 launches.
2. Group median particle-pass saving >= 3%.
3. Group median ordinary frame-mean saving >= 1%.
4. Group median client CPU/frame not higher (<= 0%).
5. Group median allocation/frame regression <= 2%.
6. Group median frame p95 and p99 regression <= 5%; tick-frame mean and p99 regression <= 5%.
7. Particles rendered within 2% between A and B.
8. Control drift: a launch with |A2/A1 - 1| frame mean > 10% is flagged; if a group's median drift
   exceeds 10% the group is noisy and additionally requires B's particle pass to beat A1 and A2
   separately in at least 3 of 4 launches.

Startup gates (cross-launch, per scale x phase, medians of the absent and on launches): startup-on
particle pass and frame mean lower than startup-absent, CPU/frame not higher, allocation/frame not
higher by more than 2%.

Selection: all four A/B groups and all four startup comparisons passing selects the option on by
default and into `render.defaults` (with `AlacerConfigTest` and the plugin test updated). Anything
else keeps it off by default as a retained experiment, with the evidence and a recommendation for
the next roadmap item. Two candidate JFR profiles (option on, scale 1/2) are recorded after the
measured launches for attribution only.
