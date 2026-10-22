# Session 21 JFR attribution (detail off, scored windows, client thread)

`jfr_attribution.py RECORDING BENCH_DIR` assigns each client-thread execution sample inside
`ParticleManager.render` to the deepest matching category: light entry (`World.isChunkLoaded`
subtree or `ParticleLightEntry`), light entry position copy (`BlockPos`/`Vec3i` constructors), light
query (`ParticleLight`, `World.getLightColor`, `WorldChunk`), `getLightLevel` self, vertex writes,
draw, particle render self. `jfr_stacks.py` prints the stacks under a given leaf method.

## Baseline: shipped defaults (le21-jfr-1/2: session 20's shipped source, taken before any session 21 change)

| Scale / phase | Frame mean | Pass | Pass share | Light query | Entry | `getLightLevel` self | Light total | Alloc/frame |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 0.822 ms | 0.551 ms | 71.9% | 23.4% | 8.2% | 5.7% | 37.3% | 37 KB |
| 1 / pan | 1.652 ms | 0.822 ms | 44.9% | 19.4% | 11.5% | 9.4% | 40.3% | 151 KB |
| 2 / static | 1.302 ms | 0.878 ms | 68.8% | 23.8% | 7.9% | 8.7% | 40.4% | 91 KB |
| 2 / pan | 1.753 ms | 0.963 ms | 57.6% | 22.5% | 7.5% | 9.5% | 39.5% | 244 KB |

Every entry sample is `Long2ObjectHashMap.getNode <- contains <- ServerChunkCache.hasChunk <-
World.isChunkLoadedAt <- World.isChunkLoaded (x2) <- Entity.getLightLevel` (or
`BlockParticle.getLightLevel`), all frames `Inlined`. The particles' world is the client world (their
light queries all take optimization 7's `ClientWorld`-only fast path), whose chunk source is
`ClientChunkCache`; its `hasChunk` is `iconst_1; ireturn` in the runtime jar. Session 20's reading
("one chunk-source hash lookup per particle") took this attribution at face value.
In pan, the pass's sampled allocations are all `BlockPos <- Entity.getLightLevel`; static had none
sampled.

## Candidate: option on (le21-profile-1/2, frozen source, attribution only)

| Scale / phase | Frame mean | Pass | Pass share | Light query | Entry | `getLightLevel` self | Light total | Alloc/frame |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 0.898 ms | 0.604 ms | 69.5% | 27.0% | 0.2% | 9.3% | 36.5% | 40 KB |
| 1 / pan | 1.226 ms | 0.654 ms | 50.2% | 31.6% | 0.2% | 8.5% | 40.3% | 58 KB |
| 2 / static | 1.051 ms | 0.736 ms | 75.9% | 16.3% | 0.0% | 25.0% | 41.3% | 74 KB |
| 2 / pan | 1.505 ms | 0.841 ms | 57.0% | 33.5% | 0.2% | 8.9% | 42.6% | 109 KB |

(Cross-launch: frame and pass values are single launches, not a comparison.) The entry samples and
the pass's `BlockPos` allocations disappear, but the light share of the pass does not shrink: the
samples move into `ParticleLight.brightness`, `ParticleLight.fastLightColor`, the light redirect
handler and `getLightLevel` itself (how C2 inlines decides which of these frames is charged). The
measured pass time barely changed (comparison.md). The baseline's entry samples therefore did not
measure removable work: the client-side check costs little, and the time charged to it belongs to
the light query that follows (its chunk-section reads), which every variant still performs.

What remains in the pass: the light query (chunk section and nibble-array reads in
`ParticleLight.brightness`/`WorldChunk.getLight`, about 27-34% of the pass with the entry removed),
each particle class's own render code (28-31%), the `ParticleManager.render` loop (15-17%), vertex
writes (12-15%, mostly `ByteBuffer` index checks) and the draw (1-3%).
