# Session 21 particle light entry: measured results

Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla chunk check and position copy (hooks installed,
option off); B: range-test answer and reused position. Nine shipped optimizations on, every experiment off, all options explicit.
B is compared with pooled A1+A2. Negative changes mean less time/allocation. Detail/JFR/verification off.

| Launch | Phase | Pass A/B ms | Pass change | Pass excl. draw A/B ms | Draw A/B ms | Frame mean change | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | CPU/frame | Alloc/frame | Rendered | A2/A1 drift | B vs A2 frame |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| le21-ab-1-r1 | static | 0.624/0.612 | -1.91% | 0.437/0.409 | 0.187/0.203 | -2.51% | -2.19% | -1.75% | -0.16% | +12.04% | -0.85% | -66.53% | -0.39% | +16.63% | -9.99% |
| le21-ab-1-r1 | pan | 0.646/0.609 | -5.71% | 0.414/0.393 | 0.231/0.216 | -5.43% | -9.99% | -2.94% | -1.17% | +10.32% | -3.44% | -62.86% | +0.23% | -2.68% | -4.15% |
| le21-ab-1-r2 | static | 0.614/0.605 | -1.49% | 0.417/0.408 | 0.197/0.197 | -1.34% | +0.23% | +1.27% | +3.34% | +30.10% | +0.32% | -68.16% | -0.28% | -16.92% | +7.78% |
| le21-ab-1-r2 | pan | 0.616/0.616 | -0.03% | 0.389/0.389 | 0.227/0.228 | -0.15% | +2.31% | -0.71% | -0.55% | -7.51% | -1.01% | -63.58% | +0.58% | -0.34% | +0.02% |
| le21-ab-1-r3 | static | 0.569/0.576 | +1.33% | 0.386/0.382 | 0.183/0.194 | +1.70% | +2.94% | +2.85% | +3.10% | +10.26% | +2.74% | -67.61% | -0.58% | -1.48% | +2.46% |
| le21-ab-1-r3 | pan | 0.685/0.701 | +2.34% | 0.444/0.461 | 0.242/0.241 | +3.51% | -6.51% | +5.23% | +3.50% | -24.06% | +3.97% | -57.48% | -0.16% | -21.04% | +15.67% |
| le21-ab-1-r4 | static | 0.580/0.524 | -9.61% | 0.413/0.381 | 0.167/0.143 | -10.00% | -19.33% | -4.26% | -6.23% | -7.76% | -8.08% | -70.50% | -0.05% | -13.33% | -3.57% |
| le21-ab-1-r4 | pan | 0.666/0.727 | +9.14% | 0.434/0.463 | 0.232/0.264 | +10.65% | +19.79% | +16.15% | +12.03% | +88.17% | +10.16% | -56.28% | +0.04% | -3.23% | +12.47% |
| le21-ab-2-r1 | static | 0.738/0.749 | +1.53% | 0.601/0.600 | 0.137/0.149 | +2.36% | +5.89% | +2.46% | +2.59% | -2.55% | +3.01% | -61.62% | -0.31% | +6.02% | -0.62% |
| le21-ab-2-r1 | pan | 0.802/0.781 | -2.59% | 0.614/0.593 | 0.188/0.189 | -2.92% | -2.67% | -4.18% | -3.18% | +4.74% | -3.45% | -57.48% | +0.07% | +7.47% | -6.42% |
| le21-ab-2-r2 | static | 0.728/0.733 | +0.63% | 0.602/0.601 | 0.126/0.131 | +0.98% | -1.87% | +4.08% | +2.20% | +1.46% | +0.31% | -64.20% | -0.05% | -3.18% | +2.61% |
| le21-ab-2-r2 | pan | 0.831/0.786 | -5.36% | 0.639/0.605 | 0.192/0.181 | -6.30% | -17.05% | -5.02% | -4.26% | +0.40% | -4.96% | -59.83% | +0.12% | +3.43% | -7.87% |
| le21-ab-2-r3 | static | 0.757/0.721 | -4.77% | 0.617/0.583 | 0.140/0.138 | -5.11% | -7.48% | -2.61% | -3.77% | -8.16% | -3.24% | -62.11% | +0.21% | -0.93% | -4.66% |
| le21-ab-2-r3 | pan | 0.837/0.800 | -4.37% | 0.641/0.605 | 0.196/0.195 | -5.03% | -10.83% | -5.52% | -3.48% | +8.16% | -3.15% | -56.09% | -0.10% | -7.57% | -1.30% |
| le21-ab-2-r4 | static | 0.779/1.029 | +32.19% | 0.629/0.807 | 0.150/0.222 | +40.62% | +62.23% | +26.91% | +24.83% | +73.46% | +37.61% | -45.68% | -0.10% | -7.41% | +46.03% |
| le21-ab-2-r4 | pan | 0.799/0.943 | +17.93% | 0.626/0.714 | 0.173/0.228 | +26.34% | +61.89% | +28.90% | +21.45% | +43.22% | +23.77% | -45.40% | +0.05% | -5.86% | +30.16% |

## Predeclared gates (group medians of per-launch changes)

