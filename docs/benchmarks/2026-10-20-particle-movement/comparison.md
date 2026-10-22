# Particle movement: measured results

Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla movement reads; B: cached chunk reads.
Eight shipped optimizations on in both. Negative changes mean less time/allocation.
Tick figures include only frames with particle ticks. Detail timers/verification/JFR off.

| Launch | Phase | Particle tick A/B ms | Client tick mean change | Tick-frame mean change | Tick-frame p95 change | Tick-frame p99 change | Frame mean change | Frame p95 change | CPU change | Allocation change | Population change |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pm-ab-1-r1 | static | 1.496/1.326 | -6.67% | +1.48% | +6.05% | +16.26% | +11.72% | +12.93% | +12.79% | +8.22% | +0.36% |
| pm-ab-1-r1 | pan | 1.622/1.280 | -16.65% | -17.39% | -26.79% | -3.84% | -14.68% | -24.04% | -15.58% | -8.46% | -0.35% |
| pm-ab-1-r2 | static | 1.695/1.427 | -12.20% | -9.13% | -9.21% | +3.90% | -4.09% | -8.64% | -4.55% | -3.85% | +0.23% |
| pm-ab-1-r2 | pan | 1.701/1.487 | -8.99% | -3.97% | +3.63% | -1.83% | -1.64% | -5.80% | -2.05% | -1.32% | -0.40% |
| pm-ab-1-r3 | static | 1.789/1.532 | -9.17% | -3.76% | -0.45% | +22.81% | +3.44% | +8.18% | +3.44% | +2.95% | -0.41% |
| pm-ab-1-r3 | pan | 1.896/1.579 | -13.13% | -7.72% | -13.57% | -21.77% | -0.26% | -8.65% | -0.26% | -0.30% | -0.22% |
| pm-ab-1-r4 | static | 1.508/1.344 | -9.08% | -3.45% | +6.83% | +13.10% | +2.98% | +16.37% | +2.32% | +3.50% | +0.66% |
| pm-ab-1-r4 | pan | 1.555/1.337 | -10.57% | -5.53% | -10.80% | +9.76% | -1.12% | -1.57% | -1.01% | -1.15% | -0.34% |
| pm-ab-2-r1 | static | 2.517/2.175 | -9.24% | -4.46% | -6.29% | -17.36% | +3.98% | -2.01% | +4.13% | +2.17% | -0.05% |
| pm-ab-2-r1 | pan | 2.640/2.187 | -13.18% | -6.96% | -7.07% | -6.02% | -1.63% | -7.40% | -1.23% | -0.91% | +0.11% |
| pm-ab-2-r2 | static | 2.559/2.238 | -10.21% | -7.56% | -9.36% | -7.67% | -6.00% | -7.65% | -7.61% | -6.08% | -0.21% |
| pm-ab-2-r2 | pan | 2.710/2.287 | -11.42% | -6.11% | -12.36% | -21.89% | +1.37% | -3.90% | +2.57% | +0.96% | -0.08% |
| pm-ab-2-r3 | static | 2.644/2.226 | -12.92% | -8.12% | -15.83% | -16.69% | +0.26% | -5.20% | +0.29% | -0.13% | +0.06% |
| pm-ab-2-r3 | pan | 2.755/2.267 | -14.04% | -9.34% | -16.71% | -16.93% | -5.22% | -11.78% | -3.78% | -3.56% | -0.12% |
| pm-ab-2-r4 | static | 2.598/2.077 | -15.74% | -11.44% | -22.79% | -13.23% | -5.26% | -18.34% | -5.72% | -5.39% | -0.16% |
| pm-ab-2-r4 | pan | 2.659/2.000 | -20.77% | -18.44% | -31.62% | -41.92% | -16.17% | -21.35% | -14.98% | -10.32% | -0.04% |

## Predeclared gates

- Scale 1, static: FAIL. frame_p95_within_5_percent; client_cpu_within_2_percent; allocation_within_2_percent
- Scale 1, pan: PASS. 
- Scale 2, static: PASS. 
- Scale 2, pan: PASS. 

## Drift and allocation checks

| Launch | Phase | Frame A1/A2/B ms | B vs A2 frame change | Tick-frame alloc A/B KB | Non-tick alloc A/B KB |
|---|---|---:|---:|---:|---:|
| pm-ab-1-r1 | static | 1.309/1.621/1.618 | -0.18% | 2144.2/2152.9 | 31.5/31.6 |
| pm-ab-1-r1 | pan | 2.700/2.027/1.976 | -2.54% | 2200.0/2189.3 | 79.7/79.5 |
| pm-ab-1-r2 | static | 1.974/1.806/1.809 | +0.17% | 1840.5/1842.7 | 2.3/2.3 |
| pm-ab-1-r2 | pan | 2.804/2.659/2.685 | +0.98% | 1925.0/1916.3 | 80.2/79.8 |
| pm-ab-1-r3 | static | 2.108/1.953/2.097 | +7.40% | 2122.7/2114.2 | 2.3/2.3 |
| pm-ab-1-r3 | pan | 2.786/3.347/3.033 | -9.37% | 2200.4/2198.1 | 80.0/79.9 |
| pm-ab-1-r4 | static | 1.545/1.462/1.547 | +5.83% | 2106.4/2119.4 | 2.3/2.3 |
| pm-ab-1-r4 | pan | 2.064/2.206/2.108 | -4.42% | 2163.1/2156.6 | 50.1/49.7 |
| pm-ab-2-r1 | static | 1.946/2.673/2.342 | -12.39% | 3533.1/3532.6 | 122.2/122.1 |
| pm-ab-2-r1 | pan | 3.098/3.309/3.148 | -4.87% | 3527.4/3532.8 | 122.0/122.2 |
| pm-ab-2-r2 | static | 2.687/2.888/2.617 | -9.39% | 3407.7/3402.0 | 2.3/2.3 |
| pm-ab-2-r2 | pan | 3.806/3.389/3.635 | +7.24% | 3482.5/3479.0 | 70.9/70.9 |
| pm-ab-2-r3 | static | 3.069/2.726/2.895 | +6.19% | 2975.4/2977.8 | 2.3/2.3 |
| pm-ab-2-r3 | pan | 3.326/4.484/3.620 | -19.26% | 3099.8/3094.0 | 122.1/121.9 |
| pm-ab-2-r4 | static | 2.636/2.094/2.211 | +5.60% | 3411.5/3404.1 | 2.2/2.2 |
| pm-ab-2-r4 | pan | 3.377/2.904/2.618 | -9.86% | 3532.8/3531.1 | 122.1/122.1 |

Source identity is checked against archived ZIP snapshots. Analysis does not require the working tree
to still match the measured build. Raw CSVs and logs remain local; their hashes are verified.
