# Java 25 measurements

Two fresh launches per density; within each launch, A/B/B/A static/pan segments.
A uses vanilla paths; B uses the same six existing defaults. No default change selected.

| Mobs | Phase | Mean frame change | Client CPU/frame | Allocation/frame | Allocation/second | p99 |
|---:|---|---:|---:|---:|---:|---:|
| 0 | static | -47.8% | -48.1% | -28.9% | +36.2% | -26.8% |
| 0 | pan | -33.3% | -33.8% | -98.8% | -98.1% | -28.5% |
| 200 | static | -17.5% | -19.9% | -40.8% | -27.8% | -9.7% |
| 200 | pan | -28.3% | -28.9% | -88.4% | -83.8% | -26.1% |
| 1000 | static | -17.5% | -17.1% | -22.7% | -6.3% | -17.5% |
| 1000 | pan | -15.6% | -16.5% | -73.4% | -68.1% | -15.5% |

## Absolute baseline with current defaults

Median of two launches; B segments pooled within each launch.

| Mobs | Phase | Frame mean ms | Client CPU ms/frame | Allocation KB/frame | Allocation MB/s | p99 ms |
|---:|---|---:|---:|---:|---:|---:|
| 0 | static | 1.177 | 1.129 | 3.645 | 3.025 | 2.833 |
| 0 | pan | 2.606 | 2.511 | 5.263 | 1.994 | 4.752 |
| 200 | static | 2.790 | 2.617 | 55.944 | 19.635 | 5.091 |
| 200 | pan | 3.707 | 3.582 | 60.545 | 16.145 | 6.707 |
| 1000 | static | 4.960 | 4.838 | 160.241 | 31.877 | 9.236 |
| 1000 | pan | 5.956 | 5.779 | 168.632 | 27.700 | 10.544 |

## Terrain backend controls

Two launches per backend, balanced DL/VBO/VBO/DL. Negative values mean lower cost.
These are new runtime baselines, not a repetition of the historical four-pair selection gate.

| Phase | Mean frame | Client CPU/frame | Allocation/frame | Allocation/second | p99 | Maximum |
|---|---:|---:|---:|---:|---:|---:|---:|
| steady | -39.5% | -65.5% | +7.2% | +77.3% | -11.1% | -30.4% |
| pan | -58.6% | -67.5% | -16.6% | +100.9% | -35.4% | -33.1% |
| sort | -56.5% | -66.1% | -43.5% | +29.7% | -27.2% | -22.9% |
| fly | -52.7% | -66.7% | -47.4% | +11.2% | -23.1% | -3.1% |

| Backend | Phase | Frame mean ms | Client CPU ms/frame | Allocation KB/frame | Allocation MB/s | p99 ms | Max ms |
|---|---|---:|---:|---:|---:|---:|---:|
| dl | steady | 1.476 | 1.370 | 5.055 | 3.396 | 3.248 | 9.852 |
| dl | pan | 2.700 | 2.574 | 6.157 | 2.234 | 5.532 | 14.309 |
| dl | sort | 2.616 | 2.544 | 19.269 | 7.199 | 5.023 | 11.150 |
| dl | fly | 2.591 | 2.520 | 30.459 | 11.480 | 5.233 | 13.014 |
| vbo | steady | 0.892 | 0.473 | 5.401 | 5.953 | 2.856 | 6.779 |
| vbo | pan | 1.119 | 0.837 | 5.083 | 4.437 | 3.575 | 9.126 |
| vbo | sort | 1.136 | 0.861 | 10.858 | 9.341 | 3.654 | 8.481 |
| vbo | fly | 1.223 | 0.839 | 15.999 | 12.771 | 4.033 | 12.556 |

Full per-run means, CPU, allocations, tails, maxima and slow-frame rates are in `metrics.json`.
CSV identities and exact commands are in `runs/*.record.json`; raw CSVs remain in the local run directory.
JFR/detail and verification runs are excluded from these tables.
