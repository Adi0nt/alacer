# Session 13: chunk-build pipeline responsiveness

**Outcome: investigation completed; no production scheduling change selected.** Added
`profiler.chunkDetail=false`, an opt-in attribution mode. The six selected render defaults
remain on. Frustum, model-list batching and zero translations remain off. Session 12's
archive is untouched. There is no candidate A/B comparison or FPS-improvement claim.

## Fresh observations

Three full-duration controls with intrusive diagnostics off, each from a fresh copy of the
same base world (`cp-baseline-r1/r2/r3`), recorded:

| Fly metric | Observed control range |
|---|---:|
| Mean frame | 2.94–3.63 ms |
| p99 frame | 5.99–7.09 ms |
| Maximum frame | 22.86–26.04 ms |
| Client CPU/frame | 2.86–3.40 ms |
| Process CPU | 1.50–1.65 cores |
| Client allocation/frame | 37,494–46,271 bytes |
| Pending entries, frame mean | 70.5–108.8 |
| Pending entries, maximum | 325–336 |
| All four terrain layers/frame | 1.08–1.30 ms |
| compileChunksUntil/frame | 0.23–0.29 ms |

The historical "worst about 6 ms / terrain about 0.11 ms" figures do not describe this fresh
series. The largest frames occur in different sections, including terrain draws, visibility,
swap, ticks and render residual. `metrics.json` retains the 20 worst frames and the complete
section breakdown for every phase of every run. Nested sections are never added together.

`pending_chunks` counts dirty entries + queued build tasks + per-layer upload jobs. It is
not unique chunks, can count the same chunk at multiple stages, and omits active builds.
The diagnostics separate these gauges and reveal that the dirty set dominates the count.

Three non-JFR diagnostic flights (`cp-detail-r1`, `cp-detail-age-r3/r4`) give attribution:

| Measurement | Observed range |
|---|---:|
| Dirty-set gauge, frame mean | 66–93 |
| Queued-build gauge, frame mean (capacity 100) | 4.6–4.8 |
| Ready per-layer uploads, frame mean | 3.4–4.3 |
| Available shared buffers, frame mean (capacity 5) | 2.4–2.9 |
| Worker geometry build wall time/completed call | 0.38–0.42 ms |
| Task construction to COMPILING/start | 0.69–0.78 ms |
| Worker buffer acquisition wait/completed call | 0.58–0.87 ms |
| UPLOADING to successful DONE/task | 3.45–4.53 ms |
| Display-list compilation/client frame | 0.24–0.27 ms |
| Display-list compilation/call | 0.20–0.21 ms |

No synchronous near builds occurred during these recorded flights. This does not generalize
to ground-level block edits. In the two age diagnostics, the sampled build queue reached
capacity in only 0.018–0.062% of frames. Shared buffers were unavailable in 26–34% of frames;
that alone does not justify increasing the pool while upload jobs await the client.

The task-start timer excludes residency **before task creation**. Two diagnostics therefore
also sampled first-observed dirty-set age. Accepted rebuilds with a prior observation waited
41 and 61 ms on average before enqueue. Snapshot dirty entries averaged 61 and 102 ms of
observed age, with maxima of 570 and 1,237 ms. These are lower bounds from frame sampling,
not exact invalidation-to-visible latency. Origins, observation gaps and successful enqueues
reset the stamp. Repeated invalidations coalesce; sub-frame entries are not observed. The
upload-drain deadline was already expired at entry in 14% and 22% of frames. Vanilla still
drains at least one upload and attempts at least one rebuild when the deadline is expired.

This identifies scheduling as a possible responsiveness follow-up, rather than demonstrating
a safe new budget or priority policy. More enqueue work can create more obsolete tasks;
more GL work can increase frame tails. The existing moving path includes empty-section
builds and cannot establish visible-update latency or its acceptable tradeoff. No production
change was implemented without that evidence. Worker count, queue/buffer capacities, FIFO
ordering, cancellation, publication, GL thread, visual settings and rendering code stay intact.

## Protocol and environment

Eight complete Java 8 launches: three controls, three non-JFR diagnostics, two JFR diagnostics.
Every launch copies `run/saves/alacer-benchmark` to its dedicated run world before playing;
`world.json` hashes every base file and the analyzer requires it to remain unchanged. Every
run records static 10 s, pan 15 s and a single 20 s flight at 20 blocks/s; setup-frame capture
is discarded. No second flight over built terrain is treated as an A/B sample. Natural
animals remain (the first diagnostic had 11 at capture; most runs had 9), with zero added
mobs/chests. The controls are repeated observations, not an estimate of diagnostic overhead:
separate launches have JIT, server and GL timing variability. Initial control r1 predates the
new empty metric columns/bookkeeping; subsequent controls have diagnostic hooks disabled.

Verified in the client: i7-12700F / RTX 3060 Ti / NVIDIA 616.92, Temurin 8u492, 2G requested
heap (HotSpot reports 1820 MiB usable maximum), 1920x1080, distance 12, fancy graphics/clouds,
smooth lighting 2, four mip levels, VBO off, vsync off, FPS option 260 (unlimited here), six
selected render defaults on, all experiments and verification off. Driver settings were left
alone; CPU utilization is not evidence of driver threading.

The first three diagnostic launches developed the instrumentation. `detail-r1-classes.json`
records the first loaded diagnostic classes; that pilot predates the new config field's
inclusion in describe() (the JVM arguments and nonzero metrics establish detail=true).
The next JFR pilot adds correct config export, the warmed-selection guard and excludes
client lifecycle buffer drains from worker waits. `diagnostic-age-sources.json` freezes the
next pilot, adding dirty-set ages and scheduling counters. `implementation.json` freezes
all four final launches: two controls, a repeated age diagnostic and the final JFR diagnostic
with wall-clock alignment. No earlier pilot is silently represented as the final source.

