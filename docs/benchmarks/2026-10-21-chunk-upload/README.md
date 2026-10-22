# Session 27: chunk upload/compile in fresh-flight frame tails

October 21, 2026. Roadmap item 1 of "Next session (27)". **Measured; no candidate qualifies.**
Neither scene passes the frozen region-size/repeatability rule. List compilation is most of upload time, but the
data do not identify an exact removable subset. No renderer optimization was implemented or promoted; all nine
defaults and all disabled experiments remain unchanged.

## Frozen scenario and evidence

[protocol.md](protocol.md), [manifest.json](manifest.json) and `sources-fd575d1dbef39179.zip` were frozen before the
first measured launch. The ZIP hashes to `8f10d78efa3fdb4af37bff6a0110c5621e5b9baef3d9c1830a69a9133f788bb3`.
The manifest also freezes the compiled runtime classes/resources and base world. Runtime vanilla bytecode and its
jar identity are included for the exactness audit. The source change adds an opt-in benchmark and attribution probe,
not a rendering/scheduling path. See [validation.json](validation.json) for the final preservation audit.

Two scenes follow the same unrepeated eastward path at 20 blocks/s, yaw -90, pitch 20, with fixed heights 24 and
64 blocks above spawn ground (y=89 and y=129). Every launch byte-compares a fresh copy of the seed-1789 base world
before opening it; session.lock is excluded. Stationary turning warmup 10 s, still reference 10 s, then ONE 40 s,
800-block fresh flight. Warmup never translates and is never scored. Each setup frame is discarded. No second
flight over built chunks, block-edit test or candidate A/B exists in this series.

Actual Adoptium Java 25.0.4.1 executable and flags verified on every launch; 1920x1080, 2 GiB, distance 12, fancy
graphics/clouds, smooth lighting 2, four mip levels, vsync off, unlimited FPS, no added mobs/chests/particle scenario.
Display lists explicitly requested and checked through GLX and actual GlListRenderChunk storage. Natural animals
remain (9-10 at capture starts). i7-12700F, RTX 3060 Ti, Windows 11, NVIDIA 616.92; no driver setting change.

Ten serial measured launches: six ordinary-profiler controls (near/high, high/near, near/high), then four intrusive
diagnostics (high/near, near/high). **All ten passed.** One short pre-freeze smoke also passed, but is excluded from
metrics. Every attempt/log/command/actual launcher is retained; no failed or rejected launch was dropped. No build,
analysis or file edit ran alongside a measured client. Gradle only launched the already built classes/resources
(compileJava/processResources UP-TO-DATE, runtime hashes unchanged). No JFR/JIT sampling flags in any measured client.

Controls have chunkDetail/GPU probes/screenshots off; diagnostics have chunkDetail and sparse GPU timestamps on,
with screenshots after capture. These populations are separate. The ordinary profiler remains installed in both.
Raw CSVs are under `run/alacer/benchmarks/`; logs under `build/cu27-*.log`; `runs/` archives commands, hashes,
summaries and configurations. [results.md](results.md) lists every phase; [metrics.json](metrics.json) retains all
frame groups, section columns, worst 20 rows, time bins, counters and GPU distributions.

## Ordinary-profiler controls, per scene

Values below are medians of three launches; brackets give the full range. Shares are medians of per-launch ratios,
not ratios of separately rounded medians. Times are client-thread wall time, including driver waits/descheduling.

