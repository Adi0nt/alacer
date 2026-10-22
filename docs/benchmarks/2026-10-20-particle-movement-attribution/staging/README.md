# Roadmap 1: staged particle movement census

Diagnostic-only source copies, deliberately outside the production source set.
Nothing here is integrated, compiled, transformed or runtime-validated. No client
was launched. This is attribution, not another movement replacement or selection
result. The light confirmation and all earlier frozen evidence remain controls.

Three files under `staging/src/main/java/` mirror their eventual package paths:
`render/ParticleMovementCensus.java`, `mixin/EntityParticleMovementCensusMixin.java`
and `mixin/WorldParticleMovementCensusMixin.java` in `net/adiont/alacer/`.
They reuse the existing `profiler.JsonWriter` and installed MixinExtras API;
no Gradle/dependency change is proposed. Do not add staging to a source set.

## Integration only after restoring the light frozen source

1. Copy the three Java files to their matching production package paths in a
   separately recorded movement-attribution source snapshot. Preserve the NaN
   vertex comparison fix. Do not modify or refreeze any light archive.
2. Add both mixin simple names to the mixin config's `client` list. In the
   mixin plugin, gate BOTH names before its permissive default return, using the
   following startup rule, also evaluated by `ParticleMovementCensus.ENABLED`:

   ```java
   Boolean.parseBoolean(System.getProperty("alacer.profiler.particleMovementCensus", "false").trim())
       && Boolean.parseBoolean(System.getProperty("alacer.benchmark", "false").trim())
       && Boolean.parseBoolean(System.getProperty("alacer.profiler.enabled", "true").trim())
   ```

   Mirror this rule in the plugin without loading the census/Minecraft classes
   from plugin initialization. No config-file override or benchmark toggle is
   needed. With any gate false both mixins MUST be absent, not installed-but-off.
3. On the client thread call `ParticleMovementCensus.phase(null)` before scenario
   setup and ALL replay/probe calls. This establishes ownership with capture off.
   Keep it off during preparation, settle and warmups. In `BenchmarkRunner`'s
   `onFrame` / `phase.capturePending` branch, alongside the existing draw diagnostic
   activation AFTER the setup frame is discarded, call:

   ```java
   ParticleMovementCensus.phase(phase.name);
   ```

   Call `phase(null)` at `stopRecording` entry, before world replacement/abort,
   and before any later diagnostic replay. Names containing `warmup` are rejected
   from capture automatically (including `warmup-static-a` and `pan-warmup`).
   Repeated scored names accumulate in the same bucket; do not rearm every frame.
4. At normal benchmark export, on the owner thread and between moves, call
   `ParticleMovementCensus.finish(dir)`. It closes capture and writes ONLY a new
   `particle-movement-census.json` using `CREATE_NEW`; it refuses overwrite. An
   I/O failure can be retried with a fresh output directory. No hook performs I/O.
   Record ENABLED and all effective properties in environment/launch metadata.

These are instructions, not edits to the runner, plugin or mixin config. In the
inspected runner the analogous draw calls were in `stopRecording`, the
`capturePending` branch, and `finish`; locate by method rather than old line numbers.
Capture is a client call-window census, not an independently verified exact CSV
frame-window join. Record its boundary placement with the source snapshot.

## Source anchors and query meaning

Original mapped sources are available at
`build/session18-source/net/minecraft/entity/Entity.java` and
`build/session18-source/net/minecraft/world/World.java`; their contents match the
local Loom Minecraft source JAR after normalizing line endings. JAR SHA-256:
`9a8846594ccc8ac1b4f0913caecd5dd8fcbdbc256dc9784d89b8eec0d06ff78b`.

- `Entity.move(DDD)V`: lines 399-661. Entry offsets are recorded BEFORE cobweb
  scaling. Both signs of zero count as zero; nonfinite input is separately flagged.
- The five raw `World.getCollisions` call sites are at 425, 437, 447, 467, 497.
  The first three are player sneak-edge checks: raw ordinals 0/1 are NOT the
  ordinary/step sites. Both expression hooks slice from the first
  `Box.expanded(DDD)Lnet/minecraft/util/math/Box;` at 467 to method end. Within this
  slice getCollisions ordinal 0 is ordinary and ordinal 1 is the actual step query.
  Each expression hook requires and allows exactly one match.
- The invocation descriptor is
  `Lnet/minecraft/world/World;getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;`.
  World HEAD/RETURN observe that exact method (original body at 895-948).
