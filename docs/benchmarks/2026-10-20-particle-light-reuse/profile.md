# Session 22 JFR attribution (detail off, scored windows, client thread; attribution only)

`jfr_attribution.py RECORDING BENCH_DIR` is session 21's script with one more category: samples whose
deepest light-related frame is `ParticleLightReuse` (the table lookup itself) count as "light reuse
table"; samples under `ParticleLight.brightness` called from the table (a position's first query in
a pass) still count as "light query". Candidate launches `lr22-profile-1/2` ran the frozen source with
the option on (`profile-candidate-*.txt`). The baseline is session 21's shipped-defaults profile
(`le21-jfr-1/2`, same optimization 7 code and the same scenario, before this session's hooks), so
the comparison is cross-session and cross-launch.

| Scale / phase | Frame mean | Pass | Pass share of client samples | Reuse table | Light query | `getLightLevel` self | Entry (`isChunkLoaded`) | Light total | Pass samples |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 / static | 0.730 ms | 0.495 ms | 67.2% | 6.2% | 5.1% | 6.2% | 3.4% | 20.9% | 353 |
| 1 / pan | 1.054 ms | 0.551 ms | 46.3% | 1.2% | 9.5% | 10.2% | 4.7% | 25.6% | 401 |
| 2 / static | 0.928 ms | 0.631 ms | 71.2% | 7.5% | 2.4% | 10.3% | 5.7% | 25.9% | 456 |
| 2 / pan | 1.462 ms | 0.779 ms | 52.6% | 5.8% | 5.0% | 12.5% | 4.6% | 27.9% | 520 |

Session 21 baseline (shipped defaults): light total 37.3% / 40.3% (scale 1 static/pan) and 40.4% /
39.5% (scale 2), of which the light query alone was 19-24%. With the table the light query falls to
2-10% of the pass and the lookup itself takes 1-8%; the light total falls by roughly 12-16 points.
The remaining light cost is mostly outside the query: `getLightLevel`'s own work and the
`isChunkLoaded` entry (session 21's subject), which the table does not touch. Each phase has only
350-520 pass samples, so single categories carry a few points of sampling noise.

Every allocation sampled inside the pass is vanilla's `BlockPos` in `Entity.getLightLevel` (the table
allocates nothing): almost none in scale 1 static (two samples), hundreds of sampled MB in the other
three phases. Session 21's baseline showed the same split (none sampled in static, all `BlockPos` in
pan), and whole-frame allocation per frame differs between launches with the same code by more than
the variants do (33 KB in this profile vs 74-111 KB in the scale 1 static startup launches; 104-192 KB
in scale 2 static), consistent with session 21's finding that C2's escape analysis of that
`BlockPos` varies by compilation. Passing the position on to the table (for verification) may also
affect it; this session did not isolate that.