| Fresh-flight metric | Near (y=89) | High (y=129) |
|---|---:|---:|
| Mean frame ms | 1.757 [1.622-1.996] | 1.997 [1.906-2.054] |
| Frame p95 ms | 3.596 [3.400-4.061] | 3.778 [3.701-3.922] |
| Frame p99 ms | 4.817 [4.553-5.457] | 5.014 [4.990-5.295] |
| Slowest-1% mean frame ms | 6.043 [5.690-6.983] | 6.282 [6.273-6.678] |
| Tick-frame p99 ms | 5.964 [5.319-7.263] | 6.499 [5.277-6.937] |
| Maximum frame ms | 17.309 [12.282-43.097] | 17.255 [13.401-18.961] |
| Upload mean ms | 0.161 [0.145-0.188] | 0.175 [0.163-0.182] |
| Upload in slowest-1% frames ms | 1.226 [1.070-1.345] | 1.081 [0.821-1.176] |
| Upload share of mean frame | 9.15% [8.96-9.41] | 8.76% [8.56-8.87] |
| Upload share of slowest-1% mean | 17.70% [17.56-23.64] | 16.18% [13.07-18.75] |
| Client CPU ms/frame | 1.704 [1.566-1.919] | 1.924 [1.850-1.989] |
| Whole-process CPU ms/frame | 3.510 [3.193-4.013] | 3.922 [3.586-4.063] |
| Client allocation bytes/frame | 28,625 [26,515-32,522] | 33,636 [31,288-34,724] |
| Client allocation MB/s (decimal) | 16.30 [16.29-16.34] | 16.85 [16.41-16.90] |

Each control flight contains 800 ticking frames and 195-247 slow-1% frames. All controls together have four frames
over 16.67 ms. The worst near frame is 43.097 ms, with 39.224 ms in terrain_cutout_mipped and 1.582 ms in upload.
Other maxima sit in swap, solid terrain or entities. These wall sections locate the stall; they do not establish
GPU/driver/GC/safepoint causality. No GC trace was recorded in this series.

| Median section ms per frame | Near all | Near slow-1% | High all | High slow-1% |
|---|---:|---:|---:|---:|
| chunk_upload | 0.161 | 1.226 | 0.175 | 1.081 |
| terrain_setup | 0.270 | 0.847 | 0.392 | 1.152 |
| terrain_solid | 0.316 | 0.908 | 0.332 | 0.885 |
| terrain_cutout_mipped | 0.206 | 0.682 | 0.225 | 0.777 |
| terrain_cutout | 0.165 | 0.422 | 0.176 | 0.444 |
| terrain_translucent | 0.056 | 0.168 | 0.062 | 0.153 |
| swap | 0.262 | 0.714 | 0.280 | 0.733 |
| entities | 0.120 | 0.517 | 0.150 | 0.569 |
| tick | 0.015 | 0.084 | 0.017 | 0.093 |

Upload is the largest single section of slow-1% frames in all three near controls and one of three high controls;
terrain setup leads the other two high controls. Terrain layers combined are substantial but are separate draws,
not compile/upload work. Never sum a subregion with its enclosing section.

Ticking-frame mean frame/upload/tick ms: near 2.475 / 0.101 / 0.431, high 2.645 / 0.105 / 0.416. Slowest 1% OF
ticking frames: near 7.159 / 0.816 / 1.219, high 7.356 / 0.504 / 0.896. Upload is therefore not the dominant
explanation of these tick-frame tails. Full nontick, slow95, slow99 and tick_slow99 sections are in metrics.json.

## Departure, drift and comparison boundaries

The first 10 s still traverse some initially built view-distance terrain: upload means are 0.067-0.073 ms near
and 0.078-0.095 ms high. Later bins rise to 0.161-0.285 / 0.169-0.293 ms. Some later bins exceed 10% upload share,
but selecting those bins after seeing the result would change the frozen unit (the whole 40 s flight). They do not
rescue a failed gate. They also do not prove exact invalidation-to-visible latency.

Near frame means vary by 23% between lowest/highest controls, with much larger variation in maxima. Diagnostics
have near slow-1% mean +16.9% and tick-frame p99 +32.6% versus the control medians; high diagnostic mean frame is
6.2% lower, while its slow-1% mean is 1.9% higher. These are observations across different launches, **not causal
overhead estimates or an optimization improvement**. Matching mean upload fractions does not imply matching tails.