- Ordinary resolution has three scans (470, 477, 483). A returned step query
  supplies seven scans (502, 509, 516, 524, 531, 538, 557). Returned-move work
  estimates are therefore `3 * ordinary size` and `7 * step size`. They are NOT
  observed clip-invocation counts, nor numbers of Boxes allocated. Custom list
  mutation or transformed loops invalidate this structural interpretation.
- `positive_step_height_at_entry` is eligibility only. `step_query_returns` is
  an observed successful return from the actual step call site; it is neither
  successful stepping nor accepted-step/rollback count. No such counts are invented.

## Reading the diagnostic

Output has phase totals and class identity slots (64 named classes plus overflow).
Each reports started/returned moves, individual nonzero axes, all-zero vectors,
noClip/positive stepHeight at entry, World query calls/returns, result size sum,
empty/max results, ordinary/step query returns and size sums, scan-work estimates,
and completed-move wall/gather/rest totals. An empty phase is exported with zero
counters; zero results are not proof that a hook matched.

World gather timing is a coarse HEAD/RETURN host-wall interval. Same-move nested
queries contribute their time union; move wall minus gather is the residual
(clipping, callbacks, fire checks, scheduling and instrumentation). It is NOT
client CPU time. No per-element clocks or list traversal are added. Nested moves
have inclusive overlapping timings and are explicitly counted; require zero nested
moves for uncomplicated phase timing comparisons. World overrides that bypass
the base body can produce site results without World clocks: check coverage.

Only exact vanilla `ArrayList` sizes are read, without iterating or retaining lists.
Unknown/null results are counted and omitted from size/scan estimates to avoid
calling potentially observable custom-list methods. No shape getter, setter,
world query, block callback, collision arithmetic or result identity is replaced.

State is bounded: 16 phase buckets (96-character names), 64 class identities plus
one aggregate overflow bucket, and 16 reusable move frames. No per-hook census
allocation is intended; Mixin callbacks/instrumentation can still allocate or
change escape analysis. Only active frames retain entities, and phase boundaries
and finish clear them. Other threads are ignored. No ThreadLocal or world cache.
HEAD/RETURN cannot unwind exceptions: incomplete scopes, query imbalance and
depth overflow are reported and cleared at boundaries. Reject anomalous captures;
this census is not an exception-safe movement wrapper.

## First integration checks (not performed here)

- Verify default/benchmark-false/profiler-false startup controls omit BOTH hooks.
  Confirm every injector matches the mapped descriptor, the slice, and its target
  count on the restored source before trusting counters. Compilation alone will
  not establish transformed hook application.
- For attribution keep experiments 17-19, light entry/reuse, other intrusive detail
  flags and all `.verify` flags off; retain all nine shipped optimizations and the
  NaN vertex fix. Use explicit Java 25, fresh source identity, scales 1/2, static/pan.
- Set `benchmark.particleDiagnostic`, `particleRemovalDiagnostic`,
  `particleEntityQueryDiagnostic`, `particleBoxDiagnostic` and
  `particleMovementDiagnostic` false (all with `alacer.benchmark.` prefix).
  Probes/replays must run with `phase(null)`, never inside scored capture; inspect
  class coverage for unexpected Probe classes. The census does not silently filter
  particle subclasses. The excluded counter starts at the first `phase(null)`;
  earlier ignored probes cannot be claimed as counted exclusions.
- Expect started = returned, World calls = returns, zero overflow/imbalance and
  zero unknown lists. Confirm ordinary-query coverage for collision-enabled moves
  and count actual step-query returns separately from positive stepHeight entries.
  Existing `ParticleEntityQueryChecks` replays (139-165, 262-276) trace intermediate
  setters/fall/block checks; run them separately, with census capture off. Check
  original result identity/order and gameplay state, not just final position.
- Compare hook-absent, installed-but-inactive, and active-census companions for
  perturbation. Pair with scored-window JFR; allocation sites and wall-time
  residuals alone do not select a scalar replacement. Retain all failures and
  per-phase populations/tick counts. No runtime results are supplied by staging.

Staging-only review checked five original getCollisions sites and two in the
chosen slice, the local MixinExtras 0.5.5 ModifyExpressionValue annotation API,
original list passthrough, bounded state and reference cleanup. A combined hash
over 210 existing src/build.gradle/session22-light-reuse files was identical
before/after staging (`8f0a3114a00065fd77b2a780f8791c2680359d88e0f7affb492956e8fdeb5455`).
This is file-preservation/static evidence only, not Java compilation, transformed
injector validation, timing validation, gameplay correctness or runtime coverage.
