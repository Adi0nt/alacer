# Session 19 scalar particle entity query: measured results

Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla nearby-entity query; B: scalar grown bounds.
Eight shipped optimizations on; Sessions 17/18 experiments off in both. Negative changes mean less time/allocation.
Tick figures include only frames with particle ticks. Detail timers/verification/JFR off.

| Launch | Phase | Particle tick A/B ms | Client tick mean change | Tick-frame mean change | Tick-frame p95 change | Tick-frame p99 change | Frame mean change | Frame p95 change | CPU change | Allocation change | Population change |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pe19v3-ab-1-r1 | static | 1.443/1.495 | +3.74% | +2.97% | +6.21% | +23.53% | -0.23% | +3.58% | +0.74% | -13.51% | -0.40% |
| pe19v3-ab-1-r1 | pan | 1.460/1.413 | -0.83% | +0.59% | +3.52% | +16.80% | +2.06% | +4.36% | +2.28% | -5.65% | +0.01% |
| pe19v3-ab-1-r2 | static | 1.542/1.493 | -4.02% | -3.04% | -5.04% | -5.76% | -1.28% | -8.42% | -0.16% | -14.21% | -0.20% |
| pe19v3-ab-1-r2 | pan | 1.463/1.474 | +0.06% | +0.28% | -0.97% | -5.13% | +0.88% | -1.46% | +0.53% | -6.20% | +0.10% |
| pe19v3-ab-1-r3 | static | 1.410/1.365 | -3.84% | -3.24% | -3.72% | -5.23% | +0.04% | +1.42% | -0.93% | -5.82% | -0.23% |
| pe19v3-ab-1-r3 | pan | 1.382/1.367 | -0.59% | -0.06% | -4.16% | +5.69% | -1.58% | -3.70% | -2.00% | -7.05% | +0.44% |
| pe19v3-ab-1-r4 | static | 1.342/1.309 | -1.34% | +0.67% | +12.55% | -14.55% | +3.25% | +14.12% | +2.75% | -10.46% | -0.15% |
| pe19v3-ab-1-r4 | pan | 1.440/1.317 | -5.57% | -7.19% | -13.22% | -12.88% | -8.20% | -9.80% | -8.69% | -10.08% | +0.29% |
| pe19v3-ab-2-r1 | static | 2.274/2.328 | +4.02% | +5.52% | +11.29% | +16.32% | +8.71% | +20.46% | +7.32% | -5.65% | +0.15% |
| pe19v3-ab-2-r1 | pan | 2.143/2.114 | -1.59% | -0.67% | -2.62% | -9.58% | -1.45% | -0.52% | -0.83% | -8.19% | -0.24% |
| pe19v3-ab-2-r2 | static | 2.098/2.156 | +4.09% | +2.62% | -0.43% | +5.05% | +2.68% | -0.97% | +3.51% | -10.61% | -0.07% |
| pe19v3-ab-2-r2 | pan | 2.157/2.048 | -3.00% | -0.92% | -5.70% | -3.71% | -2.60% | -4.28% | -0.28% | -8.47% | +0.20% |
| pe19v3-ab-2-r3 | static | 2.142/2.142 | -0.70% | +1.01% | +7.62% | -2.05% | +2.82% | +13.32% | +4.63% | -9.94% | +0.04% |
| pe19v3-ab-2-r3 | pan | 2.123/2.073 | -2.55% | -2.29% | -5.67% | -14.61% | -0.52% | -1.80% | +2.07% | -7.48% | +0.09% |
| pe19v3-ab-2-r4 | static | 2.094/2.086 | -1.14% | -0.95% | -0.38% | +2.03% | +1.38% | -2.72% | +1.53% | -11.42% | +0.22% |
| pe19v3-ab-2-r4 | pan | 2.099/2.104 | -0.25% | -0.07% | -0.45% | +0.29% | -1.88% | -0.87% | -2.50% | -10.04% | +0.15% |

## Predeclared gates

- Scale 1, static: FAIL. every_launch_particle_tick_improves
- Scale 1, pan: FAIL. control_drift_within_10_percent; every_launch_particle_tick_improves
- Scale 2, static: FAIL. control_drift_within_10_percent; every_launch_particle_tick_improves; median_particle_tick_saving_at_least_2_percent; frame_mean_within_2_percent; tick_p99_within_5_percent; frame_p95_within_5_percent; client_cpu_within_2_percent
- Scale 2, pan: FAIL. every_launch_particle_tick_improves; median_particle_tick_saving_at_least_2_percent

## Drift and allocation checks

| Launch | Phase | Frame A1/A2/B ms | B vs A2 frame change | Tick-frame alloc A/B KB | Non-tick alloc A/B KB |
|---|---|---:|---:|---:|---:|
| pe19v3-ab-1-r1 | static | 1.264/1.276/1.267 | -0.71% | 2051.7/1765.0 | 2.6/2.6 |
| pe19v3-ab-1-r1 | pan | 1.835/1.705/1.804 | +5.80% | 2135.2/1855.1 | 80.3/80.2 |
| pe19v3-ab-1-r2 | static | 1.386/1.402/1.376 | -1.86% | 2058.9/1776.1 | 2.6/2.6 |
| pe19v3-ab-1-r2 | pan | 1.902/1.840/1.887 | +2.55% | 2133.7/1852.0 | 80.0/80.1 |
| pe19v3-ab-1-r3 | static | 1.377/1.378/1.378 | +0.03% | 2147.7/1864.6 | 79.7/79.6 |
| pe19v3-ab-1-r3 | pan | 1.829/1.914/1.841 | -3.84% | 2144.2/1869.1 | 79.7/80.0 |
| pe19v3-ab-1-r4 | static | 1.206/1.301/1.292 | -0.67% | 2066.0/1783.0 | 2.3/2.3 |
| pe19v3-ab-1-r4 | pan | 1.649/2.377/1.788 | -24.81% | 2138.8/1867.5 | 79.3/79.6 |
| pe19v3-ab-2-r1 | static | 2.182/1.824/2.160 | +18.44% | 3331.8/2903.1 | 2.3/2.3 |
| pe19v3-ab-2-r1 | pan | 2.406/2.359/2.348 | -0.47% | 3455.7/3012.0 | 122.1/121.8 |
| pe19v3-ab-2-r2 | static | 1.786/1.782/1.832 | +2.79% | 3333.1/2895.1 | 2.3/2.3 |
| pe19v3-ab-2-r2 | pan | 2.359/2.469/2.350 | -4.82% | 3448.9/3023.9 | 121.9/122.1 |
| pe19v3-ab-2-r3 | static | 1.858/1.810/1.885 | +4.16% | 3332.5/2899.6 | 2.3/2.3 |
| pe19v3-ab-2-r3 | pan | 2.399/2.334/2.354 | +0.83% | 3449.0/3019.8 | 121.8/122.0 |
| pe19v3-ab-2-r4 | static | 1.806/1.797/1.826 | +1.64% | 3329.5/2902.7 | 2.3/2.3 |
| pe19v3-ab-2-r4 | pan | 2.401/2.361/2.336 | -1.07% | 3396.3/2968.3 | 70.9/70.9 |

Source identity is checked against archived ZIP snapshots. Analysis does not require the working tree
to still match the measured build. Raw CSVs and logs remain local; their hashes are verified.
