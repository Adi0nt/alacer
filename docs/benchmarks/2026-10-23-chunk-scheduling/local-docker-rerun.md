# Chunk responsiveness and scheduling experiments, 2026-10-23

**The frozen series failed. Both experiments remain off by default and outside
`render.defaults`.** All 16 new launches completed on their first attempt with
zero dropped frames and zero latency overflow. G1, G2 and G6 failed; the other
four gates passed. This is indicative software-rendering evidence, not a default
selection or a reference-hardware result.

## What was implemented

Commits `a737afb` and `9b087aa` added:

- `profiler.chunkLatency` (off, startup-only): stamps a chunk when terrain setup
  first finds it dirty and visible, records elapsed time when a completed rebuild
  is published, and counts never-built chunks in the visible set. Phase summaries
  include count, mean, p50/p90/p99/max and overflow.
- `benchmark.chunkEdit=true`: a fixed 16-cell edit burst each second, after an
  unscored pan warmup and a still control. The scored 30-second edit phase makes
  28 bursts. `benchmark.chunkFlight=near` is the retained fresh-flight control.
- `render.feedChunkWorkers` (off): after vanilla's deadline, continue queueing
  dirty chunks in the same order and through the same calls until the queue
  reaches a bounded target derived from observed worker starts. Uploads retain
  vanilla's deadline. The pure `FeedTarget` caps its target at 64 tasks.
- `render.chunkTimeFloor` (off): give uploads and rebuild queueing at least 1/32
  of the preceding frame, capped at 4.167 ms. Keep a later vanilla deadline;
  ignore gaps over 250 ms and reset on world/renderer replacement or disable.

The experiments preserve vanilla's build/upload/publication operations, but can
change when terrain becomes available. This series is not lifecycle, cancellation,
mod-compatibility or pixel-equivalence certification. Defaults were not changed.

## Complete frozen rerun

[protocol.md](protocol.md) and [evaluate.py](evaluate.py) are unchanged. Source
under test was `5030c5f`, whose `src/` is identical to `9b087aa`. A generated base
world (seed 1789) was retained; its generation launch is unscored. Every subsequent
launch verified a byte-identical fresh copy (excluding `session.lock`).

Both arms used 854x480, view distance 8, display lists (requested and active),
2 GiB heap, `profiler.chunkLatency=true`, `profiler.chunkDetail=false`, build
defaults plus explicit JVM overrides, and the protocol's durations. A disables
`chunkTimeFloor`; B enables it. Order was `ABBABAAB` for edit, then `ABBABAAB`
for near flight, with labels `cs-<scene>-<n><a|b>`. No builds, tests or metric
analysis overlapped the scored clients. The temporary camera harness was installed
only after all 16 launches and the final hash check completed.

The original cloud host was unavailable. This complete rerun used local Docker
on an i7-12700F, a four-CPU quota (not four dedicated physical cores), 8 GiB memory,
Temurin 25.0.5+8 for Gradle/compiler/tests/client, Xvfb 1280x720x24, and Mesa
26.0.8 / llvmpipe LLVM 21.1.8. The original cloud measurements used a Xeon and
Mesa 25.2.8 / LLVM 20.1.2. This environment difference was recorded before the
rerun in [rerun-note.md](rerun-note.md); exact environment and JVM details are in
[rerun-environment.txt](rerun-environment.txt) and every launch summary. Do not
pool the hosts or compare their absolute times as a treatment effect.

The three `raw/*-cs-edit-*.summary.json` files remain byte-for-byte evidence of
an **aborted original attempt**, not scored pairs. See
[raw/ABORTED-ATTEMPT.md](raw/ABORTED-ATTEMPT.md). No new launch aborted or repeated.

## Results

Values below are the median of four paired `B / A - 1` changes, not ratios of
pooled medians. Negative time changes are improvements. The unchanged command was:

```bash
python3 -I docs/benchmarks/2026-10-23-chunk-scheduling/evaluate.py run/alacer/benchmarks
```

| Gate | Observed | Frozen requirement | Result |
|---|---|---|---|
| G1 edit latency p90 | Lower in 4/4 pairs; median -10.72% | Lower in 4/4; <= -25% | Fail |
| G2 flight latency p90 | Lower in 2/4 pairs; median -0.17% | Lower in >=3/4; <= -10% | Fail |
| G3 flight visible never-built chunks | +0.10% | <= +5% | Pass |
| G4 edit frame mean / p99 | +1.99% / +9.17% | <= +5% / +10% | Pass |
| G5 flight frame mean / p99 | +2.02% / +3.00% | <= +5% / +10% | Pass |
| G6 still frame mean | Edit +6.81%; flight -2.34% | Both within +/-5% | Fail |
| G7 validity | 16 completed; 0 dropped frames; 0 latency overflow | All required | Pass |

