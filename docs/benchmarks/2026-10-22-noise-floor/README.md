# Session 31: noise floor of the reference-hardware series (new analysis of existing data)

**Status: analysis run; results below.** The method section was committed (`ef97ad3`) before any number was computed;
additions made after the first run are listed separately. This is a new analysis of the session 29 and session 30
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

The script is [noise.py](noise.py); its output is [noise.json](noise.json).

## Additions after the first run

The first run showed that the null data are not one roughly normal spread but mostly tight pairs plus a block of
launches that ran far slower (below). Three things were added to `noise.py` after seeing that, and are reported
beside the pre-written ones, not instead of them:

1. **Symmetric resampling** of set A: |d| with random signs. Set A's 32 values have a mean of -11.4% in frame mean
   (most of it from two pairs), and plain resampling cannot move the median of the draws away from that offset, so
   its R95 stops shrinking near 11-12% at any n. With balanced order the null is symmetric; this model keeps the tails
   and removes the offset.
2. **A planning table:** R95 of the median and mean of n pairs for a given per-pair spread (normal noise, 1-17%).
3. **A launch speed index:** for each startup launch, the median over its still phases of ln(x / the scene-phase's
   median over every launch of its series), in frame mean, client CPU and process CPU per frame, with the launch's
   start time. In session 29 the option works in d200 static and pan; its frame-mean effect there (medians -2.5% to
   +2.3%) is small against the deviations shown.

The normal model on set B's pooled standard deviation (pre-written) is reported in `noise.json` but not used for
planning: session 30's spread is dominated by the slow block, which turns its standard deviations into 50-220%.

## Results

### Where the spread came from (launch speed index, addition 3)

Session 30's startup launches (result folders 13:20-14:47 local, an afternoon with background apps running) did not
scatter randomly. Frame mean relative to each scene-phase's median, by start time:

| Window | Launches | Frame-mean index |
|---|---|---|
| 13:20-13:42, pair 1 | d0, d200, terrain, near, high | -1.6% to -17.5% (d0/d200/terrain); -29.6% to -42.4% (near, high) |
| 13:42-14:04, pair 2 | all scenes | -23.2% to +13.5%; d200 within -2.4 / -0.8% |
| 14:04-14:20, pair 3 (d0, d200, terrain) and `near-3-on` | 7 launches | -12.4% to +17.5%; d0 +0.1 / -0.1% |
| 14:21-14:41, `near-3-off` to `terrain-4-on` | 9 launches | +58% to +373%, except `d200-4-on` (+5.1%) |
| 14:42-14:48, near and high pair 4 | 4 launches | +22.8% to +48.1% |

