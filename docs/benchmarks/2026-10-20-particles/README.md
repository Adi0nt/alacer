# Particles: scenario, attribution and particle light queries (session 15)

Roadmap item 1 after the Java 25 migration. All launches are Java 25 (Temurin 25.0.4.1, G1),
the same machine and settings as [the Java 25 archive](../2026-10-20-java25/README.md): 1920x1080,
render distance 12, fancy graphics and clouds, smooth lighting, VBOs off, vsync off, unlimited FPS,
seed-1789 copied world, NVIDIA driver settings unchanged. Nothing here is pooled with Java 8 data.

## Scenario (`benchmark.particles=true`)

An opt-in benchmark mode; the ordinary static/pan/fly phases are unchanged.

- **Emitters:** 16 stationary points on rings 4-11.5 blocks around the camera anchor, at
  0.5/1.25/2 blocks above each point's terrain in the copied world (logged and exported as
  `benchmark.particleEmitters`; identical in every launch here).
- **Schedule, per client tick** (20 per second), emitted at the start of the tick, before the
  world and particle manager tick (where vanilla's own sources emit): one 64-particle stone
  block break (`addBlockMiningParticles`), 48 smoke, 32 flame, 48 redstone dust, 16 small
  explosion (poof) and 2 large explosion particles (the lit pass, one draw each), times
  `benchmark.particleScale` (1-8). Emitter choice is round-robin; position jitter, poof
  velocities and explosion sizes come from a generator seeded from the world seed, so every
  launch emits the same schedule. Each particle's lifetime and motion use vanilla's unseeded
  randomness; the per-frame counters below show the resulting load.
- **Visual quality fixed:** Particles option All (recorded per phase), every other video option
  as in the Java 25 baselines. No mobs or chests are added (the copied world holds eight
  animals; the client sees 9 entities including the player, 10 in every phase of `pt-opt-1-r1`).
- **Camera:** `static` (10 s) and `pan` (15 s, one turn per 7.5 s) at the anchor, or the usual
  warmed A/B/B/A segments with a toggle. No flight: it would leave the emitters behind. The
  particle scenario settles at least 5 s between segments so the population returns to steady
  state after `benchmark.disableParticles` (the longest lifetimes are a few seconds).
- **Load:** scale 1 keeps about 3,300 particles alive and drawn per frame, 3,500 ticked per
  client tick, about 4,200 removed per second. Scale 2 reaches about 5,130: the misc-atlas list
  hits vanilla's 4,000-per-list cap, so `ParticleManager.add` drops the oldest particle.

Counters added to every profile (always on, a few list sizes per frame and tick):
`particles_rendered` (list sizes at the start of each render pass), `particles_ticked` (all lists
when the manager ticks) and `particles_spawned` (scenario only). New coarse sections:
`particle_tick` (`ParticleManager.tick`, inside `tick`), `particle_draw` (the unlit pass's
`Tesselator.end` calls, inside `particles`) and `particle_spawn` (scenario emission, inside `tick`).
`profiler.particleDetail` (startup only, intrusive) adds `particle_update` (each `tickParticle`),
`particle_move` (`Entity.move` called by particles), `particle_remove` (the dead-particle
`removeAll`), `particle_light` (`Entity.getLightLevel` for particles while drawing) and the
`particles_removed` count.

## Attribution (entity and particle detail off unless stated)

The attribution launches (`pt-attr-r1`, `pt-attr-r2` at scale 1, `pt-attr2-r1` at scale 2,
`pt-detail-r1`, `pt-jfr-r1`) ran the attribution build (`attribution_source_sha256`), before the
light redirect existed. `benchmark.toggle=benchmark.disableParticles` stops the schedule and clears
every particle in B (vanilla's `setWorld` clear); it changes the image and is not an option.

| Scale | Phase | Frame reduction with particles off, median (range) | Particle pass in A | Client CPU saved | Allocation saved | p99 A -> B |
|---|---|---:|---:|---:|---:|---:|
| 1 (2 launches) | static | 59.3% (59.2 to 59.3%) | 0.96-0.98 ms | 1.08 ms | 252 KB/frame | 4.6-5.2 -> 1.3 ms |
| 1 | pan | 52.9% (52.9 to 52.9%) | 1.05-1.07 ms | 1.19 ms | 268 KB/frame | 5.4-5.8 -> 2.2-2.7 ms |
| 2 (1 launch) | static | 77.6% | 1.57 ms | 1.85 ms | 471 KB/frame | 7.2 -> 1.1 ms |
| 2 | pan | 71.5% | 1.64 ms | 2.03 ms | 501 KB/frame | 8.1 -> 2.0 ms |

Without particles the frame is bounded elsewhere: `swap` rises from about 0.03 to 0.29-0.37 ms in B.
At scale 1, frames that run a client tick average 4.6-5.6 ms with particles and 1.2-2.3 ms
without, so the tick side mostly shows in the tails.

**Where it goes.** JFR (`pt-jfr-r1`, 3,376 client-thread execution samples, `jfr-summary.txt`):
the unlit particle pass is 63.8% of client-thread samples. Inside it, the light queries
(`Entity.getLightLevel` and block particles' second query, through `World.getLightColor`) are
27.5% of all client samples, and the vertex building (`BufferBuilder`) 22.0%; `Tesselator.end` is
0.6%. The top self frames are the client chunk cache's hash map (`Long2ObjectHashMap.get/getNode`,
28.1%; 83% of those samples are inside the light queries, 95% inside the particle pass) and
`Objects.checkIndex` (10.8%, all from `ArrayList.get` in the pass's per-particle loop). The
particle tick is 4.8% (`Entity.move` 2.4%, `removeAll` 2.2%). Of sampled client allocation,
the light queries are 56% (`BlockPos.offset` 38%: vanilla copies five neighbor positions for
neighbor-lit blocks such as tall grass and slabs) and particle movement 16%.

The intrusive detail run (`pt-detail-r1`) agrees: light 0.44-0.45 ms/frame (including two timer
calls per particle) of a 1.2-1.3 ms particle pass; per client tick about 2.7 ms of particle
ticking, of which updates 1.6 ms (movement and collision 1.4 ms) and the dead-particle `removeAll`
0.9 ms (vanilla's `ArrayList.removeAll` with an `ArrayList` argument compares each live particle with
every dead one, about 3,500 x 210 `Entity.equals` calls per tick here), emission 0.15 ms.

**Target selected:** the per-frame particle light queries, a bounded part (about a quarter of
client CPU in this scene) whose answer can be computed exactly. Vertex building would need
replacing every particle class's render method; the tick-side `removeAll` is a smaller, per-tick
cost left as a follow-up (see BENCHMARKS session 15).

## Optimization 7: `render.fastParticleLight`

Design and lifecycle: [ARCHITECTURE.md](../../ARCHITECTURE.md#optimization-7-particle-light-queries).
Two `@Redirect`s replace `World.getLightColor` in `Entity.getLightLevel` (particles only) and in
`BlockParticle.getLightLevel`. For particles in the exact `ClientWorld` on the client thread, the
same answer is computed with vanilla's range checks, clamp, `hasChunk`, chunk methods and order,
with chunks from a 16-entry reference cache (validated by the client load/unload generation) and
one reused position for neighbors. Block states and light are read on every query.

**Verification:** 66,729,546 queries compared, 0 mismatches (`verification.md`), with blocks under
the emitters cycling glowstone/slab/air, particles above the build limit and below y = 0.

**Measured series:** six warmed in-process A/B/B/A launches, `pt-opt-{1,2}-r{1,2,3}` (scale 1 and 2,
order alternating by round, with `pt-attr2-r1` between rounds 1 and 2). The frozen build hashes
are in `implementation.json`; all launches pass `analyze.py`'s checks (full durations, one
discarded setup frame, no dropped frames, configuration per phase, particle counts, zero detail
columns, no GL errors, crashes or verification lines, identical emitters/anchor/JVM/GPU/mods).

| Scale | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median | ms saved | Particle pass saved, median (range) | B faster than A / A2 |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | static | 23.4% (23.3 to 28.2%) | 24.2% | +30.6% | 0.283 | 0.256 (0.251 to 0.313) ms | 3/3 / 3/3 |
| 1 | pan | 19.8% (18.9 to 21.2%) | 18.8% | +24.7% | 0.301 | 0.259 (0.251 to 0.286) ms | 3/3 / 3/3 |
| 2 | static | 30.9% (12.4 to 33.2%) | 29.8% | +44.7% | 0.592 | 0.496 (0.338 to 0.564) ms | 3/3 / 3/3 |
| 2 | pan | 25.5% (12.7 to 28.1%) | 23.5% | +34.2% | 0.570 | 0.473 (0.362 to 0.489) ms | 3/3 / 3/3 |

- **Every pair:** particle pass, client CPU per frame and allocation lower in 12/12; allocation
  falls by about half (scale 1: 245-270 -> 118-138 KB/frame; scale 2: 442-486 -> 198-253).
- **Tails:** p99 lower in 10/12 and 1% low higher in 10/12. The exception is `pt-opt-2-r1`, the
  first dense launch: frame time fell only 12.4/12.7%, its p99, maximum and tick-frame mean rose in
  B, and its particle tick did not change, unlike the other five launches. It is reported as
  measured; no cause is assigned.
- The particle tick section was 15-35% lower in B in five of six launches although the option
  does not touch ticking; lower allocation (fewer young collections) is a plausible but untested
  explanation, so the frame-time medians may include more than the light queries themselves.
- `swap` did not absorb the saving here (it stayed at 0.025-0.05 ms): this scene is client-thread
  bound. `particle_draw` changed by -0.03 to +0.016 ms.

Remaining: the particle pass still costs 0.65-0.80 ms per frame at scale 1 (vertex building, the
light queries' chunk reads and one `BlockPos` per particle that vanilla allocates before the
query), and 118-138 KB/frame of allocation.

**Decision: `render.fastParticleLight=true` by default**, and the `render.defaults` preset switches
it (seven optimizations). It is exact, verified, and lowered frame time, client CPU and allocation
in every measured pair at both densities. Only `AlacerConfig.java` changed after the measured
series (`final-default.diff`); `pt-final-defaults` is a short smoke run of the final build through
the preset. This is not a claim for scenes with few particles, where the queries are a small
cost, or for other hardware.

## Files

- `analyze.py`: rebuilds `comparison.md`, `metrics.json` and `selection.json` from the raw CSVs in
  `run/alacer/benchmarks/` and the launch logs in `build/`, validating every launch; checks that
  the current sources match `final_source_sha256`. Copies each summary to `runs/`.
- `implementation.json`: attribution build, frozen measured build and final build hashes.
- `final-default.diff`: the only change after measurement.
- `jfr_summary.py`, `jfr-summary.txt`: client-thread JFR attribution of `run/pt-jfr-r1.jfr`.
- `verification.md`: the correctness run.

Smoke runs (`pt-smoke-detail`, `pt-smoke-ab`, `pt-final-defaults`, all with `timeScale` 0.3) checked
hooks and harness only and are not used.

## Limits

One machine, one settings profile, one world area and a fixed particle mix; other particle types
(water, portal, enchanting, footsteps) use the same light method but were not timed. Both paths run
in the same instrumented client with the redirect handler present in A (the attribution build had
none). Particle internals are not seeded, so per-frame loads differ slightly between segments; the
counters show identical means within 1%. A mod that also `@Redirect`s `World.getLightColor` in
`Entity.getLightLevel` or `BlockParticle.getLightLevel` conflicts; injections compose. No driver
settings were changed and no driver mode is inferred from CPU utilization. Do not add these
percentages to other sessions' or pool them with Java 8 results.

## Reproduce

```sh
./gradlew build
# attribution (scale 1; add -Palacer.benchmark.particleScale=2 for the dense launch)
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pt-attr-r1 -Palacer.benchmark.toggle=benchmark.disableParticles -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# intrusive split and JFR
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pt-detail-r1 -Palacer.profiler.particleDetail=true -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pt-jfr-r1 -Pjfr=pt-jfr-r1 -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# optimization A/B (scale 1 and 2)
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pt-opt-1-r1 -Palacer.benchmark.toggle=render.fastParticleLight -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# verification
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.particles=true -Palacer.benchmark.label=pt-verify-r1 -Palacer.benchmark.particleDiagnostic=true -Palacer.render.fastParticleLight=true -Palacer.render.fastParticleLight.verify=true -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
python docs/benchmarks/2026-10-20-particles/analyze.py
```

Every launch also passed `-Palacer.profiler.entityDetail=false -Palacer.profiler.hud=false
-Palacer.render.batchModelLists=false -Palacer.render.skipZeroTranslates=false
-Palacer.render.fastChunkFrustum=false` (the defaults) and logged to `build/<label>.log`. Launches
ran serially, with a check that no other game client was running. Nothing was committed or pushed.
