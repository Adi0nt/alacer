# Session 20 profiles (attribution only)

JFR execution samples on the client thread, scoped to the scored static/pan windows (profiler
epoch plus CSV start times). Detail timers off; JFR is intrusive and never selection evidence.
`jfr_attribution.py` produced `profile-*.txt`. Categories inside the particle render pass take
the deepest matching frame: light (`getLightLevel`, `ParticleLight`, chunk light), BufferBuilder
(`BufferBuilder`, `VertexFormat`, `DirectByteBuffer`: with the option on these are the direct
writes' `ByteBuffer` puts), draw (`Tesselator.end`).

## Baseline: shipped defaults, pre-change source (pv20-jfr-1/2)

| Scale / phase | Frame | Pass | Pass share of client samples | Particle tick share | Entity.move share | BufferBuilder+VertexFormat in pass | Light in pass |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 1.039 ms | 0.718 ms | 73.9% | 2.8% | 2.5% | 53.1% | 31.6% |
| 1 / pan | 1.499 ms | 0.834 ms | 54.5% | 4.0% | 3.6% | 52.5% | 27.4% |
| 2 / static | 1.519 ms | 1.087 ms | 75.9% | 5.2% | 4.3% | 46.7% | 34.3% |
| 2 / pan | 3.811 ms | 1.830 ms | 48.7% | 8.6% | 6.1% | 38.5% | 30.7% |

Leaf frames: `Objects.checkIndex` (from `VertexFormat.getOffset`/`getElement` list access)
21-29% of the pass and `BufferBuilder.nextElement` 14-21%.

## Candidate on (pv20-profile-1/2, frozen source)

| Scale / phase | Frame | Pass | Pass share | Light in pass | Direct writes in pass | Draw in pass | Entity.move share |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 0.781 ms | 0.533 ms | 73.2% | 33.9% | 15.6% | 3.2% | 4.4% |
| 1 / pan | 1.138 ms | 0.616 ms | 50.7% | 39.9% | 14.1% | 2.3% | 2.8% |
| 2 / static | 0.984 ms | 0.694 ms | 76.8% | 42.6% | 11.6% | 3.4% | 4.1% |

(Scale 2 pan is in profile-candidate-2.txt.) `VertexFormat` and `nextElement` no longer appear.
Inside the light share, `Long2ObjectHashMap.getNode` (6.7-9.5% of the pass) comes from
`Entity.getLightLevel` -> `World.isChunkLoaded` -> the chunk source's `hasChunk` hash lookup,
made before the light query that optimization 7 answers from cached chunks (and again in
`BlockParticle.getLightLevel`). `Entity.getLightLevel` also allocates one `BlockPos` per particle
per frame (sampled as the render pass's dominant allocation in the baseline pan profile).
These are the next bounded, exact candidates in the particle pass.
