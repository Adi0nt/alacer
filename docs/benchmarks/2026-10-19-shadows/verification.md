# Verification: `render.fastEntityShadows.verify`

With verification on, every shadow query the optimization answers (block state below, raw
brightness, brightness) is also asked of the world in vanilla's way. Block states are compared
by identity, raw brightness exactly and brightness by its float bits; vanilla's answer is used
and the first ten differences are logged. Runs are normal three-phase benchmarks (static, pan,
fly) and are excluded from performance selection.

| Run | Build | Scene | Queries compared | Mismatches |
|---|---|---|---:|---:|
| `sh-verify-1000` | final (`@Redirect`) | 1,000 motionless mobs | 23,328,521 | 0 |
| `sh-verify-diag200` | final | 200 mobs with AI, 36 cells toggled 588 times | 19,139,644 | 0 |
| `shd-smoke3` | final, timeScale 0.3 | 200 motionless mobs | 24,720,341 | 0 |
| `shd-smoke2` | pilot (`@WrapOperation`), timeScale 0.3 | 200 motionless mobs | 14,645,074 | 0 |

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=sh-verify-1000 -Palacer.benchmark.entities=1000 -Palacer.render.fastEntityShadows.verify=true -Pwindow=1920x1080 -Pxmx=2G
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=sh-verify-diag200 -Palacer.benchmark.entities=200 -Palacer.benchmark.shadowDiagnostic=true -Palacer.render.fastEntityShadows.verify=true -Palacer.profiler.shadowTiming=true -Palacer.benchmark.screenshots=true -Pwindow=1920x1080 -Pxmx=2G
```

`benchmark.shadowDiagnostic=true` keeps the mobs' AI, so they walk (shadows cross block and
chunk borders) and sheep eat grass. Every 30 frames the 36 cells diagonally between the mobs
nearest the anchor, inside their shadow footprints, cycle through glowstone (block light and a
full cube under the shadow), stone slab (neighbor-lit, half-height bounds) and air, so block
states and light data under shadows change throughout the run. The screenshots
(`run/screenshots/alacer-sh-verify-diag200-*.png`) show slabs, glowstone and moved mobs.

An earlier diagnostic ran the daylight cycle through dusk to change ambient darkness. It found
nothing to test: the client sets `World.ambientDarkness` only when the world is created
(`ClientWorld`'s constructor), so shadows read the same value all session. That version was
replaced before the final series; the final runs above use the block-toggling diagnostic.

The first smoke (`shd-smoke`, 21.9 million queries, 0 mismatches) ran an earlier build that
left neighbor-lit blocks to vanilla. Every non-air block with light opacity 0 (tall grass,
flowers) or translucency is neighbor-lit in 1.8.9, and 16% of its queries took that path, so
the final version computes vanilla's five-neighbor maximum itself.

A short entity-detail run (`shd-detail-smoke2`, final build) confirmed that the session 8
stage timers still attach to the redirected calls: pigs recorded 39.5 shadow queries per draw,
as before. No run logged a GL error, rendering exception or matrix fallback.

Limits: comparisons cover the benchmark world (grass, tall grass, flowers, the toggled blocks)
in the overworld. Other dimensions, mod-added blocks or world subclasses are not exercised;
world subclasses take vanilla's path by design. This compares query results, not framebuffers.
