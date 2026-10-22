# Session 21: particle light entry (`render.fastParticleLightEntry`)

Roadmap item 0 after session 20. Fresh Java 25 JFR of the shipped defaults (detail off) charged
7.5-11.5% of the particle pass to `World.isChunkLoaded(pos)` in `Entity.getLightLevel` (and again in
`BlockParticle.getLightLevel`), as an inlined `ServerChunkCache.hasChunk` hash lookup, and showed
`getLightLevel`'s `BlockPos` copy as the pass's only sampled allocation in pan. The client world's
chunk source is `ClientChunkCache`, whose `hasChunk` returns a constant `true` (checked with javap
on the runtime jar), so the check is vanilla's `World.contains` range test plus that call; the hash
lookup attribution comes from `World.isChunkLoadedAt`'s compiled call site, which the integrated
server's worlds share.

`render.fastParticleLightEntry` (startup-gated, runtime-toggleable, `.verify`) answers that check for
particles in the exact `ClientWorld` with the exact `ClientChunkCache`, on the client thread, with
the same range test, and, when optimization 7 answers the light query (both on, neither verifying),
replaces the position copy with one reused mutable position holding vanilla's floored coordinates.
See ARCHITECTURE.md, session 21.

## Decision: off by default (retained experiment), not in `render.defaults`

Correctness passed: **181,454,290 chunk checks (7,981,313 not loaded) and 152,140,431 positions
compared, 0 mismatches** at scales 2 and 1 with both particle diagnostics (verification.md), plus
unit tests. Performance failed: **all four predeclared A/B groups failed** and one of the four
startup comparisons failed, so the protocol keeps the option off. Its hooks are not installed
unless the option, its verification or a benchmark toggle of it is on at startup, so the default
client runs vanilla's code there.

| Scale / phase | Particle pass | Frame mean | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Pass lower | Median A1/A2 drift | Gates |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | -1.70% | -1.92% | -0.27% | -67.88% | -0.98% | -0.24% | +1.47% | +11.15% | 3/4 | 14.98% (noisy) | FAIL (2, 6d, 8) |
| 1 / pan | +1.16% | +1.68% | +1.48% | -60.17% | -2.10% | +2.26% | +1.47% | +1.40% | 2/4 | 2.95% | FAIL (1, 2, 3, 4) |
| 2 / static | +1.08% | +1.67% | +1.66% | -61.86% | +2.01% | +3.27% | +2.39% | -0.54% | 1/4 | 4.60% | FAIL (1, 2, 3, 4) |
| 2 / pan | -3.48% | -3.98% | -3.30% | -56.78% | -6.75% | -4.60% | -3.33% | +6.45% | 3/4 | 6.66% | FAIL (6d) |

Medians of per-launch B-vs-pooled-A changes over eight warmed in-process A/B/B/A launches (four per
scale, scale order alternating by round, frozen source `sources-adcbef098e0dbd77.zip`, every option
explicit). Gate numbers refer to protocol.md. Per-launch pass changes ran from -9.6% to +32.2%.

**Not excluded:** `le21-ab-2-r4` regressed in both phases (pass +32.2% / +17.9%, frame mean +40.6% /
+26.3%): its B segments were slow throughout the launch, a disturbance of the kind seen in session
20's r4. Without it the scale-2 static median pass change would still be about +0.6%, so the
decision does not depend on it. `le21-ab-1-r4` pan regressed +9.1%; scale-1 static was flagged noisy
(median A1/A2 drift 15%). The particle draw call was unchanged (median 0.185 -> 0.196 ms at scale 1
static, otherwise within 0.005 ms).

### Startup-on versus hooks absent (separate launches, per scale absent/on/on/absent)

| Scale / phase | Frame mean | Particle pass | CPU/frame | Frame p95 | Frame p99 | Alloc KB/frame | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| 1 / static | 0.835 -> 0.824 (-1.2%) | 0.562 -> 0.562 (+0.1%) | -1.3% | -4.4% | -1.8% | 76.5 -> 33.9 (-55.7%) | FAIL (pass) |
| 1 / pan | 1.299 -> 1.219 (-6.2%) | 0.680 -> 0.656 (-3.6%) | -3.5% | -9.3% | -6.7% | 122.4 -> 52.6 (-57.0%) | PASS |
| 2 / static | 1.086 -> 0.993 (-8.6%) | 0.756 -> 0.696 (-7.9%) | -5.9% | -15.4% | -6.6% | 77.4 -> 60.0 (-22.5%) | PASS |
| 2 / pan | 1.505 -> 1.463 (-2.8%) | 0.847 -> 0.820 (-3.3%) | -1.4% | -6.8% | -5.3% | 175.8 -> 90.8 (-48.4%) | PASS |

