# Session 26: whole-client attribution of the shipped defaults

Roadmap item 1 of "Next session (26)" in `docs/ARCHITECTURE.md`. Predeclared and frozen with the sources and
tooling (manifest.json and the sources ZIP) before any measured launch of this session. Sessions 1-25 are frozen:
no launch of theirs is rescored, replaced or pooled. All nine shipped defaults stay on, every experiment stays off and
outside `render.defaults`, and no Java source under `src/` changes in this session.

## Question

Every session since the Java 25 migration worked inside the particle scenario. Which client-thread regions dominate
the default static/pan/fly path (0 and 200 mobs) and the terrain backend scenario under the shipped defaults, and
does any of them, by a rule fixed here, name the next candidate? The particle scenario is a reference only.

## Pre-freeze smoke launches (attempts, not evidence)

Three smoke launches at time scale 0.3 checked that the runner, JFR settings, JIT flag, streamer, line tables and
analyzer work: `wc26-smoke-d200` (passed), `wc26-smoke-terrain` (the client completed, but the runner rejected it
because tooling was edited during the launch; retained as a failed attempt) and `wc26-smoke-terrain-2`. What they
showed, and what changed because of it, before any measured launch:

- The profiler epoch is only known to about 1 ms (`profiler.epochWallUncertaintyNanos`), while frames last 0.7-1.4 ms.
  At offset 0 only 48 of 76 client-tick samples landed in ticking frames; all 76 did for offsets from -0.9 to -0.5 ms.
  The streamer therefore calibrates the offset (below).
- In d200 pan and fly, 12-15% of Java samples were attributed to `WorldRenderer.shouldRenderEntityOutlines` ->
  `HashMap.getNode` in `renderEntities`' global section, while the ordinary profiler measured that section at
  0.000 ms per frame. Line/leaf attribution in compiled code can be wrong by whole regions (sessions 21 and 25 saw the
  same in smaller form). The CSV corroboration gate below was added because of this.
- The terrain smoke showed the same artifact with no mobs (15% at the same line, CSV 0.000 ms), and d200 showed a
  similar concentration on `Entity.isOnFire` -> `SyncedData.getEntry` inside entity draws. Both sit at the first Java
  code after a native GL sequence; such single cheap leaves are treated as attractors (selection rule, item 3).
- Only 110-330 Java samples per second reached the client thread, and native samples (20 ms) were 65-97% `swap`.
  The time scale was raised from the planned 3 to 4 for every scene.
- Allocation samples on the client thread were few (41-144 per smoke phase at the 300/s throttle shared by all
  threads); allocation shares are reported with their sample counts.

## Scenes and launches

Fresh identical copies of the seed-1789 base world (`run/saves/alacer-benchmark`, hashes in the manifest), 1920x1080,
2 GiB heap, view distance 12, vsync off, `benchmark.vbo=false` (the game option's default; Alacer forces no backend,
so this is the shipped backend: display lists). Time scale 4 everywhere.

| Scene | Options | Phases (seconds at scale 4) | Ranked |
|---|---|---|---|
| d0 | default path, `benchmark.entities=0` | static 40, pan 60, fly 80 | yes |
| d200 | default path, `benchmark.entities=200` | static 40, pan 60, fly 80 | yes |
| terrain | `benchmark.backendScenario=true` | warmup-pan 60, warmup-sort 64, steady 40, pan 60, sort 64, fly 80 | warmups recorded, never ranked |
| particles | `benchmark.particles=true`, `particleScale=1` | static 40, pan 60 | reference only |

Eight launches, serial, in the order d0, d200, terrain, particles (`wc26-<scene>-r1`) then particles, terrain, d200,
d0 (`-r2`). Every launch passes all nine shipped options as true; `fastChunkFrustum`, `skipZeroTranslates`,
`batchModelLists`, `fastParticleMovement`, `reuseParticleBoxes`, `scalarParticleEntityQuery`, `fastParticleLightEntry`
and `reuseParticleLight` as false; every `.verify` as false; `profiler.entityDetail`, `entityStages`, `particleDetail`,
`particleLightRepeats`, `chunkDetail`, `shadowTiming`, `frustumTiming`, `hud`, `particleMovementCensus`,
`particleMovementRegions`, `particleThreadCycles` and `particleDrawDiagnostics` as false; and every benchmark
diagnostic (`screenshots`, `shadowDiagnostic`, `cloudSweep`, `particleDiagnostic`, `particleRemovalDiagnostic`,
`particleEntityQueryDiagnostic`, `particleBoxDiagnostic`, `particleMovementDiagnostic`, `movementCensusInactive`,
`toggleReverse`) as false, with no `benchmark.toggle`. Hooks absent: with these values the mixin plugin applies none of
the optional detail, census, region, verification-only or experiment mixins. The ordinary frame profiler (sections,
counters, allocation per frame) stays installed in every launch, as in all prior controls; it produces the CSVs and
the phase windows and is part of what is measured (its own frames are reported as `alacer.profiler`).