Session 26 is not pooled/rescored: its flights lasted 80 s after much longer warmup, with JFR/JIT flags and a
different scene protocol. Here mean frames are 1.62-2.05 ms, terrain draws are much larger, and client CPU/frame is
about 96-97% of frame time; its launches were commonly about 53-58% in flight. No driver threading mode was measured
or changed, so the cause is unresolved. Similar absolute upload tail cost does not reproduce the same relative
bottleneck or establish a regression from this benchmark-only source change.

## Intrusive attribution (never pooled with controls)

| Fresh-flight diagnostic metric | Near, two launches | High, two launches |
|---|---:|---:|
| Upload mean ms/frame | 0.158-0.174 | 0.154-0.169 |
| Drain mean ms/frame (inside upload) | 0.154-0.170 | 0.151-0.164 |
| GL-list compile mean ms/frame | 0.149-0.164 | 0.147-0.160 |
| Enqueue mean ms/frame | 0.0030-0.0035 | 0.0030-0.0038 |
| GL-list fraction of upload | 94.0-94.6% | 94.9-95.1% |
| GL-list calls/s | 428-446 | 423-445 |
| GL-list wall ms/call | 0.192-0.202 | 0.186-0.192 |
| Worker geometry build calls/s | 877-912 | 1226-1295 |
| Worker geometry build wall ms/call | 0.250-0.270 | 0.171-0.180 |
| Task construction-to-start mean ms | 0.621-0.698 | 0.607-0.618 |
| UPLOADING-to-successful-DONE mean ms | 1.050-1.330 | 0.731-0.918 |
| Worker buffer wait mean ms/completed call | 0.107-0.162 | 0.076-0.106 |
| Observed dirty age at accepted schedule mean ms | 43.4-68.2 | 39.6-54.4 |
| Snapshot dirty age mean/max ms | 131-262 / 1115-2086 | 95-150 / 632-1224 |

There are zero synchronous/client geometry builds and zero VBO uploads in these flights. Dirty entries average
65-85 near / 71-95 high; queued builds about 4.6-4.7, ready layer uploads 1.3-1.6, available buffers about 4.0-4.1.
The queue is full in 0.10-0.15% of diagnostic frames; buffers empty in 7.5-9.4%; drain deadlines expired in 3.8-6.3%.
More high-altitude builds with similar GL-list rates are compatible with empty/canceled/otherwise nonuploading work,
but these counters do not classify each build or prove a removable client operation.

Completion sums can cross frame/phase boundaries, are not worker CPU time and do not give per-task p99 latency.
Dirty ages are first-observation lower bounds. They cannot select a scheduling/budget change. The dirty set still
dominates pending entries, consistent with session 13's warning against equating pending with worker saturation.

### GPU command-stream companion

4,859/4,859 timestamp pairs completed across all diagnostic phases, zero dropped or outstanding, 64-bit timestamps.
Every 32nd captured drain is sampled; results are read only after both markers are available, with no flush or wait.
The probe sits outside glNewList/glEndList, around the drain, and records empty versus nonempty drains separately.

| Flight diagnostic | Nonempty samples | Host mean ms | Command-stream mean ms | Host p99 ms | Command-stream p99 ms |
|---|---:|---:|---:|---:|---:|
| near r1 | 197 | 0.623 | 0.265 | 2.798 | 3.735 |
| near r2 | 181 | 0.586 | 0.217 | 2.455 | 2.509 |
| high r1 | 181 | 0.538 | 0.212 | 2.024 | 2.015 |
| high r2 | 196 | 0.519 | 0.202 | 2.046 | 2.952 |

Empty-drain command-stream means are about 0.0008 ms. Nonempty pairs are variable and some command-stream tails
exceed host tails. Markers include host submission/idle gaps, query overhead and command-stream behavior; GL_COMPILE
records commands rather than drawing the geometry at this point. These are **not GPU busy times**, driver CPU
times or the eventual terrain draw cost. Subtracting these clocks would not isolate driver waits. No GPU/driver
causal optimization claim is supported, and the sparse samples do not describe exact control-tail frames.

