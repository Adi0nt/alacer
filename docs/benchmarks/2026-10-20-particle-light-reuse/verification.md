# Session 22 verification: per-pass particle light reuse

## Design checked

`render.reuseParticleLight.verify` (with the option on) keeps the table running exactly as in the
candidate (lookups, stores, emptying), and for every query the table answers, reused or computed,
also runs vanilla's `World.getLightColor(pos, blockLight)` on the original position, uses vanilla's
result and counts and logs differences. Optimization 7's own verification is off, as the table only
runs when optimization 7 answers without verifying.

Both scored launches use the frozen source (`manifest.json`) with
`benchmark.particleDiagnostic=true` (the blocks under the emitters cycle through glowstone, slab and
air between passes, and particles are emitted above the build height and below y = 0) and
`benchmark.particleRemovalDiagnostic=true`. A table that kept answers across passes would return the
light from before a block change; the negative control in `attribution/` shows that this
verification catches exactly that (51.5 million mismatches).

## Results (final log lines)

| Launch | Scale | Passes | Reused | Computed (not stored) | Compared (reused) | Mismatched |
|---|---:|---:|---:|---:|---:|---:|
| lr22-check-2 | 2 | 20,171 | 88,434,430 | 6,368,371 (0) | 94,802,801 (88,434,430) | **0** |
| lr22-check-1 | 1 | 21,100 | 70,419,209 | 4,423,759 (0) | 74,842,968 (70,419,209) | **0** |

Total **169,645,769 queries compared, 158,853,639 of them reused, 0 mismatches**. The table never
filled (0 not stored). Optimization 7's and the light entry's final lines show 0 queries compared
(their verification was off) and the runner rejects any mismatch line in the logs.

## Unit tests

`ParticleLightReuseTest`: the table against a `HashMap` over 200 passes of 4,000 random lookups
(x/z near the origin and up to +-1,000,000, y from -20 to 279), emptying at every pass, and the
3/4-full limit; `HOOKED` is false without the mod. `AlacerMixinPluginTest`: the pass hooks follow the
option, its verification or a benchmark toggle, by the default constant, and benchmark mode ignores
the file. `AlacerConfigTest`: default, runtime toggle, `.verify` not toggleable, preset.

## Limits

- Skyless dimensions are not exercised live (the table stores whatever optimization 7 computes,
  including `hasNoSky`'s 0).
- The +-30,000,000 limits are not exercised live; unit tests cover large coordinates in the table.
- The exactness argument rests on the client world not changing during `ParticleManager.render`
  (protocol.md); another mod that changed it from particle rendering would break it.
