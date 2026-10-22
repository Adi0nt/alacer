# Session 14: display lists versus VBO

This phase compares Minecraft 1.8.9's existing terrain backends with visual quality fixed.
It keeps Java 8 for the game and bytecode, JDK 25 for Gradle/tests, the six selected Alacer
defaults on and all three experiments off. Particles remain the next independent scenario.
No driver settings are changed. **VBO is the consistent throughput winner on this profile;
no production renderer or global backend/default change is selected.** Typical frames and
CPU improve strongly, but allocation per second and isolated fly maxima regress, and a
diagnostic queue-start wait regresses with a different task rate. This is a qualified
throughput result, not an unqualified smoothness/no-responsiveness-regression recommendation.

## Performance result (eight controls)

Negative paired change means lower cost. Ranges contain all four launches of each backend;
each percentage is the median of four adjacent, balanced launch pairs, not a ratio of pooled
frames or a confidence interval. VBO wins mean frame, client CPU, p99 and slowest-1% mean in
**all 16 launch-pair × scored-phase comparisons**.

| Phase | DL mean range ms | VBO mean range ms | Paired mean change | Client CPU/frame change | p99 change | Slowest 1% mean change |
|---|---:|---:|---:|---:|---:|---:|
| steady | 1.376–1.900 | 0.665–0.976 | -51.2% | -70.3% | -48.6% | -47.5% |
| pan | 2.177–6.860 | 0.971–1.391 | -58.1% | -67.1% | -27.5% | -27.0% |
| sort | 2.486–8.218 | 0.959–1.571 | -61.5% | -69.9% | -34.1% | -36.9% |
| fly | 2.872–3.790 | 1.050–1.719 | -58.8% | -70.3% | -41.7% | -42.5% |

The >=5%-cost-reduction/3-of-4 agreement throughput gates and median p99/slowest-1% gates
from the protocol pass in every phase. They do **not** establish every responsiveness gate.
Submitted terrain-layer wall time falls by median paired 75–80%; sky time also falls. Both
are reported separately because vanilla's backend setting changes sky too. These are wall
timers, not GPU time or a driver-thread identity measurement. The complete per-run CPU,
process cores, allocation, tails, section breakdowns, threshold counts/rates, pending work,
paired effects and same-backend drift are in [measurements.md](measurements.md) and
[metrics.json](metrics.json); [controls.json](controls.json) records selection independently.

**Allocation tradeoff:** median paired bytes/frame change is -12.9/-19.2/-48.8/-54.2%
for steady/pan/sort/fly, while bytes/second changes **+72.7/+82.5/+28.3/+13.6%**. The
faster backend renders more frames per second and amortizes time-based ticks/build work
over more frames. This is not an allocation-rate optimization. Client-thread allocation
does not include builder, server or native/driver memory.

**Rare stalls remain:** fly maxima are **132.6–309.6 ms (DL)** versus **249.9–391.9 ms
(VBO)**. VBO's maximum is worse in all four pairs; the median paired maximum regression is
22.8%. There are one >33.33 ms fly frame per DL run and one/two per VBO run. Improved p99
and slowest-1% means do not remove these spikes. The worst frames occupy different
sections (solid terrain, visibility, sky, residual render or swap); `chunk_upload` is small
in them. No control GC/safepoint log exists, so their precise cause is unverified.

**Drift is retained:** `bvs-dl-r3` pan/sort means are 6.860/8.218 ms, versus 2.177–2.521 /
2.486–3.040 ms in the other DL runs. Its settings/CSV/log checks pass and it is not excluded.
VBO r3 also has higher terrain times than its other controls. From r1 to r4, same-backend
mean drift is DL +19/+12/+22/+4% and VBO +6/+24/+22/+20% across the four phases. Even the
slowest VBO mean remains below the fastest DL mean in each phase. This does not identify
a driver mode, causal thread, thermal state or reason for the anomalous control.

## Diagnostic attribution and responsiveness