## Decision under the frozen rule

[decision.py](decision.py) applies the rule to metrics and writes [decision.json](decision.json). The thresholds were
frozen before measurement; the exactness audit was written after measurement and says why each possibility fails.

| Gate | Near | High |
|---|---|---|
| >=10% mean upload OR >=20% and >=0.5 ms slow-1% upload, in >=2/3 controls | **fail, 1/3** | **fail, 0/3** |
| List compile >=70% of upload in both diagnostics | pass | pass |
| Diagnostic median upload/frame fraction within 25% relative of controls | pass (-0.19%) | pass (-1.46%) |
| Exact removable operation set >=10% of upload or >=20% of slow-1% upload | not established | not established |

**Named candidate: none.** No implementation/promotion follows. Even apart from the first failed gate, list
compilation is a required aggregate, not a proven redundant subset. The worker already filters rebuild uploads by
`CompiledChunk.hasLayer`; never-started layers are skipped. A started layer can still emit zero vertices, but this
series has no per-list vertex/byte census to quantify that cost. Deduplication needs exact content/invalidation
identity and cancellation/lifecycle invariants. Deferral/budget changes need exact visible-update latency and
delivered-throughput checks. Backend forcing is excluded by the protocol and session 14 boundary. None is renamed
as a candidate from a favorable aggregate or time bin.

## Validation and remaining gaps

- Build/pure logic: explicit Java 25 `gradlew test build`, plus `test --rerun --info`: **88 tests pass**, zero
  failures/errors/skips. Test worker executable confirmed; 151 main classes at classfile major 69. These tests
  validate the single-flight path/scene parsing and existing logic, not live rendering/lifecycle equivalence.
- Live: ten actual Java 25 development-client launches passed all runner checks, zero dropped rows and one discarded
  setup frame per phase. Source/resources/tooling/base/config hashes unchanged during every launch. Four inspected
  r1 still/flight screenshots (both heights) show terrain, foliage, water and clouds without an obvious hole or
  corruption in those views. No framebuffer identity or candidate correctness verifier is claimed.
- Missing: block/light-change-to-visible latency, per-list bytes/vertices/obsolete-upload identity, worker/native
  allocation and CPU attribution, driver trace/whole-frame GPU busy time, causal diagnostic overhead, resource reload,
  view-distance/storage changes, backend switching, re-entry/cancellation stress and arbitrary mods/production launcher.
  A fresh separately frozen candidate series must meet ALL correctness, lifecycle, ordering, throughput, latency,
  frame-tail, CPU and allocation gates in protocol.md. None were met by a candidate here because none was tested.
- Session 26's CPU/draw timing difference remains unexplained. This is one machine/seed, two heights and three
  controls per scene, with noisy maxima and diagnostic tail disagreement. No speedup is claimed.
- Historical archives (including Java 8), existing session 23/25/26 tooling, shared config and all defaults are
  preserved. No commit, push, publish, stash, reset, clean or branch switch.

## Reproduction

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat test build --console=plain
python tools/run_chunk_upload.py smoke
python tools/run_chunk_upload.py freeze
python tools/run_chunk_upload.py measure
python docs/benchmarks/2026-10-21-chunk-upload/analyze.py
python docs/benchmarks/2026-10-21-chunk-upload/decision.py
python docs/benchmarks/2026-10-21-chunk-upload/validate.py
```

The frozen series refuses existing labels/logs/attempts/manifest. Do not rerun its launches or edit its frozen
protocol/analyzer/runner. A later independent series needs a new archive/prefix and a new freeze. The analysis/validation
commands safely validate/rebuild the current archive's derived metrics/decision after all clients exit. The analyzer
is frozen; decision.py is a post-measurement audit, archived separately with final evidence hashes.
