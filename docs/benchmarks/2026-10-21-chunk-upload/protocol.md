# Session 27: chunk upload/compile in fresh-flight frame tails

Roadmap item 1 of "Next session (27)". Date: October 21, 2026 (America/Los_Angeles).
Freeze this protocol, analyzer, runner, all source/resources, build configuration and compiled runtime artifacts
in manifest.json and a hash-verified sources ZIP BEFORE the first measured launch. No renderer change is tested.
All nine shipped defaults stay on, all experiments and verification off. Historical archives and session 23/25/26
tools remain unchanged. Builds and analysis run only outside client launches; no source/tooling/document edits while
a client runs. All attempts, including preflight failures, smoke failures and rejected captures, remain on disk.

## Question and scenario

Is upload/compile a repeatable ordinary-profiler cost in fresh-flight tails, which of its timed subregions carries
it, and does the evidence support one exact removable-work transformation? Session 13 found dirty-set-dominated
pending counts, short actual worker queues, client GL compilation and first-observed ages that are LOWER BOUNDS,
not invalidation-to-visible latency. Session 14 found backend throughput/tail tradeoffs on Java 8. Neither archive
is a Java 25 control, a scheduling policy, or grounds to force a backend. Session 26 motivates this question through
ordinary CSV times; Java samples alone cannot rank driver compilation and are not collected in this series.

`benchmark.chunkFlight=near|high` is an opt-in dedicated scenario in BenchmarkRunner. Both scenes use one eastward
straight path at 20 blocks/s from the seed-1789 spawn anchor (-200.5, ground 65, 267.5), yaw -90, pitch 20. Heights
above anchor ground are 24 blocks (near) and 64 (high), fixed along the entire path (not terrain following).

Each launch: fresh byte-identical copy of `run/saves/alacer-benchmark` into the harness-owned run world, excluding
session.lock; byte comparison finishes before Minecraft opens the copy and is logged. Base hashes are checked
before and after every launch. At the flight origin only: stationary turning warmup 10 s, then still reference
10 s (same altitude/orientation as the flight start). Last: ONE 40 s fresh flight, 800 blocks, no loops or return
flight. The first approximately view-distance worth of terrain is prebuilt during settling, as in real departures;
report 10 s time bins so this is not silently equated to sustained generation. The high scene varies visible terrain
and empty-section work; it does not establish generality across seeds. There is no block-change latency experiment.

1920x1080, 2 GiB heap, render distance 12, fancy graphics/clouds, smooth lighting 2, four mip levels, vsync off,
unlimited FPS (option 260), no added mobs/chests or particles. Natural animals remain. `benchmark.vbo=false` explicitly
selects display lists, the existing option default. Check actual GLX backend AND render-chunk storage class in each
phase. No driver/global JVM/config settings are changed. Snapshot visual settings per phase and reject changes.
The existing harness discards one setup frame and rejects dropped rows; phase arrays may affect memory/GC.

## Launch order and hygiene

Prefix `cu27`, unique `build/<label>.log` for every attempt; existing attempts/logs/raw labels/JFR files are refused.
One pre-freeze smoke: cu27-smoke-near, same diagnostic flags, timeScale 0.1 (1/1/4 s), not evidence. If any smoke
requires correction, retain it and choose a new suffix; freeze only after tooling works. No measured capture is
silently retried, deleted or rescored under changed code. Any measured-source change needs a separately frozen series.

Ten measured launches at timeScale 1, in this exact order:

1. cu27-near-control-r1
2. cu27-high-control-r1
3. cu27-high-control-r2
4. cu27-near-control-r2
5. cu27-near-control-r3
6. cu27-high-control-r3
7. cu27-high-detail-r1
8. cu27-near-detail-r1
9. cu27-near-detail-r2
10. cu27-high-detail-r2

Three controls per scene with chunkDetail and GPU probe OFF, no JFR, no screenshots. Two intrusive diagnostics per
scene with chunkDetail AND sparse GPU probe ON, screenshots only after each capture stops. These two modes are
separate populations, never pooled or treated as an optimization A/B. Diagnostic perturbation is reported; this
series cannot isolate the GPU probe's overhead from other detail hooks. Warmup frames are archived, never ranked.

