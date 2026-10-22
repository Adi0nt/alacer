# Fancy cloud display list: in-process A/B, 200 mobs

Generated from the local per-frame CSVs by `analyze.py`; summaries are in `runs/`.
A = off, B = on. B pools b1/b2. A1/A2 are shown separately to expose drift;
the last column compares B with both A segments pooled. Times are ms/frame.

| Run | Phase | A1 frame | A2 frame | B frame | B vs A2 | B vs pooled A |
|---|---|---:|---:|---:|---:|---:|
| cc-on-r1 | static | 3.447 | 3.037 | 2.519 | -17.0% | -22.0% |
| cc-on-r1 | pan | 4.132 | 4.656 | 4.262 | -8.5% | -2.7% |
| cc-on-r2 | static | 3.732 | 3.346 | 2.805 | -16.1% | -20.5% |
| cc-on-r2 | pan | 3.954 | 4.747 | 3.542 | -25.4% | -17.9% |
| cc-on-r3 | static | 3.417 | 3.166 | 2.528 | -20.2% | -23.1% |
| cc-on-r3 | pan | 5.464 | 4.149 | 3.887 | -6.3% | -17.6% |

## Attribution (A2 -> B)

| Run | Phase | Clouds | Render | Swap | Client CPU | Process cores | Allocation KB/frame | B rebuilds |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| cc-on-r1 | static | 0.378 -> 0.010 | 2.346 -> 1.883 | 0.517 -> 0.477 | 2.941 -> 2.464 | 1.075 -> 1.103 | 59.7 -> 58.3 | 0 |
| cc-on-r1 | pan | 0.409 -> 0.013 | 3.841 -> 3.420 | 0.583 -> 0.612 | 4.510 -> 4.088 | 1.077 -> 1.104 | 67.4 -> 66.4 | 2 |
| cc-on-r2 | static | 0.430 -> 0.012 | 2.585 -> 2.093 | 0.560 -> 0.528 | 3.209 -> 2.686 | 1.062 -> 1.111 | 62.5 -> 60.7 | 1 |
| cc-on-r2 | pan | 0.422 -> 0.011 | 3.894 -> 2.880 | 0.611 -> 0.478 | 4.519 -> 3.434 | 1.113 -> 1.106 | 67.8 -> 64.6 | 2 |
| cc-on-r3 | static | 0.422 -> 0.012 | 2.478 -> 1.905 | 0.498 -> 0.457 | 3.095 -> 2.464 | 1.071 -> 1.128 | 62.0 -> 60.2 | 1 |
| cc-on-r3 | pan | 0.391 -> 0.013 | 3.444 -> 3.142 | 0.501 -> 0.542 | 4.079 -> 3.747 | 1.112 -> 1.126 | 66.8 -> 66.1 | 2 |

## Tails (A2 -> B, percentiles calculated from pooled raw frames)

| Run | Phase | p99 ms | 1% low FPS |
|---|---|---:|---:|
| cc-on-r1 | static | 4.940 -> 4.882 | 182.7 -> 170.8 |
| cc-on-r1 | pan | 7.073 -> 7.483 | 125.9 -> 117.9 |
| cc-on-r2 | static | 5.638 -> 4.934 | 141.3 -> 169.1 |
| cc-on-r2 | pan | 8.570 -> 6.193 | 104.4 -> 130.7 |
| cc-on-r3 | static | 5.416 -> 4.484 | 166.3 -> 196.0 |
| cc-on-r3 | pan | 6.106 -> 6.325 | 150.0 -> 129.9 |
