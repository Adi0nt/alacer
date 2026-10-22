# Session 31: noise floor of the reference-hardware series (new analysis of existing data)

**Status: method written before the analysis was run.** This is a new analysis of the session 29 and session 30
`gates.json` files. It measures how much the judged metrics move between launches when nothing changes, and how the
median of per-pair changes tightens with more pairs. It does not rescore either session, change either archive or
revisit either verdict, and it selects nothing. No launch was run for it: it was written in a Linux container with
software GL, where frame times judge nothing.

## Inputs (read only)

- [Session 29 gates.json](../2026-10-22-shadow-positions-reference/gates.json): `startup` (per pair and phase, both
  sides and the change) and `in_process` (A, B and the A2/A1 frame drift per launch and phase).
- [Session 30 gates.json](../2026-10-22-section-faces-reference/gates.json): `pairs` (per scene, phase and pair, both
  sides).

Both series ran on the same reference machine (Windows 11, i7-12700F, RTX 3060 Ti, 1920x1080, Java 25.0.4.1) on
October 22, 2026, from fresh copies of the same base world.

## Method

**Unit.** One launch's value of one metric in one phase. A pair compares two launches with the same settings except the
option; its change is the log ratio d = ln(x_on / x_off). Results are reported back as percentages, e^d - 1, which is
the archives' on/off - 1.

**Metrics.** Frame mean, p95, p99, slow-1% mean, tick-frame p99, client CPU per frame, process CPU per frame, allocation
per tick-free frame, and `terrain_setup` per frame (session 30 only; session 29 did not record it).

**Null sets.** Where the true change is zero, everything measured is noise.

- **A. Still pairs (primary).** Session 30's eight still scene-phases (d0 and d200 static and pan, terrain steady and
  pan, near and high still), four pairs each: 32 log ratios per metric. `render.fastCameraSectionFaces` does no work
  there (the camera never enters another block).
- **B. Off-side launches (between-launch spread).** For every scene-phase, the standard deviation of ln x across the
  launches that ran without the option: session 30's "off" launches (14 scene-phases, 4 launches each, moving phases
  included) and session 29's "absent" launches (d200 static, pan and fly with 4 launches; d0 static, pan and fly and
  terrain steady, pan, sort and fly with 2). Pooled within each class (still, moving) by degrees of freedom. If launches
  are independent, a pair's log ratio has standard deviation sqrt(2) times this; comparing that with set A checks the
  model. In moving phases, allocation per tick-free frame depends on frame rate (per-block-crossing work is spread over
  more or fewer frames), so its spread there is partly a frame-rate effect.
- **C. Secondary, reported but not pooled.** Session 29's d0 startup pairs (mixin absent against mixin present with no
  mob shadows to draw; 2 pairs x 3 phases) and session 29's in-process A2/A1 frame-mean drift (8 values: two phases
  of the same "off" path within one launch, the only within-launch null that `gates.json` stores).

**Order effect.** For set A, ln(second launch / first launch) of each pair: mean, median and how many are positive.
With alternating order a constant order effect cancels in the mean over balanced pairs but adds to each pair's spread.

**Statistics per metric.**

- Spread of set A: root mean square of d around zero (the true change is zero), the robust scale
  1.4826 x median |d|, the largest |d|, and the eight 4-pair medians (the quantity the session 30 gates read).
- Spread of set B per class, and sqrt(2) times it.
- **Resolution of a median of n pairs, R95(n):** half-width of the central 95% interval of the median of n null pair
  changes, so that under no effect an observed median beyond +/-R95 has at most 5% probability. Two models, each with
  200,000 draws per n and a fixed seed: (1) empirical, n values drawn with replacement from set A's 32 log ratios
  (keeps the heavy tails); (2) normal, standard deviation sqrt(2) x set B's pooled spread, separately for still and
  moving phases. n = 4, 6, 8, 12, 16, 24, 32, 48, 64. Also the smallest true effect that a median of n pairs puts
  beyond -R95(n) with 80% probability (MDE80, from the same draws), and the smallest n with R95 <= 2% and <= 1%.
- The same for the mean of n pairs, for comparison (more efficient for normal noise, less robust to outliers).

**Phase length.** Each scene-phase has a single phase length in these files, so its effect cannot be measured
directly. Reported instead: frames and tick frames per phase, the number of tick frames that a tick-frame p99 rests
on, and the set A spread per scene-phase beside its phase length.

**Time cost.** Launch durations from the attempt files of both series, to cost a proposed series on the reference
machine.

**Limits stated before running.** 32 null pairs from one afternoon and one machine; set A's draws assume pairs are
independent and alike across scenes (they are not quite: variance differs by scene and pair n of every scene ran
together); the bootstrap cannot produce values larger than the largest observed; set B has only 3 degrees of freedom
per scene-phase (1 for session 29's d0 and terrain). The numbers are a planning estimate, not a gate.

The script is [noise.py](noise.py); its output is [noise.json](noise.json). Results follow below once run.