Profiling: `-Pjfr=<label>` with session 25's `movement-1ms.jfc` (JDK 25 `profile.jfc`, Java execution sampling 1 ms,
native method sampling 20 ms, allocation sampling as in `profile.jfc`, unchanged file, hash in the manifest) and
`-PjfrDebugNonSafepoints=true`. The JFR stack depth is the default 64; truncated stacks are counted.

`tools/run_whole_client_attribution.py` refuses reused labels, logs, JFR files and records, and refuses to start while
any `java.exe`/`javaw.exe` KnotClient or devlaunchinjector process exists; captures the actual launcher and requires
`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe`, the JIT debug flags, the JFR settings path and
every explicit `-Dalacer.*` value on its command line; checks the source tree, tooling, shared config SHA256
(`694ff84d...`) and base world before and after; checks the exported environment (Java 25, no generation run, window,
backend inactive, view distance, vsync, mobs, scenario flags, allocation available, no toggle) and every phase's
config, frame counts, zero dropped frames, one discarded setup frame and target duration; rejects GL errors, mixin or
injection failures, verifier mismatch lines, aborts and timeouts. Builds and analysis never run during a launch; every
attempt is kept.

## Tooling (frozen)

- `JfrWholeFrame.java` (generalizes session 25's `JfrMovementRegions.java`) streams the recording and keys every
  client-thread execution, native and allocation sample by its anchor chain: exact frames (class, method and JVM
  descriptor, or any overload of one exact class and method) from the root-most `Minecraft.runGame` towards the leaf,
  each with its frame line, plus package-scope markers for Alacer's profiler, benchmark driver, render code and
  `$alacer$` mixin methods, the frame below the deepest chain element and the leaf. Prefix-scope counts (for example
  `ParticleManager.render*`, which also matches `renderLit`) are reported per anchor next to the exact counts and are
  never used to classify. Samples are assigned to CSV frames; categories: all, ticking frames (`tick_ns > 0`),
  non-ticking, frames at or above the phase's nearest-rank p95 and p99 frame time.
- Offset calibration: over offsets within the profiler's reported epoch uncertainty (0.1 ms steps), the plateau of
  offsets placing the most samples directly under `Minecraft.tick` inside ticking frames that contains the
  smallest-magnitude best offset; its midpoint is applied. Counts at offset 0 and at the chosen offset are reported.
- `line_table.py` derives line -> section tables from the runtime jar's own bytecode (as session 25's did) for
  `Minecraft.runGame`, `Minecraft.tick`, `GameRenderer.render(FJ)`, `renderWorld`, the pass method `render(IFJ)`,
  `WorldRenderer.renderEntities` and `setupRender`, using vanilla's own profiler `push`/`swap`/`pop` names; lines in
  more than one section are reported as boundaries. The pass method's four terrain-layer call lines are mapped to their
  `BlockLayer` from the `getstatic` before each call.
- `analyze.py` classifies root-most first: client tick (particles, entities, world, display ticks, textures, game and
  world renderer, pick, interaction, network, sound, GUI, benchmark driver), scheduled tasks, swap, GL error check,
  frame-rate sync, sound listener, frame-level Alacer hooks, GUI/HUD, lightmap, pick, and inside the pass: terrain
  setup, chunk upload/compile, each terrain layer, entities (prepare, global, outlines, loop, draws, visibility, name
  tags), block entities, particles (lit and unlit), sky, clouds, weather, hand, camera/fog, overlays, with explicit
  `other` regions that list their uncovered anchor or section. Aggregates (all tick, render pass, terrain, entities,
  particles) are ranked next to leaf regions.
- Session 23's `stream_jfr.py`/`JfrPassAttribution.java` and session 25's helpers and `movement-1ms.jfc` are unchanged
  (hashes tracked in the manifest).

## Outputs (per launch/phase and per scene)

Ranked shares of client-thread Java execution samples (median, minimum and maximum over the scene's launch/phase
combinations, per-phase medians, minimum sample counts); the same shares pooled within ticking frames and slow (p99,
p95) frames, and each region's excess samples per tail frame converted to sample-equivalent milliseconds; native-method
sample shares (20 ms period, reported separately, never added to Java samples); sampled allocation weight by region and
by object class; CSV section means for all, ticking, non-ticking and slow frames, including `swap` and its share of
frame time; client and process CPU; other threads' sample counts; anchor exact versus prefix counts; offset and
alignment. A combination with fewer than 300 samples in a region is marked low for that region.

