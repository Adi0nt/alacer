# Roadmap recovery and Phase 3.1 native scheduling result

October 24, 2026, Windows reference machine. Local source changes based on `91efebe`.
The earlier [audit](../../audits/2026-10-24/subsystems.md) and its crash/counterexample
evidence remain historical before evidence. This follow-up repairs those failures;
it does not overwrite that audit or the October 23 software-renderer archives.

**Outcome:** native world loading works again; 225 Java tests pass; the repaired
verification paths run on display lists and VBOs. The frozen Phase 3.1 core series
completed 16/16 valid launches, but **render.chunkTimeFloor fails selection and stays
off**. No optimization default was enabled, no release was published, and no PR or
remote branch was changed.

## Repairs

- **Phase 3.2 worker cache:** replace invalid NEWARRAY/ANEWARRAY injection points
  with supported constructor field/constant/return hooks. A compile wrapper acquires
  a per-thread scope and releases it in finally. Separate live regions, including
  nested compiles, receive distinct arrays. Standalone regions retain vanilla allocation.
  Release clears retained BlockState references. Verification constructs a separate,
  unpooled vanilla region, compares both block and light queries, and returns the
  reference answer. Diagnostic counters use LongAdder. Return-value hooks keep the
  disabled verifier from requiring per-query callback objects/boxed light results.
  Two small zero-length arrays remain per pooled constructor; the large 8,000-element
  allocations are avoided. This is not a claim of zero allocation or measured speedup.
- **Phase 4.2 frustum:** replace nested FMA with multiply/add operations in vanilla's
  left-to-right rounding order. Share the conservative foreign-frustum-mixin guard.
  Introduce a lazy boundary without Vector API types so an unresolved Vector module
  selects the scalar/culler path without linking vector classes. Normal, no-AVX and
  no-module probes pass; the cancellation counterexample is also a unit regression.
- **Phase 4.1 visibility reuse:** remove approximate movement thresholds. Reuse now
  requires exact camera/current/previous pose, interpolation, eye height, perspective,
  frustum planes/offsets, options, storage identity and compiled-data references.
  Render-dirty events invalidate the state; only a completed stable-input search
  updates it. New render.cameraHysteresis.verify compares retained candidates against
  a real vanilla search. No claim of a performance win from this stricter guard is made.
- **Entity culling:** resolve renderer policy before applying the base distance-first
  predicate. Subclass/foreign policies, including leash/guardian behavior, retain
  virtual dispatch. Verification returns the reference decision. Unit regressions
  cover renderers that intentionally accept or reject contrary to the base rule.
- **Raycast verification:** compare with the actual intercepted vanilla method,
  remove the separately maintained reference DDA, and include precise hit coordinates,
  block/face/type and entity identity. Verification returns vanilla's result.
- **Ambient ticks:** resolve and validate the callback name through runtime mappings;
  unknown mappings keep callbacks. Cache by class rather than numeric block ID.
  Preserve vanilla's per-tick Random lifetime. Foreign base/air/client-world mixins
  and client-world subclasses conservatively retain vanilla. Production-name resolver
  tests cover mapped, inherited, overridden and unavailable methods.
- **Model transforms:** route both zero-translation options through the existing
  matrix verifier, compare raw matrix bits, and correct descriptions that advertised
  unimplemented hierarchy flattening/push-pop removal. Verification remains sampled.
- **Occlusion/benchmarking:** occlusion verification also works when the candidate
  flag is off and uses vanilla's decision. Both-path warmup registration now includes
  the new entity, model, worker, raycast, ambient, sound, visibility and vector options.
  Benchmark completion logs expose the relevant verification counters.

## Validation layers

Gradle daemon criteria require Java 25; --release 25 remains explicit. The test
executor is Microsoft OpenJDK 25.0.3; native clients and standalone probes used
Adoptium 25.0.4.1+1-LTS from the explicitly checked installation. Runtime launcher
selection is independent of JAVA_HOME; the reference runner constrains the vendor
and validates the actual executable. See [validation.json](validation.json), retained
test logs, and the frozen manifest's source/artifact hashes.

| Check | Evidence |
| --- | --- |
| Java suite | 225 tests, zero failures/errors/skips, including new scope/reentry, rounding, mapping, renderer-policy, hit-coordinate and exact-input tests |
| Runner suite | 8 tests pass; the real native core series also exercised its launch/freeze/receipt checks |
| Packaging | remapJar succeeds; class files target Java 25 |
| Boundary/fallback probes | corrected cancellation decision passes normally and with -XX:UseAVX=0; factory returns fallback without the Vector module |
| Native display-list verifier smoke | 61,161,044 block queries; 8,558,776 light queries; 9,401,716 vector boxes; 14,665 raycasts; zero mismatches |
| Final native VBO verifier smoke | 54,297,126 block queries; 7,523,563 light queries; 5,524,581 vector boxes; 8,775 raycasts; zero mismatches |
| Final VBO other verifiers | 953,223 culling decisions; 2,328,007 renderer lookups; 209,782 ambient block checks; 432 sampled zero translations; zero mismatches |
| Stable visibility | zero mismatches reported with unchanged compiled inputs; the last VBO checkpoint had 41 concurrent-publication comparisons classified as inconclusive, not passes |
| Rendered output | captured and inspected actual terrain/entities for display-list and VBO smokes; this is not a paired pixel-identity certification |