In the slow block the client thread's own CPU time per frame rose with the frame time (client CPU index +61% to
+229%), so the client thread executed more slowly per frame; this is not only waiting. The cause is not in the
archives (candidates: CPU clock or power state, the scheduler moving the client to efficiency cores, competing load,
GPU clocks; the series' limitations record background apps at 10-13% CPU before it started). Because pair n of every
scene ran together, the block hit both sides of some pairs (d0 pair 4: +362% / +318%) and one side of others
(near pair 3: +10.0% on, +193.4% off; d200 pair 4: +96.9% off, +5.1% on).

Session 29 (02:35-03:32 local, night) was quieter: 12 of its 16 startup launches are within +/-6% (all four terrain
launches within +/-0.6%), and the rest are single slow launches of +10.9% to +17.6% (`d200-1-on`, `d200-2-absent`,
`d200-4-absent`, `d0-2-on-r2`), with no block.

### Set A: session 30 still pairs (32 null log ratios per metric)

| Metric | RMS | Robust scale | Largest | Mean | Range of the eight 4-pair medians |
|---|---|---|---|---|---|
| Frame mean | 38.0% | 16.7% | 166.7% | -11.4% | -16.9% to +1.1% |
| p95 | 59.4% | 19.9% | 362.7% | -13.2% | (not gated per phase) |
| p99 | 60.2% | 24.4% | 319.6% | -14.1% | -17.5% to +11.1% |
| Slow-1% mean | 73.6% | 27.4% | 633.3% | -14.2% | -15.3% to +18.3% |
| Tick-frame p99 | 64.4% | 31.2% | 484.3% | -6.9% | -12.7% to +28.0% |
| Client CPU per frame | 31.3% | 19.0% | 97.1% | -6.7% | -15.1% to +3.5% |
| Process CPU per frame | 23.9% | 10.9% | 86.5% | -5.7% | -10.0% to +4.4% |
| Allocation per tick-free frame | 17.4% | 7.9% | 65.3% | -0.8% | -11.3% to +8.1% |
| `terrain_setup` per frame | 30.7% | 24.2% | 106.8% | -6.6% | -14.1% to +8.1% |

(RMS around zero; robust scale 1.4826 x median |d|; all as e^d - 1.) Allocation per tick-free frame is stable where
the frame path allocates a lot and does not depend on frame rate: in d200 static and pan, the four off launches of
session 30 span 1.1% and 1.5% even across the slow block, and session 29's absent launches 0.6% and 1.4%. Where it
is small (terrain steady, 3-5 KB) it moves by tens of percent.

**Order effect.** The second launch of a pair was slower in 19 of 32 still pairs for frame mean (17-19 for every
metric); mean ln(second / first) +0.7% for frame mean, +2.6% for p99. No order effect is resolved beyond the noise,
but the series ran in one direction through a drifting afternoon, so a time trend is confounded with pair index.

### Set B: between-launch spread of the off and absent sides

Pooled standard deviation of ln x across launches without the option, per class (degrees of freedom in brackets):

| Metric | S30 still (24) | S30 moving (18) | S29 still (10) | S29 moving (6) |
|---|---|---|---|---|
| Frame mean | 91.8% | 56.8% | 11.3% | 8.4% |
| p99 | 182.8% | 91.7% | 15.3% | 12.0% |
| Tick-frame p99 | 189.0% | 149.2% | 23.6% | 15.4% |
| Client CPU per frame | 65.4% | 46.4% | 13.1% | 7.4% |
| Process CPU per frame | 52.0% | 50.6% | 12.0% | 9.8% |
| Allocation per tick-free frame | 15.2% | 51.0% | 13.4% | 9.9% |
| `terrain_setup` | 67.2% | 44.3% | - | - |

sqrt(2) times session 30's still spread (151% for frame mean) is far above set A's robust scale (16.7%): launches of
one pair ran a few minutes apart and shared most of the slow drift, so pairing adjacent launches removed most of it.
Session 29's per-scene-phase spreads range from 0.2% (terrain steady frame mean) to 19.9% (d200 pan, one slow
launch). Moving-phase allocation per tick-free frame spreads widely because per-block-crossing work is divided over
more or fewer frames as the frame rate changes.

### Resolution of the median of n pairs

R95(n), the 95% half-width of the median of n null pair changes. Left: plain resampling of set A (pre-written; keeps
its -11% offset). Right: symmetric resampling (addition 1).

