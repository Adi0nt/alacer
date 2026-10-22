# Session 9: model-part display lists

This archive separates selection measurements, native verification and sampling. Driver
settings were not changed. The session 8 correction still applies: the user confirmed Global
Threaded optimization was On before the earlier sessions. Neither process CPU utilization
nor this experiment establishes actual driver worker behavior. Use the paired comparisons.

## Files

- `runs/`: original game summaries, including environment and effective configuration.
- `metrics.json`, `comparison.md`, `selection.json`: raw-frame-derived comparisons and per-population medians.
- `compilations.json`: observed original-face and alternate-list compilation counts from each final run.
- `analyze.py`: reproduces the performance tables and checks populations, detail/verification
  off, cloud caching on, VBO off, no generation runs, no dropped frames and zero detail columns.
- `samples.json`, `samples.py`: detail-off JFR attribution with full-depth stacks.
- `model-sampling.jfc`: the installed Temurin 8 profile settings with both method sampling
  periods changed to 1 ms. This is diagnostic instrumentation, excluded from FPS selection.
- `verification.md`, `verification.json`, `verify.py`: native feedback results, the deliberate-fault negative control and disabled-startup check.

Raw CSVs remain under ignored `run/alacer/benchmarks/`, logs under `build/ml-*.log`, and the
recording at `run/ml-sample1000.jfr`. `ml-200-r1` and `ml-1000-r1` were pilots before the final
list ownership/source guards. Selection uses r2/r3/r4 only, alternating 200 and 1,000 mobs.
Additional cold compilation owner/stride guards were added during the series; the primitive
selector and the accepted vanilla vertex stream are unchanged throughout r2-r4. Both lists are built when vanilla compiles the part;
toggling itself does not compile geometry.

## Commands

Use JDK 25 for Gradle, which launches Temurin 8. Normal settings: 1920x1080, render distance
12, VBO off, fancy graphics/clouds, no vsync, cloud caching on, entity detail off.

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=ml-200-r2 -Palacer.benchmark.entities=200 -Palacer.benchmark.toggle=render.batchModelLists -Palacer.profiler.entityDetail=false -Palacer.render.cacheClouds=true -Pwindow=1920x1080 -Pxmx=2G
# Repeat r2/r3/r4, alternating populations 200 and 1000.
python docs/benchmarks/2026-10-19-model-lists/analyze.py

./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=ml-sample1000 -Palacer.benchmark.entities=1000 -Palacer.profiler.entityDetail=false -Pjfr=ml-sample1000 -PjfrSettings=../docs/benchmarks/2026-10-19-model-lists/model-sampling.jfc -Pwindow=1920x1080 -Pxmx=2G
jfr print --json --stack-depth 128 --events jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ActiveSetting run/ml-sample1000.jfr > build/ml-samples.json
python docs/benchmarks/2026-10-19-model-lists/samples.py
```

Every A/B launch records static and pan A/B/B/A, plus a discarded pan warmup. A selects the
retained original list, B the combined list, with the same selector hooks installed in both.
The ordinary disabled startup default omits those hooks. No fly comparison is claimed.
Counters for compilation describe the lists built during warmup, not a count of native draws
executed per recorded frame. Percentiles are computed from pooled raw frames.
