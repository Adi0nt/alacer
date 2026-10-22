# Session 25: particle movement region attribution

Roadmap item 1 after session 24. Work and launches on October 21. **No movement replacement is
eligible under the predeclared rule; none was implemented.** All nine shipped defaults,
experiments 17-19 (false, outside `render.defaults`), light entry/reuse, the original light
sources, optimization 9's writers and the NaN verifier fix are unchanged.

## What was measured

[protocol.md](protocol.md) was frozen with the sources before any measured launch. Two kinds of
launch, six in total, all passing their checks; two earlier time-scale-0.3 smoke launches only
validated the tooling (`attempts/`, not evidence).

- **Hooks-absent JFR** (`mr25-jfr-{1,2}-r{1,2}`, 40 s static / 60 s pan): 1 ms Java execution
  sampling and `-XX:+DebugNonSafepoints`, no census/detail hooks. 550-1,433 movement samples per
  phase (session 24 had 31-66). Movement is 95-97% of samples under the exact
  `ParticleManager.tickParticle` frame; the always-on particle tick was 1.45-1.50 ms (scale 1) and
  2.27-2.35 ms (scale 2) per ticking frame, tick frames 3.1-5.1 ms.
- **Region census** (`mr25-regions-{1,2}`): session 24's census plus six boundary marks and
  structural scan counts. Intrusive (about 10 clock calls per move at 21-22 ns each); shares are
  reported with and without a per-interval clock estimate, never as savings.

## Where movement time goes (median of eight launch/phase JFR shares)

| Region | Median | Range | Main parts (median) |
|---|---:|---:|---|
| `getCollisions` gathering | 44.2% | 42.5-47.9% | column check 12.8%, block reads 10.6%, entity query 7.3%, `addCollisions` 7.1%, border 2.0% |
| Box bounds for the query (`getShape().expanded`) | 9.0% | 7.9-11.6% | |
| fire scan (`contract` + `containsFireSource`) | 11.9% | 8.7-12.9% | block reads 5.5%, area check 3.3% |
| `checkBlockCollisions` | 10.7% | 6.0-14.6% | block reads 4.2%, area check 2.8% |
| clipping and the three `setShape` publications | 9.6% | 7.9-12.6% | |
| block under the entity | 5.4% | 4.3-7.2% | block reads 4.3% |
| entry (profiler, cobweb, sneak check) | 3.5% | 2.4-5.4% | |
| `isInWaterOrRain` | 0.3% | 0-0.6% | |
| step trial lines (stepHeight 0) | 0.9% | 0.1-1.5% | `if` and skid; no step query ran |