| Scale / phase | Pass | Frame mean | CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Median abs drift | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | -1.70% | -1.92% | -0.27% | -67.88% | -0.98% | -0.24% | +1.47% | +11.15% | 14.98% (noisy) | FAIL: 2_median_particle_pass_saving_at_least_3_percent, 6d_median_tick_frame_p99_within_5_percent, 8_noisy_group_pass_beats_a1_and_a2_in_3_of_4_launches |
| 1 / pan | +1.16% | +1.68% | +1.48% | -60.17% | -2.10% | +2.26% | +1.47% | +1.40% | 2.95% | FAIL: 1_particle_pass_lower_in_3_of_4_launches, 2_median_particle_pass_saving_at_least_3_percent, 3_median_frame_mean_saving_at_least_1_percent, 4_median_client_cpu_not_higher |
| 2 / static | +1.08% | +1.67% | +1.66% | -61.86% | +2.01% | +3.27% | +2.39% | -0.54% | 4.60% | FAIL: 1_particle_pass_lower_in_3_of_4_launches, 2_median_particle_pass_saving_at_least_3_percent, 3_median_frame_mean_saving_at_least_1_percent, 4_median_client_cpu_not_higher |
| 2 / pan | -3.48% | -3.98% | -3.30% | -56.78% | -6.75% | -4.60% | -3.33% | +6.45% | 6.66% | FAIL: 6d_median_tick_frame_p99_within_5_percent |

Source identity is checked against the frozen manifest and its ZIP snapshot.

## Startup gates: option on at startup versus hooks absent (separate launches)

Medians over launches; cross-launch, so confounded by launch-to-launch drift. Values absent -> on.

| Scale / phase | Launches absent/on | Frame mean ms | Particle pass ms | CPU/frame ms | Frame p95 ms | Frame p99 ms | Tick-frame mean ms | Tick-frame p99 ms | Alloc KB/frame | Rendered | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | 2/2 | 0.835 -> 0.824 (-1.2%) | 0.562 -> 0.562 (+0.1%) | 0.792 -> 0.782 (-1.3%) | 1.287 -> 1.230 (-4.4%) | 2.993 -> 2.939 (-1.8%) | 3.764 -> 3.131 (-16.8%) | 38.663 -> 6.310 (-83.7%) | 76.5 -> 33.9 (-55.7%) | 3325 -> 3309 | FAIL: median_particle_pass_lower |
| 1 / pan | 2/2 | 1.299 -> 1.219 (-6.2%) | 0.680 -> 0.656 (-3.6%) | 1.179 -> 1.138 (-3.5%) | 2.524 -> 2.290 (-9.3%) | 4.252 -> 3.966 (-6.7%) | 4.945 -> 3.852 (-22.1%) | 46.336 -> 6.773 (-85.4%) | 122.4 -> 52.6 (-57.0%) | 3314 -> 3319 | PASS |
| 2 / static | 2/2 | 1.086 -> 0.993 (-8.6%) | 0.756 -> 0.696 (-7.9%) | 1.021 -> 0.961 (-5.9%) | 1.677 -> 1.418 (-15.4%) | 4.384 -> 4.096 (-6.6%) | 4.582 -> 4.333 (-5.4%) | 7.778 -> 9.165 (+17.8%) | 77.4 -> 60.0 (-22.5%) | 5130 -> 5137 | PASS |
| 2 / pan | 2/2 | 1.505 -> 1.463 (-2.8%) | 0.847 -> 0.820 (-3.3%) | 1.388 -> 1.369 (-1.4%) | 2.782 -> 2.594 (-6.8%) | 5.340 -> 5.057 (-5.3%) | 5.156 -> 4.953 (-3.9%) | 8.679 -> 9.141 (+5.3%) | 175.8 -> 90.8 (-48.4%) | 5138 -> 5134 | PASS |

| Launch | Phase | Frame ms | Particle pass ms | Draw ms | CPU/frame ms | Rendered |
|---|---|---:|---:|---:|---:|---:|
| le21-startup-absent-1-r1 | static | 0.822 | 0.549 | 0.179 | 0.791 | 3314 |
| le21-startup-absent-1-r1 | pan | 1.326 | 0.678 | 0.251 | 1.195 | 3313 |
| le21-startup-absent-1-r2 | static | 0.847 | 0.574 | 0.173 | 0.793 | 3337 |
| le21-startup-absent-1-r2 | pan | 1.273 | 0.682 | 0.235 | 1.164 | 3315 |
| le21-startup-absent-2-r1 | static | 1.061 | 0.742 | 0.139 | 1.015 | 5131 |
| le21-startup-absent-2-r1 | pan | 1.479 | 0.839 | 0.192 | 1.365 | 5140 |
| le21-startup-absent-2-r2 | static | 1.110 | 0.771 | 0.152 | 1.026 | 5129 |
| le21-startup-absent-2-r2 | pan | 1.531 | 0.855 | 0.202 | 1.411 | 5136 |
| le21-startup-on-1-r1 | static | 0.761 | 0.522 | 0.143 | 0.731 | 3316 |
| le21-startup-on-1-r1 | pan | 1.123 | 0.615 | 0.206 | 1.067 | 3323 |
| le21-startup-on-1-r2 | static | 0.888 | 0.602 | 0.209 | 0.832 | 3302 |
| le21-startup-on-1-r2 | pan | 1.314 | 0.697 | 0.252 | 1.210 | 3316 |
| le21-startup-on-2-r1 | static | 1.038 | 0.721 | 0.134 | 0.999 | 5136 |
| le21-startup-on-2-r1 | pan | 1.562 | 0.865 | 0.198 | 1.450 | 5141 |
| le21-startup-on-2-r2 | static | 0.948 | 0.672 | 0.120 | 0.922 | 5138 |
| le21-startup-on-2-r2 | pan | 1.365 | 0.775 | 0.172 | 1.288 | 5127 |