The runner sets JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot for Gradle and client. Builds and
tests use that JAVA_HOME too. Before EVERY launch, query java.exe AND javaw.exe for devlaunchinjector.Main/KnotClient
and require none. Observe the real child ExecutablePath, require this JDK's bin/java.exe, --enable-native-access,
-Xmx2G, every forwarded -Dalacer.* option, and absence of JFR/JIT sampling flags. JVM/runtime identity also comes
from the exported environment. Clients run serially; no analysis/build/test alongside them. Gradle runClient uses
--offline; compileJava and processResources must be UP-TO-DATE and frozen class/resource hashes must not change.
Sources/tooling, base world and shared configuration hashes are checked both sides. Shared alacer.properties must
remain SHA256 694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31. Reject GL/task/mixin errors, verifier
mismatches, timeouts/aborts, wrong scene/backend/settings, missing/nonpositive CPU/allocation, drops or duration gaps.

## Measurements and interpretation

Rank with ordinary-profiler CSV wall sections. All/tick (`tick_ns>0`)/nontick, slowest exact ceil(1%*N) and
ceil(5%*N) by frame_ns, and slowest 1% OF ticking frames. Report frame mean/p95/p99/max, slow-1% mean, tick-frame
p99, over-8/16.67 ms counts, chunk_upload alongside tick/terrain setup/draw/swap, per-scene and per-launch. Ties in
slow-group membership follow stable CSV order. Nested regions are NOT added to enclosing sections. Store worst
20 rows per phase, 10 s bins, allocation B/frame and B/s, client and whole-process CPU ms/frame, visible/pending
gauges. Frame sums must match exported wall duration within .05 s and timestamps must be contiguous.

Only diagnostics expose the subcolumns: chunk_drain, chunk_gl_list, chunk_vbo, chunk_enqueue, synchronous/client
builds and worker build/queue/buffer/upload completion sums; per-layer submit counters, calls/s, dirty/queued/ready
gauges, empty buffers and expired deadlines. Worker sums complete asynchronously across frames and may cross phase
boundaries; they are neither CPU time nor nested client work. Dirty/pending gauges count entries/tasks/jobs, not
unique visible updates. No per-task latency percentile is inferred from per-frame completion sums.

Sparse GPU timestamp pairs bracket runTasksUntil, OUTSIDE glNewList/glEndList (GL_COMPILE records geometry rather
than drawing it immediately). Every 32nd captured drain call attempts one pair from 64 bounded slots; 8192 bounded
rows, phase identity retained at issuance, host start/duration plus whether drain ran any job. Check extension and
timestamp counter bits. Poll BOTH markers for availability before reading; no glFinish, glFlush or blocking read;
delete all queries at benchmark finish, report unsupported/dropped/outstanding counts, negative rows unresolved.
Report nonempty and empty drain distributions separately, exact sampled counts and tails. GPU command-stream
elapsed includes host emission/idle gaps and diagnostic markers; it is NOT GPU busy time, geometry draw time, or
driver CPU time. Host drain time is also wall time (may include descheduling/driver waits). No GPU/driver causality
or expected speedup is inferred by subtracting these clocks. A GPU/driver-sensitive candidate needs independent
corroboration; an unresolved timing disagreement disqualifies naming it from this series.

## Predeclared selection rule

Units are the two scene flights independently; still is a reference. A large upload region requires in at least
2/3 controls in EACH scene: upload at least 10% of mean frame time OR at least 20% AND 0.5 ms of slow-1% mean frames.
This deliberately ranks observed wall cost, not JFR sample share. Diagnostic corroboration additionally requires
GL-list compilation at least 70% of upload time in BOTH diagnostics in each scene, and the median diagnostic upload
fraction of frame time within 25% relative of the median control fraction. Failure flags perturbation/instability,
not an excuse to replace controls with favorable diagnostics. Report section ranks and all contrary observations.

Naming a candidate requires ALL of:

- the region passes those repeatability and corroboration gates;
- one named, independently justified transformation removes an exact bounded set of operations totaling at least
  10% of upload time or 20% of upload time in slow-1% frames in each scene, supported by sub-timers AND runtime
  source/bytecode/counters; an entire required GL compile is not automatically removable;
