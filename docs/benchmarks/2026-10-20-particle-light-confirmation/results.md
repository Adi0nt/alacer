# Session 23 results

All changes are percentages; negative means lower. No launch is excluded.

## In-process confirmation: median per-launch changes

| Phase | Pass | Frame | Ordinary frame | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick frame | Tick p99 | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| static | -15.37% | -6.35% | -6.34% | -6.33% | -6.34% | -5.22% | -0.64% | -1.84% | -9.65% | PASS |
| pan | -14.37% | -5.04% | -5.00% | -4.91% | -2.06% | -5.68% | -6.66% | -2.33% | -16.99% | PASS |

## Startup confirmation: changes between launch medians

| Phase | Pass | Frame | Ordinary frame | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick frame | Tick p99 | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| static | -16.82% | -7.66% | -7.74% | -7.43% | +65.56% | -4.63% | -5.12% | -3.44% | +1.85% | FAIL: median_allocation_within_2_percent |
| pan | -14.67% | -5.62% | -5.94% | -4.76% | -4.95% | -3.67% | +1.42% | +1.02% | +5.66% | FAIL: tick_frame_p99_ms_within_5_percent |

## Startup launches (actual values)

| Launch | Phase | Frame ms | Pass ms | Client CPU ms/frame | Allocation KB/frame | Tick-frame p99 ms |
|---|---|---:|---:|---:|---:|---:|
| lc23-startup-absent-1-r1 | static | 1.213 | 0.420 | 1.194 | 52.2 | 6.916 |
| lc23-startup-absent-1-r1 | pan | 1.841 | 0.471 | 1.751 | 156.6 | 7.750 |
| lc23-startup-absent-1-r2 | static | 1.250 | 0.430 | 1.195 | 131.3 | 4.871 |
| lc23-startup-absent-1-r2 | pan | 1.977 | 0.497 | 1.907 | 166.7 | 6.314 |
| lc23-startup-absent-1-r3 | static | 1.248 | 0.436 | 1.205 | 53.7 | 6.058 |
| lc23-startup-absent-1-r3 | pan | 1.903 | 0.491 | 1.839 | 159.3 | 6.851 |
| lc23-startup-absent-1-r4 | static | 1.536 | 0.517 | 1.490 | 95.3 | 7.798 |
| lc23-startup-absent-1-r4 | pan | 2.252 | 0.553 | 2.163 | 176.4 | 9.113 |
| lc23-startup-on-1-r1 | static | 1.197 | 0.372 | 1.152 | 121.6 | 6.142 |
| lc23-startup-on-1-r1 | pan | 1.850 | 0.431 | 1.790 | 150.8 | 7.622 |
| lc23-startup-on-1-r2 | static | 1.025 | 0.324 | 0.970 | 101.4 | 4.741 |
| lc23-startup-on-1-r2 | pan | 1.795 | 0.407 | 1.740 | 133.7 | 7.672 |
| lc23-startup-on-1-r3 | static | 1.110 | 0.348 | 1.070 | 125.1 | 7.072 |
| lc23-startup-on-1-r3 | pan | 1.812 | 0.412 | 1.778 | 159.0 | 7.756 |
| lc23-startup-on-1-r4 | static | 1.607 | 0.485 | 1.541 | 140.5 | 9.768 |
| lc23-startup-on-1-r4 | pan | 2.745 | 0.592 | 2.653 | 190.7 | 9.751 |

The frozen analyzer's startup Markdown header omits the ordinary-frame columns it prints. This report reads its unchanged metrics.json and supplies correctly labelled columns. Gate evaluation and raw evidence are unchanged.
