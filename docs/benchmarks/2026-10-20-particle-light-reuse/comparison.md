# Session 22 per-pass particle light reuse: measured results

Java 25, display lists, 1920x1080, warmed A/B/B/A. A: optimization 7 answers every light query (pass hooks installed,
option off); B: repeated positions answered from the pass table. Nine shipped optimizations on, every experiment off, all options explicit.
B is compared with pooled A1+A2. Negative changes mean less time/allocation. Detail/JFR/verification off.

| Launch | Phase | Pass A/B ms | Pass change | Pass excl. draw A/B ms | Draw A/B ms | Frame mean change | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | CPU/frame | Alloc/frame | Rendered | A2/A1 drift | B vs A2 frame |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| lr22-ab-1-r1 | static | 0.577/0.643 | +11.44% | 0.389/0.372 | 0.188/0.271 | +15.87% | +39.58% | +12.38% | +11.85% | +32.01% | +12.17% | +14.63% | -0.31% | +7.24% | +11.82% |
| lr22-ab-1-r1 | pan | 0.610/0.536 | -12.15% | 0.393/0.308 | 0.217/0.228 | -9.01% | -0.39% | -5.43% | -3.71% | +10.79% | -8.62% | -2.83% | +0.41% | +1.85% | -9.85% |
| lr22-ab-1-r2 | static | 0.498/0.480 | -3.63% | 0.347/0.285 | 0.151/0.195 | -2.37% | -0.74% | -0.19% | +0.34% | +9.12% | -4.45% | -1.89% | +0.16% | -1.39% | -1.69% |
| lr22-ab-1-r2 | pan | 0.588/0.520 | -11.53% | 0.379/0.304 | 0.208/0.216 | -9.23% | -11.37% | -6.05% | -4.69% | -8.31% | -10.81% | -3.14% | +0.03% | +0.42% | -9.42% |
| lr22-ab-1-r3 | static | 0.611/0.500 | -18.26% | 0.409/0.291 | 0.203/0.209 | -20.49% | -47.76% | -22.68% | -23.52% | -57.51% | -20.49% | -19.11% | +0.34% | +63.04% | -39.55% |
| lr22-ab-1-r3 | pan | 0.626/0.586 | -6.33% | 0.398/0.334 | 0.227/0.252 | -3.81% | -7.45% | -3.59% | -2.07% | -0.19% | -2.87% | -1.32% | +0.22% | -11.67% | +2.15% |
| lr22-ab-1-r4 | static | 0.576/0.688 | +19.47% | 0.397/0.408 | 0.179/0.281 | +26.60% | +57.62% | +24.24% | +21.13% | +7.66% | +28.30% | +25.37% | +0.15% | -12.49% | +35.03% |
| lr22-ab-1-r4 | pan | 0.642/0.562 | -12.36% | 0.416/0.326 | 0.225/0.236 | -9.89% | -12.48% | -6.24% | -4.49% | -15.36% | -10.58% | -4.29% | -0.59% | -10.27% | -5.01% |
| lr22-ab-2-r1 | static | 0.701/0.634 | -9.59% | 0.575/0.462 | 0.126/0.172 | -6.79% | -4.96% | -1.33% | -2.77% | -4.11% | -8.80% | -2.63% | -0.27% | +6.18% | -9.58% |
| lr22-ab-2-r1 | pan | 0.753/0.662 | -12.06% | 0.581/0.452 | 0.172/0.210 | -9.26% | -9.82% | -3.58% | -4.21% | -6.50% | -10.53% | -3.87% | -0.12% | -1.48% | -8.59% |
| lr22-ab-2-r2 | static | 0.701/0.581 | -17.11% | 0.565/0.423 | 0.136/0.158 | -14.77% | -13.17% | -7.66% | -5.65% | +8.73% | -16.69% | -13.92% | +0.27% | +7.75% | -17.95% |
| lr22-ab-2-r2 | pan | 0.739/0.647 | -12.51% | 0.576/0.458 | 0.163/0.189 | -8.90% | -8.06% | -1.40% | -1.45% | -1.26% | -10.47% | -3.49% | +0.06% | -0.91% | -8.48% |
| lr22-ab-2-r3 | static | 0.714/0.616 | -13.68% | 0.574/0.448 | 0.140/0.169 | -12.63% | -20.35% | -6.63% | -7.75% | -30.71% | -12.48% | -11.83% | +0.38% | +17.31% | -19.59% |
| lr22-ab-2-r3 | pan | 0.790/0.767 | -2.89% | 0.604/0.534 | 0.185/0.233 | +1.46% | +16.89% | +10.59% | +5.26% | -16.30% | +1.47% | +0.68% | +0.04% | -6.70% | +4.98% |
| lr22-ab-2-r4 | static | 0.749/0.645 | -13.96% | 0.595/0.454 | 0.154/0.190 | -12.70% | -16.83% | -8.34% | -6.73% | -2.84% | -13.85% | -12.42% | -0.10% | -9.10% | -8.54% |
| lr22-ab-2-r4 | pan | 0.772/0.722 | -6.45% | 0.594/0.500 | 0.177/0.222 | -2.11% | +5.10% | +3.91% | +2.67% | +0.51% | -2.10% | -0.76% | +0.09% | -0.22% | -2.00% |

## Predeclared gates (group medians of per-launch changes)

