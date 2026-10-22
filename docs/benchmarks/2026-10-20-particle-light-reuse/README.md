# Session 22: per-pass particle light reuse (`render.reuseParticleLight`)

Roadmap item 0 after session 21. Step 1 measured before designing anything: a new opt-in counter
(`profiler.particleLightRepeats`) showed that one unlit particle pass makes about 3,910 light queries
at particle scale 1 and 6,355 at scale 2 but hits only about 395-416 distinct block positions:
**89.9% (scale 1) and 93.5% (scale 2) of the queries repeat a position already queried in the same
pass**, in static and pan alike, with `blockLight` always 0 (attribution/README.md). Only 6-7%
repeat the immediately preceding query, so a table is needed rather than a last-value memo. The
decompiled client confirms that nothing can change the client world while `ParticleManager.render`
runs (protocol.md), so reusing optimization 7's answer within a pass is exact.

`render.reuseParticleLight` (startup-gated pass hooks, runtime-toggleable, `.verify`) answers
optimization 7's queries inside each unlit pass from a 4,096-slot table of the sky and block
brightness already computed in that pass for the same (x, y, z), emptied at every pass. See
ARCHITECTURE.md, session 22.

## Decision: off by default (retained experiment), not in `render.defaults`

Correctness passed: **169,645,769 queries compared against `World.getLightColor`, 158,853,639 of them
answered from the table, 0 mismatches** at scales 2 and 1 with both particle diagnostics
(verification.md); a deliberately broken table that is never emptied produced 51.5 million
mismatches under the same verification. Performance: **three of the four predeclared A/B groups
passed with large savings, but scale 1 static failed**, and one of the four startup comparisons
failed (scale 1 static allocation). The protocol's selection rule requires all eight, so the option
stays off. Its hooks are not installed unless the option, its verification or a benchmark toggle of
it is on at startup.

| Scale / phase | Particle pass | Pass excl. draw | Draw call | Frame mean | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Pass lower | Median A1/A2 drift | Gates |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | +3.90% | -11.1% | +36.6% | +6.75% | +3.86% | +6.37% | +19.42% | +6.10% | +6.10% | +8.39% | 2/4 | 9.86% | FAIL (1-6) |
| 1 / pan | -11.84% | -20.8% | +4.9% | -9.12% | -9.60% | -2.98% | -9.41% | -5.74% | -4.10% | -4.25% | 4/4 | 6.06% | PASS |
| 2 / static | -13.82% | -22.9% | +22.3% | -12.67% | -13.16% | -12.13% | -15.00% | -7.15% | -6.19% | -3.47% | 4/4 | 8.43% | PASS |
| 2 / pan | -9.25% | -18.2% | +23.6% | -5.50% | -6.29% | -2.13% | -1.48% | +1.26% | +0.61% | -3.88% | 4/4 | 1.20% | PASS |

Medians of per-launch B-vs-pooled-A changes over eight warmed in-process A/B/B/A launches (four per
scale, scale order alternating by round, frozen source `sources-e281d3d4c90842f4.zip`, every option
explicit). Gate numbers refer to protocol.md. "Pass excl. draw" is the pass minus its
`Tesselator.end` call; it is not a gate.

**Scale 1 static.** Two launches improved (pass -3.6% and -18.3%) and two regressed strongly:
`lr22-ab-1-r1` (pass +11.4%, frame +15.9%, p95 +39.6%) and `lr22-ab-1-r4` (pass +19.5%, frame +26.6%,
p95 +57.6%). In both the CPU part of the pass barely changed (0.389 -> 0.372 ms, 0.397 -> 0.408 ms)
while the draw call grew (0.188 -> 0.271 ms, 0.179 -> 0.281 ms), and frame time grew by more than
the pass. Across all sixteen comparisons the pass excluding the draw fell in 15 and the draw call
took longer in all 16: the CPU saving partly turns into waiting at the draw, the next synchronizing
call, as session 20 saw at scale 1. At scale 1 static the frame is about 0.8 ms (over 1,200 frames
per second), the lightest load measured. Its group's drift was 9.86%, just under the 10% noisy flag,
with `lr22-ab-1-r3` (+63% A2/A1 drift) and `lr22-ab-1-r4` (-12.5%) flagged. No launch is excluded.

### Startup-on versus hooks absent (separate launches, per scale absent/on/on/absent)