## Instrumentation and interpretation

Three startup-gated mixins forward to ordinary components. Client geometry building,
rebuildSync, enqueue, upload drain, GL lists and VBO upload have separate timers. Two task
timestamps measure construction to COMPILING and UPLOADING to successful DONE. Canceled
tasks do not contribute successful upload latency; geometry timers include compile calls
that return early after cancellation. Buffer wait measures takeBuffer only on workers.

Worker wall times and task latencies are summed **when each interval completes**, using
fixed-size synchronized duration/count pairs. The client drains them once per frame, then
updates its own ring. These intervals can cross frames/phases; no worker touches FrameProfiler.
They are not client CPU time and must not be added to render. Dirty ages/maxima are snapshot
gauges, also not render work. Counts are tasks/jobs/calls, not unique visible chunk updates.
No per-task latency percentile is inferred from per-frame sums. The ages add one small stamp
per diagnostic RenderChunk, with no retained world/task/buffer references or global map.
Replacing storage/world drops stamps with their owner; resetting a slot's origin resets age
on observation. Fixed aggregates may include late completions across a world transition;
they carry no world identity, so transition frames are unsuitable for per-world attribution.

`uploadGlList` is client wall time from glNewList through glEndList, including geometry upload,
state changes and any driver/GC wait inside it. It is not GPU execution time. `chunk_upload`
includes drain and subsequent enqueue; `chunk_sync` is inside terrain setup; compile/upload
timers nest inside their callers. Final environment metadata includes the wall-clock anchor
for `start_ns`, with explicit millisecond resolution/sampling uncertainty. JFR attribution
uses that uncertainty and assumes no clock jump during the run.

The preliminary JFR recorded 21 GC pauses during the logged flight, 3–110 ms. It is too
intrusive for selection and only log-second aligned. The final recording is analyzed against
frame timestamps by `analyze_jfr.py`; `jfr.json` retains conservative overlap estimates,
worst frames, GC/VM intervals and sampled client stacks. With 1.004 ms clock uncertainty,
the final flight contains 20 GC pauses totalling 396.8 ms. Its 181.3 ms worst frame contains
a 158.1 ms SafepointBegin interval followed by a 19.4 ms ParallelGCFailedAllocation VM
operation with the same safepoint ID; only 0.097 ms is in chunk_upload and 0.023 ms in list
compilation. A 144.1 ms frame overlaps at least 139.0 ms of GC, and a 78.3 ms frame contains
59.1 ms of SafepointBegin followed by 15.1 ms of the GC operation. `jfr-vm.json` retains those
three frames. GC pause and ExecuteVMOperation intervals overlap: do not add them. This
locates the diagnostic stalls in VM intervals without identifying which thread delayed a
safepoint or proving the same cause for every control outlier. These describe the diagnostic process,
not the exact GC causes of the controls. The JFR maximum is not a normal-play performance
claim. Raw `.jfr` and event JSON remain ignored under run/ and build/.

## Reproduction and validation

From the repository root (existing labels are protected; use new labels for a new series):

```powershell
.\gradlew.bat test build --console=plain
& docs/benchmarks/2026-10-19-chunk-pipeline/run.ps1
python docs/benchmarks/2026-10-19-chunk-pipeline/analyze.py
jfr print --json --events 'jdk.ExecutionSample,jdk.NativeMethodSample,jdk.GCPhasePause,jdk.JavaMonitorEnter,jdk.SafepointBegin,jdk.ExecuteVMOperation' run/cp-detail-jfr-r5.jfr > build/cp-jfr-final-events.json
python docs/benchmarks/2026-10-19-chunk-pipeline/analyze_jfr.py
```

`run.ps1` runs the four final launches. Earlier pilots and initial control have archived
summaries and exact JVM arguments. `analyze.py` validates all eight existing runs against
raw CSVs: settings/config, complete phase sets/full durations, no dropped rows, one excluded
setup frame, frame-clock continuity/totals, zero detail columns in controls, matching source
hashes/base world, successful logs and absence of rendering/task-state failures. It rebuilds
`metrics.json`, `measurements.md` and copies summaries to `runs/`. Raw CSVs stay under ignored
`run/alacer/benchmarks`; launch logs are `build/<label>.log`. The legacy missing Realms/Twitch
and console-appender startup messages are unchanged and are not rendering failures.

The final build passes **44 tests, zero failures/errors/skips** on the Gradle JVM. Added tests
exercise paired totals under four concurrent writers/draining, dirty-age reset behavior,
startup flag precedence and the gating of every chunk diagnostic mixin. All game measurements
run the actual Java 8 client. Final static and fly screenshots were visually inspected:
terrain, foliage, water and clouds show no obvious missing geometry or corruption in those
two views. Screenshots are saved outside captures; this is limited visual inspection, not
framebuffer identity or arbitrary-mod compatibility proof. Paths/hashes and the exact test
totals are in `verification.json`.

Limits: one machine/world/settings profile, zero added mobs, three control flights; no ground
edit or continuous-light-invalidation scenario, VBO performance comparison, arbitrary-mod
certification, GPU timestamps, worker CPU/allocation accounting, or per-task tail-latency
histogram. Standard profiler allocation covers the client thread only; JFR changes allocation
and GC behavior. No performance improvement was selected or claimed. Nothing is committed,
pushed, tagged, published or released; the checkout remains wholly untracked on master.
