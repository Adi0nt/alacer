# Chunk scheduling series (2026-10-23): frozen protocol and gates

Written and committed before the first scored launch. Code under test: commit `9b087aa` (the two commits that add
`profiler.chunkLatency`, `benchmark.chunkEdit`, `render.feedChunkWorkers`, `render.chunkTimeFloor` and the camera
settings). Nothing in `src/` changes during the series.

## Why this candidate

Exploratory diagnostic launches (one per arm and scene, `profiler.chunkDetail=true`, not scored; see README) found
the upload drain, not idle workers, limiting visible updates on this machine: the frame deadline had already passed
at `compileChunksUntil` in 72-83% of frames, 10-17 per-layer uploads were waiting, and 0.2-0.6 of the 5 shared
buffer sets were free, so workers waited for buffers. `render.feedChunkWorkers` changed nothing measurable there
(edit latency p50 1.96 -> 1.92 s, flight p50 813 -> 812 ms). `render.chunkTimeFloor` cut edit latency p50 from
1.96 to 0.29 s but drew more terrain in flight. Only `render.chunkTimeFloor` is scored.

## Environment (indicative only)

Linux cloud container, 4 CPU threads, no GPU: Xvfb, Mesa llvmpipe (software OpenGL), Temurin 25.0.5+8. llvmpipe
rasterizes on the same CPU threads as the chunk workers and the client, so drawing cost grows with the amount of
built terrain. These timings are not comparable with the benchmark archives and cannot select a default.

## Launches

`runClient --offline -Pwindow=854x480 -Pxmx=2G` with `alacer.benchmark=true`, `benchmark.viewDistance=8`,
`benchmark.vbo=false` (display lists, checked by the runner), `profiler.chunkLatency=true` in both arms,
`profiler.chunkDetail=false`, every other option at its default. A: `render.chunkTimeFloor=false`.
B: `render.chunkTimeFloor=true`. Each launch plays a fresh byte-identical copy of the same base world (seed 1789).

- Edit scene, `benchmark.chunkEdit=true`: warmup-pan 10 s (unscored), still 10 s, edit 30 s (28 bursts of 16 edits).
- Flight scene, `benchmark.chunkFlight=near`: warmup-pan 10 s (unscored), still 10 s, fresh-flight 40 s.

Order: edit A B B A B A A B, then flight A B B A B A A B; labels `cs-<scene>-<n><arm>`, n = 1..8. Pairs are
launches (1,2), (3,4), (5,6), (7,8). Serial launches, no builds or analysis while a client runs. Every attempt is
kept; a launch that aborts is reported and repeated once at the end of its scene.

## Gates (all must hold to pass on this machine)

Paired change = B / A - 1 within a pair; "median" is over the four pairs.

| Gate | Phase | Rule |
|---|---|---|
| G1 edit responsiveness | edit | update-latency p90 lower in B in 4/4 pairs, median change <= -25% |
| G2 flight responsiveness | fresh-flight | update-latency p90 lower in B in >= 3/4 pairs, median change <= -10% |
| G3 holes | fresh-flight | visible never-built chunks (`chunk_visible_uncompiled_mean`): median change <= +5% |
| G4 edit frame cost | edit | frame mean (1000 / avg_fps) median change <= +5%; frame p99 median change <= +10% |
| G5 flight frame cost | fresh-flight | frame mean median change <= +5%; frame p99 median change <= +10% |
| G6 no-op control | still (both scenes) | frame mean median change within +/-5% |
| G7 validity | all | 16 completed launches, 0 dropped frames, 0 latency overflow |

## Decision rule

`render.chunkTimeFloor` stays off by default and outside `render.defaults` whatever the result: llvmpipe evidence
cannot select a default (AGENTS.md). If every gate passes, it is documented as an opt-in experiment that passed on
software rendering and the reference-hardware series below is the next step. If any gate fails, the failure is
recorded and the option stays an off-by-default experiment.

Reference-hardware follow-up (not run here): the same scenes and gates at 1920x1080, render distance 12, on the
Windows/RTX 3060 Ti machine, adding the session 27 near/high flights and the default static/pan/fly path as
no-regression controls, and `render.feedChunkWorkers` as a third arm, since session 13 found buffers available
there (2.4-2.9 of 5) and the deadline expired in only 14-22% of frames.