| Scale / phase | Frame mean | Particle pass | CPU/frame | Frame p95 | Frame p99 | Alloc KB/frame | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| 1 / static | 0.826 -> 0.799 (-3.3%) | 0.561 -> 0.544 (-3.0%) | -3.9% | +2.7% | -2.2% | 73.7 -> 111.4 (+51.2%) | FAIL (allocation) |
| 1 / pan | 1.208 -> 1.125 (-6.8%) | 0.652 -> 0.598 (-8.3%) | -8.8% | -11.7% | -5.5% | 128.6 -> 128.8 (+0.2%) | PASS |
| 2 / static | 1.014 -> 0.918 (-9.5%) | 0.713 -> 0.632 (-11.4%) | -11.5% | -5.8% | -3.6% | 191.1 -> 103.5 (-45.8%) | PASS |
| 2 / pan | 1.443 -> 1.316 (-8.8%) | 0.820 -> 0.723 (-11.8%) | -7.7% | -5.2% | -2.6% | 224.3 -> 210.3 (-6.3%) | PASS |

Two launches per side, cross-launch. Frame mean, particle pass and CPU per frame fell in every
comparison, including scale 1 static (whose frame p95 rose 2.7%). The
allocation swings in both directions (+51% at scale 1 static, -46% at scale 2 static) are larger than
anything the table can cause (it allocates nothing); every sampled allocation in the pass is
vanilla's `BlockPos` in `getLightLevel`, whose scalar replacement varies by compilation (profile.md,
session 21). Scale 1 static's tick-frame p99 rose 21% (6.2 -> 7.5 ms).

### Where the time went (profile.md)

Candidate JFR (option on, attribution only) puts the light work at 21-28% of the pass against
37-40% in session 21's shipped-defaults profile: the query itself falls to 2-10% and the table
lookup takes 1-8%. The remaining light cost is `getLightLevel`'s own work and its `isChunkLoaded`
entry, which the table does not change.

## Recommendation for the next item

The repetition is real and the table removes most of the light query; in the three passing groups
the median savings were 9-14% of the pass and 5.5-12.7% of the frame, with the pass lower in all 12
of their launches and the frame in 11 (`lr22-ab-2-r3` pan: pass -2.9%, frame +1.5%). What is unresolved is the
lightest load, where the frame is about 0.8 ms and the saving can reappear as draw-call waiting.
Before any promotion, a separate predeclared confirmation should measure scale 1 static with more
launches and separate CPU time from driver/GPU waiting at the draw (for example with GL timer
queries); do not promote from the passing groups alone, and do not re-score this session's
launches. Otherwise continue with roadmap item 1 (particle movement), whose session 17-19
experiments stay disabled.

## Files

- `protocol.md`: predeclared gates (written before the frozen manifest and every scored launch).
- `attribution/`: the counter launches, the smoke launch and the negative control (not scored).
- `verification.md`: verification design and live results.
- `profile.md`, `profile-candidate-*.txt`, `jfr_attribution.py`: JFR attribution with the option on.
- `comparison.md`, `tails.md`, `metrics.json`: generated by `analyze.py` from `runs/` (validated
  against exported files, logs, shared-config hash and the frozen manifest/snapshot).
- `manifest.json`, `sources-e281d3d4c90842f4.zip`: frozen source (option default false, toggled or set
  by the runner). The source did not change after freezing; the final tree matches the manifest.
- `runs/`, `attempts/`: every scored launch's record, summary and command (18 verification/measured
  launches, 2 profiles; no failed attempts).

## Reproduction

```bash
cd tools
python run_particle_light_reuse.py diagnostics   # lr22-check-2, lr22-check-1 (verification)
python run_particle_light_reuse.py controls      # lr22-ab-{1,2}-r{1..4}
python run_particle_light_reuse.py startup       # lr22-startup-{absent,on}-{1,2}-r{1,2}
python run_particle_light_reuse.py profiles      # lr22-profile-{1,2} (JFR, attribution only)
python ../docs/benchmarks/2026-10-20-particle-light-reuse/analyze.py
```

The runner refuses reused labels/logs and overlapping clients, checks the source tree against
`manifest.json` before and after every launch, passes every render option, `.verify` and profiler
switch explicitly, and validates each phase's exported config. Java 25.0.4 (Adoptium), 1920x1080,
display lists, view distance 12. Nothing was committed; no driver settings, shared config
(`694ff84d...`) or earlier archives changed.
