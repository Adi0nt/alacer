# Chunk scheduling series (2026-10-23): results

**Verdict: fails on this machine. `render.chunkTimeFloor` stays off by default and outside `render.defaults`.** The
frozen series in [protocol.md](protocol.md) ran once in full (16 of 16 launches completed and valid). Gates G1, G2,
G4, G6 and G7 pass; **G3 (visible holes in the fresh flight) and G5 (flight frame cost) fail**. The floor makes visible
terrain updates much faster, above all after block edits, but in the fresh flight it costs about a fifth more frame
time and leaves slightly more never-built chunks in view. `render.feedChunkWorkers` was not scored here (it changed
nothing in the exploratory launches) and also stays off. Neither option was ever a default candidate on this machine:
llvmpipe evidence cannot select a default (AGENTS.md).

Scored with `python3 -I evaluate.py run/alacer/benchmarks` (unchanged since the freeze in `7ad4d2f`); its output is
[evaluation.json](evaluation.json). The gates were not changed.

## Environment (indicative only)

Linux cloud container, 4 CPU threads (Xeon @ 2.80 GHz), no GPU: Xvfb, Mesa 25.2.8 llvmpipe (software OpenGL 4.5),
Temurin 25.0.5+8 for Gradle, tests and the client. Window 854x480, 2 GiB heap, view distance 8, display lists
(`vboActive=false` in every launch), `profiler.chunkLatency=true` and `profiler.chunkDetail=false` in both arms.
llvmpipe rasterizes on the same 4 threads that run the two chunk workers and the client thread, so drawing cost
grows with the amount of built terrain and competes with chunk building. **Times here are indicative only**; they
are not comparable with the benchmark archives or the reference machine.

The code under test is `9b087aa`'s `src/` (unchanged at the branch head `5030c5f`; nothing in `src/` changed during
the series). The seed-1789 base world was generated in this container by one discarded benchmark launch
(`-Palacer.benchmark=true -Palacer.benchmark.viewDistance=8`) before the series; each launch played a fresh copy,
and both scenes logged the byte-identical world-copy check. Launches were serial (07:26-07:51 UTC) with no builds or
analysis while a client ran. [run_series.sh](run_series.sh) is the exact command sequence; [logs/](logs/) holds each
launch's client log (proxy settings removed) and [raw/](raw/) each summary.

## Results

Paired change is B / A - 1 within a launch pair; the gate uses the median of the four pairs. A = floor off, B = on.

| Gate | Metric | A (four pairs) | B | Paired changes | Median | Rule | Result |
|---|---|---|---|---|---:|---|---|
| G1 | edit latency p90 (ms) | 1461, 870, 1779, 2020 | 384, 404, 375, 386 | -74, -54, -79, -81% | -76.3% | 4/4 lower, <= -25% | pass |
| G2 | flight latency p90 (ms) | 1492, 1420, 1456, 1449 | 1161, 1205, 1180, 1262 | -22, -15, -19, -13% | -17.1% | >= 3/4 lower, <= -10% | pass |
| G3 | flight visible never-built chunks | 39.6, 39.0, 38.2, 36.5 | 43.5, 43.1, 43.6, 42.2 | +10, +10, +14, +16% | **+12.2%** | <= +5% | **fail** |
| G4 | edit frame mean (ms) | 62.1, 58.0, 61.8, 63.6 | 59.6, 63.1, 59.2, 60.2 | -4, +9, -4, -5% | -4.2% | <= +5% | pass |
| G4 | edit frame p99 (ms) | 87.6, 75.0, 88.8, 91.4 | 76.4, 90.4, 85.6, 83.2 | -13, +21, -4, -9% | -6.3% | <= +10% | pass |
| G5 | flight frame mean (ms) | 41.2, 40.6, 41.1, 41.8 | 48.4, 51.3, 49.7, 50.1 | +17, +26, +21, +20% | **+20.3%** | <= +5% | **fail** |
| G5 | flight frame p99 (ms) | 69.0, 83.2, 67.8, 79.1 | 84.9, 100.8, 84.7, 92.0 | +23, +21, +25, +16% | **+22.0%** | <= +10% | **fail** |
| G6 | still frame mean, edit / flight launches | | | -4, +7, -10, +5% / -6, +4, +2, +4% | +0.6% / +3.1% | within +/-5% | pass |
| G7 | validity | 16 completed launches, 0 dropped frames, 0 latency overflow | | | | | pass |

Other recorded metrics (not gated):

