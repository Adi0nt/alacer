# Bounded VAO regression follow-up (2026-10-23)

**The large regression reproduced, including against hooks-present forced
fallback.** Eight serial native Windows clients completed on the unchanged
95cceb6 build. The active VAO path is implicated on this machine; the individual
cost of the query, active Java checks, LWJGL bookkeeping, binding, and downstream
draw/driver work remains unresolved. No fix or feature is selected.

Java 25.0.4.1, Windows 11, i7-12700F, RTX 3060 Ti, NVIDIA 616.92 / OpenGL 4.6,
1920x1080, distance 12, vanilla VBOs, 2 GiB. The ordinary static/pan/fly route
and fresh copies of the retained base world were used. Neighbor math and named
uploads were off throughout. JFR, verification, screenshots and detailed probes
were off. [Protocol](protocol.md) was recorded before launching.

## Results

Stage 1 was A V V A: two adjacent balanced pairs of ordinary control (A, no new
GL hooks at startup) and VAO-only (V). Stage 2 was F V V F: the same startup
hooks in both arms, with forced fallback (F) versus active VAOs (V).

Median of the two paired percentage changes; positive means more cost:

| Baseline | Phase | Frame | Client CPU/frame | p99 | Slowest 1% mean | Allocation/frame | Visibility | Visible chunks |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| A | static | +42.02% | +150.33% | +10.92% | +7.44% | +17.91% | +16.67% | +0.05% |
| A | pan | +64.20% | +113.26% | +26.85% | +26.04% | +20.31% | +3.63% | +0.24% |
| A | fly | +55.79% | +110.10% | +40.28% | +37.19% | +26.85% | +2.98% | -0.38% |
| F | static | +37.27% | +151.41% | +5.96% | +5.45% | +8.43% | +58.33% | +0.00% |
| F | pan | +62.66% | +106.13% | +18.92% | +12.44% | +19.84% | -0.12% | +0.20% |
| F | fly | +52.39% | +106.59% | +34.05% | +35.65% | +23.74% | -1.90% | +0.16% |

Static visibility is tiny and rounded; its percentages are not useful effect
estimates. These are two-pair attribution results, not the four-pair/two-scene
frozen selection protocol or a characterization of small-effect detection power.

Pan pair details (baseline -> V, milliseconds):

| Baseline | Pair | Frame mean | Client CPU/frame | Four terrain layers combined |
|---|---|---|---|---|
| A | A then V | 0.6115 -> 1.0231 | 0.4446 -> 0.9399 | 0.1638 -> 0.4687 |
| A | V then A | 0.6167 -> 0.9936 | 0.4503 -> 0.9687 | 0.1624 -> 0.4552 |
| F | F then V | 0.6165 -> 1.0254 | 0.4560 -> 0.9581 | 0.1619 -> 0.4759 |
| F | V then F | 0.6340 -> 1.0081 | 0.4748 -> 0.9598 | 0.1669 -> 0.4638 |

Both stage-1 pan pairs exceeded the predeclared 20% reproduction threshold in
both frame and CPU metrics, permitting stage 2. Every pair's visible-chunk mean
in every phase differed by less than 1%. This is a coarse workload bound, not
proof of equal draw calls, vertices, upload work or scene animation.

The four terrain sections increased about 183% (A) and 186% (F) in pan. They
include terrain filtering and drawing, not isolated API or GPU latency. Process
CPU cores decreased about 37% and 38% in pan despite higher client CPU/frame;
swap wall time decreased about 4% and 5%. This work-distribution observation
does not establish a driver cause or rescue the frame/CPU/tail regressions.
All absolute values, individual layer/upload/swap timings and pair deltas are
in [stage 1](stage1-evaluation-r2.json) and [stage 2](stage2-evaluation-r2.json).

The F result weakens baseline hook installation as the dominant explanation.
It leaves active-path Java checks, the query, LWJGL/GL binding and downstream
effects grouped together. F short-circuits the active path and can affect JIT
behavior, so it is not a pure native-call isolator. No new profiling capture was
made. The earlier query-bypass result remains retired under its own stop rule;
no private LWJGL access is proposed from these data.

## Validation, retention and limits

The first control completed but the r1 validator rejected the nondeterministic
ordering of its mod display string. Exact sorted mod/version tokens and phase
configs matched. [Amendment r2](amendment-r2.md) preserved all r1 files, corrected
only validation of that representation, and revalidated/adopted the first
control before looking at its performance metrics. Seven further launches
completed: eight total, with no replacement launch or expanded budget.
[r1 failure](failure.json) and [r1 receipt](launches.json) retain that failure;
[r2 receipts](launches-r2.json) explicitly record adoption and all exact commands.

All eight launches passed backend/JVM/settings, configuration, GL-error-log,
zero-dropped-frame and expected-activity validation. VAO binds were positive
only for V, with no named uploads or intrusive verification. The inherited
240-entry source/world and 279-entry class/config/tool manifests still match.
All 1,004 r2 frozen entries match after the run, including the earlier archive,
original tools/failed-attempt records and retained first-control inputs.
No source, compiled class, saved option, original gate or archive was changed.
The original 166-test/live-correctness evidence is historical and was not rerun;
these timing launches add no screenshot/mod/context-loss certification.

Full summary and phase JSON, losslessly compressed CSV and complete logs are
under `raw/` and `logs/`. `raw-manifest-r2.json` hashes the 56 original raw files;
launch receipts hash all eight logs. Replay restored/hash-checked every raw
file and reproduced both evaluation objects exactly (JSON structural/numeric
equality; no cross-platform byte-identity claim). See `replay-verification.json`.

Replay requires only Python's standard library and a new output directory:

```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-followup/replay.py build/modern-rendering-followup-replay-NEW
```

Do not rerun either measurement runner: both attempts are immutable and refuse
an existing freeze. The bounded stage is complete; no client or runner remains.
All experimental defaults remain off. See [Antigravity handoff](HANDOFF.md).