- a stated invariant preserves exact geometry, per-layer/translucent order, callbacks, cancellation/publication,
  GL object ownership and lifecycle. It must specify what work disappears rather than deferring uploads or moving
  a wait into terrain draws/swap;
- it does not rebrand a closed experiment, force the backend from session 14, or infer a safe scheduling policy
  from first-observed ages/empty-section builds. Any scheduling/budget candidate requires exact visible-update
  latency evidence, which this fresh-flight-only series does not claim;
- GPU/driver-sensitive claims have agreeing independent timing and no unexplained contradictory signal.

If multiple qualify, name the one with the largest minimum (across scenes) removable fraction of slow-1% upload
time. If no exact set qualifies, record NONE, even when the aggregate upload region is large. Explicitly distinguish
an eligible region, a possible future design, a named candidate, and a selected optimization. This session never
implements or promotes a candidate. Exactness is a documented post-measurement audit under the frozen rule.

## Separately frozen candidate validation gates (NOT met by attribution)

Before implementation, freeze a separate series/design. The controls here never promote an optimization.
Correctness: Java 25 build/tests; verify every replaced result/geometry against retained vanilla/control in both
scenes (at least 10 million operations, or every affected frame for per-frame output), zero stable-input mismatches.
Compare screenshots and exact geometry/layer/submission order where affected. Exercise world replacement/re-entry,
chunk unload/reload, resource reload, distance/storage change, F3+A and BOTH backend switches during diagnostic
capture; dispose/recreate GL objects only on their owning render context/thread; compare cancellation and callbacks.
Pending tasks from old worlds must never publish into replacement storage. Keep startup hooks absent when off,
option outside render.defaults and existing control path available. No foreign performance-mod implementation.

Performance: FOUR startup absent/on pairs per scene, alternating A/B, B/A, B/A, A/B, a new identical copied world for
EVERY member and ONE scored flight each. No warmed in-process second flight is a comparison. Four separate still/pan
in-process warmed A/B/B/A or B/A/A/B launches are allowed as nonmoving companions only. No diagnostics on controls.
Per scene, medians of paired changes, at least 3/4 pairs agree in target cost:

- upload/target ordinary CSV section at least 5% lower; frame mean at least 2% lower in one selecting scene and at
  most +1% in every other scene/phase; delivered upload/build work/s at least control (no unfinished-work saving);
- frame p95/p99 and tick-frame p99 at most +5%, slow-1% mean not higher, >16.67 ms frame incidence not higher;
- client CPU/frame not higher; whole-process CPU/frame at most +2%; allocation/frame at most +2%, report allocation/s
  and worker/process allocation too where buffers/worker ownership change;
- visible/layer/entity work counters within 2%, same path/distance/content and outstanding backlog at exit not worse;
- exact block-change-to-visible latency in a separately built ground/block/light scenario: mean/p95/p99 at most +5%,
  maximum and canceled/unloaded/world-replaced task correctness reported. First-observed ages cannot satisfy this;
- report pair-order/time drift over 10%; with median drift over 10%, beat both order groups independently. Other
  default/d200/terrain/particle reference scenes must keep frame mean within +1% and tail gates within +5%.

Any missing correctness, lifecycle, ordering, throughput, latency, frame-tail, CPU or allocation gate keeps the
candidate unselected/off. Report noisy/regressing metrics without discarding launches.

## Boundaries

Build/tests validate compilation/pure logic; live development-client launches validate this scenario/platform only.
One seed, two heights, one Windows/NVIDIA machine. Client-thread allocation excludes workers/native/driver memory.
No framebuffer identity, arbitrary mods, production launcher, network server, exact invalidation latency, driver
profiler trace, whole-frame GPU busy time, or causal FPS gain is claimed. Preserve Java 8 and frozen archives;
no commit/push/publish/stash/reset/clean/branch switch. Later documentation edits preserve legacy bytes and endings.

Pre-freeze tooling check: cu27-smoke-near passed all launcher/copy/backend/config/CPU/allocation checks;
165/165 timestamp pairs completed, zero dropped/outstanding. It is excluded from results. Analyzer CSV/group/GPU
smoke checks passed. Java 25 tests: 88 passed, zero failures/errors/skips. Runtime vanilla bytecode and its jar
identity are frozen alongside the protocol for the exactness audit; no Minecraft or third-party mod code is imported.
