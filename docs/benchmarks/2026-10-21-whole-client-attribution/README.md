# Session 26: whole-client attribution of the shipped defaults

Roadmap item 1 of "Next session (26)". Work and launches on October 21. **No candidate qualifies under the
predeclared rule; nothing was implemented or promoted.** All nine shipped defaults stay on, every experiment stays
off and outside `render.defaults`, no file under `src/` changed, and sessions 1-25 (including the Java 8 archives) are
untouched and not rescored.

## What was measured

[protocol.md](protocol.md) was frozen with the sources and tooling ([manifest.json](manifest.json),
`sources-4167e8295a7171d9.zip`) before any measured launch. Eight hooks-absent JFR launches, all passing every runner
check, two per scene in the order d0, d200, terrain, particles, then reversed:

| Scene | Content | Phases at time scale 4 | Java samples per phase | Native (20 ms) | Allocation samples |
|---|---|---|---:|---:|---:|
| d0 | default static/pan/fly, 0 mobs | static 40 s, pan 60 s, fly 80 s | 5,504-20,229 | 484-859 | 435-999 |
| d200 | the same, 200 motionless mobs | static, pan, fly | 7,539-17,833 | 389-853 | 955-5,292 |
| terrain | terrain backend scenario, display lists (the game option's default; Alacer forces no backend) | steady, pan, sort, fly (+2 unranked warmups) | 5,728-20,092 | 463-849 | 456-1,148 |
| particles | particle scenario, scale 1 (reference only) | static, pan | 15,873-26,417 | 226-300 | 2,867-11,001 |

Session 25's `movement-1ms.jfc` (1 ms Java sampling, 20 ms native, unchanged file) and `-PjfrDebugNonSafepoints=true`;
every experiment, `.verify`, detail, census, region and benchmark-diagnostic flag explicitly false; the ordinary
frame profiler installed as in every control. Three pre-freeze smoke launches (time scale 0.3) only validated the
tooling and shaped it (offset calibration, CSV corroboration gate, time scale 4); `wc26-smoke-terrain` completed but was
rejected by the runner because tooling was edited during it. All are kept under `attempts/`.

Every sample is assigned to its CSV frame after a calibrated wall-epoch offset (-0.2 to -1.0 ms; 100% of client-tick
samples landed in ticking frames in seven launches, 97% in `wc26-d0-r1`, whose plateau reached the 1 ms search limit).
No stack was truncated.

## Rankings (median share of client-thread Java samples over each scene's launch/phase combinations)

Full tables, per launch/phase values and sub-regions: [results.md](results.md), [metrics.json](metrics.json).

| Region | d0 | d200 | terrain | particles (ref.) |
|---|---|---|---|---|
| `terrain_setup` (visibility search and its setup; optimization 1) | **59.8** [7.3-60.7] | **41.1** [4.3-60.3] | **59.8** [8.9-62.2] | 14.6 [1.7-27.5] |
| `entities.loop.global` (attribution artifact, below) | 15.4 [0-32.1] | 14.8 [14.2-23.2] | 16.2 [14.4-37.1] | 8.4 [0-10.2] |
| terrain layers (solid / cutout mipped / cutout / translucent) | 12.3 (6.1/2.6/2.2/2.1) | 13.2 (5.9/2.4/2.1/2.1) | 12.6 (5.7/2.5/2.3/2.3) | 7.8 |
| `entities.draw` | 0.7 | **25.6** [1.6-41.6] | 1.4 | 0.2 |
| all client tick | 1.9 | 2.5 (entities 1.2) | 1.7 | 6.2 (particles 4.7) |
| particle pass (unlit) | 1.1 | 0.7 | 0.8 | **55.8** [46.5-65.8] |
| frame-level Alacer profiler/benchmark hooks | 0.9 | 0.8 | 0.9 | 0.5 |
| chunk upload/compile (Java part) | 0.1 | 0.1 | 0.2 | 0.0 |

By phase, `terrain_setup` is about 60% whenever the camera moves (pan, sort, fly; 41% in d200 pan, where mob draws
take a quarter) and 2-9% when it is still; in moving phases 41-45% of it is the search loop itself, 14-19% `RenderChunk.setFrame`, 10-13% `CompiledChunk.isVisible`, 11-13%
`FrustumCuller.isVisible` and 3-4% `Math.floorMod`. The CSV agrees: terrain setup 0.21-0.34 ms per frame in moving
phases, 18-29% of frame time. With 200 mobs, entity draws are 39.5% of static and 25.6% of pan samples but 1.6% in fly
(mobs out of view). Terrain-layer samples are about half `CompiledChunk.hasBlock`, the per-chunk, per-layer emptiness
check; the draws themselves are display-list calls that Java samples barely see.

## Tails

Ticking frames are 1.7-2.6% of frames and slow-1% frames 1% (1.9-6.6 ms against means of 0.56-1.58 ms). From the
ordinary profiler (medians of per-launch differences):

- **Ticking frames** take 0.9-1.9 ms longer than other frames in d0, d200 and terrain, almost all of it `tick`
  (+1.05-1.95 ms); in the particle scene +2.8-3.5 ms, 1.73-1.78 ms of it the particle tick. Java tick-frame excess:
  random display ticks 0.21-0.33, client world/chunk-cache tick 0.17-0.30 and entity ticks 0.21-0.23 sample-ms per
  ticking frame (0.61 with 200 mobs).
- **Slow-1% frames**: d0 excess over the mean: tick +0.91, terrain setup +0.54, chunk upload +0.34, entities +0.29 ms;
  d200: tick +1.67, terrain setup +0.28, entities +0.27; terrain: tick +0.70, terrain setup +0.40, chunk upload +0.30.
  In fresh flight, chunk upload averages 1.23-1.32 ms of the 4.0-5.4 ms slow-1% frames; it is native/driver work that
  Java samples do not rank (4.5-6.6% of native samples). Pooled slow-1% Java shares: `terrain_setup` 41.6 / 31.8 / 44.6%
  (d0 / d200 / terrain), all client tick 16.8 / 28.8 / 15.1%, entity ticks 14.3% in d200.

## Native and driver time

Native samples were 82.7-84.4% `swap` in d0, d200 and terrain (chunk upload 4.5-6.6%, texture ticks 3.6-4.3%), and
77% particle pass in the particle scene (swap 9.6%). CSV `swap` is 20-61% of frame time (scene medians 33.3-38.4%,
particles 2.9%); no scene is driver-bound by the protocol's 50% test. Client-thread CPU utilization is 0.41-0.65 in
the three ranked scenes and 0.94-0.95 in the particle scene: outside the particle scene the client thread waits in
the driver for much of each frame.

## Allocation (sampled weights)

d0 allocates 3.1-4.3 KB per frame still or turning and 26-29 KB in flight; `world.pick` (per-frame crosshair ray trace:
`Vec3d`, `HitResult`) 23%, client world tick 13%, entity draws 8%, terrain setup 7%. d200 allocates 53-58 KB per frame
still or turning, **89% in entity draws** (61% `BlockPos`, mostly `BlockPos.offset` in shadow/brightness code, 8%
regex `Matcher`). In terrain, terrain setup is 23%: vanilla's camera-section flood fill `getOpenChunkFaces`
(`Direction.values()` copies, `LinkedList` nodes) when the camera enters a new block, and `HashSet` nodes from rebuild
scheduling. Particle-scene allocation is 58% particle tick (`Box`, `BlockPos`) and 27% particle pass. Allocation is
reported, not ranked, by the selection rule.

## Attribution artifact

In every scene 14-16% of Java samples land on one line of `renderEntities`' global section:
`shouldRenderEntityOutlines` -> `HashMap.getNode` (a single `isSpectator` lookup per frame). The ordinary profiler times
that whole phase at 0.000 ms. In `wc26-d0-r1` static the mass appeared instead in the block-entity loop (41% of
samples, 32 points at `Lighting.turnOn`) and nothing at the global line. Both sites are the first Java code after native GL sequences.
The CSV corroboration gate flags the global phase in 26 of the 28 launch/phase combinations (warmups included;
estimate 0.06-0.16 ms per frame against 0.000 ms) and the block-entity loop in the other two. Where these samples really belong is unknown; every other region's share is therefore uncertain by
up to about 15 points (they are diluted, not inflated, if the artifact is time spent elsewhere). The prefix scope also
shows why exact frames matter: `WorldRenderer.render*` matches 2.5-6 times as many samples as the exact layer method.

## Decision

[decision.py](decision.py) applies the protocol to the frozen classification ([decision.json](decision.json)):

- Regions passing the share/tail test and CSV corroboration: `terrain_setup` (20%+ in d0 and terrain; slow-1% 20%+ in
  d0, d200, terrain) and aggregates (render pass, terrain, terrain layers 12-13% in three scenes, entities, client tick
  in d200 tails). `entities.loop.global` passes the share test but fails corroboration everywhere.
- Removable sets of conceivable transformations (shares are per scene medians; a scene counts only if every
  combination is at least 5% with 300 samples):

| Set | d0 / d200 / terrain median % | Slow-1% pooled % | Share or tail rule | Exact / not closed |
|---|---|---|---|---|
| whole visibility search | 52.6 / 38.5 / 54.3 (still phases ~0) | 33.3 / 25.2 / 37.6 | tail yes | no: required per-chunk work, already optimization 1 |
| `RenderChunk.setFrame` stamps | 9.0 / 7.9 / 9.8 | 6.8 / 4.9 / 7.0 | no | no: vanilla state write; first-touch cache miss would move |
| occlusion-bit and frustum lookups | 13.9 / 9.2 / 13.7 | 7.9 / 5.6 / 9.1 | no | no: required reads; frustum closed (12) |
| per-layer chunk filter | 7.0 / 7.3 / 7.0 | 7.9 / 6.1 / 6.6 | no | no: compiled data changes between searches |
| entity draws | 0.7 / 25.6 / 1.4 | 2.5 / 9.4 / 2.0 | no (d200 fly 1.6%) | no: spread; thin parts closed (9, 11) |
| `renderEntities` attractor | 16.5 / 14.9 / 16.3 | 14.9 / 11.0 / 17.7 | share yes | fails CSV corroboration; unexplained |
| client entity ticks | 0.4 / 1.2 / 0.4 | 3.5 / 14.3 / 4.0 | no | no |
| random display ticks | 0.7 / 0.5 / 0.6 | 5.4 / 5.6 / 4.7 | no | no |

**Named candidate: none.** The largest region by far is the visibility search that optimization 1 already replaced;
its remaining cost is the per-chunk traversal the result depends on. Its pieces that one change could remove are each
below 10% and vanish in still phases, and the one large set that passes the share test is an attribution artifact.
The exactness judgments were written after the data (as session 25's were) and are stated with their reasons; the
thresholds and corroboration test are the frozen protocol's.

## Validation boundaries

- Build and tests: `gradlew build` and `test --rerun` with JDK 25.0.4.1: 86/86 tests pass, 147 main classes at
  classfile major 69 (no source change). This validates compilation and pure logic only.
- Live attribution: 8 measured launches on the actual Adoptium 25.0.4.1 launcher with the JFR/JIT flags verified on
  its command line; shared `run/config/alacer.properties` unchanged (`694ff84d...`); base world unchanged; the runtime
  jar's line table regenerated after the launches is byte-identical. See [validation.json](validation.json).
- JFR shares are sample counts (138-440 Java samples per second reached the client thread), not times; Java samples do
  not see driver time; line attribution in compiled code can be wrong by whole regions (the artifact). CSV sections
  come from the ordinary profiler installed in every launch. One machine (Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA
  616.92, unchanged), one world, two launches per scene, frame rates of 600-1,800 FPS.
- No optimization was measured, changed or selected; this is attribution evidence only.

## Remaining gaps

- The rule ranks Java samples; native-dominated costs (chunk upload/compile in flight tails, display-list draws, swap)
  are visible only through the CSV and 20 ms native samples.
- The renderEntities artifact (14-16%) is unresolved; Windows JFR has no CPU-time sampler to cross-check it.
- Three launches' offset plateaus touched the 1 ms search limit (alignment still 97-100%).
- Allocation is outside the rule: d200 entity-draw `BlockPos` allocation (53-58 KB per frame) and the per-frame pick ray
  trace are the largest steady allocators; a later session could predeclare an allocation-based rule for them.

## Reproduction

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_whole_client_attribution.py freeze
python tools/run_whole_client_attribution.py profiles
javac -cp build/classes/java/main -d build/wc26-attribution-tools docs/benchmarks/2026-10-21-whole-client-attribution/JfrWholeFrame.java
python docs/benchmarks/2026-10-21-whole-client-attribution/line_table.py
python docs/benchmarks/2026-10-21-whole-client-attribution/stream_frames.py <runs/label.record.json> <runs/label.stream-keys.json.gz>
python docs/benchmarks/2026-10-21-whole-client-attribution/analyze.py
python docs/benchmarks/2026-10-21-whole-client-attribution/decision.py
```

Existing manifests, labels, logs and JFR files are refused; use `--archive`/`--prefix` and a new archive for another
series. Raw CSV/JFR stay in ignored `run/`, logs in ignored `build/`; records keep their hashes, the actual launcher and
its JVM flags. Session 23's `stream_jfr.py`/`JfrPassAttribution.java` and session 25's helpers are unchanged. No commit,
push, publish, shared-config or driver change; no third-party performance-mod code was used.
