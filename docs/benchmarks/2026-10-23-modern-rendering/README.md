# Exact culling math and modern OpenGL trials (2026-10-23)

Three independent opt-ins are implemented, all **off by default**, including
under `render.defaults`: `render.fastChunkNeighbours`, `render.terrainVaos` and
`render.directBufferUploads`. No production backend or visual-quality default
changes. See [implementation and API boundaries](implementation.md), the
[attribution protocol](attribution-protocol.md), and the unchanged
[correctness/performance gates](gates.md).

**The combination failed its frozen performance gates.** It is not a selected
speedup. Live correctness passed; the much larger native frame/CPU costs prevent
enabling it. The individual options also remain unselected: one attribution
launch each cannot establish an independent performance benefit.

## Machine and attribution

Native Windows 11, i7-12700F (20 logical threads), RTX 3060 Ti, NVIDIA 616.92,
OpenGL 4.6.0, Temurin Java 25.0.4.1 for Gradle, compiler/tests and game.
Control source d8abd3f; explicit vanilla VBOs, 1920x1080, distance 12, 2 GiB heap.
The ordinary default route and retained terrain scenario use fresh copies of
the same seed-1789 base world. Clients run serially; no concurrent builds, tests
or metric analysis. These are native-GPU results, separate from the earlier
llvmpipe chunk-scheduling evidence (whose timings are indicative only).

Before implementation, two 1 ms JFR launches with DebugNonSafepoints corroborated
ordinary section timings. Moving visibility cost 0.17-0.23 ms/frame. In the
moving phase windows, 3-6% of client Java samples included slot calculation;
pointer setup appeared in roughly 1-3%. The client is sampled thread ID 3
(`main` in JFR metadata), and windows are restricted by CSV start timestamps and
the exported wall-clock calibration. Do not require a `Minecraft.runGame` root:
some stacks start at GameRenderer. Native-call return attribution is a known
sampling limitation; samples alone are not driver timings. Details:
[attribution.json](attribution.json) and [source manifest](attribution-freeze.json).

## Reproduction and records

From the repository root, with an installed JDK 25 in JAVA_HOME and its bin on
PATH, configure assets online once (`gradlew.bat downloadAssets configureClientLaunch`).
The retained base world must already exist at `run/saves/alacer-benchmark`.
The runner uses native Windows OpenGL and `runClient --offline`, not Xvfb or
software rendering. In PowerShell quote individual `-Palacer.*=...` arguments;
the Python runner avoids that parsing problem.

Commands used locally (existing attempts are deliberately not overwritten):

```
python docs/benchmarks/2026-10-23-modern-rendering/run_series.py correctness r2
python -I docs/benchmarks/2026-10-23-modern-rendering/check_correctness.py
python docs/benchmarks/2026-10-23-modern-rendering/compare_images.py
python docs/benchmarks/2026-10-23-modern-rendering/run_series.py performance
python -I docs/benchmarks/2026-10-23-modern-rendering/evaluate.py .
python docs/benchmarks/2026-10-23-modern-rendering/run_series.py ablations
python docs/benchmarks/2026-10-23-modern-rendering/run_series.py smoke
```

To rescore the committed performance evidence without launching Minecraft,
restore and hash-check its inputs in a new directory, then use the same frozen
evaluator (Python standard library only):

```
python -I docs/benchmarks/2026-10-23-modern-rendering/restore_raw.py build/modern-rendering-replay
python -I docs/benchmarks/2026-10-23-modern-rendering/evaluate.py build/modern-rendering-replay
```

The runner refuses to overwrite an attempt and verifies source/base-world hashes
before and after each launch. Pass a new explicit suffix for a documented new
attempt. Freeze manifests and launch receipts preserve commands and raw paths;
the scored source snapshot is `measured-source.zip`. Raw summaries/per-phase
JSON and losslessly gzipped CSV are under `raw/`; logs and original framebuffer
captures are under `logs/` and `screenshots/`. `raw-manifest.json` hashes original
CSV/JSON/log/image and local JFR files. Large JFR binaries remain in ignored
`run/`, rather than being claimed as committed evidence. Existing Java 8 and
other Java 25 archives remain unchanged. See [attempts and limitations of
record retention](attempts.md).

## Validation and outcome

Java 25 `test build`: **166 tests, zero failures**, including 1.2 million exact
neighbor-address comparisons across signed coordinates, wrapping, top/bottom
layers and non-square grids, plus malformed/overflow inputs and config/startup
isolation. Build logs are under `validation/`. These tests do not exercise GL.

Eight live diagnostic launches passed (`correctness.json`): combined
default/terrain/edit verification, two image controls and a candidate, forced GL
fallback, and display lists. After tightening class-load order in the mixin
guard, the final terrain diagnostic compared **105,300,880 addresses**,
**32,479,970 VAO states**, and **671,115,104 uploaded bytes** (19,025 uploads),
all with zero failures (`guard-correctness.json`). Whole-visibility comparisons
reported zero stable-input mismatches; compiled-data changes are separately
inconclusive, including differences, and are not counted as passing comparisons.

The standalone reload/distance/world-detach harness retired all 3,848 created
VAOs at final detach, after an earlier detach and successful reattachment.
Its source, log and PASS receipts are in `validation/`; it is absent from src
and the shipping build. Lifecycle/image checks precede the class-load-order
guard tightening; final live terrain verification covers the tightened build.
The standalone lifecycle receipt did not record window size; it supplies no
resolution-specific timing evidence.