| Metric | n = 4 | 8 | 16 | 24 | 32 | 64 | n for R95 <= 2% / 1% (symmetric) |
|---|---|---|---|---|---|---|---|
| Frame mean | 63.0 / 31.6% | 31.7 / 14.6% | 15.6 / 7.4% | 13.0 / 4.3% | 12.1 / 3.1% | 11.0 / 1.7% | 64 / 96 |
| p99 | 98.1 / 48.6% | 41.1 / 23.0% | 22.8 / 12.9% | 16.7 / 10.4% | 14.8 / 8.5% | 11.9 / 5.6% | over 256 |
| Slow-1% mean | 101.2 / 45.4% | 30.6 / 21.3% | 19.0 / 15.1% | 15.8 / 10.9% | 14.4 / 8.1% | 10.2 / 6.4% | over 256 |
| Tick-frame p99 | 72.5 / 50.6% | 26.4 / 25.2% | 17.4 / 17.5% | 14.9 / 14.5% | 14.6 / 13.0% | 11.6 / 9.9% | over 256 |
| Client CPU | 44.7 / 33.0% | 28.8 / 16.8% | 15.4 / 9.1% | 12.9 / 6.6% | 10.6 / 5.6% | 6.6 / 3.2% | 128 / 192 |
| Process CPU | 33.8 / 23.4% | 19.1 / 12.5% | 12.7 / 6.4% | 10.1 / 4.9% | 8.5 / 3.6% | 6.4 / 1.5% | 64 / 96 |
| Allocation per tick-free frame | 17.2 / 16.9% | 8.4 / 8.4% | 4.6 / 3.4% | 2.7 / 1.2% | 1.4 / 1.1% | 1.1 / 1.0% | 24 / 96 |
| `terrain_setup` | 39.9 / 29.9% | 22.9 / 18.3% | 18.3 / 11.2% | 17.0 / 7.4% | 16.5 / 3.1% | 11.9 / 2.7% | 192 / over 256 |

The smallest true effect a median of n pairs detects with 80% probability (MDE80) is about 1.2-1.7 times R95 at these
n (in `noise.json`). The mean of n pairs is worse here than the median at every n and metric except client CPU at
n = 4 (frame mean, symmetric: 17.3% at n = 16 against 7.4%), because the slow block's outliers dominate it. The
normal model on set B gives 131% / 58% / 27% for the frame-mean median at n = 4 / 16 / 64.

At session 30's four pairs per scene, a scene's frame-mean median is resolved to about +/-32-63%; a 24-pair pooled
median, if moving phases are no noisier than still ones, to about +/-4-13% (not applied to session 30's verdict, which
stands as frozen).

### Planning table (addition 2): what per-pair spread a target needs

R95 of the median (mean in brackets) of n pairs with normal per-pair noise of standard deviation sigma:

| sigma | n = 4 | 8 | 12 | 16 | 24 | 32 | 64 |
|---|---|---|---|---|---|---|---|
| 1% | 1.07 (0.98) | 0.80 (0.69) | 0.67 (0.57) | 0.59 (0.49) | 0.49 (0.40) | 0.42 (0.35) | 0.30 (0.24) |
| 2% | 2.14 (1.96) | 1.60 (1.38) | 1.33 (1.13) | 1.18 (0.98) | 0.97 (0.80) | 0.85 (0.69) | 0.60 (0.49) |
| 3% | 3.22 (2.94) | 2.41 (2.08) | 2.01 (1.69) | 1.76 (1.47) | 1.45 (1.19) | 1.27 (1.03) | 0.91 (0.72) |
| 4% | 4.32 (3.93) | 3.20 (2.77) | 2.69 (2.25) | 2.34 (1.94) | 1.93 (1.57) | 1.68 (1.37) | 1.20 (0.96) |
| 6% | 6.45 (5.88) | 4.78 (4.12) | 4.00 (3.34) | 3.49 (2.90) | 2.88 (2.36) | 2.50 (2.05) | 1.78 (1.44) |
| 8% | 8.63 (7.88) | 6.42 (5.50) | 5.32 (4.43) | 4.64 (3.83) | 3.83 (3.14) | 3.33 (2.70) | 2.37 (1.90) |
| 17% | 18.29 (16.60) | 13.51 (11.50) | 11.12 (9.31) | 9.71 (7.99) | 7.95 (6.48) | 6.92 (5.59) | 4.91 (3.94) |

A 2% resolution on frame mean needs a per-pair spread of about 3% with 12-16 pairs, or 4% with about 24. With the
spread of session 30's still pairs (robust 16.7%) it needs several hundred pairs: more pairs alone cannot reach it.
The per-pair spread has to come down first.

### Phase length