## Selection rule (fixed now)

Unit: a set X of samples, either a region/aggregate or the removable set of one named exact transformation. Scene
share = median of X's share over the scene's non-warmup launch/phase combinations (6 for d0 and d200, 8 for terrain,
4 for particles). A scene counts for X only if X's share is at least 5% and X has at least 300 samples in every one of
its combinations, and the scene is not driver-bound (median CSV `swap` at least 50% of frame time).

X is eligible to be named the next candidate only if all hold:

1. Share: scene share at least 10% in at least two counted scenes, at least one of them d0, d200 or terrain; or at
   least 20% in one counted scene among d0, d200 and terrain. Tail alternative: pooled share of slow-1% frame samples
   at least 20% in d0, d200 or terrain with at least 300 pooled samples. The particle scene never qualifies alone.
2. Corroboration: where an ordinary-profiler section covers X's region (terrain setup, chunk upload, each layer, the
   five entity phases, particles, sky, clouds, weather, hand, GUI, lightmap, pick, tick), the JFR estimate (share times
   non-swap frame time) must not exceed three times the CSV mean plus 0.02 ms in any counted combination.
3. Exactness: X is the removable set of one named exact transformation, justified from the runtime bytecode and the
   counts: the samples it removes are work that disappears, not an aggregate whose required work remains. Time charged
   to constant-true client chunk checks counts only together with the read it precedes (sessions 21, 25). A sub-region
   carried by one cheap leaf at the first Java code after a native call (an attractor, as in the smoke) is unexplained
   and cannot form a removable set without independent timing (a CSV section or a separately frozen timing series).
4. Not closed: X is not on roadmap item 4's closed list and does not re-promote or rebrand a failed experiment
   (sessions 9, 12, 17-19, 21-24); installed-but-off hooks are never an allocation control (session 24).

Aggregates that pass 1-2 but no exact transformation's removable set does are reported as large but not selectable.
If several sets qualify, the one with the largest minimum counted scene share is named; the others are listed.
Attribution launches never select an optimization by themselves: naming a candidate is not implementing or promoting
it. If no set qualifies, the session records that none does.

## Gates for a later candidate (fixed now; only a separate, separately frozen series can meet them)

Correctness and lifecycle: full Java 25 build/tests; an opt-in `.verify` mode comparing every replaced answer or
output with vanilla's over at least 10 million operations (or every frame, for per-frame state) in every selecting
scene with zero mismatches; screenshots of the selecting scenes compared with vanilla; world replacement, resource
reload, view-distance change, backend change (display lists and VBO), chunk load/unload and world re-entry
exercised during capture; draw order, GL state order and callback order preserved and compared where the change
touches them; the mixin absent when the option is off; the option outside `render.defaults` until selected.

Performance: per selecting scene, four in-process A/B launches (A1/B1/B2/A2 and B1/A1/A2/B2 alternating, both paths
warmed) plus four startup pairs (absent versus on, alternating order), fresh identical copied worlds, no diagnostics,
hooks absent on the A side of startup pairs. Medians of per-launch pooled changes per phase:

1. the target region (its CSV section where one exists, otherwise hooks-absent JFR share) lower in at least 3/4
   launches with a median saving of at least 5%;
2. frame-time mean saving at least 2% in at least one selecting scene's phases, and no scene/phase regression above 1%;
3. frame p95/p99 and tick-frame p99 regression at most 5% in every scene/phase; slow-1% frame mean not higher;
4. client CPU per frame not higher; process CPU per frame regression at most 2%;
5. allocation per frame regression at most 2% (allocation per second reported beside it);
6. work counters (visible chunks, chunks per layer, entities and block entities drawn, particles) within 2%;
7. flag |A2/A1 frame drift| over 10%; with median absolute drift over 10%, B must beat A1 and A2 separately in the
   target region in at least 3/4 launches.

Startup pairs (medians per side): frame mean lower, p95/p99 and tick-frame p99 at most +5%, CPU per frame not higher,
allocation per frame at most +2%. The other non-selecting scenes of this protocol must also show no regression above 1%
in frame mean. Intrusive attribution and favorable medians alone never select; any failure keeps the candidate off.

## Validation boundaries

Build and unit tests (86 at the freeze) validate compilation and pure logic only. JFR shares are sample counts at a
1 ms period, not times; Java samples do not see driver time; native samples run at 20 ms; line attribution in compiled
code is approximate (the smoke's 12-15% artifact). CSV sections come from the ordinary profiler, which is installed in
every launch. One machine (Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA 616.92), one world, two launches per scene.
No commit, push, publish, shared-config or driver change; no third-party performance-mod code.
