# Session 19: scalar particle nearby-entity query

The roadmap's remaining movement/Box work was investigated from the retained Session 17/18
controls. Nonzero Y/X/Z boxes cross virtual `setShape` boundaries. A separately gated candidate
instead removes materialization of the grown bounds inside `World.getCollisions`' nearby-entity
query. It uses scalar bounds while retaining loaded checks, chunk/section order, shape tests,
filtering, parts and result identity/order. Immutable movement boxes, block collision gathering,
border behavior, axis/step resolution, intermediate setters and callbacks remain vanilla.
No third-party performance-mod implementation was used.

`render.scalarParticleEntityQuery` stays **false**, outside `render.defaults`; its one World
mixin is absent unless requested at startup. Both earlier experiments remain false. All eight
shipped optimizations and the shared config are preserved.

## Decision: retain the disabled experiment

All eight corrected-source Java 25 A/B/B/A launches completed. **All four density/phase groups
failed the predeclared gates.** Allocation per tick frame fell **12.32-13.97%** in every one
of the 16 comparisons, but particle tick regressed in **6/16** (range **-8.54% to +3.60%**).
Every group failed the requirement that particle tick improve in every launch.

| Scale / phase | Median particle tick | Allocation/tick frame | Ordinary frame mean | Frame p95 | Client CPU/frame | Gates |
|---|---:|---:|---:|---:|---:|---|
| 1 / static | -2.83% | -13.72% | -0.09% | +2.50% | +0.29% | FAIL |
| 1 / pan | -2.14% | -12.98% | -0.35% | -2.58% | -0.74% | FAIL |
| 2 / static | +1.21% | -12.93% | +2.75% | +6.18% | +4.07% | FAIL |
| 2 / pan | -1.86% | -12.52% | -1.66% | -1.34% | -0.55% | FAIL |

Dense static also missed the median tick-saving gate, ordinary mean/p95, whole-tick p99 and
CPU gates. Dense pan missed the 2% median tick-saving gate. Scale-1 pan and scale-2 static
failed the predeclared 10% control-drift gate. A2/A1 frame-mean drift ranged **-16.42% to
+44.17%**; no final launch was excluded. Tick-frame p99 regressed in 7/16 (up to **23.53%**),
ordinary-frame mean in 8/16, p99 and CPU in 9/16. Particle populations differed by at most
0.45%. Per-frame allocation fell in every comparison, while allocation/second and non-tick
allocation are reported separately. Natural client entity counts varied 8-10 across launches.

These A/B results compare two paths with the new startup hook installed. They do not establish
an allocation benefit against a startup-disabled vanilla client. Earlier baseline JFRs sampled
the grown allocation; the final startup-disabled profile did not. JIT/sampling sensitivity and
the hook-only follow-up are documented in profile.md. Sample absence is not proof of zero
allocation, and intrusive/cross-launch profile timings do not select the candidate.

## Correctness and the retained correction

All **65 Java 25 tests** pass. Final real-client verification compared **3,405,003 queries**
with **zero mismatches**, plus **10,306 direct checks**, **5,760 paired movement steps** and
an explicit real boat collision check. Bounds use raw double bits; movement compares primitive
Entity/Particle state and intermediate shape/fall/block-collision traces. Positive entity/part
fixtures compare duplicate identities, order, exclusion and virtual-call traces.

Review found that the first version passed different equal Box objects to separate virtual
fallbacks. A new regression fixture failed on that version, then passed after the fix: all
fallbacks now share one lazily materialized immutable Box per query. Five completed earlier
controls and the failed command/log/source are preserved; they are superseded, not selection
evidence. All eight final controls use fresh `pe19v3-ab-*` labels on the corrected source.
See correction.md and verification.md for the before/after evidence and exact scope.

Visual gameplay, actual network chunk/dimension lifecycle transitions, continuous borders,
SyncedData and exhaustive external callback effects remain incomplete. Synthetic parts do not
certify a complete dragon integration or arbitrary mod injections. No selection or default
change is justified, independently of the failed performance gates.

## Evidence and reproduction

- [Protocol](protocol.md), [source investigation](source-investigation.json), [profile](profile.md).
- [Verification](verification.md), [correction and retained failure](correction.md).
- [All final comparisons](comparison.md), [tails/CPU](tails.md), [complete metrics](metrics.json),
  [selection](selection.json), [validation/provenance](validation.json).
- implementation.patch is relative to the initial Session 19 pre-change source ZIP. Final
  source ZIPs, commands, phase configs, export/log/JFR hashes and supplemental records are
  retained in this archive. validation-evidence.zip preserves tests/build/runtime/failure logs;
  tooling-source.zip preserves the runner, analyzer and validator. Raw CSV/JFR remain in ignored
  run/, full logs in ignored build/.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_entity_query.py diagnostics --prefix fresh --archive docs/benchmarks/fresh-query
python tools/run_particle_entity_query.py controls --prefix fresh --archive docs/benchmarks/fresh-query
python docs/benchmarks/2026-10-20-particle-entity-query/analyze.py --prefix fresh --archive docs/benchmarks/fresh-query
```

The runner refuses reused raw labels/logs and overlapping clients. The analyzer selects the
explicit final prefix and validates archived source bytes rather than requiring today's source.
For profile reproduction, use profile, candidate-profile and dispatch-profile groups with a fresh
prefix/archive, then tools/particle_box_jfr.py on each record. Copy analyze.py into an independent
archive before using the validator there; its historical packaging defaults target this session.

Next: establish startup-disabled and hook-only nonintrusive controls and current allocation/JIT
behavior, then investigate nonzero axis boxes and remaining block/axis loops. A larger scalar
movement region is allowed if independently justified, with explicit ownership and observability
gates for every intermediate shape, step, collision order and callback. Allocation savings alone
must not rebrand or combine the three failed experiments.

No shared config, optimization defaults, driver settings or historical archives changed.
Nothing was committed, pushed or published.
