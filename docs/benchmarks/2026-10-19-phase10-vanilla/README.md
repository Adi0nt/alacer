# Phase 10: current defaults against vanilla render paths

This phase measures the combined effect of the five shipped optimizations in one process.
It adds a benchmark selector and recording fixes; it does not add a rendering optimization.

| Path | Visibility | Entity chunk lookup | GL error checks | Camera matrix reads | Fancy clouds | Experiments |
|---|---|---|---|---|---|---|
| A | Vanilla | Vanilla | Every frame | OpenGL | Rebuild every frame | Off |
| B | Alacer | Cached | About once per second | CPU copies with periodic checks | Cached | Off |

A uses the vanilla implementations through the existing hooks. Both paths have the same
Alacer profiler, Fabric/OSL and benchmark installed. This is a controlled comparison of vanilla
render paths with Alacer defaults, not a separately launched, completely unmodified client.
It does not measure startup footprint or compatibility with every GPU/mod combination.

## Protocol

Three launches each with 0, 200 and 1,000 **added** mobs, interleaved by round. The base world
already contains animals, so 0 does not mean an entity-free world. Same copied seed-1789 world,
1080p, distance 12, VBO off, fancy graphics/clouds, no vsync, unlimited FPS, Temurin 8u492.
No base-world generation is included. No driver settings are changed; the session 8 correction
still applies. User-confirmed Global Threaded optimization was already On. Neither process
CPU utilization nor these timings identifies the driver's actual worker behavior.

Each launch records:

1. Static A and B warmups, discarded from selection.
2. Static A/B/B/A, five seconds per segment.
3. Full-turn pan A and B warmups, discarded from selection.
4. Full-turn pan A/B/B/A, 7.5 seconds per segment.

The option changes **before settling**, with at least two quiet seconds before each recording.
Each segment exports its actual config. The setup frame containing recorder arrays, metadata
and phase logging is excluded; CPU/wall clocks arm at the next completed-frame boundary.
Changes to config during recording abort the run. The ordinary on-disk defaults are unchanged.
Old archives are preserved and are not pooled into this phase. The old `ml-200-r2` static-A1
first row recorded 6,413,224 allocated bytes including setup; the corrected screenshot smoke
first row recorded 52,488 after one deliberate setup-frame exclusion. These are diagnostic
checks from different scenarios/durations, not a claimed allocation or FPS optimization. No fly comparison is claimed:
repeating that path in one world changes which chunks are already loaded.

`render.defaults` is a virtual benchmark preset, not a new persisted config option. It toggles
visibility, entity lookup, GL check throttling, matrix reads and cloud caching together;
zero translations and model-list batching stay off in A and B. Verification and entity detail
must be off. A startup check rejects verification/detail settings for this comparison.

## Reproduction

Use JDK 25 for Gradle. It launches the Java 8 game toolchain. Launch labels are p10-0-r1,
p10-200-r1, p10-1000-r1, then rounds 2 and 3 with the same population order.

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=p10-200-r1 -Palacer.benchmark.entities=200 -Palacer.benchmark.toggle=render.defaults -Palacer.profiler.entityDetail=false -Palacer.profiler.hud=false -Palacer.render.batchModelLists=false -Palacer.render.skipZeroTranslates=false -Palacer.benchmark.vbo=false -Pwindow=1920x1080 -Pxmx=2G
python docs/benchmarks/2026-10-19-phase10-vanilla/analyze.py
```

The screenshot smoke launch `p10-smoke` uses 200 mobs and timeScale=0.15. It is excluded from
performance selection. It checked all twelve phase configs, matching per-phase report configs,
one discarded setup frame per phase, zero detail columns and no dropped frames. Screenshots
are under `run/screenshots/alacer-p10-smoke-*.png`. The smoke preceded the additional target/wall
duration and GL-log-status metadata; the full runs use the final frozen implementation.

## Outcome

All nine final launches pass `analyze.py`'s checks. B (current defaults) has a lower mean
frame time than pooled A and than the later A2 control in all 18 launch × phase pairs.

| Added mobs | Static FPS gain, median (range) | Pan FPS gain, median (range) |
|---|---:|---:|
| 0 | +68.9% (+68.8 to +80.4%) | +57.5% (+28.7 to +57.9%) |
| 200 | +17.3% (+12.7 to +17.8%) | +37.8% (+20.1 to +39.4%) |
| 1,000 | +15.7% (+12.7 to +32.0%) | +25.3% (+18.2 to +72.0%) |

`p10-1000-r3` recorded a different GL timing state in all four segments: A's GL error checks took
1.47–1.78 ms, swap 0.09–0.15 ms, and the process used about 1.7–1.8 cores. It supplies both
1,000-mob maxima. A and B share that state within the launch, so it is kept; no driver mode is
inferred. CPU, tails, allocation and sections are in `comparison.md`. The interpretation and
limits are in BENCHMARKS.md, Phase 10.

### Validation notes

- **Mod list check.** The environment's `mods` string is printed in Fabric Loader's hash order,
  which varies per launch. All nine launches loaded the same nine `id@version` entries. The
  original check compared the raw strings and failed for that reason. It now compares the
  set of entries, so any mod or version change still fails. The other environment keys are
  still compared as exact strings.
- **Same build.** `BenchmarkRunner.java` was last edited at 20:50:50 and compiled at the start
  of `p10-0-r1`. Every later launch reports `compileJava UP-TO-DATE`, and the hashes in
  `implementation.json` still match.
- **Relaunch.** The first `p10-1000-r3` attempt stopped at 21:11:03 during `pan-a1`, when the
  pause menu's quit button was clicked. It wrote no results, and its log is
  `build/p10-1000-r3-aborted.log`. With the user's approval, it was repeated unchanged at
  21:14–21:16; it was already the last launch in the round order.

## Archive

- `implementation.json`: source hashes captured before the first full run.
- `harness-checks.json`: diagnostic evidence for excluding recorder setup (not a performance comparison).
- `runs/`: original game summaries, including actual config and durations for every segment.
- `metrics.json`: per-launch raw-frame statistics, A1/A2/B1/B2 and pooled A/B separately.
- `selection.json`: per-population median paired FPS/frame-time changes, ranges and win counts.
- `comparison.md`: per-launch comparisons, paired summary, CPU/allocation/tails and coarse sections.
- `analyze.py`: rebuilds the archive from local CSVs and validates the protocol and source hashes.

Raw CSVs remain in ignored `run/alacer/benchmarks/`; full logs are `build/p10-*.log`. Tail
percentiles and 1% lows come from pooled raw frames, not averages of phase percentiles.
Coarse sections overlap. All comparisons use measured frame times; per-feature percentages
from previous phases are neither added nor multiplied into these results.
