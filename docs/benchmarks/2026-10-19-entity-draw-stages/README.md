# Session 8 evidence

See BENCHMARKS.md session 8 for interpretation and limits. No driver settings were changed.
The user's screenshot and follow-up confirm Global Threaded optimization was On before the
session 7 and 8 launches. Earlier CPU-based inference did not establish a single-threaded mode.
The read-only DRS probe was inconclusive; native-thread start addresses also cannot reliably
identify a driver's worker behavior. Thread sample JSON is retained but not used to classify
the launches. No temporary profile was created.

## Archive

- `runs/`: original summaries from three cloud A/B repeats, six entity profiling/control
  launches and the detail-off JFR launch. Configuration and environment are included.
- `cloud-comparison.md`, `cloud-metrics.json`: paired cloud repeats, detail off, 200 mobs.
- `comparison.md`, `metrics.json`: full stage runs and extra-timers-off controls, 200/1,000 mobs.
- `classes.md`, `classes.json`: class times and call counts from actual phase logs.
- `samples.json`: sparse detail-off JFR cross-check; not reliable timing percentages.

Per-frame CSVs remain in ignored `run/alacer/benchmarks/`; full logs in `build/<label>.log`.
The 94-second JFR remains in `run/eds-sample1000.jfr`. The short `eds-smoke` launch is not
pooled into the reported results. All six full profiling/control runs completed without
dropped frames; every stage-on phase reported zero incomplete spans. The analysis validates
parent/child timing bounds and disjoint top-level sums on every raw frame. The detail-off
sampling run's stage/draw-detail columns were checked to be zero.

## Reproduction

Use JDK 25 for Gradle; Gradle launches the game on Temurin 8. Same scene/settings as prior
sessions: 1920x1080, distance 12, VBO off, fancy clouds, cloud caching on except A segments.

```bash
# Three cloud repeats, changing the label each launch:
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=cc-on-r1 -Palacer.benchmark.entities=200 -Palacer.benchmark.toggle=render.cacheClouds -Pwindow=1920x1080 -Pxmx=2G

# For each population (200, 1000), stages true / false / true in separate launches:
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=eds-200-r1 -Palacer.benchmark.entities=200 -Palacer.profiler.entityDetail=true -Palacer.profiler.entityStages=true -Pwindow=1920x1080 -Pxmx=2G

python docs/benchmarks/2026-10-19-entity-draw-stages/clouds.py
python docs/benchmarks/2026-10-19-entity-draw-stages/analyze.py
```

Stage labels are `eds-200-r1`, `eds-200-control`, `eds-200-r2`, then the equivalent three
`eds-1000-*` labels. Controls set `profiler.entityStages=false`, retaining the existing entity
detail and GL counts. See BENCHMARKS.md for the JFR command and `samples.py` usage. Timer
calibration is between JVMs and includes drift/JIT variation; it is not an optimization A/B.

Final validation: `./gradlew build` passed with 31 tests, zero failures/errors, and access
widener validation. Edited source/docs/JSON use LF. No commits were made.
