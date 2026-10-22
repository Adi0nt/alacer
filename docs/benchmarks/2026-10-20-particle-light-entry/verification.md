# Session 21 verification

## Design

`render.fastParticleLightEntry.verify=true` (requires the option on) keeps vanilla's behavior and
compares:

- **Positions.** `Entity.getLightLevel` still allocates vanilla's `new BlockPos(x, y + eyeHeight, z)`.
  For every particle the option answers (exact `ClientWorld`, exact `ClientChunkCache` chunk source,
  client thread), the coordinates the reused mutable position would take (`MathHelper.floor` of each
  argument, as `Vec3i(double, double, double)` does) are computed into a separate scratch position
  and compared with vanilla's.
- **Chunk checks.** For every answered `World.isChunkLoaded(pos)` call in `Entity.getLightLevel` and
  `BlockParticle.getLightLevel`, the range test is computed, vanilla's `isChunkLoaded` is called on
  the same position and **vanilla's result is used**; differences are counted and logged.

The reused-position path itself does not run in verify mode (vanilla's position is used), so its
correctness rests on the position comparison, the chunk-check comparison and the fact that its only
readers are the range test and optimization 7's fast path, which reads the three coordinates once.
Optimization 7's own verification (66.7 million queries in session 15) ran with vanilla positions.

The unit tests (`ParticleLightEntryTest`, no mixins) compare the floored coordinates with
`new BlockPos(x, y, z)` for every combination of 30 edge doubles (signed zero, NaN, infinities,
`Double.MIN_VALUE`, values just below integers, the +-30,000,000 limits, `int` overflow, +-1e300,
`Double.MAX_VALUE`) and 2,000,000 random and bit-pattern triples, and check the range test's
boundaries. `AlacerMixinPluginTest` checks that the two hooks are absent unless the option, its
verification or a benchmark toggle of it is on at startup, and that benchmark mode ignores the file.

## Live results (frozen source `sources-adcbef098e0dbd77.zip`)

| Launch | Scale / diagnostics | Chunk checks compared | Not loaded | Check mismatches | Positions compared | Position mismatches |
|---|---|---:|---:|---:|---:|---:|
| le21-check-2 | 2, light + removal diagnostics | 82,229,965 | 5,267,309 | **0** | 72,069,982 | **0** |
| le21-check-1 | 1, light + removal diagnostics | 99,224,325 | 2,714,004 | **0** | 80,070,449 | **0** |

The light diagnostic cycled the blocks under the 16 emitters through glowstone, slab and air (246
and 265 toggles) and emitted particles above the build height and below y = 0 (the not-loaded
checks); the removal diagnostic added 7,235 probe particles (layer-moving, cap-evicting, equal-id,
duplicated and `equals`-overriding). Optimization 7's light verification was off in these launches
(its line reports 0 queries compared). Pre-freeze smoke launches le21-smoke-1/2 only confirmed that
the redirects apply (le21-smoke-2: 100 million checks, 0 mismatches; le21-smoke-1 ran verification
without the option, which counts positions only, and led to positions also requiring the option).

Not exercised live: the +-30,000,000 horizontal limits (unit-tested range only), skyless
dimensions, other client world or chunk-source classes (they take vanilla's call by construction).
