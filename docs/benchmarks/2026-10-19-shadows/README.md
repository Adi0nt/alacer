# Session 11: entity shadow world queries

This session attributes the cost of vanilla entity shadows with detail profiling off, then
measures `render.fastEntityShadows`, which answers the shadows' block and light queries
through cached chunk references (see ARCHITECTURE, optimization 6).

| Series | Labels | A | B |
|---|---|---|---|
| Attribution | `sh-attr-{200,1000}-r{1,2}` | Vanilla shadow queries | Game option Entity Shadows off (changes the image) |
| Optimization | `sh-{200,1000}-r{1,2,3}` | Vanilla shadow queries | `render.fastEntityShadows` |
| Timing | `sh-time-{200,1000}-r1` | Vanilla shadow queries | `render.fastEntityShadows` |

All three use the five shipped defaults in A and B, both experiments off, entity detail and
verification off. Attribution and timing launches also enable the coarse shadow timer
(`profiler.shadowTiming`: two `nanoTime` calls and one counter per shadow); the optimization
launches do not. Launches were interleaved by round: attribution 200/1,000, optimization
200/1,000 (rounds 1 and 2), then optimization round 3 and the timing pair.

## Protocol

Same machine and settings as Phase 10: 12700F / RTX 3060 Ti (driver 616.92), 1080p, distance
12, VBO off, fancy graphics and clouds, vsync off, unlimited FPS, Temurin 8u492 / 2G heap, the
copied seed-1789 world (its natural animals are present), motionless NoAI mobs. No driver
settings were changed; the session 8 correction still applies (user-confirmed Global Threaded
optimization On). Neither process CPU utilization nor these timings identify the driver's
worker behavior.

Each launch uses the Phase 10 protocol, now also selected for these two toggles: static A and B
warmups, static A/B/B/A (5 s each), full-turn pan A and B warmups, pan A/B/B/A (7.5 s each).
Toggles apply before settling; the setup frame is excluded; each segment exports its actual
config, which now also records the game's `options.entityShadows`. No fly comparison is made.

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=sh-200-r1 -Palacer.benchmark.entities=200 -Palacer.benchmark.toggle=render.fastEntityShadows -Palacer.profiler.shadowTiming=false -Palacer.profiler.entityDetail=false -Palacer.profiler.hud=false -Palacer.render.batchModelLists=false -Palacer.render.skipZeroTranslates=false -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
# Attribution: -Palacer.benchmark.toggle=benchmark.disableEntityShadows -Palacer.render.fastEntityShadows=false -Palacer.profiler.shadowTiming=true
# Timing:      -Palacer.benchmark.toggle=render.fastEntityShadows -Palacer.profiler.shadowTiming=true
python docs/benchmarks/2026-10-19-shadows/analyze.py           # comparison.md, metrics.json, selection.json
python docs/benchmarks/2026-10-19-shadows/analyze.py --pilot   # pilot.md (superseded, see below)
```

Game output went to `build/<label>.log`. `analyze.py` checks every launch before using it: all
twelve phases, each segment's config matching its path, matching per-phase profile configs,
exactly one discarded setup frame, no dropped frames, full recorded duration matching the raw
frame sum within 20 ms, fixed settings, no base-world generation, no JFR, all entity-detail and
GL-call columns zero, the shadow timer column zero exactly when it should be, a successful
build and no GL errors, rendering exceptions, matrix fallbacks or shadow mismatches in the log,
the same Java/CPU/GPU/driver/mod set in every launch, and the source hashes in
`implementation.json`. All 14 final launches pass.

## Outcome

Full tables: `comparison.md`. Medians are over launches; ranges show every repeat.

**Attribution.** Removing shadows lowered frame time in all 8 pairs: 200 mobs 17.1% static
(16.7–17.4%) and 7.7% pan (6.0–9.4%); 1,000 mobs 6.7% (5.9–7.6%) and 10.3% (10.0–10.5%).
The shadow section was 0.187–0.245 ms per frame for 54–60 shadows (about 3.5–4.1 us each,
including the draw), and vanilla shadows allocated about 26–31 KB per frame.

**Optimization.** In all 12 pairs the entity loop was lower (by 0.04–0.19 ms) and allocation
was lower (non-tick median by 19–22 KB per frame); client CPU per frame was lower in 11. Swap
grew in all 12, so part of the saving became waiting. Frame time:

| Mobs | Phase | Frame reduction vs pooled A, median (range) | B faster than A / A2 |
|---|---|---:|---:|
| 200 | static | 5.7% (1.0 to 8.3%) | 3/3 / 2/3 |
| 200 | pan | 6.9% (2.0 to 13.8%) | 3/3 / 3/3 |
| 1,000 | static | 1.7% (−0.2 to 3.5%) | 2/3 / 2/3 |
| 1,000 | pan | −3.3% (−5.2 to 4.5%) | 1/3 / 1/3 |

**Timing.** The shadow section fell 0.211 → 0.139 and 0.245 → 0.156 ms at 200 mobs and
0.296 → 0.236 and 0.345 → 0.276 at 1,000 (20–36%). `sh-time-1000-r1` recorded a different GL
timing state in all its segments (entity loop about 2.9 ms instead of about 1.3, about 1.2
process cores, swap about 0.45 ms). There the entity loop did not change (2.928 → 2.928 static)
and frame time rose 1.1–1.6%: the time saved in shadows was spent waiting later in the loop.
No driver mode is inferred from this.

**The A path is not identical across series.** The same vanilla queries allocated about
55 KB per frame in the attribution launches but 72–74 KB in the optimization launches (200
mobs, static). The difference is vanilla's own `BlockPos` copies (five per neighbor-lit block such as
tall grass, repeated by `getBrightness`) that the JIT removed in one series and not the other;
`render.fastEntityShadows` never makes them. The timing launch's A shadow section was likewise
0.02–0.03 ms above attribution A. Against attribution A at 200 mobs, B's shadow section is
about 25–30% lower (about 0.05 ms per frame), so the optimization's frame-time medians above are an upper
estimate of the saving over clean vanilla paths.

**Decision: `render.fastEntityShadows=true` by default.** It is exact (see `verification.md`),
every CPU-side measure improved in every pair, frame time improved in all 200-mob pairs, and the
1,000-mob results show no dependable change in either direction. This is not a frame-time claim
for 1,000 mobs or for the slower GL timing state.

## Superseded pilot

The first version wrapped the three queries with `@WrapOperation`. In some launches the JIT did
not remove `Operation.call`'s argument arrays and `Float` boxing in vanilla's path. The pilot
(`shd-attr-*`, `shd-{200,1000}-r{1,2}`, rebuilt into `pilot.md` and `runs/pilot/`) is kept as
evidence for that change and is not used for selection: its attribution ran a build without the
diagnostic flag, and its optimization rounds were stopped after round 2 when the hooks became
`@Redirect`. Interrupted `shd-200-r3`/`shd-1000-r3` wrote no results. Pilot hashes are in
`implementation.json`.

## Limits

- Both paths run in the same instrumented client; this is not an unmodified-client launch.
  The redirect handler itself remains in A.
- Mobs are motionless NoAI pigs, cows, sheep and wolves on grass; other species, terrain or
  dense neighbor-lit blocks change the query mix. Moving mobs and changing blocks are covered
  by verification, not by these timings.
- One machine, one settings profile, two GL timing states observed. CPU utilization is not used
  to infer driver threading. Do not add these percentages to Phase 10's or other sessions'.
