# Session 25: particle movement region attribution

Predeclared before freezing sources and before any measured launch of this session. Two unscored
pre-freeze smoke launches (`mr25-smoke-regions`, `mr25-smoke-jfr`, time scale 0.3) only checked that
the new hooks apply and that the JFR settings, JIT flag and helper work; they are retained as
attempts and are not evidence. Sessions 17-24 are frozen: none of their launches is rescored,
replaced or pooled. All nine shipped defaults, the NaN verifier fix, optimization 9's writers and the
original light sources are preserved; experiments 17-19, light entry and light reuse stay false and
outside `render.defaults`.

Roadmap item 1 asked to separate block gathering from callbacks, block-collision and fire scans.
Source inspection of `Entity.move` for a non-noClip particle (stepHeight 0, session 24) gives this
sequence: entry (profiler, cobweb, sneak check), `World.getCollisions` (block cell loop with column,
border and state reads plus `addCollisions` callbacks, then the nearby-entity query), axis clipping
with three virtual `setShape` publications, then "rest": `setPositionFromShape`, flags, the block
under the entity (one or two state reads), `checkFallDamage`, velocity, `beforeCollision` on vertical
collision, `checkBlockCollisions` (cell loop: positions, state reads, `onEntityCollision` callbacks),
`isInWaterOrRain` (two positions, `isRaining`), `containsFireSource` on the contracted shape (cell
scan), fire effects. Nothing here selects a target; the unused step trial and ordinary axis
clipping are not targets unless the new attribution shows otherwise.

## Tooling

- `profiler.particleMovementRegions=true` (command line only; requires the census's own benchmark,
  profiler and census gates) installs `EntityParticleMovementRegionsMixin` and
  `WorldParticleMovementRegionsMixin`. They use `@ModifyArg`, `@ModifyReceiver` and
  `@ModifyExpressionValue` handlers that return the value, receiver or argument they receive; no
  cancellation, replacement, reordering, world/shape/list write, element iteration or extra virtual
  call. They mark six boundaries (entity-query bounds, `push("rest")`, before/after
  `checkBlockCollisions`, after `isInWaterOrRain`, after `containsFireSource`) on top of the census's
  move/gather clocks, and record structural scan sizes (getCollisions columns/cells/in-height reads,
  entity-query chunks/sections, block-collision and fire cells) computed from the bounds each method
  receives, plus observed block/entity collision-box counts, entity-query result sizes, second
  block-under reads, vertical callbacks and fire results. Structural sizes assume the client chunk
  source reports every column loaded; unit tests compare them with the vanilla loop bounds.
  With the flag false the census output is session 24's format unchanged.
- `movement-1ms.jfc` is the JDK 25 `profile.jfc` with Java execution sampling at 1 ms (label marked);
  `-PjfrDebugNonSafepoints=true` adds `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints` so
  inlined-frame lines are recorded at non-safepoint PCs. It changes debug info, not compiled code
  shape; these launches are attribution only.
- `JfrMovementRegions.java` / `stream_regions.py` aggregate each client-thread sample under the exact
  `ParticleManager.tickParticle` frame (session 23's streaming scope; the `tickParticle` prefix count
  is reported separately) into raw stack keys: the `Entity.move` frame's line and callee, the nearest
  anchor (`getCollisions`, `checkBlockCollisions`, `containsFireSource`, `isInWaterOrRain`) with its
  line and callee, the leaf, and chunk-map/block-read flags. `line_table.py` derives the move
  line -> region table from the runtime Minecraft jar named in Loom's argument file (its lines differ
  from the decompiled sources jar, found in the pre-freeze JFR smoke), using landmark invocations;
  lines spanning a region boundary are reported as `boundary:*`. `analyze.py` classifies regions,
  callee-based sub-regions (leaves are listed but not used: inlined bimorphic chunk-source code can
  be misattributed, as session 21 found) and evaluates eligibility. Session 23's `stream_jfr.py` and
  `JfrPassAttribution.java` are unchanged.

## Attribution launches (never scored, never used to select by themselves)