Two full detail launches per backend are a separate timing population. They run slower and
show smaller draw differences than the controls; do not use their FPS for selection or
subtract them from controls to claim exact diagnostic overhead. The first DL diagnostic's
setup/warmup/early steady interval also overlapped control CSV validation. Its later sort
and fly intervals and the subsequent diagnostics did not overlap heavy analysis.

| Diagnostic fly metric | DL range | VBO range |
|---|---:|---:|
| Terrain submission wall ms/frame | 0.948–1.184 | 0.867–0.897 |
| Backend GL upload wall ms/frame | 0.193–0.266 | 0.027–0.030 |
| Backend GL upload wall ms/call | 0.176–0.213 | 0.022–0.024 |
| Worker geometry wall ms/completed call | 0.369–0.386 | 0.354–0.407 |
| Worker sort wall ms/completed call | 0.017–0.025 | 0.014–0.020 |
| Construction to task-start ms/completed interval | 0.713–0.724 | **0.878–0.879** |
| Task starts/second | 545–621 | 702–714 |
| UPLOADING to successful DONE ms/task | 2.570–4.552 | 1.275–1.508 |
| First-observed dirty wait to accepted enqueue ms | 27.3–60.5 | 9.3–13.7 |
| Time-weighted pending entries | 65.6–108.2 | 27.2–35.0 |

VBO uploads take much less client wall time in these diagnostics, while worker geometry
cost is similar. Per-call upload means have different geometry mixes/counts; they are not
matched job-by-job causal comparisons. Fly time-weighted submitted chunk counts are close
but not identical; moving publication/visibility state differs. For the local orbit, counts
are especially close (solid about 311, translucent about 62), and 895–984 worker sorts
complete in each 16-second diagnostic. The scene exercises actual translucent work.

Queue-start wait rises about **22%** for VBO while task-start rate is 13–31% higher across
these runs. Other pending/dirty/upload proxies improve, but the task-rate difference alone
does not prove the cause of that regression or clear a universal no-latency-regression gate.
The phase supplies no exact invalidation-to-visible-update timing. No worker/queue/buffer,
scheduling, publication, cancellation or draw-order change is justified by it.

**Decision:** retain VBO as an explicitly measured throughput preference for this machine
and profile, with the stated tail/allocation/responsiveness tradeoffs. Do not globally force
it, add a seventh optimization toggle, or claim universally smoother play. The six selected
Alacer defaults and three disabled experiments remain unchanged. Freeze both Java 8 backend
controls for future work. No production optimization is invented to accompany a setting win.

## Post-hoc GC companions

Two additional full Java 8 launches (`bvgc-dl/vbo`) keep the frozen sources, detail off and
fresh world copies, adding GC/safepoint log flags only. They were added after the performance
result and are excluded from selection. Full method, timestamp semantics and primary-source
reference are in [gc-protocol.md](gc-protocol.md); parsed raw events, complete companion
metrics, hashes and worst-frame overlaps are in [gc.json](gc.json).

Their worst fly frames are **243.470 ms (DL)** and **347.342 ms (VBO)**. With about 10.003 ms
alignment allowance (anchor + log rounding + observed 7 ms wall/uptime spread), conservative
overlap with total safepoints is **223.464/327.335 ms**. The corresponding safepoints last
238.562/343.600 ms, with synchronization only 0.073/0.065 ms; full-GC (Ergonomics) entries
last 221.584/328.001 ms, alongside young collection. Upload occupies 0.023/0.313 ms in the
worst frames. GC and total-safepoint intervals overlap and must not be added.

This establishes GC/safepoint attribution for those companion frames. It does not establish
which thread or allocation site triggered GC, whether recorder retention caused it, or that
every original control spike has the same cause. Logging itself can perturb execution.

## Investigation and protocol

The existing `benchmark.vbo` property is sufficient to select the backend before world load:
`WorldRenderer.setWorld` calls `reload`, which replaces storage and switches factories/list
implementations. Sky geometry changes backend too. Merely changing the option mid-world
would not supply a warmed, identically fresh comparison. This phase uses separate launches,
explicit GLX/storage checks, discarded warmups and balanced repeated order instead.
Cloud caching and entity/model display lists remain active in both settings; this does not
replace every display list in the game with a VBO.

