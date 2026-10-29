# 1.0.0 frame pacing check (2026-10-28)

`render.fastDisplaySync`, `render.fastSwapBuffers` and `render.suppressThreadYield` were added on
2026-10-25 as default-on options without an archived measurement. Before the 1.0.0 release each
was toggled in process against the 1.0.0 candidate with all three off, following the
[protocol](PROTOCOL.md) written before the first launch. **All three fail their gates and ship
off.**

Setup: Alacer `1.0.0+mc1.8.9` working tree (uncommitted release candidate), Temurin Java
25.0.4.1, Windows 11, NVIDIA GeForce RTX 3060 Ti (driver 616.92, GL 4.6), 1920x1080, 2 GiB heap,
seeded `alacer-benchmark` world (seed 1789), no mobs or chests, build defaults plus JVM overrides,
verification and detail profiling off. Six launches, all completed, none retried. The
environmental monitor and `aa_pilot.py` quiet checks were not used.

`frame_ms` values come from each segment's per-frame CSV (the `runs/` directory keeps
`summary.json` only; raw CSVs stay under `run/alacer/benchmarks/*r100-pacing-l*`). CPU is the client
thread's CPU time per frame from `summary.json`. Change is B (option on) relative to A (off).
[`results.json`](results.json) holds every value; [`analyze.py`](analyze.py) recreates it.

| Option | Launch (order) | Phase | Median | p99 | Std dev | FPS | Client CPU/frame |
|---|---|---|---:|---:|---:|---:|---:|
| `fastDisplaySync`, 120 FPS cap | 1 (ABBA) | static | +2.9% | -11.9% | -91.9% | -0.0% | -7.6% |
| | | pan | +2.7% | -12.5% | -67.6% | -0.0% | **+3.5%** |
| | 2 (BAAB) | static | +3.4% | -13.3% | -96.4% | -0.1% | -15.4% |
| | | pan | +3.3% | -12.2% | -87.0% | -0.0% | **+12.8%** |
| `fastSwapBuffers`, unlimited | 3 (ABBA) | static | -14.7% | -28.4% | -41.0% | +21.1% | -16.1% |
| | | pan | **-4.5%** | **+15.2%** | +11.7% | +0.5% | -2.6% |
| | 4 (BAAB) | static | **+5.7%** | **+5.4%** | +36.6% | -6.0% | **+5.7%** |
| | | pan | **+7.5%** | **+7.6%** | +27.3% | -6.3% | **+7.2%** |
| `suppressThreadYield`, unlimited | 5 (ABBA) | static | -7.4% | -15.4% | -30.7% | +9.3% | -4.4% |
| | | pan | **-0.7%** | -8.5% | -6.1% | +3.6% | -2.9% |
| | 6 (BAAB) | static | **+1.7%** | -2.6% | -4.8% | -0.9% | +0.7% |
| | | pan | **+4.2%** | **+3.5%** | +6.3% | -4.4% | **+3.2%** |

Bold marks a value that fails its gate.

- **`fastDisplaySync`** held 120.0 FPS in every segment and made frame times far more even: p99
  12-13% lower, standard deviation 68-96% lower. Its median is about 3% higher because it waits
  until the exact 8.33 ms deadline. Client CPU per frame rose 3.5% and 12.8% in pan, past the 3% limit.
  The spin-wait tail is the likely cost, but this check did not attribute it. It fails.
- **`fastSwapBuffers`** and **`suppressThreadYield`** look better in the ABBA launches and worse
  in the BAAB launches. The sign follows segment position, not the option, so this short
  in-process design cannot separate them from a drift during the run. Both fail.

The large uncapped static changes (around 1,000 FPS) are in frames of about 1 ms, where small
scheduling shifts are large percentages. No claim is made from them.

Outcome: all three stay off in 1.0.0, out of the Video Settings menu and out of the
`render.defaults` preset. `fastDisplaySync`'s pacing result may be worth an Extended campaign that
measures whole-process CPU and power at common frame caps and with vsync. The 2026-10-25 ROADMAP
figures for these options are superseded by this archive.

The check also added `alacer.benchmark.fpsLimit` (10 to 260, default unlimited), recorded in each
report's environment as `benchmark.fpsLimit`. Benchmarks force an unlimited frame rate, which
never calls `Display.sync`, so the frame limiter could not be measured before.