| Metric | A | B | Median change |
|---|---|---|---:|
| edit latency p50 (ms) | 729, 484, 635, 804 | 226, 239, 223, 235 | -66.9% |
| edit latency p99 (ms) | 12430, 1902, 7064, 13928 | 467, 470, 445, 505 | -95.0% |
| edit updates published in the 30 s phase | 402, 501, 378, 339 | 525, 516, 523, 514 | +34.5% |
| flight latency p50 (ms) | 577, 616, 602, 653 | 478, 493, 499, 482 | -18.6% |
| flight latency p99 (ms) | 2707, 2972, 2977, 3103 | 2646, 2983, 2539, 2388 | -8.5% |
| flight updates published in the 40 s phase | 2130, 2122, 2050, 1772 | 2982, 2825, 2995, 2736 | +43.0% |
| flight frame max (ms) | 238, 125, 207, 475 | 115, 118, 284, 118 | -28.8% |
| edit frame max (ms) | 115, 86, 131, 102 | 81, 106, 273, 95 | +8.4% |

Reading: with the floor, an edited chunk is visible again in about a quarter of a second instead of 0.5-0.8 s (p50)
and its worst case falls from several seconds to half a second. In the fresh flight the floor publishes 43% more
updates, but every published chunk is drawn by llvmpipe on the CPU threads the workers also need, so frames get about
8 ms slower and the never-built count in view does not fall (it rises 12%). The frame-cost failure is therefore
partly the software-rendering confound the protocol anticipated, but it is the frozen result and it stands.

## Exploratory attribution (before the freeze, not scored)

One launch per arm and scene in [raw/](raw/) (`diag-*` with `profiler.chunkDetail=true`, `x-*` without). These came
from an earlier container with its own base world, so their absolute values are not comparable with the series.

- **Uploads, not idle workers, limit visible updates on llvmpipe.** With vanilla scheduling the frame deadline had
  already passed at `compileChunksUntil` in 72% (flight) to 83% (edit) of frames, 11-17 chunk layers were waiting to
  upload, and only 0.2-0.6 of the 5 shared buffer sets were free, so workers waited for buffers (mean wait 50-129 ms).
- `render.feedChunkWorkers` (queue more rebuilds after the deadline) changed nothing measurable: edit latency p50
  1.96 -> 1.92 s, flight p50 813 -> 812 ms. More queued work cannot help when workers are waiting for buffers.
- `render.chunkTimeFloor` (at least 1/32 of the previous frame for uploads and queueing) cut edit latency p50 from
  1.96 to 0.29 s; flight frame p50 went from 38 to 59 ms, mostly from drawing more terrain. It was therefore the only
  arm scored.

## Aborted attempt (recorded, not scored)

`raw/20261023-070409-cs-edit-1a`, `20261023-070538-cs-edit-2b` and `20261023-070710-cs-edit-3b` are the first three launches of an attempt that was interrupted with the
earlier container. They are kept as evidence and not scored: the series was rerun from launch 1 in this container,
with a regenerated base world. Their edit-phase values (A p50 1.95 s, B 0.31-0.32 s) agree in direction with the
series; the A arm's absolute latency is higher than here (0.48-0.80 s), which matches the other runs from that
container and is not explained further (different base world and container).

## Limitations

- Software rendering only: llvmpipe makes drawing a CPU cost shared with the chunk workers, which inflates the frame
  cost of showing more terrain. The reference machine (GPU rendering, 20 threads) may differ in either direction.
- Four pairs per scene; launch-to-launch variation in still phases was up to 10% in frame mean, so effects of a few
  percent are not resolvable here. The failing G3 and G5 changes (+10 to +26% in every pair) are well outside that.
- One edit pattern (16 chunks per second from a fixed view) and one flight (`chunkFlight=near`) at view distance 8.
- Latency starts at frame resolution and is a lower bound for updates that arrive while their chunk rebuilds
  (see `ChunkUpdateLatency`).

## Reference-hardware follow-up (not run)

As in protocol.md: the same scenes and gates at 1920x1080 and render distance 12 on the Windows/RTX 3060 Ti machine,
adding the session 27 near/high flights and the default static/pan/fly path as no-regression controls, and
`render.feedChunkWorkers` as a third arm, because session 13 found 2.4-2.9 of 5 buffer sets free there and the deadline
expired in only 14-22% of frames, so idle workers rather than uploads may be the limit on that machine. Freeze the
protocol and gates before measuring; both options stay off by default until a reference series passes.