1. `mr25-regions-1`, `mr25-regions-2` (scales 1, 2; time scale 1): census plus region marks. Every
   phase must satisfy session 24's census invariants and: zero region order anomalies, region-timed
   moves equal ordinary queries, noClip region moves equal noClip entries, zero unknown entity-query
   lists, block plus entity collision boxes equal the ordinary list-length sum. Reject a capture that
   fails; retain it and diagnose before any replacement label. Region times include their marks'
   clock costs (the census reports a post-capture clock calibration) and the hooks change compiled
   code: report them as intrusive shares, never as savings.
2. `mr25-jfr-1-r1`, `mr25-jfr-2-r1`, `mr25-jfr-2-r2`, `mr25-jfr-1-r2` (time scale 4: 40 s static,
   60 s pan; census/detail/region hooks absent; 1 ms sampling; DebugNonSafepoints). Report client,
   exact-tick, prefix-tick and move samples, region shares, chunk-lookup and block-read sample shares,
   detail keys, leaves, and allocation sampled weights by region. Flag any phase with fewer than 300
   move samples. Samples are counts, not times; allocation weights are estimates.

Shipped-default movement cost per ticking frame comes from session 24's census-absent detail
(1.37-2.22 ms); it is not remeasured here. A region's estimated cost is its median sample share
times that range.

## Candidate eligibility (decided before seeing the data)

A movement replacement may be implemented in this session only if all hold:

- a region, or a set of sub-regions removable by one exact transformation, has a median share of at
  least 20% of hooks-absent move samples over the eight launch/phase combinations, and at least 15%
  in every combination;
- the region-census counts show work an exact change can remove (for example repeated reads), and
  the transformation keeps vanilla collision results and ordering, intermediate bounds and all three
  `setShape` publications, step/border/entity behaviour, every callback (`addCollisions`,
  `onEntityCollision`, `beforeCollision`, `checkFallDamage`, `setOutsideWorldBorder`) with its
  arguments and order, `onGround`/velocity effects, and world reads seen after any callback;
- no previously failed experiment (17-19) is re-promoted or rebranded, and installed-but-off is not
  used as the allocation control.

If no region qualifies, no candidate is implemented; the session documents measurements and gaps.

## Gates for an eligible candidate (fixed now; launches only after a separate freeze)

Correctness and lifecycle: full Java 25 build/tests; opt-in verification comparing every replaced
answer with vanilla's over at least 10 million operations at scales 2 and 1 with the existing
particle and removal diagnostics, zero mismatches; actual world replacement and chunk load/unload
during capture; collision-list order and intermediate shape traces compared for paired moves;
hooks absent unless requested; option outside `render.defaults` until selected.

Performance, eight in-process launches at scale 1 (A1/B1/B2/A2 and B1/A1/A2/B2 alternating, both
paths warmed) plus four at scale 2, and four startup pairs at scale 1 (hooks absent versus on,
alternating order), fresh identical copied worlds, no diagnostics. Per phase, medians of per-launch
pooled changes:

1. particle tick lower in at least 6/8 (scale 1) and 3/4 (scale 2), median saving at least 5%;
2. tick-frame mean saving at least 2%; tick-frame p99 regression at most 5%;
3. ordinary (non-tick) frame mean regression at most 1%, frame and non-tick p95/p99 at most 5%;
4. client CPU/frame not higher; process CPU/frame regression at most 2%;
5. allocation/frame and tick-frame allocation regression at most 2%;
6. particles ticked and rendered within 2%;
7. flag |A2/A1 frame drift| over 10%; with median absolute drift over 10%, B must also beat A1 and
   A2 separately in the particle tick in at least 6/8.

Startup pairs (medians per side): particle tick and tick-frame mean lower, ordinary frame mean at
most +1%, CPU/frame not higher, allocation/frame at most +2%, p95/p99 and tick-frame p99 at most +5%.
Intrusive attribution and tick savings alone never select. Any failure keeps the candidate off.

Java 25 is explicit for Gradle, tests and the client; the runner checks the actual launcher
executable and JVM flags. Fresh identical copied worlds, 1920x1080, display lists, view distance
12, vsync off, 2 GiB heap. Serial launches, no overlap, unique labels/logs, no builds or analysis
during launches, source/tooling/protocol frozen in a hash manifest and ZIP before the first measured
launch; every attempt retained. No commit, push, publish, shared-config or driver change.