| Still scene-phase | Seconds | Frames (median) | Tick frames | Tick p99 rests on | RMS d: frame mean / p99 / tick p99 |
|---|---|---|---|---|---|
| d0 static | 20 | 34,002 | 399 | 4 frames | 24.2 / 48.5 / 45.3% |
| d0 pan | 30 | 36,460 | 600 | 6 | 11.4 / 22.2 / 21.4% |
| d200 static | 20 | 19,933 | 400 | 4 | 62.2 / 105.5 / 143.4% |
| d200 pan | 30 | 22,629 | 600 | 6 | 17.6 / 19.8 / 15.7% |
| terrain steady | 20 | 32,724 | 400 | 4 | 37.0 / 37.9 / 54.1% |
| terrain pan | 30 | 37,730 | 600 | 6 | 29.8 / 47.3 / 49.0% |
| near still | 10 | 10,125 | 200 | 2 | 64.6 / 74.2 / 36.2% |
| high still | 10 | 9,212 | 200 | 2 | 36.0 / 98.3 / 109.4% |

The client ticks 20 times a second at every time scale, so a phase has 20 tick frames per second, and the tick-frame p99
of a 10-30 s phase is fixed by its 2-6 slowest tick frames. The slow block dominates every row, so phase length
cannot be separated from it in these data; the 30 s pan phases have the smaller spread in all three scenes that have
both. A tick-frame p99 resting on at least 20 frames needs 100 s of recording per phase (or judge the tick-frame mean
or p90 instead).

### Secondary checks (set C)

- Session 29 d0 pairs (mixin absent against present, no mob shadows): frame mean +5.4, +2.7, -3.6% (pair 1, static /
  pan / fly) and +1.3, +29.4, +52.4% (pair 2, whose "on" launch is one of the slow ones, +15.2% index).
- Session 29 in-process A2/A1 frame drift (same off path, one launch, about 30-50 s apart): static -3.8, +30.6, +15.4,
  +0.3%; pan +2.9, -0.4, -2.8, +4.5%. With 200 wandering mobs a "static" view is not stationary: what is in view
  changes between segments, so in-process A/B in d200 static carries scene drift as well as machine drift.

### Time cost on the reference machine

Mean launch duration from the attempt files (start to exit; the runner starts the next launch within about a second):
d0 124-127 s, d200 123-126 s, terrain 230 s, near 96 s, high 95 s; verify launches 92-500 s. One pair costs about
4.2 min (d0, d200), 7.7 min (terrain) or 3.2 min (near, high); 16 pairs of each of the five scenes take about 6.0 h.

## Reading the result

- The noise floor in session 30 was set by when launches ran, not by the launches themselves: adjacent launches in a
  quiet stretch agree to a few percent (d0 pair 3 within 0.2%, d200 pair 2 within 1.6%), and a 20-minute slow block
  moved whole launches by up to +373%. Session 29's night series shows what a quieter machine gives: most launches
  within +/-6%, terrain within +/-0.6%, with single slow launches.
- Pairing adjacent launches already removes most of the slow drift (sqrt(2) x set B's still spread is 151% against
  set A's robust 16.7%). What remains are pairs that straddle a change in machine state. Those need to be detected and
  excluded by a rule written in advance, not out-voted by more pairs.
- Allocation per tick-free frame resists machine state where the frame path dominates allocation (d200 still), so
  allocation and correctness gates can be measured on a busy machine; frame time, tails and CPU cannot.

## Limitations

- 32 still pairs and 24 off-side scene-phases, from one afternoon and one night on one machine. The slow block is one
  event; how often such events happen is unknown.
- The models treat pairs as independent and alike across scenes; they are neither (pair n of every scene ran together,
  and the spread differs by scene). The bootstrap cannot produce values beyond those observed.
- The symmetric model, planning table and launch index were added after the first run; they are reported as such.
- The cause of the slow block is not established; the archives record no CPU clock, core placement, foreground window
  or per-process load during launches.
- Planning numbers, not gates. Neither session is rescored.