All four fixed-end-pose image checks passed the predeclared noise threshold
(`image-comparison.json`). Candidate/control differing-pixel fractions were
0.326% steady, 0.879% pan, 2.370% sort, and 1.713% flight, below twice the measured
control/control noise (limits 2.181%, 1.086%, 5.075%, 2.340%). Original full-frame
captures include clouds, mobs and simulated vegetation differences; this is a
noise bound, not bitwise scene identity. Visual inspection found matching terrain
and overlapping glass layout/blending, without apparent candidate-specific holes.

The 16-launch ordinary series used four adjacent pairs per scene in ABBABAAB
order. `performance-freeze.json` covers 240 source/world/runner/gate entries;
`selection-freeze.json` covers 279 compiled-class/config/tool entries. Both
remained byte-identical through the series and after the temporary query test
was removed. Every launch used the requested native backend and settings, with
zero dropped frames; verification, detailed probes and JFR were off. The machine
and methods above limit generalization. Full per-pair changes and absolute
phase medians are in `evaluation.json`; failed gates are retained, not waived.


Median paired changes (candidate relative to control; positive is more cost):

| Scene/phase | Frame mean | CPU/frame | p99 | Slowest 1% mean | Allocation/frame | Visibility | Visible chunks |
|---|---:|---:|---:|---:|---:|---:|---:|
| default/static | +34.70% | +145.76% | +6.94% | +11.89% | +3.05% | -16.67% | +0.00% |
| default/pan | +65.35% | +106.83% | +29.44% | +20.92% | +18.85% | -2.62% | +0.05% |
| default/fly | +61.63% | +114.34% | +48.24% | +48.86% | +28.77% | +1.43% | -0.32% |
| terrain/steady | +36.75% | +139.94% | -1.56% | -4.46% | -4.27% | -16.67% | +0.00% |
| terrain/pan | +58.56% | +110.29% | +20.31% | +16.44% | +20.24% | -6.68% | +0.04% |
| terrain/sort | +64.60% | +116.02% | +24.44% | +29.12% | +31.64% | -1.35% | -0.07% |
| terrain/fly | +59.18% | +113.77% | +37.90% | +35.48% | +31.93% | -1.99% | -0.40% |

Still visibility is tiny and rounded at export precision; its percentage is not
a useful speedup claim. The target was moving visibility and frame/CPU costs.

Pan absolute medians (milliseconds; control -> candidate):

| Scene | Frame mean | CPU/frame | Visibility |
|---|---:|---:|---:|
| default | 0.6713 -> 1.1032 | 0.5142 -> 1.0652 | 0.1681 -> 0.1628 |
| terrain | 0.6899 -> 1.0976 | 0.4959 -> 1.0518 | 0.1588 -> 0.1493 |

Visibility was lower in 3/4 ordinary pan pairs and 3/4 terrain pan pairs. The improvement target failed outside terrain pan; frame/CPU, allocation and tail gates failed despite unchanged visible-chunk means. No option is selected.

Additional attribution (`ablations.json`) uses one ordinary control and one
launch per option, not repeated selection pairs. In pan, neighbor math changed
visibility -13.3%, CPU/frame -5.0% and frame mean +1.1%; VAOs changed frame mean
+66.9% and the solid-terrain section +183.7%; named uploads changed frame mean
+19.5% alongside visibility +9.4% (launch/workload noise prevents attribution of
that whole-frame difference to uploads). Do not promote individual features
from these single launches or claim their costs sum to the combined result.

The v1 JFR (`candidate-v1-attribution.json`) did not establish a driver cause.
The separate [query diagnostic](query-diagnostic.md) changed only a temporary
guard bypass: static frame mean -31.8%, pan -12.0%, flight +4.4%; CPU changed
-62.0%, -16.0%, +5.5%. It failed the predeclared 25% static-and-pan criterion.
No private LWJGL access was introduced. The bypass source is archived separately
and removed from shipping code. No frozen performance gate was changed.

Final shipping smoke: the ordinary default route with the original display-list
backend and all new options off completed with zero dropped frames, zero new GL
operations and no reported GL error. This is an automated benchmark-path smoke,
not manual gameplay or third-party-mod certification. `shipping-verification.json`
records the actual backend/JVM, unchanged source/class/config/tool manifests,
225 Alacer classfiles at major version 69, the jar hash and absence of the harness.
Archive replay restored and hash-checked all 160 scored raw files and reproduced
`evaluation.json` byte-for-byte (`replay-verification.json`).
That byte comparison was local on Windows; other platforms can format the
generated JSON with different line endings. Recorded artifacts are marked
verbatim in `.gitattributes` so their original hashes remain meaningful.

## Remaining scope

This one NVIDIA machine cannot certify other vendors/driver versions, arbitrary
mod integration or context-loss recovery. Forced fallback is a diagnostic of the
original path on supported hardware, not a physical older-GPU test. Static
guards for external mixins, subclasses and changed contexts are not live
integration certification. Further GL features or approximate math need a new
profiled mechanism and frozen gates. The retained chunk-scheduling protocol's
reference-hardware series (including feedChunkWorkers as a third arm) is still
separate work, as are the already closed frustum/camera-section experiments.
The A/B controls use the same new build with experiments disabled; any cost of
the added disabled adjacency branch versus d8abd3f was not separately isolated.