Two launches per side, cross-launch: within each side the two launches differ by up to 17% (for
example startup-on scale 1 static 0.761 vs 0.888 ms, scale 2 static 1.038 vs 0.948 ms), as large as
the differences between sides. The absent launches' scale-1 tick-frame p99 (39-46 ms) are outliers.
These favorable medians do not override the failed in-process groups, which compare the paths
within one JVM.

### Why the attributed cost did not turn into a saving

The candidate profiles (profile.md) show the entry samples and the pass's `BlockPos` allocations
gone, but the light share of the pass unchanged (36-43%): the samples reappear in optimization 7's
light query (`ParticleLight.brightness`, `fastLightColor`, the redirect handler) and in
`getLightLevel` itself. With `hasChunk` a constant on the client, the work removed was a range test,
an interface dispatch and a 24-byte allocation per particle; the time JFR charged to the inlined
entry belongs mostly to the light query that follows (its chunk-section reads), which still runs.

**Allocation.** The installed-but-off hooks made vanilla's path allocate more than a client without
them (A segments 115-212 KB/frame against 76-176 KB absent): the redirected `isChunkLoaded` call and
the `NEW` redirect defeat C2's escape analysis of vanilla's `BlockPos`, which the unhooked client
partly scalar-replaces. So the in-process allocation savings (-57% to -68%) overstate the saving
against a default client; the startup comparison (-22% to -57%) is the relevant one. Allocation is
not a selection criterion on its own in the protocol, and a few tens of KB per frame of short-lived
`BlockPos` objects did not show up as time.

## Recommendation for the next item

The remaining particle pass is light queries (27-34% with the entry removed, chunk-section reads in
`ParticleLight`), each particle class's own render code (28-31%), the manager loop (15-17%), vertex
writes (12-15%) and the draw. The next bounded light candidate is not the entry but **reusing light
answers within one pass**: the client world should not change while `ParticleManager.render` runs
(the client thread applies block, light and chunk changes in its tick and packet handling, not during
the pass; to be confirmed for the design), and thousands of particles cluster around a few emitters, so many query the same block. First
measure the distinct-position ratio per pass with an always-off counter, then, only if it is low,
design a per-pass position-keyed memo of optimization 7's answer with verification against
`World.getLightColor`, and the same gates. Otherwise continue with roadmap item 1 (particle
movement), whose session 17-19 experiments stay disabled.

## Files

- `protocol.md`: predeclared gates (written before the frozen manifest and every measured launch).
- `verification.md`: verification design and live results.
- `profile.md`, `profile-baseline-*.txt`, `profile-candidate-*.txt`, `jfr_attribution.py`,
  `jfr_stacks.py`: JFR attribution before and with the option.
- `comparison.md`, `tails.md`, `metrics.json`: generated by `analyze.py` from `runs/` (validated
  against exported files, logs, shared-config hash and the frozen manifest/snapshot).
- `manifest.json`, `sources-adcbef098e0dbd77.zip`: frozen source (option default false, toggled or set
  by the runner); `final-source.diff` is the only source change since: `render.defaults` switches the
  option off like the other experiments, and `AlacerConfigTest` checks the default, toggle and preset.
- `runs/`, `attempts/`: every launch's record, summary and command (18 measured/verification
  launches, 2 profiles; no failed attempts).

## Reproduction

```bash
cd tools
python run_particle_light_entry.py diagnostics   # le21-check-2, le21-check-1 (verification)
python run_particle_light_entry.py controls      # le21-ab-{1,2}-r{1..4}
python run_particle_light_entry.py startup       # le21-startup-{absent,on}-{1,2}-r{1,2}
python run_particle_light_entry.py profiles      # le21-profile-{1,2} (JFR, attribution only)
python ../docs/benchmarks/2026-10-20-particle-light-entry/analyze.py
```

The runner refuses reused labels/logs and overlapping clients, checks the source tree against
`manifest.json` before and after every launch, passes every render option, `.verify` and profiler
switch explicitly, and validates each phase's exported config. Java 25.0.4 (Adoptium), 1920x1080,
display lists, view distance 12. Nothing was committed; no driver settings, shared config
(`694ff84d...`) or earlier archives changed.
