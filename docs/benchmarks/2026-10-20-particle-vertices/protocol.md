# Session 20 predeclared protocol: particle vertex writing

Written after the fresh Java 25 attribution (pv20-jfr-1/2, source snapshot
`sources-35f26964345f80d8.zip`, identical to Session 19's final source) and before any
implementation or measured launch. Java 25 is explicit for Gradle, compiler/tests and launcher.
Shared config SHA-256 694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31 must be
unchanged (benchmark mode ignores it). All eight shipped optimizations stay on in every launch;
Sessions 17-19 experiments and frustum/zero-translate/model-list experiments stay off.

## Why this item instead of particle movement

Non-intrusive JFR (detail off) of the shipped defaults, scored windows only:

| Scale / phase | Frame mean | Particle pass | Render pass share of client samples | Particle tick share | `Entity.move` share |
|---|---:|---:|---:|---:|---:|
| 1 / static | 1.039 ms | 0.718 ms | 73.9% | 2.8% | 2.5% |
| 1 / pan | 1.499 ms | 0.834 ms | 54.5% | 4.0% | 3.6% |
| 2 / static | 1.519 ms | 1.087 ms | 75.9% | 5.2% | 4.3% |
| 2 / pan | 3.811 ms | 1.830 ms | 48.7% | 8.6% | 6.1% |

Inside the unlit particle pass, `BufferBuilder`/`VertexFormat` element bookkeeping is
38.7-53.1% of samples (`Objects.checkIndex` under `VertexFormat.getOffset`'s `List<Integer>`,
`BufferBuilder.nextElement`), light queries 27.4-34.3%, the draw under 1.1%. Movement runs once
per client tick (20/s) while the pass runs every frame (700-1,000/s here), so vertex writing is
the larger and broader cost. Roadmap item 1 (movement) is deferred, not rebranded.

## Candidate

`render.fastParticleVertices` (runtime-toggleable, `.verify`, startup-gated mixins). Inside
`Particle.render` (also reached by every `super.render` caller: smoke, flame, redstone,
explosion, cloud, crit, ...) and `BlockParticle.render`, the four per-vertex element calls
(`vertex(DDD)`, `texture(DD)`, `color(FFFF)`, `texture(II)`) are redirected. When the buffer's
format is the `DefaultVertexFormat.PARTICLE` object, its layout was confirmed at the start of
the pass (POSITION FLOAT3 @0, UV0 FLOAT2 @12, COLOR UBYTE4 @20, UV1 SHORT2 @24, 28 bytes), the
element index is 0, the buffer is not `uncolored`, the option is on and the call is on the
client thread, each element is written with exactly the primitive `ByteBuffer` puts that
vanilla's switch branch for that element type performs, at the constant offset, without the
per-element `getOffset`/type switch/`nextElement` bookkeeping. `nextVertex` (vertex count and
`grow`) stays vanilla. Every other case calls vanilla's method. Each particle's own render
method, sizes, UVs, `getLightLevel`, particle order, lists, the pass and the draw are unchanged.
No data is retained across frames except the per-pass layout flag and the owner thread; no GL
objects. No third-party performance-mod source is used.

## Correctness gates (before any selection)

- Unit tests: the pure writer against a real vanilla `BufferBuilder` for random and edge inputs
  (signed zero, NaN, infinities, subnormals, out-of-range colors and light, nonzero offsets),
  byte for byte; layout check rejects altered copies of the format.
- Live verification (`render.fastParticleVertices.verify`, scale 2 with
  `benchmark.particleDiagnostic` and `benchmark.particleRemovalDiagnostic`): vanilla writes
  every element, the candidate's bytes for the same element are compared with the bytes vanilla
  wrote; **0 mismatches** over at least 10 million compared elements, every verified vertex has
  all four elements in order, fallback/layout counts reported.
- Ordering/lifecycle: no reordering is possible (the iteration is vanilla); world change, resource
  reload, render distance and backend changes do not interact (no retained geometry). Reported as
  limits: other mods' `@Redirect` of the same four calls would conflict; mods that mutate the
  `PARTICLE` format mid-pass; non-vanilla particle render methods (they keep vanilla's path
  unless they call `super.render`).

## Performance protocol

Eight fresh copied-world launches, four per particle scale 1/2, alternating scale order by round,
`benchmark.toggle=render.fastParticleVertices` (A off, B on), both paths warmed, static and pan
A1/B1/B2/A2. Java 25, 1920x1080, display lists, view distance 12, every render option passed
explicitly. No detail timers, JFR or verification in scored launches; no builds or analysis
competing with launches. A source-hash manifest is frozen before the first measured launch and
checked after each. All launches and failures are retained.

Supplementary (reported, not gates): one startup-absent launch per scale (option false, no toggle,
hooks not installed) to compare against the A segments across launches.

Per group (scale x phase), B against pooled A1+A2, gates:

1. Particle pass mean (`particles` section) lower in every launch.
2. Group median particle-pass saving >= 5%.
3. Group median ordinary frame-mean saving >= 2%.
4. Group median client CPU/frame not higher (<= 0%).
5. Group median allocation/frame regression <= 2%.
6. Group median frame p95 and p99 regression <= 5%; tick-frame mean and p99 regression <= 5%.
7. Particles rendered within 2% between A and B.
8. Control drift: a launch with |A2/A1 - 1| frame mean > 10% is flagged; if a group's median
   drift exceeds 10% the group is noisy and additionally requires B's particle pass to beat A1
   and A2 separately in every launch.

All four groups passing selects the option on by default (and into `render.defaults`, with
`AlacerConfigTest` updated). Any failed group keeps it off by default, retained as an experiment.

## Addendum (written after the eight A/B/B/A controls were analyzed, before further launches)

All four predeclared groups passed. The two startup-absent supplementary launches put the
scale-2 particle pass about 20% below the A segments of the A/B launches (cross-launch, so
confounded by drift), which suggests the installed-but-off hooks slow vanilla's path and the
in-process A/B overstates the saving against a client without them. Because the real choice is
"option on at startup" versus "option off, hooks absent", eight more fresh launches compare
exactly that: per scale, absent / on / on / absent (`startup-absent`: option false, no toggle;
`startup-on`: option true, no toggle), scale order alternating. Static and pan are recorded
once each per launch, with the same explicit options. Selecting the option on by default
additionally requires, for every scale/phase, the median startup-on frame mean and particle
pass to be lower than the median startup-absent ones (including the two earlier absent
launches), and median startup-on CPU/frame not higher. Reported with their cross-launch limits.