Vanilla display-list drawing iterates each submitted chunk, pushes/translates its matrix,
calls that layer's list, then pops. Vanilla VBO drawing also multiplies the chunk matrix,
binds its buffer, establishes the interleaved vertex/color/two-UV pointers and draws it.
Pointer state refers to the bound buffer; moving those calls out of the loop is not a safe
unmeasured optimization. The list path captures geometry/matrix during upload; the VBO path
uploads buffer data. Both retain vanilla's translucent ordering and client-thread GL work.
These source differences suggest costs to measure, not a performance conclusion.

The new opt-in scene creates four 13-by-7 white stained-glass walls (364 blocks) at z offsets
10/14/18/22 in the run copy. The orbit moves at radius 12 around their center, looking at the
walls, to provoke vanilla's >1-block resort condition. Natural terrain/water/animals remain;
no mobs/chests are added. White glass demonstrates overlapping transparency but is a limited
ordering oracle: this is not a multicolor pixel-identity test.

| Phase | Seconds | Use |
|---|---:|---|
| warmup-pan | 15 | Discarded, two turns, exposes all surrounding terrain |
| warmup-sort | 16 | Discarded, two orbits, warms local translucent motion |
| steady | 10 | Fixed camera, existing built terrain |
| pan | 15 | Two turns, visibility plus drawing |
| sort | 16 | Two local orbits, translucent sorting/upload |
| fly | 20 | One 400-block flight, fresh copied input/build state |

Each phase settles pending work first and excludes one recorder setup frame. A fresh flight
does not mean every chunk was unseen: both backends start from the same saved input and path;
the warmups build nearby chunks. No second flight is used as an A/B sample. Full predeclared
selection gates and exact camera/settle rules are in [protocol.md](protocol.md).

Eight controls run DL/VBO/VBO/DL/VBO/DL/DL/VBO, paired by adjacent opposite backends. Each
process gets a new copy of the byte-identical base world. Detail, JFR and screenshots are off.
Four diagnostics then run DL/VBO/VBO/DL, detail on, screenshots outside recorded intervals.
Diagnostics describe attribution and responsiveness proxies, never selection FPS. Frames
within one launch are correlated; there are four independent launch pairs, not thousands of
independent replicates. No assumption about driver threading follows from CPU utilization.

## Instrumentation and evidence

Environment: i7-12700F, RTX 3060 Ti, NVIDIA 616.92/OpenGL 4.6, Windows 11, Temurin
8u492, requested 2G heap (1820 MiB usable maximum). Window 1920x1080, view distance 12,
fancy graphics/clouds, smooth lighting 2, mipmaps 4, FOV 70, gamma 0, anaglyph off,
particles option 0, entity shadows and view bobbing on, vsync off, FPS option 260
(unlimited). The backend flag is the intended varying setting. These values are checked
against exported metadata and per-phase snapshots. Driver settings are left alone.

Session 13's opt-in pipeline timers already distinguish list compilation, VBO upload, worker
geometry and sorting, queue/buffer waits and successful task upload completion. A fourth
startup-gated mixin adds terrain submission wall time and per-layer submitted chunk counts.
It forwards into ordinary components; no GL calls or rendering behavior are replaced.

Submission is nested within the four terrain-layer sections, which also contain filtering
and translucent scheduling. `chunk_upload` includes draining and enqueue work. Do not add
these nested times. Native GL wall time includes possible waits and VM interruptions and
does not measure GPU execution. Completed background duration/count pairs can cross frame
and phase boundaries and are not client CPU sections. Pending entries include dirty chunks,
build tasks and per-layer upload jobs, omit active builds and can represent a chunk twice.
Dirty ages are sampled lower bounds, not exact invalidation-to-visible latency. Per-job
upload means do not normalize geometry size or prove a matched job-by-job saving.

