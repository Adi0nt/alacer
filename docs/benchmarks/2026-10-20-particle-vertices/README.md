# Session 20: particle vertex writing (`render.fastParticleVertices`)

Fresh Java 25 profiling of the shipped defaults showed that the particle **render pass**, not
particle movement, dominates the particle scenario: 49-76% of client-thread samples, against
3-9% for the whole particle tick (`Entity.move` 2.5-6.1%). The pass runs every frame (700-1,000
per second here), the tick 20 times per second. Inside the pass, `BufferBuilder`'s per-element
bookkeeping (`VertexFormat.getOffset`'s `List<Integer>` bounds check and unboxing, the element
type switch, `nextElement`) was 39-53% of samples, light queries 27-34%, the draw about 1%.
Roadmap item 1 (movement) was therefore deferred; Sessions 17-19's experiments are untouched.

`render.fastParticleVertices` redirects the four element calls of each vertex inside
`Particle.render` (reached by every `super.render` caller) and `BlockParticle.render`. For the
`DefaultVertexFormat.PARTICLE` object, with its layout confirmed at the start of every pass, at
element index 0, not `uncolored`, on the client thread, each element is written with exactly the
`ByteBuffer` puts of vanilla's branch for its type at its fixed offset. `nextVertex`, every
particle's own render code (sizes, UVs, alpha, `getLightLevel`), particle order and the draw stay
vanilla. Anything else calls vanilla's method. See ARCHITECTURE.md, optimization 9.

## Decision: on by default, in `render.defaults`

All four predeclared scale/phase groups passed every gate, and the addendum comparing startup-on
with startup-absent launches passed as well. `final-default.diff` changes the default to true
and adds the option to `render.defaults` (now nine optimizations); the measured source is the
frozen `sources-21fb3f8c163ea5c9.zip` (default false, toggled by the benchmark).

| Scale / phase | Particle pass | Frame mean | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Median A1/A2 drift | Gates |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | -24.70% | -20.41% | -21.57% | -19.20% | -13.39% | -9.36% | -6.31% | -11.01% | 10.66% (noisy) | PASS |
| 1 / pan | -31.21% | -25.93% | -26.98% | -11.13% | -33.34% | -16.30% | -11.62% | -24.83% | 10.26% (noisy) | PASS |
| 2 / static | -37.25% | -32.73% | -33.00% | -31.98% | -30.51% | -14.99% | -10.68% | -12.82% | 4.02% | PASS |
| 2 / pan | -34.38% | -28.52% | -28.80% | -14.67% | -27.34% | -19.57% | -16.24% | -28.98% | 7.16% | PASS |

Medians of per-launch B-vs-pooled-A changes, eight warmed in-process A/B/B/A launches (four per
scale, scale order alternating by round). The particle pass was lower in 16/16 comparisons
(-6.8% to -42.2%) and frame mean in 15/16. Excluding the draw call, the pass fell 39.5-43.2%
(group medians), for example 0.653 -> 0.379 ms (scale 1 static) and 1.089 -> 0.610 ms (scale 2
static). Scale-1 groups were flagged noisy (median A1/A2 drift above 10%) and also passed the
stricter check that B's pass beat A1 and A2 separately in every launch.