The census agrees in shape: clock-adjusted gather 43-44% (blocks 30%, entities 13-14%), fire
12%, block collisions 11.5%, under/flags 9.5%, clip/publish 8-9%, entry 13-14% (it also contains
the query-bounds Box construction, JFR's 9.0% plus 3.5%), 526-573 ns per ordinary move. Per ordinary move the gather loop reads
3.5 in-height cells over 1.5 columns, the entity query covers 1.62 chunks and **returned zero
entities in every move**, 0.74-0.78 moves read a second block under, and `checkBlockCollisions`
and `containsFireSource` cover **the same 1.70 cells** (their bounds are identical by
construction); no fire source was ever found. Collision lists held 0.11 / 0.14 block boxes and
zero entity boxes. JFR allocation in movement is 64-72% `Box`, 9-12% `BlockPos`, 8-10%
`ArrayList`, 5-15% `BlockPos$Mutable` (sampled weights).

## Decision

The protocol required a region, or one exact transformation's removable set, at a median of
at least 20% (at least 15% in every combination). [decision.py](decision.py) applies it to the
frozen analyzer's [metrics.json](metrics.json) without recomputing anything:

- `gather` (44%), all block-state reads (24%) and all chunk-map frames (28%) pass the share
  test as **aggregates**, but no single exact change removes them: first reads of each cell are
  required, `addCollisions` callbacks and the entity query must run, and only lookups are
  removable (block reads with a chunk-map frame: 8.2%, which is session 17's failed approach).
- Re-reads after gathering (fire scan, block-collision and block-under reads), the only large
  repeated work: **19.7% median (18.0-21.1%)**, below 20%. Reusing them would also cross
  `addCollisions`, `checkFallDamage`, `beforeCollision` and `onEntityCollision` callbacks, so it
  would be exact only with a world-mutation epoch covering every client mutation path.
- Answering client column/area checks as range tests: 19.6% median but 1.2% minimum. In
  `mr25-jfr-2-r1` the column check fell to 0.7-0.8% while gather block reads rose by about 11
  points; their sum is stable (21.8-24.4%). This repeats session 21's lesson: time charged to the
  inlined constant-true check belongs to compiled code around the read, and removing the check
  need not save it.
- Fire scan alone 11.9%, entity query 8.4%: below the threshold; the entity query is also
  session 19's territory.

So the remaining movement cost is spread across necessary reads, callbacks, Box construction and
bookkeeping. Rather than inventing a replacement, this session leaves movement as it is. The
complete gates for a future candidate are recorded in the protocol.

## Implementation (diagnostic only)

- `profiler.particleMovementRegions=true` (command line; needs the census's benchmark, profiler and
  census gates) installs `EntityParticleMovementRegionsMixin` and `WorldParticleMovementRegionsMixin`.
  Their `@ModifyArg` / `@ModifyReceiver` / `@ModifyExpressionValue` handlers return what they receive
  (no `CallbackInfo`, cancellation, reordering or writes). With the flag false, census output is
  session 24's format. Both region launches had zero order anomalies, region-timed moves equal to
  ordinary queries, noClip moves equal to noClip entries, and block plus entity boxes equal to the
  ordinary list-length sum.
- `-PjfrDebugNonSafepoints=true` (build.gradle) adds the diagnostic JIT flags to `-Pjfr` launches.
- [JfrMovementRegions.java](JfrMovementRegions.java) + [stream_regions.py](stream_regions.py) stream raw
  stack keys (exact `tickParticle` scope; the prefix count is reported separately).
  [line_table.py](line_table.py) builds the line -> region table from the **runtime** jar's bytecode
  ([lines.json](lines.json)): the runtime `minecraft-merged-9173d2371f` jar's lines differ from the
  sources jar's line map, which the smoke JFR exposed. Six boundary lines are reported separately.
- Tests: 86 pass (3 new: structural counts against vanilla loop bounds, default-off flags, plugin gating);
  147 main classes, all classfile 69 ([validation.json](validation.json)).

## Limits

JFR shares are sample counts at 1 ms resolution, not times. Without precise CPU-time sampling on
Windows, attribution at line level stays approximate even with `DebugNonSafepoints`, as the
column-check flip shows. Census counts for cells are structural (every column assumed loaded,
true for the client chunk cache) and its times are intrusive. One scenario, one machine, scales 1
and 2, a stationary camera and a turn. No visual, lifecycle, gameplay or third-party-mod claim is
made, and no optimization was measured.

## Reproduction

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_movement_regions.py freeze
python tools/run_particle_movement_regions.py regions
python tools/run_particle_movement_regions.py profiles
javac -cp build/classes/java/main -d build/mr25-attribution-tools docs/benchmarks/2026-10-21-particle-movement-regions/JfrMovementRegions.java
python docs/benchmarks/2026-10-21-particle-movement-regions/line_table.py
python docs/benchmarks/2026-10-21-particle-movement-regions/stream_regions.py <record> <runs/label.stream-keys.json>
python docs/benchmarks/2026-10-21-particle-movement-regions/analyze.py
python docs/benchmarks/2026-10-21-particle-movement-regions/decision.py
```

Existing manifests, labels and logs are refused; use `--archive`/`--prefix` and a new archive for
another series. Raw CSV/JFR stay in ignored `run/`, logs in ignored `build/`; records keep their
hashes, the actual launcher and its JVM flags. No commit, push, publish, shared-config or driver
change; no third-party performance-mod code was used.
