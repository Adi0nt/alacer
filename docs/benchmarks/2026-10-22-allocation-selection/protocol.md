# Session 28: allocation-based selection

Roadmap item 1 of "Next session (28)" in `docs/ARCHITECTURE.md`. Predeclared and frozen (git commit recorded in
`freeze.json`, together with the base world's hashes) before any measured launch of this session. Sessions 1-27 are
frozen: no launch of theirs is rescored, replaced or pooled. All nine shipped defaults stay on, every experiment stays
off and outside `render.defaults`, and nothing under `src/` changes before the attribution series is complete.

## Environment (stated before measuring)

This series runs in a Linux cloud container: 4 vCPU Xeon at 2.1 GHz, Adoptium 25.0.4.1, Xvfb, **Mesa llvmpipe
(software OpenGL)**. A pre-freeze generation launch ran at about 20 FPS (854x480), against 600-1,800 FPS on the
reference machine (Windows 11, i7-12700F, RTX 3060 Ti). Consequences fixed now:

- Frame-time, tail, CPU and swap numbers from this container do not represent a GPU machine. They are recorded and
  reported but **cannot select or reject anything on their own**, and no default can change from this container.
- At 20 FPS nearly every frame contains a client tick (20 Hz). Whole-frame allocation per frame therefore depends on
  the frame rate. Selection uses only the **per-frame path**: client-thread allocation outside `Minecraft.tick`.
  Tick-path allocation is reported, not ranked. Work in the per-frame path that runs per camera event rather than per
  frame also scales with the frame rate; the phases and sets this affects are not ranked (below).
- Allocation counts are CPU-side and do not depend on the GPU. Which objects the JIT scalar-replaces can differ
  between machines; that is why any candidate needs confirmation on the reference hardware (below).

## Question

Session 26 reported, from 300/s JFR allocation samples on the reference machine: 200 mobs make entity draws allocate
53-58 KB per frame (61% `BlockPos`, mostly `BlockPos.offset` in shadow and brightness code, 8% regex `Matcher`); without
mobs the crosshair pick (`world.pick`: `Vec3d`, `HitResult`) is 23% (d0) and 18% (terrain) of client allocation; and
vanilla's camera-section flood fill allocates `Direction.values()` copies in flight. Does any exact transformation
remove enough per-frame allocation, by the rule fixed here, to be named and implemented?

## Scenes and launches

Fresh identical copies of the seed-1789 base world (the benchmark copies `alacer-benchmark` to a new run world for every
launch; the base world's file hashes are checked before and after each launch and against `freeze.json`), 854x480
(Xvfb), 2 GiB heap, view distance 12, vsync off, `benchmark.vbo=false` (display lists; Alacer forces no backend). Time
scale 2.

| Scene | Options | Phases (seconds at scale 2) | Ranked |
|---|---|---|---|
| d0 | default path, `benchmark.entities=0` | static 20, pan 30, fly 40 | yes |
| d200 | default path, `benchmark.entities=200` | static 20, pan 30, fly 40 | yes |
| terrain | `benchmark.backendScenario=true` | warmup-pan 30, warmup-sort 32, steady 20, pan 30, sort 32, fly 40 | warmups recorded, never ranked |

Only static, pan and terrain steady/pan are ranked. Fly and terrain sort move the camera through blocks and
chunks: work that runs per block crossing or per chunk arrival happens once per event, not once per frame, so its
size per frame scales with 1/FPS (in the smoke the open-faces flood fill was 56% of fly frame-path allocation here,
where the camera crosses about one block per frame, against one crossing per about 50 frames at 1,000 FPS). Those
phases are recorded and reported in bytes per second, never ranked. Pan has a milder form of the same effect (the camera
turns 2.4 degrees per frame here against about 0.05 at 1,000 FPS, so visibility-change work such as `setup_render`
allocation is larger per frame here); that inflates the share denominator and makes the share test conservative for
per-frame sets.

Six launches, serial: `alloc28-d0-r1`, `alloc28-d200-r1`, `alloc28-terrain-r1`, then `-r2` in reverse order. Every
launch passes the nine shipped options as true, the eight experiments as false, every `.verify` as false, every
profiler detail, census, region and diagnostic option as false and every benchmark diagnostic as false, with no
`benchmark.toggle`, exactly as session 26's protocol lists them. **Hooks absent:** with these values the mixin plugin
applies none of the optional detail, census, region, verification-only or experiment mixins, so the controls are not
installed-but-off (session 24). The ordinary frame profiler stays installed, as in all prior controls; it produces
the CSVs, the phase windows and the per-frame `alloc_bytes` (ThreadMXBean).

Profiling: `-Pjfr=<label>` with `allocation.jfc` (JDK 25's `profile.jfc` with one change: the
`jdk.ObjectAllocationSample` throttle raised from 300/s to 10000/s) and `-PjfrTlab=16k` (`-XX:TLABSize=16k
-XX:-ResizeTLAB`, attribution launches only). Allocation samples are taken only at TLAB refills; with adaptive TLABs the
client thread yielded 3-11 samples per second in the first smoke. A fixed TLAB changes how often the sampler fires and
how often threads refill, not what or how much the code allocates (the ThreadMXBean counter is checked against the
samples, rule item 2). No DebugNonSafepoints: allocation stacks are taken at the allocation site. JFR stack depth is
the default 64; truncated stacks are counted.

`tools/run_allocation_selection.py` refuses reused labels, logs and JFR files and any second client; reads the actual
launcher from `/proc` and requires the session's JDK 25 `bin/java`, the JFR settings path, `-Xmx2G` and every explicit
`-Dalacer.*` value on its command line; requires the frozen paths (sources, build files, tooling, this protocol) to
equal the frozen commit; checks the base world before and after; checks the exported environment (Java 25.0.4.1
Adoptium, not a generation run, window, display lists, view distance, vsync, mobs, scenario flags, allocation
available, no toggle, llvmpipe) and every phase's config, frame counts, zero dropped frames, one discarded setup frame
and target duration; rejects GL errors, mixin or injection failures, `mismatch` lines, aborts and timeouts. Builds and
analysis never run during a launch; every attempt is kept under `attempts/`.

## Pre-freeze smoke launches (attempts, not evidence)

Three smoke launches at time scale 0.5 validated and shaped the tooling; all are kept under `attempts/`:
`alloc28-smoke-d200` (rejected: tooling edited during it; JFR found the client thread under its first name `main`
and only 14-55 client samples per phase), `alloc28-smoke-d200-2` and `alloc28-smoke-terrain` (both completed; rejected
because the log check matched the zero-count `... final: 0 mismatched` exit summaries; the check now rejects only a
nonzero count or another mismatch line). Changes made because of them, before any measured launch: the client thread
is matched as `Client thread` or `main`; the fixed 16 KiB TLAB and 10000/s throttle (above); fly and sort are not
ranked, and the flood-fill sets are reported but not ranked (above). In the last two smokes the JFR estimate of
client-thread bytes per frame was within 1% of ThreadMXBean `alloc_bytes` in every phase.

## Tooling (frozen)

- `AllocSamples.java` writes every `jdk.ObjectAllocationSample` (thread, wall time, weight, class, full stack).
- `classify.py` assigns client-thread samples to phases by wall time (the profiler's wall epoch plus `start_ns`), to the
  tick or frame path (`Minecraft.tick` on the stack or not), and to the named sets below, and lists the top 30
  frame-path allocation sites (class plus first three non-JDK frames).
- `decision.py` applies the selection rule to the six classified launches (items 1 and 2; items 3 and 4 are written
  judgments, recorded next to its output).

## Named sets (fixed now; each is the removable set of one transformation)

Frames are matched by declaring class and method name on the sampled stack.

| Set | Samples | Transformation it stands for |
|---|---|---|
| `shadow.offset_pos` | `BlockPos` made by `down`/`offset` called directly from `EntityRenderer.renderShadow` | supply the block-below position to the (already redirected, optimization 6) state query without allocating |
| `entity_light.pos` | `BlockPos` under `Entity.getBrightness` / `Entity.getLightLevel` (the entity position and the world light reads' neighbor offsets beneath it) | answer the render-time entity brightness with vanilla's reads and no position objects |
| `name_check.strip` | everything under `Formatting.strip` within `LivingEntityRenderer.applyRotation` (regex `Matcher`, its buffers, the result) | decide `strip(name).equals("Dinnerbone"/"Grumm")` without the regex |
| `pick.block_trace` | everything under `World.rayTrace` within `GameRenderer.pick` | an allocation-free block ray march returning vanilla's `HitResult` (the returned objects themselves are not removable and are estimated from the bytecode, below) |
| `flood.all` | everything under `WorldRenderer.getOpenChunkFaces` | an allocation-free flood fill returning the same faces to optimization 1's cache (reported per second; event-driven, not ranked here) |
| `flood.direction_values` | `Direction.values()` copies within it | reuse one direction array (reported per second; event-driven, not ranked here) |

Also reported, never selectable by themselves: `shadow.all`, `entity_light.all`, `name_check.all` (with
`name_check.get_name`, the translated-name string), `pick.all`, `pick.other`, `agg.render_entities`,
`agg.setup_render` and Alacer's profiler and benchmark hooks. A set not listed here may be named after measurement
only with its exactness argument from the runtime bytecode written next to its numbers, and is marked post hoc.

## Selection rule (fixed now)

Unit: a (scene, phase) combination among the six ranked ones (d0 and d200 static and pan, terrain steady and pan).
Frame-path share of X = X's sampled weight divided by all client-thread frame-path sampled weight in that phase.
Bytes per frame of X = X's sampled weight in the phase divided by the phase's frame count. Only per-frame sets can be
ranked (`shadow.offset_pos`, `entity_light.pos`, `name_check.strip`, `pick.block_trace`); the flood-fill sets are
event-driven and reported.

X qualifies on a combination when, **in both launches** of the scene, its frame-path share is at least 10%, its bytes
per frame are at least 2,048, and it has at least 50 samples. X is eligible to be named only if all hold:

1. **Share and size:** X qualifies on at least two ranked combinations.
2. **Sampler corroboration:** in every launch/phase used for item 1, the JFR estimate of all client-thread bytes per
   frame (frame plus tick path) is within a factor of two of the ordinary profiler's ThreadMXBean `alloc_bytes` mean
   for the same frames. Otherwise the combination does not qualify.
3. **Exactness:** X is the removable set of one named exact transformation, justified from the runtime bytecode: the
   objects it removes are allocations that disappear (not ones the result keeps, such as a returned `HitResult` and
   its `Vec3d`/`BlockPos`), and the world reads, their order and the answers stay vanilla's.
4. **Not closed:** X is not on roadmap item 3's closed list and does not re-promote or rebrand a failed experiment
   (sessions 9, 12, 17-19, 21-24). "Shadow queries beyond optimization 6" stay closed: a shadow set qualifies only if
   it removes argument allocation without changing which queries run or how they are answered. Installed-but-off hooks
   are never an allocation control (session 24).

If several sets qualify, the one with the largest minimum share over its qualifying combinations is named; the others
are listed. Attribution launches never select an optimization by themselves: naming a candidate is not promoting it.
If no set qualifies, the session records that none does and implements nothing.

## Gates for a named candidate (fixed now; a separate, separately frozen series must meet them)

Implementation: an option `render.<name>`, **off by default and outside `render.defaults`**, applied at startup by the
mixin plugin only when the option, its `.verify` or a benchmark toggle of it is set (the mixin is absent otherwise), and
runtime-toggleable when applied, for in-process A/B. `.verify` computes vanilla's answer next to every replaced one,
uses vanilla's, and counts and logs mismatches.

Correctness and lifecycle (session 26's, unchanged): full Java 25 build and tests; `.verify` comparing every replaced
answer with vanilla's over at least 10 million operations (or every frame, for per-frame state) in every selecting scene
with zero mismatches; screenshots of the selecting scenes compared with vanilla; world replacement, resource reload,
view-distance change, backend change (display lists and VBO), chunk load/unload and world re-entry exercised during
capture where the change keeps any state across calls; draw, GL-state and callback order preserved where the change
touches them; the mixin absent when the option is off.

Performance (session 26's, unchanged), per selecting scene: four in-process A/B launches (A1/B1/B2/A2 and B1/A1/A2/B2
alternating, both paths warmed) plus four startup pairs (absent versus on, alternating order), fresh identical copied
worlds, no diagnostics, hooks absent on the A side of startup pairs. Medians of per-launch pooled changes per phase:

1. the target region lower in at least 3/4 launches with a median saving of at least 5%;
2. frame-time mean saving at least 2% in at least one selecting scene's phases, and no scene/phase regression above 1%;
3. frame p95/p99 and tick-frame p99 regression at most 5% in every scene/phase; slow-1% frame mean not higher;
4. client CPU per frame not higher; process CPU per frame regression at most 2%;
5. allocation per frame regression at most 2% (allocation per second reported beside it);
6. work counters (visible chunks, chunks per layer, entities and block entities drawn, particles) within 2%;
7. flag |A2/A1 frame drift| over 10%; with median absolute drift over 10%, B must beat A1 and A2 separately in the
   target region in at least 3/4 launches.

Startup pairs (medians per side): frame mean lower, p95/p99 and tick-frame p99 at most +5%, CPU per frame not higher,
allocation per frame at most +2%. The other non-selecting scenes must show no frame-mean regression above 1%.

Allocation-specific additions for this session: the **target region is the client thread's allocation per frame**
(`alloc_bytes`, ThreadMXBean), which must fall in at least 3/4 in-process launches and in the startup pairs' median,
by at least half of the candidate's selection estimate (median bytes per frame in its qualifying combinations).

**This container's limit:** only correctness, `.verify`, allocation (target region and gate 5) and work counters (6) are
judged here. Gates 2-4 and 7 and the startup frame/CPU conditions are measured and reported, but llvmpipe frame times
cannot pass or fail a candidate. A candidate that fails a correctness or allocation gate here fails. A candidate that
passes them stays **off by default** pending the same performance series on the reference hardware. Intrusive
attribution and favorable medians never select; any failure keeps the candidate off.

## Validation boundaries

Build and unit tests validate compilation and pure logic only. JFR allocation samples are weighted estimates (throttled,
shared across threads), not counts. ThreadMXBean `alloc_bytes` is an exact per-thread counter but cannot attribute.
One machine (software GL), one world, two launches per scene. No third-party performance-mod code.