**Regressions and noise, not excluded.** `pv20-ab-1-r4` static: frame mean +2.87%, frame p95
+22%, p99 +20%, tick-frame p99 +213%; its B2 segment had a 32 ms frame and a slower segment
overall (pass still -6.8%). Tick-frame p99 regressed in one launch per group (up to +39% outside
r4). Particle **draw** time (`Tesselator.end`) rose at scale 1 (median 0.098 -> 0.186 ms static,
0.160 -> 0.207 pan) and barely changed at scale 2: part of the saved client time reappears as
waiting in the next call that synchronizes with the driver. The driver mode was not changed or
inferred. Allocation per frame fell because frames are shorter; allocation per **second** rose
9-33% in pan (more frames, each with vanilla's per-frame allocations) and stayed within 2% in
static except one launch (+24.7%). Particle populations matched within 0.51%.

### Addendum: startup-on versus hooks absent

The A segments run vanilla's writes **through the installed hooks**. Two supplementary
startup-absent launches suggested that path is slower than a client without the hooks (scale 2
static: absent pass 0.939 ms vs 1.164 ms median A), so the in-process A/B can overstate the
saving against a default-off client, whose hooks are absent. A declared addendum (protocol.md)
ran eight more fresh launches, per scale absent/on/on/absent with no toggle:

| Scale / phase | Launches absent/on | Frame mean | Particle pass | CPU/frame | Frame p95 | Frame p99 | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| 1 / static | 3/2 | 1.000 -> 0.765 ms (-23.5%) | 0.748 -> 0.523 ms (-30.2%) | -24.2% | -14.8% | -5.9% | PASS |
| 1 / pan | 3/2 | 1.693 -> 1.126 ms (-33.5%) | 0.922 -> 0.612 ms (-33.6%) | -35.0% | -42.6% | -27.8% | PASS |
| 2 / static | 3/2 | 1.251 -> 0.953 ms (-23.8%) | 0.939 -> 0.679 ms (-27.6%) | -24.4% | -28.3% | -11.1% | PASS |
| 2 / pan | 3/2 | 1.746 -> 1.308 ms (-25.1%) | 1.063 -> 0.760 ms (-28.5%) | -25.2% | -26.9% | -15.3% | PASS |

Cross-launch medians of few launches; launch-to-launch variance is large. In the first scale-1
pair, the absent client's vanilla path ran unusually fast (pass 0.583 ms) and the on launch was
not faster in frame mean (static 0.799 vs 0.815 ms, pass 0.583 vs 0.551 ms). Scale-1 static
tick-frame p99 was higher (+25%), and median scale-2 static allocation per frame was higher
(+42%: one absent launch allocated much less), so neither tails nor allocation are claimed
across launches. Full per-launch values are in comparison.md.

## Correctness

- **Unit tests** (70 total, all pass): the writer against a real vanilla `BufferBuilder` for
  24,000 quads of random and edge values (signed zero, NaN, infinities, subnormals, out-of-range
  colors and light coordinates, nonzero and NaN buffer offsets) at a nonzero vertex, byte for
  byte; the layout check rejects reordered, padded and other formats; startup-gating tests.
- **Live verification** (`pv20-check-2`, frozen source, scale 2 with the light and removal
  diagnostics: light-changing blocks, particles above/below the build height, 7,235 probe
  particles that change layers while ticking, overfill lists, share ids, appear twice or override
  `equals`): **950,264,304 elements compared, 0 mismatched**, 237,566,076 complete vertices (all
  four elements in order), 0 incomplete, 0 vanilla-path vertices, 0/16,457 passes with another
  layout. A pre-freeze pilot (`pv20-pilot-verify`, scale 1) compared 1,139,892,640 elements, 0
  mismatched (supplementary).
- **Ordering, lifecycle, ownership.** No particle, list or draw order changes: only bytes inside
  an element are written differently. Nothing is retained between frames except the per-pass
  layout flag and the owner thread; no geometry, no world/chunk references, no GL objects, so
  world change, resource reload, render distance and backend changes do not interact.
- See verification.md for the verification design and limits.

**Limits.** Byte identity of the vertex buffer is verified; framebuffer identity was not
compared (A and B draw different random particle motion). Another mod's `@Redirect` of the same
four calls would conflict (injections around them compose). Particle classes with their own
render methods that do not call `super.render` (barrier, firework overlay, large explosion,
item pickup, ...) keep vanilla's element path. A mod that changes the `PARTICLE` format object
in the middle of a pass is not detected until the next pass; a change between passes is (logged
once; vanilla's path is used). Skyless dimensions and multiplayer were not exercised.

## Remaining costs (profiles with the option on)

`pv20-profile-1/2` (JFR, attribution only): the pass is 0.533/0.616 ms (scale 1 static/pan) and
0.694/0.797 ms (scale 2). Light queries are now the largest part: 34-43% of pass samples. Of
those, 7-10% of the pass is one chunk-source hash lookup per particle in `World.isChunkLoaded`,
called by `Entity.getLightLevel` (and again by `BlockParticle.getLightLevel`) before the light
query that optimization 7 already answers from cached chunks; `Entity.getLightLevel` also
allocates a `BlockPos` per particle per frame. The direct writes themselves are now 12-16%
(`ByteBuffer` index checks). Movement remains 2.8-4.4% of client samples.

## Evidence and reproduction

- [Protocol and addendum](protocol.md) (predeclared before implementation / before the
  addendum launches), [measured results](comparison.md), [tails, CPU and allocation](tails.md),
  [metrics.json](metrics.json), [verification](verification.md), [profiles](profile.md).
- `manifest.json` freezes the source hashes before the first measured launch; every record is
  checked against it and its ZIP snapshot. `sources-35f26964345f80d8.zip` is the pre-change
  source (identical to Session 19's final source) on which the baseline profiles ran.
- `runs/` holds every measured record and summary; `attempts/` the commands; raw CSV/JFR stay in
  ignored `run/`, logs in ignored `build/` (hashes in the records and
  `supplementary-records.json`).
- `final-default.diff`: the post-selection default change.
- `validation.json`: final build/test result (70 tests), shared-config hash, measured record list.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_vertices.py freeze --prefix fresh --archive docs/benchmarks/fresh-vertices
python tools/run_particle_vertices.py diagnostics --prefix fresh --archive docs/benchmarks/fresh-vertices
python tools/run_particle_vertices.py controls --prefix fresh --archive docs/benchmarks/fresh-vertices
python tools/run_particle_vertices.py startup --prefix fresh --archive docs/benchmarks/fresh-vertices
python docs/benchmarks/2026-10-20-particle-vertices/analyze.py --prefix fresh --archive docs/benchmarks/fresh-vertices
```

With the default now true, `controls` still toggles A off/B on in-process; `startup` passes the
option explicitly in both kinds. Nothing was committed, pushed or published; no driver settings,
shared config or historical archives changed.
