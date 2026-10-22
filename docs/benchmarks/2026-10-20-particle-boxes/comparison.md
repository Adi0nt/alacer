# Session 18 particle Box reuse: measured results

Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla Box allocation; B: guarded immutable zero-offset Box reuse.
Eight shipped optimizations on; Session 17 cached movement reads off in both. Negative changes mean less time/allocation.
Tick figures include only frames with particle ticks. Detail timers/verification/JFR off.

| Launch | Phase | Particle tick A/B ms | Client tick mean change | Tick-frame mean change | Tick-frame p95 change | Tick-frame p99 change | Frame mean change | Frame p95 change | CPU change | Allocation change | Population change |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pb18-ab-1-r1 | static | 1.402/1.415 | +1.04% | -3.17% | -3.59% | +8.97% | -5.90% | -5.50% | -4.84% | -2.96% | -0.11% |
| pb18-ab-1-r1 | pan | 1.583/1.484 | -7.48% | -11.45% | -22.21% | -15.86% | -15.05% | -18.25% | -14.49% | -8.16% | +0.10% |
| pb18-ab-1-r2 | static | 1.540/1.416 | -8.88% | -12.47% | -28.66% | -23.39% | -13.53% | -31.95% | -12.27% | -14.11% | -0.36% |
| pb18-ab-1-r2 | pan | 1.306/1.358 | +2.66% | +2.97% | +3.53% | +13.08% | +3.23% | +7.63% | +3.34% | +1.36% | +0.13% |
| pb18-ab-1-r3 | static | 1.527/1.492 | -4.51% | -8.04% | -18.21% | -19.80% | -9.14% | -20.56% | -9.58% | -9.41% | +0.18% |
| pb18-ab-1-r3 | pan | 1.546/1.570 | +1.41% | +0.50% | +4.08% | +0.22% | +1.81% | +6.78% | +1.36% | +0.83% | +0.05% |
| pb18-ab-1-r4 | static | 1.500/1.615 | +5.35% | +4.69% | +21.80% | +17.59% | +1.05% | +8.11% | +1.89% | +0.21% | +0.03% |
| pb18-ab-1-r4 | pan | 1.648/1.535 | -7.36% | -12.43% | -23.10% | -32.68% | -14.35% | -19.65% | -13.78% | -9.68% | +0.22% |
| pb18-ab-2-r1 | static | 2.289/2.236 | -1.61% | -2.41% | -4.38% | +4.28% | -3.55% | -7.78% | -4.00% | -4.53% | -0.23% |
| pb18-ab-2-r1 | pan | 2.390/2.314 | -3.58% | -4.86% | -14.40% | -10.80% | -6.00% | -6.44% | -8.71% | -3.93% | -0.07% |
| pb18-ab-2-r2 | static | 2.216/2.206 | -0.08% | +0.83% | +5.94% | +18.07% | +1.42% | +13.48% | +2.55% | +0.59% | +0.07% |
| pb18-ab-2-r2 | pan | 2.229/2.209 | -2.08% | -3.47% | -8.81% | -10.99% | -4.53% | -28.67% | -4.94% | -3.17% | -0.06% |
| pb18-ab-2-r3 | static | 2.179/2.218 | +0.68% | +0.63% | -5.82% | +10.17% | +1.47% | -11.12% | -0.47% | +0.68% | +0.07% |
| pb18-ab-2-r3 | pan | 2.044/2.067 | -0.45% | -1.01% | -2.20% | +1.45% | -1.58% | +1.66% | +0.98% | -1.33% | +0.09% |
| pb18-ab-2-r4 | static | 2.624/2.722 | +2.83% | +2.77% | +3.06% | +9.00% | +3.00% | +22.52% | +0.99% | +1.92% | -0.08% |
| pb18-ab-2-r4 | pan | 2.495/2.400 | -3.11% | -3.40% | -9.05% | -6.20% | -6.30% | -7.86% | -5.48% | -5.04% | -0.01% |

## Predeclared gates

- Scale 1, static: FAIL. every_launch_particle_tick_improves; median_particle_tick_saving_at_least_2_percent; tick_frame_allocation_saving_at_least_2_percent
- Scale 1, pan: FAIL. every_launch_particle_tick_improves; tick_frame_allocation_saving_at_least_2_percent
- Scale 2, static: FAIL. every_launch_particle_tick_improves; median_particle_tick_saving_at_least_2_percent; tick_frame_allocation_saving_at_least_2_percent
- Scale 2, pan: FAIL. every_launch_particle_tick_improves; tick_frame_allocation_saving_at_least_2_percent

## Drift and allocation checks

| Launch | Phase | Frame A1/A2/B ms | B vs A2 frame change | Tick-frame alloc A/B KB | Non-tick alloc A/B KB |
|---|---|---:|---:|---:|---:|
| pb18-ab-1-r1 | static | 1.682/1.371/1.421 | +3.69% | 1872.6/1849.7 | 79.9/79.8 |
| pb18-ab-1-r1 | pan | 2.572/2.273/2.050 | -9.80% | 1862.6/1846.5 | 79.7/79.8 |
| pb18-ab-1-r2 | static | 1.342/2.012/1.392 | -30.82% | 2075.4/2050.1 | 2.3/2.3 |
| pb18-ab-1-r2 | pan | 1.693/1.684/1.743 | +3.50% | 2111.8/2095.4 | 50.2/50.1 |
| pb18-ab-1-r3 | static | 1.541/1.635/1.442 | -11.83% | 2174.9/2160.1 | 2.6/2.6 |
| pb18-ab-1-r3 | pan | 2.009/2.109/2.095 | -0.66% | 2229.9/2212.5 | 50.4/50.7 |
| pb18-ab-1-r4 | static | 1.573/1.521/1.563 | +2.76% | 2068.0/2050.4 | 2.3/2.3 |
| pb18-ab-1-r4 | pan | 2.131/2.816/2.078 | -26.21% | 2108.9/2100.1 | 50.1/50.3 |
| pb18-ab-2-r1 | static | 1.774/1.821/1.733 | -4.78% | 3508.2/3468.8 | 2.6/2.6 |
| pb18-ab-2-r1 | pan | 2.669/2.480/2.417 | -2.54% | 3625.2/3593.1 | 122.5/123.0 |
| pb18-ab-2-r2 | static | 1.786/1.775/1.805 | +1.74% | 3331.4/3305.1 | 2.3/2.3 |
| pb18-ab-2-r2 | pan | 2.438/2.375/2.297 | -3.28% | 3451.1/3416.4 | 122.1/122.0 |
| pb18-ab-2-r3 | static | 2.233/1.781/2.011 | +12.89% | 3328.1/3302.0 | 2.4/2.4 |
| pb18-ab-2-r3 | pan | 2.365/2.329/2.310 | -0.84% | 3449.6/3421.2 | 121.9/122.0 |
| pb18-ab-2-r4 | static | 2.207/2.067/2.199 | +6.38% | 3308.5/3274.6 | 2.6/2.6 |
| pb18-ab-2-r4 | pan | 2.684/2.465/2.408 | -2.31% | 3370.9/3341.1 | 71.1/71.1 |

Source identity is checked against archived ZIP snapshots. Analysis does not require the working tree
to still match the measured build. Raw CSVs and logs remain local; their hashes are verified.