Edit latency p50 changed -24.54%, but its p99 changed +1.74%; flight p50 changed
+7.60% and p99 +5.32%. Edit p90 was 109-131 ms in A and 108-112 ms in B; flight
p90 was 596-673 ms in A and 599-628 ms in B. Individual flight maxima remained
4.16-4.84 seconds. A p50 improvement does not rescue the failed p90 rule.

Supplemental, not new gates: client-thread CPU per frame changed +1.97% in edit
and +2.73% in flight; process CPU stayed around 3.5-3.9 cores (near the quota).
Allocation per frame changed +1.61% / +0.89%. Still-frame drift and launch-to-launch
variation limit small-effect interpretation. In particular, G6 fails independently
of the two responsiveness failures; it is not waived as noise.

[results.json](results.json) is the evaluator's verbatim output. Its unscored edit
hole-count ratios contain `Infinity` because both arms have zero holes and the
unchanged evaluator maps a zero denominator to infinity. The actual G3 uses the
nonzero **flight** hole counts; this does not affect a gate. [validity.json](validity.json)
additionally checks settings, phase consistency, fresh-world logs and backend.

## Exploratory attribution (original cloud host, not scored)

The six `raw/*-x-*.summary.json` launches used intrusive chunk-detail probes,
one launch per scene/arm. They motivated the frozen candidate; they cannot judge
its repeatability or be mixed into the new pairs.

| Arm | Edit update latency p50 | Edit frame p50 | Flight update latency p50 | Flight frame p50 |
|---|---:|---:|---:|---:|
| Vanilla scheduling | 1963 ms | 73.51 ms | 812.94 ms | 38.42 ms |
| Feed workers | 1924 ms | 72.59 ms | 812.38 ms | 38.84 ms |
| Chunk time floor | 293 ms | 76.20 ms | 581.89 ms | 58.69 ms |

The vanilla deadline had expired in 83% / 72% of edit / flight frames. About
17 / 10 per-layer uploads waited, and 0.23 / 0.59 of the five shared buffers
were free. Workers were waiting for buffers rather than simply lacking queued
work. Feeding them showed no material benefit there. A chunk time floor drained
uploads sooner, improving edit responsiveness, but flight drew more terrain and
slowed substantially. CPU rasterization competes with the client and workers,
so that flight cost is a software-rendering confound, not a hardware-GPU prediction.
The complete rerun did not reproduce the exploratory effect size.

## Evidence and limits

- Archive-local `.gitattributes` preserves evidence bytes across Git checkouts.
- [series/](series/) retains every new summary, phase JSON, gzipped per-frame CSV,
  command/attempt record and log, plus the discarded generation summary/log.
- [rerun-freeze.json](rerun-freeze.json) records pre-launch source/protocol/evaluator
  hashes; [sources-frozen.zip](sources-frozen.zip) contains those exact bytes.
  `sources-5030c5f.zip` also retains the canonical Git snapshot.
  [base-world-hashes.json](base-world-hashes.json) identifies the generated control.
- `run-series.py` executes the frozen order serially and checks hashes between
  clients and at completion. [setup.log](setup.log) records the online setup and
  Linux test run. All 162 tests passed on both local Java 25.0.4.1 and container
  Java 25.0.5; unit/build evidence is separate from these real-client measurements.
- llvmpipe timings are indicative only. This is a different CPU, driver and kernel
  from the exploratory host, and Docker/host scheduling adds noise. The container
  has no audio device; startup OpenAL fallback, legacy Twitch/Realms and log4j
  appender warnings remain in the logs. No selected launch logged an injection
  failure, GL error or benchmark abort.
- The latency start has frame resolution. Same-setup synchronous near-camera
  rebuilds are unstamped; edits arriving during a build can start later; moved
  slots discard their pending stamp. Percentiles describe published updates,
  rather than all outstanding edits. The never-built gauge does not count stale
  already-built geometry. Exact block/light-change-to-visible-pixel latency,
  cancellation/order stress, backend/reload transitions and other mods remain gaps.

## Deferred reference-hardware follow-up

Run the protocol's same scenes and gates at **1920x1080, render distance 12 on
Windows / RTX 3060 Ti**, adding session 27's near/high flights and the default
static/pan/fly path as no-regression controls. Include **feedChunkWorkers as a
third arm**: session 13 found 2.4-2.9 of five buffers available there, with the
deadline expired in only 14-22% of frames. Freeze the extended schedule before
measuring; this local software series does not stand in for it. Keep both options
off by default. An automatic thread-count policy is also deferred: this four-CPU
software renderer cannot select a policy for reference hardware.