| Scale / phase | Pass | Frame mean | CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Median abs drift | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | +3.90% | +6.75% | +3.86% | +6.37% | +19.42% | +6.10% | +6.10% | +8.39% | 9.86% | FAIL: 1_particle_pass_lower_in_3_of_4_launches, 2_median_particle_pass_saving_at_least_3_percent, 3_median_frame_mean_saving_at_least_1_percent, 4_median_client_cpu_not_higher, 5_median_allocation_regression_within_2_percent, 6a_median_frame_p95_within_5_percent, 6b_median_frame_p99_within_5_percent, 6c_median_tick_frame_mean_within_5_percent, 6d_median_tick_frame_p99_within_5_percent |
| 1 / pan | -11.84% | -9.12% | -9.60% | -2.98% | -9.41% | -5.74% | -4.10% | -4.25% | 6.06% | PASS |
| 2 / static | -13.82% | -12.67% | -13.16% | -12.13% | -15.00% | -7.15% | -6.19% | -3.47% | 8.43% | PASS |
| 2 / pan | -9.25% | -5.50% | -6.29% | -2.13% | -1.48% | +1.26% | +0.61% | -3.88% | 1.20% | PASS |

Source identity is checked against the frozen manifest and its ZIP snapshot.

## Startup gates: option on at startup versus hooks absent (separate launches)

Medians over launches; cross-launch, so confounded by launch-to-launch drift. Values absent -> on.

| Scale / phase | Launches absent/on | Frame mean ms | Particle pass ms | CPU/frame ms | Frame p95 ms | Frame p99 ms | Tick-frame mean ms | Tick-frame p99 ms | Alloc KB/frame | Rendered | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 / static | 2/2 | 0.826 -> 0.799 (-3.3%) | 0.561 -> 0.544 (-3.0%) | 0.785 -> 0.755 (-3.9%) | 1.211 -> 1.244 (+2.7%) | 2.986 -> 2.921 (-2.2%) | 3.263 -> 3.172 (-2.8%) | 6.200 -> 7.527 (+21.4%) | 73.7 -> 111.4 (+51.2%) | 3312 -> 3327 | FAIL: median_allocation_within_2_percent |
| 1 / pan | 2/2 | 1.208 -> 1.125 (-6.8%) | 0.652 -> 0.598 (-8.3%) | 1.118 -> 1.019 (-8.8%) | 2.279 -> 2.012 (-11.7%) | 3.986 -> 3.765 (-5.5%) | 3.917 -> 3.737 (-4.6%) | 7.131 -> 5.679 (-20.4%) | 128.6 -> 128.8 (+0.2%) | 3319 -> 3310 | PASS |
| 2 / static | 2/2 | 1.014 -> 0.918 (-9.5%) | 0.713 -> 0.632 (-11.4%) | 0.982 -> 0.869 (-11.5%) | 1.379 -> 1.298 (-5.8%) | 4.234 -> 4.082 (-3.6%) | 4.370 -> 4.273 (-2.2%) | 7.606 -> 8.111 (+6.6%) | 191.1 -> 103.5 (-45.8%) | 5133 -> 5133 | PASS |
| 2 / pan | 2/2 | 1.443 -> 1.316 (-8.8%) | 0.820 -> 0.723 (-11.8%) | 1.311 -> 1.211 (-7.7%) | 2.531 -> 2.399 (-5.2%) | 4.995 -> 4.863 (-2.6%) | 4.919 -> 4.856 (-1.3%) | 8.771 -> 7.983 (-9.0%) | 224.3 -> 210.3 (-6.3%) | 5126 -> 5131 | PASS |

| Launch | Phase | Frame ms | Particle pass ms | Draw ms | CPU/frame ms | Rendered |
|---|---|---:|---:|---:|---:|---:|
| lr22-startup-absent-1-r1 | static | 0.851 | 0.581 | 0.189 | 0.803 | 3316 |
| lr22-startup-absent-1-r1 | pan | 1.247 | 0.670 | 0.232 | 1.136 | 3328 |
| lr22-startup-absent-1-r2 | static | 0.801 | 0.542 | 0.154 | 0.767 | 3308 |
| lr22-startup-absent-1-r2 | pan | 1.168 | 0.635 | 0.218 | 1.099 | 3311 |
| lr22-startup-absent-2-r1 | static | 1.051 | 0.744 | 0.135 | 1.016 | 5142 |
| lr22-startup-absent-2-r1 | pan | 1.477 | 0.847 | 0.188 | 1.341 | 5127 |
| lr22-startup-absent-2-r2 | static | 0.977 | 0.682 | 0.131 | 0.947 | 5124 |
| lr22-startup-absent-2-r2 | pan | 1.409 | 0.794 | 0.188 | 1.281 | 5125 |
| lr22-startup-on-1-r1 | static | 0.778 | 0.535 | 0.211 | 0.748 | 3319 |
| lr22-startup-on-1-r1 | pan | 1.134 | 0.604 | 0.245 | 1.044 | 3317 |
| lr22-startup-on-1-r2 | static | 0.819 | 0.553 | 0.234 | 0.761 | 3336 |
| lr22-startup-on-1-r2 | pan | 1.116 | 0.593 | 0.257 | 0.994 | 3303 |
| lr22-startup-on-2-r1 | static | 0.917 | 0.632 | 0.174 | 0.873 | 5134 |
| lr22-startup-on-2-r1 | pan | 1.320 | 0.724 | 0.228 | 1.218 | 5131 |
| lr22-startup-on-2-r2 | static | 0.919 | 0.632 | 0.186 | 0.864 | 5132 |
| lr22-startup-on-2-r2 | pan | 1.313 | 0.722 | 0.212 | 1.203 | 5130 |
