# Session 22 predeclared protocol: per-pass particle light reuse

Written after the attribution counter launches (lr22-count-1/2), one unscored smoke launch
(lr22-smoke-1, to confirm that the pass hooks apply and verification runs) and one deliberately
broken negative-control launch (lr22-negative-stale, from a temporary source that never empties the
table, reverted before this protocol), and before the source manifest is frozen and before any
scored verification or measured launch. Java 25 is explicit for Gradle, compiler/tests and launcher.
Shared config SHA-256 694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31 must be
unchanged (benchmark mode ignores it). All nine shipped optimizations stay on in every launch;
`render.fastParticleLightEntry`, the session 17-19 experiments and the frustum/zero-translate/
model-list experiments stay off; `profiler.particleLightRepeats` and the detail switches stay off.
Every render option, every `.verify` and the profiler switches are passed explicitly, and each
phase's exported config is validated.

## Attribution (shipped defaults plus `profiler.particleLightRepeats`, one launch per scale)

Per unlit `ParticleManager.render` pass, means over each phase's frames:

| Scale / phase | Particles rendered | Light queries | Distinct (x, y, z, blockLight) | Distinct positions | Repeated queries | Same as previous query |
|---|---:|---:|---:|---:|---:|---:|
| 1 / static | 3,308 | 3,912 | 395.2 | 395.2 | 89.9% | 6.0% |
| 1 / pan | 3,307 | 3,914 | 395.4 | 395.4 | 89.9% | 5.9% |
| 2 / static | 5,134 | 6,358 | 416.3 | 416.3 | 93.5% | 7.3% |
| 2 / pan | 5,130 | 6,352 | 415.4 | 415.4 | 93.5% | 7.2% |

The counter costs two hash-set inserts per query, so these launches are attribution only. Over the
whole launches the largest pass made 4,050 / 6,466 queries and hit 447 / 452 distinct keys; no query
came outside the unlit pass and no pass overflowed the counter's tables. `blockLight` was always 0
(both vanilla call sites pass 0). Only 6-7% of queries repeat the immediately preceding one, so a
one-entry memo would not capture the repetition; a per-pass table would. Session 21's profiles put
the light query at 27-34% (entry removed) to 36-43% (with the entry) of the pass.

## Why a per-pass answer is exact (decompiled 1.8.9)

`Minecraft.runGame` drains the client task queue (every world packet handler is posted there by
`PacketUtils.ensureOnSameThread`), runs the client ticks (world and light updates, chunk loads and
unloads, `setWorld`), then renders. `GameRenderer.renderWorldPass` calls `ParticleManager.renderLit`
then `ParticleManager.render`; the unlit pass only calls each particle's `render`, whose light
comes from `getLightLevel`. The five handlers that run on the network thread (disconnect,
compression, tab list, keep-alive, resource pack) do not touch the world; chunk-build workers read
through `RenderChunkRegion`; the integrated server has its own worlds. `WorldChunk.getBlockState`
and `getLight` are pure reads. So between the pass's head and return, optimization 7's answer for
a given world and (x, y, z) cannot change.

## Candidate

`render.reuseParticleLight` (runtime-toggleable, `.verify`, startup-gated `ParticleManagerLightReuseMixin`:
`@Inject` at `render` head and return and at `renderLit` head; the light query itself is
optimization 7's existing `@Redirect`). While a pass is open for the manager's world (option on,
optimization 7 on and not verifying, exact `ClientWorld`, client thread), queries that optimization
7 answers and whose world is that world are answered from an open-addressing table of 4,096 slots
keyed by (x, y, z), holding the sky and block brightness optimization 7 computed for that position
earlier in the pass; a new position is computed by optimization 7's code (sky, then block) and stored;
the requested minimum block light is applied after the lookup. The table is emptied by a generation
stamp at every pass head, closed at the pass's return, at the lit pass's head and on
`WorldRenderer.setWorld`; past 3,072 positions new ones are computed without being stored. When the
hooks are absent a startup constant folds the check away. No allocation per query or frame, no GL
objects, no third-party performance-mod source.

## Correctness gates (before any selection)

- Unit tests: the table against a `HashMap` over 200 passes of 4,000 random lookups (including
  out-of-world y and large x/z), its emptying per pass and its full limit; startup gating of the
  hooks; config default, toggle and preset.
- Live verification (`render.reuseParticleLight.verify`, option on, at scale 2 and scale 1 with
  `benchmark.particleDiagnostic` and `benchmark.particleRemovalDiagnostic`; the first cycles the blocks
  under the emitters through glowstone, slab and air between passes): vanilla's
  `World.getLightColor` runs for every query the table answers (reused or computed) and is used.
  **0 mismatches** over at least 10 million compared queries per launch, at least 5 million of them
  reused; optimization 7's and the light entry's final lines must show 0 mismatches. The negative
  control (table never emptied) must have produced mismatches; it did (51.5 million of 82.4 million,
  development evidence, not a scored launch).
- Lifecycle: nothing survives a pass (stamp at head, closed at return, lit head and world change);
  render distance, resource reload and backend changes do not interact (no GL objects, no
  chunk-section references kept across passes beyond optimization 7's own cache).
- Ordering: particle lists, order, render calls, vertex data and the draw are untouched; only how
  the light integer is obtained changes. Particles rendered must match between A and B (gate 7).
- Reported limits: skyless dimensions are not exercised live; another mod that changes the client
  world from inside particle rendering, or another `@Redirect` of the same light calls, would break
  the assumption or conflict.

## Performance protocol

Eight fresh copied-world launches, four per particle scale 1/2, alternating scale order by round,
`benchmark.toggle=render.reuseParticleLight` (A off with hooks installed, B on), both paths warmed,
static and pan A1/B1/B2/A2, then eight startup launches (per scale absent/on/on/absent, scale order
1, 2, 2, 1; `absent` = option false, no toggle, hooks not installed; `on` = option true at startup, no
toggle). Java 25, 1920x1080, display lists, view distance 12. No detail timers, counter, JFR or
verification in scored launches; no builds or analysis competing with launches. A source-hash
manifest is frozen before the first scored verification launch and checked before and after every
launch. All launches and failures are retained.

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
the next roadmap item. Two candidate JFR profiles (option on, scale 1/2) may be recorded after the
measured launches for attribution only.
