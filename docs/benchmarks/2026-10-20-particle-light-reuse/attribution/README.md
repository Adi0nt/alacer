# Session 22 pre-protocol evidence (not scored)

Launched with `le_launch.sh`-style explicit options (all nine shipped optimizations on, every
experiment off, profiler detail off) before the protocol and the frozen manifest. Each folder's
`summary.json` and the relevant final log lines are copied here; the full logs are
`build/<label>.log` (SHA-256 prefixes in the session notes).

| Label | Purpose | Extra options |
|---|---|---|
| lr22-count-1, lr22-count-2 | Attribution: queries and distinct positions per unlit pass | `particleScale=1/2`, `profiler.particleLightRepeats=true` |
| lr22-smoke-1 | Confirm the pass hooks apply and verification runs | `particleScale=1`, `render.reuseParticleLight=true`, `.verify=true`, `benchmark.particleDiagnostic=true` |
| lr22-negative-stale | Negative control: verification must catch a table that is never emptied | as smoke, from a temporary source (below), reverted before the protocol |

Counter results (per pass, phase means of `particle_light_*_mean` in the summaries):

| Scale / phase | Rendered | Queries | Distinct positions | Repeated | Same as previous |
|---|---:|---:|---:|---:|---:|
| 1 / static | 3,308 | 3,912 | 395.2 | 89.9% | 6.0% |
| 1 / pan | 3,307 | 3,914 | 395.4 | 89.9% | 5.9% |
| 2 / static | 5,134 | 6,358 | 416.3 | 93.5% | 7.3% |
| 2 / pan | 5,130 | 6,352 | 415.4 | 93.5% | 7.2% |

Smoke: 70 million queries compared (90.7% reused), 0 mismatches. Negative control, temporary change
in `ParticleLightReuse.beginPass`:

```diff
-			TABLE.clear();
+			if (passes == 0) { TABLE.clear(); } // NEGATIVE CONTROL ONLY: never emptied after the first pass
```

Result: 0 mismatches until the diagnostic first replaced the blocks under the emitters, then
51,503,360 mismatches among 82,426,188 compared queries (first: `f00000` reused vs vanilla
`f000c0` next to new glowstone).