The same profiler/recorder is installed in both backends. At full scale it preallocates
190,000 rows × 111 primitive columns across all six phases: about **161 MiB** of column
capacity, plus array headers, ring history and normal game/world memory. Warmup captures
remain retained until export. Excluding setup frames removes their immediate setup work,
not their later heap/GC effects. This is an instrumented benchmark baseline, not a claim
that ordinary gameplay has the same allocation, heap residency or worst-frame behavior.

`initial.json` freezes initial source/settings and every file in the protected session 12/13
archives. `world.json` hashes the unchanged base-world files. `implementation.json` freezes
the final source, build config, runner and predeclared protocol. The original `bv-*-smoke`
pilots used ordinary cutout glass; bytecode inspection caught that before selection. The
corrected `bv-glass-*-smoke` runs use stained glass before the shutdown-restoration fix and pass the
analyzer with `--smoke --prefix bv-glass`; they are quarter-duration and excluded. No pilot
is silently treated as a final full control. Initial corrected smoke results are retained in
`smoke.json`. A subsequent shutdown-restoration review stopped the first collection
(`bv-dl-r1` completed, `bv-vbo-r1` interrupted); `development-implementation.json` and
`development.json` retain those identities. Final `bvs-*-smoke` validates the restoration
through vanilla reload after export. Its metrics are in `smoke-bvs.json`; final full labels
are `bvs-*-r1/r2/r3/r4` and `bvs-*-detail1/detail2`.

## Reproduce

From the repository root, with a **new prefix** for new collection (existing labels are
protected, including failed logs):

```powershell
.\gradlew.bat test build --console=plain
& docs/benchmarks/2026-10-19-display-lists-vbo/run.ps1 -Mode smoke -Prefix new-smoke
python docs/benchmarks/2026-10-19-display-lists-vbo/analyze.py --smoke --prefix new-smoke
& docs/benchmarks/2026-10-19-display-lists-vbo/run.ps1 -Mode controls -Prefix new
& docs/benchmarks/2026-10-19-display-lists-vbo/run.ps1 -Mode diagnostics -Prefix new
python docs/benchmarks/2026-10-19-display-lists-vbo/analyze.py --prefix new
```

The analyzer validates raw CSV continuity/count/timing/statistics, durations, setup/dropped
frames, config/quality snapshots, requested and actual backend, diagnostic gating, expected
upload backend, sort activity, successful launch logs, frozen source/base world and unchanged
historical archives. It reproduces `metrics.json`, `measurements.md` and archived summaries
under `runs/`. Use the default `--prefix bvs` to reanalyze the collected full series. Raw CSVs
stay ignored under `run/alacer/benchmarks`, launch logs under `build/<label>.log`, screenshots
under `run/screenshots`. A new full prefix writes generated metrics under `series/<prefix>/` to protect the final
series. It never runs the older generators that could overwrite evidence.

Post-hoc GC companions reproduce separately with `run_gc.ps1`/`analyze_gc.py` as described
in `gc-protocol.md`; use new prefixes. The final collection comprises **14 complete full
Java 8 launches** (8 controls, 4 detail diagnostics, 2 GC companions), plus excluded smoke
and development pilots. All full launches pass phase/CSV/settings/backend/log checks with
zero dropped frames and one discarded setup frame per phase. Final Java 8 bytecode/build
and **47 passing tests** are recorded in `verification.json`. Final steady, sort and fly
screenshots from both backends were visually inspected with no obvious geometry, water,
foliage, cloud or transparency defect in those views. They do not prove pixel identity.

`java8-baseline.json` freezes source/artifact/world/profile identities and both backend
control groups for later work. `decision.json` records which gates passed and why a blanket
responsiveness/default recommendation was not selected. A Java 25 client investigation is
separate; these Java 8 launches and JDK 25 build/tests do not validate that runtime.

Limits: one machine, saved world, view distance and visual profile; natural animal/tick/JIT/GL
and server scheduling drift; finite warmup; no GPU timestamps, driver-thread identity,
matched per-job geometry normalization, exact visible-update latency, arbitrary-mod or
pixel-identity certification, Java 25 game run, or particle scenario. A settings result does
not justify globally forcing a backend. Nothing is committed, pushed, tagged or published.
