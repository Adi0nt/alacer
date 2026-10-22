# Fancy cloud display list: in-process A/B, 200 mobs

Generated from the local per-frame CSVs by `analyze.py`; summaries are in `runs/`.
A = off, B = on. B pools b1/b2. A1/A2 are shown separately to expose drift;
the last column compares B with both A segments pooled. Times are ms/frame.

| Run | Phase | A1 frame | A2 frame | B frame | B vs A2 | B vs pooled A |
|---|---|---:|---:|---:|---:|---:|
| cc-r1 | static | 3.094 | 3.547 | 2.777 | -21.7% | -16.0% |
| cc-r1 | pan | 4.563 | 3.855 | 3.291 | -14.6% | -21.3% |
| cc-r2 | static | 3.741 | 4.927 | 2.525 | -48.8% | -40.6% |
| cc-r2 | pan | 5.785 | 3.594 | 3.518 | -2.1% | -20.6% |
| cc-r3 | static | 2.939 | 3.488 | 2.681 | -23.1% | -16.0% |
| cc-r3 | pan | 4.799 | 4.029 | 3.447 | -14.4% | -21.3% |

## Attribution (A2 -> B)

| Run | Phase | Clouds | Render | Swap | Client CPU | Process cores | Allocation KB/frame | B rebuilds |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| cc-r1 | static | 0.435 -> 0.011 | 2.660 -> 2.047 | 0.676 -> 0.549 | 3.313 -> 2.637 | 1.100 -> 1.117 | 61.0 -> 59.0 | 0 |
| cc-r1 | pan | 0.369 -> 0.010 | 3.215 -> 2.698 | 0.459 -> 0.430 | 3.806 -> 3.218 | 1.112 -> 1.087 | 65.2 -> 63.6 | 2 |
| cc-r2 | static | 0.575 -> 0.010 | 3.702 -> 1.857 | 0.872 -> 0.514 | 4.495 -> 2.434 | 1.065 -> 1.102 | 64.8 -> 58.3 | 0 |
| cc-r2 | pan | 0.356 -> 0.011 | 2.996 -> 2.860 | 0.433 -> 0.480 | 3.541 -> 3.445 | 1.094 -> 1.127 | 64.7 -> 64.7 | 2 |
| cc-r3 | static | 0.437 -> 0.011 | 2.620 -> 1.991 | 0.662 -> 0.517 | 3.269 -> 2.571 | 1.090 -> 1.115 | 60.5 -> 58.3 | 0 |
| cc-r3 | pan | 0.382 -> 0.011 | 3.340 -> 2.814 | 0.499 -> 0.466 | 3.986 -> 3.375 | 1.108 -> 1.104 | 64.9 -> 63.4 | 2 |

## Tails (A2 -> B, percentiles calculated from pooled raw frames)

| Run | Phase | p99 ms | 1% low FPS |
|---|---|---:|---:|
| cc-r1 | static | 6.048 -> 5.029 | 123.7 -> 164.5 |
| cc-r1 | pan | 6.854 -> 5.457 | 124.2 -> 151.4 |
| cc-r2 | static | 11.150 -> 4.602 | 82.4 -> 182.9 |
| cc-r2 | pan | 5.942 -> 6.050 | 132.1 -> 130.7 |
| cc-r3 | static | 6.500 -> 5.128 | 140.1 -> 158.2 |
| cc-r3 | pan | 6.370 -> 5.513 | 128.9 -> 151.3 |