Smokes use added 200 mobs, 854x480 and short static/pan/fly phases with strict/debug
checks, so their timing is **not scored performance evidence**. The first worker/vector
smoke was an earlier local recovery step; final VBO smoke and reference series use the
frozen repaired sources. Full logs are retained here. Probe commands/output are in
[probe-results.json](probe-results.json); its source is RecoveryProbe.java beside the
reference protocol. The old audit probe intentionally expects the old defect and is
preserved unchanged.

## Phase 3.1 frozen native core series

[Protocol](../2026-10-24-chunk-scheduling-reference/protocol.md),
[manifest/source freeze](../2026-10-24-chunk-scheduling-reference/native-core-01/manifest.json),
[evaluation](../2026-10-24-chunk-scheduling-reference/native-core-01/evaluation.json).

Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA 616.92, Adoptium Java 25.0.4.1, 1920x1080,
view distance 12, display lists, 2 GiB heap. Eight edit launches followed by eight
near flights, ABBABAAB in each scene, four adjacent pairs normalized B/A-1. A has
both scheduling experiments off; B enables only chunkTimeFloor. Ordinary profiling
and chunkLatency are on; intrusive/verifier/GPU-query/JFR paths are off.

**Validity:** 16 completed launches; no retries, failures, dropped frames or latency
overflow. Every launch passed runtime, actual backend, scene/phase, shared config,
base-world, source and compiled-artifact checks. The post-series check, including
after the final VBO smoke, found the frozen state unchanged. All 16 raw result folders
plus the unscored reference smoke are preserved in raw-results.zip; its receipt/hash
is [raw-archive.json](../2026-10-24-chunk-scheduling-reference/native-core-01/raw-archive.json).

Median paired percentage change is the gate statistic; A/B columns below are the
separate medians of four runs and must not be divided to reproduce that statistic.

| Scene/metric | A median | B median | Median paired change | Frozen rule/result |
| --- | ---: | ---: | ---: | --- |
| Edit update latency p90 | 86.70 ms | 72.03 ms | -16.85% | needs <=-25%, lower in 4/4; **fail** despite 4/4 lower |
| Edit frame mean | 2.291 ms | 2.245 ms | -1.00% | <=+1%; pass |
| Edit frame p99 | 4.848 ms | 4.746 ms | -0.75% | <=+2%; pass |
| Edit slow-1% mean | 5.732 ms | 5.605 ms | -1.20% | <=+2%; pass |
| Edit client CPU/frame | 2.086 ms | 2.074 ms | -0.22% | <=0%; pass |
| Edit still/no-op frame mean | | | -6.60% | must stay within +/-5%; **fail** |
| Flight update latency p90 | 919.80 ms | 698.01 ms | -18.13% | <=-10%, lower in >=3/4; pass (4/4) |
| Flight visible uncompiled chunks | 135.24 | 111.33 | -19.02% | <=+5%; pass |
| Flight frame mean | 4.049 ms | 4.480 ms | +10.74% | <=+1%; **fail** |
| Flight frame p99 | 7.896 ms | 9.575 ms | +20.59% | <=+2%; **fail** |
| Flight slow-1% mean | 9.745 ms | 11.340 ms | +14.84% | <=+2%; **fail** |
| Flight client CPU/frame | 3.857 ms | 4.282 ms | +10.97% | <=0%; **fail** |
| Flight still/no-op frame mean | | | -0.36% | within +/-5%; pass |

**Decision:** fail six gates. chunkTimeFloor remains off and outside render.defaults;
feedChunkWorkers was not scored and remains off. No expanded 80-launch series or
default promotion follows from these data. The fixed gates were not relaxed or
reinterpreted. The edit no-op drift is also evidence of launch variance; this study
does not establish 2% detection power or identify a driver/scheduler root cause.

The floor trades improved update responsiveness/fewer visible uncompiled chunks for
consistently worse flight frame/CPU cost in all four pairs. That is a measured tradeoff,
not a universally improved scheduler. These results belong to this repaired source
snapshot and display-list backend; do not pool them with earlier software rendering
or use them to select a VBO staging architecture.

## Remaining roadmap work

Phase 3.1's native **core** comparison is finished and failed. A separate, bounded
queue/buffer attribution study can determine whether feedChunkWorkers merits its own
registered reference series. It is not selected by the floor result.

Before Phase 3.3 staging, attribute VBO upload tails separately, preserve the existing
direct-upload control and only then freeze an original fenced-staging experiment.
Phase 6.1 still needs shared resident mesh arenas and per-command origins before MDI
submission. The designs in the audit remain proposals, not implementations or wins.

Residual correctness/coverage limits remain explicit: model verification samples
matrices; ambient checks are not an identical particle/sound-stream replay; the mapped
resolver is tested but the remapped production jar was not launched; no listening-based
sound/lifecycle certification or native module-disabled launch was performed. No
retained-list hit-rate/performance claim or arbitrary foreign-mod compatibility is
certified. Renderer-map mutation during a world remains a separate cache concern.
The repairs have not received a before/after performance qualification of their own.
