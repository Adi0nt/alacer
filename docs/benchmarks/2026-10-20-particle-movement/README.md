# Session 17: benchmark isolation and particle movement

The shared development config no longer controls benchmark options. See
[configuration.md](configuration.md) for the reproduced failure, fix and regression evidence.
Normal game launches still respect saved settings; no shared config or historical archive
was edited. Java 25 remains explicit for Gradle, compilation/tests and the client launcher.

The roadmap's particle movement item is investigated with a retained vanilla control and an
explicit experiment, `render.fastParticleMovement` (default **false**, outside
`render.defaults`). Only block-state reads in particle movement and collision gathering use
the existing chunk-reference cache. It caches neither block states nor shapes. Vanilla still
performs all collision loops, entity queries, world-border checks, callbacks and movement
calculations. Other entities, world subclasses and threads use the world method. With the
experiment disabled at startup, its two mixins are absent.

## Evidence

- [Predeclared protocol](protocol.md): correctness/performance gates, eight warmed launches,
  both particle densities and explicit no-promotion conditions.
- [Fresh profile](profile.json), [JFR summary](jfr-summary.txt): before the prototype, intrusive
  detail on, Java 25, about 3,500 particles ticked. Static movement 1.470 ms of 2.039 ms particle
  tick; pan movement 1.421 ms of 1.969 ms particle tick. These are means only over frames
  containing particle ticks, not all rendered frames. Full-run JFR had 2,337 client execution
  samples: movement 5.0%, collision gathering 2.6%, entity querying 0.7% of those samples.
  Sampling includes startup/warmup and cannot establish a precise speedup. Initial profile
  source snapshot was not captured; its raw/JFR identities are retained.
- [Runtime verification](verification.md): final dense diagnostic compared 23,537,260 reads
  with zero mismatches; 288 boundary reads and 200 ordered collision-list/border checks matched.
  Remaining lifecycle, entity-collision, full-movement and visual checks are stated explicitly.
- [Performance comparisons](comparison.md) and [machine-readable metrics](metrics.json)
  were generated from all eight completed control launches. They retain per-launch results, frame
  and tick tails, allocation, CPU, populations and A1/A2 drift separately.

## Decision: keep the movement experiment disabled

All eight launches completed, with matching source identities and unchanged shared-config
hashes. Particle-tick cost fell in every phase comparison, **10.8–24.8%** (about 0.16–0.66 ms
per tick frame). However, the normal-density static group failed the predeclared gates:
median ordinary frame mean **+3.21%**, frame p95 **+10.56%**, client CPU/frame **+2.88%**,
allocation/frame **+3.22%**. That group improved particle tick by 12.86% and whole tick-frame
mean by 3.61%, demonstrating why a tick saving alone is insufficient. Three of its four
launches had ordinary frame-mean regressions. No launch is removed as an outlier.

| Scale / phase | Median particle tick change | Median tick-frame mean change | Median ordinary frame mean change | Performance gates |
|---|---:|---:|---:|---|
| 1 / static | -12.86% | -3.61% | +3.21% | FAIL |
| 1 / pan | -15.41% | -6.63% | -1.38% | PASS |
| 2 / static | -14.70% | -7.84% | -2.50% | PASS |
| 2 / pan | -17.44% | -8.15% | -3.42% | PASS |

Ordinary frame means regressed in 6 of 16 comparisons. Whole tick-frame p99 regressed in
5 of 16 (up to 22.81%); these tails have only about 200 static or 300 pan tick frames per
variant. Particle populations differed by at most 0.67%. Tick-frame allocation and non-tick
allocation remain approximately unchanged: this candidate removes hash lookups, not object
allocations. All-frame allocation/frame also varies with how many frames contain a tick.
The report retains these distinctions and all per-launch numbers. Do not interpret the
all-frame allocation regression as a measured allocation inside the new cache lookup.

The experiment remains `false`, excluded from `render.defaults`; the eight shipped
optimizations retain their defaults. Correctness coverage also remains incomplete as listed
in `verification.md`. Next particle work should investigate the remaining movement cost and
immutable `Box` allocations, with stronger lifecycle/full-movement checks and the same
ordinary-frame gates. Do not select this candidate from its tick-only saving.

## Reproduce

```powershell
.\gradlew.bat build --console=plain
python tools/run_particle_movement.py diagnostics
python tools/run_particle_movement.py controls
python docs/benchmarks/2026-10-20-particle-movement/analyze.py
```

The helper skips already archived labels and refuses to overwrite raw runs or logs with the
same label. Each launch checks for another client, source changes, shared-config changes,
the Java/runtime/backend/scenario identity, phase completeness and effective options, plus
verification and rendering failures. New measurements should use fresh labels and a new
archive; exact individual commands are retained in `runs/*.record.json`.

For an independent replay, pass a fresh `--prefix` and a new `--archive` directory within
the checkout to both launcher groups, then pass that directory to the analyzer's `--archive`.
This keeps the completed session's labels and records intact.

Raw CSVs and JFR stay in ignored `run/`, full launch logs in ignored `build/`. Records include
their SHA-256 values and a compressed snapshot of the measured source. The analyzer checks
those archived source bytes instead of requiring today's working tree to match them. Build
and unit-test success are separate from runtime verification and performance evidence.

No third-party performance-mod implementation was copied. Nothing was committed, pushed or
published, and no driver settings were changed or driver threading mode inferred.
