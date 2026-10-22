# Superseded pilot (@WrapOperation hooks): raw-frame-derived tables

A pools a1/a2, B pools b1/b2; warmups are excluded. Every number derives from raw frames.
Entity detail and verification are off; the five shipped defaults are on in A and B.

## Pilot-attribution: vanilla shadows (A) versus the Entity Shadows option off (B)

A uses vanilla's shadow queries (`render.fastEntityShadows=false`) with the coarse shadow
timer on (two `nanoTime` calls per shadow). B changes the drawn image; it is not an option.

| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |
|---|---|---:|---:|---:|---:|---:|---:|
| shd-attr-200-r1 | static | 1.329 | 1.329 | 1.229 | -7.6% | -7.6% | 752.3 -> 813.7 |
| shd-attr-200-r1 | pan | 1.959 | 1.868 | 1.685 | -11.9% | -9.8% | 522.8 -> 593.3 |
| shd-attr-1000-r1 | static | 3.281 | 3.257 | 3.240 | -0.9% | -0.5% | 305.9 -> 308.6 |
| shd-attr-1000-r1 | pan | 3.856 | 4.369 | 4.207 | +2.7% | -3.7% | 244.1 -> 237.7 |
| shd-attr-200-r2 | static | 2.401 | 2.214 | 2.084 | -9.5% | -5.8% | 434.1 -> 479.8 |
| shd-attr-200-r2 | pan | 3.304 | 3.666 | 3.037 | -12.6% | -17.2% | 287.7 -> 329.2 |
| shd-attr-1000-r2 | static | 2.870 | 3.062 | 2.940 | -0.8% | -4.0% | 337.6 -> 340.1 |
| shd-attr-1000-r2 | pan | 3.379 | 3.514 | 3.524 | +2.3% | +0.3% | 290.3 -> 283.8 |

CPU, allocation and tails (pooled A -> B):

| Launch | Phase | Client CPU ms/frame | Allocation KB/frame | Non-tick median bytes | p99 ms | 1% low FPS |
|---|---|---:|---:|---:|---:|---:|
| shd-attr-200-r1 | static | 0.845 -> 0.635 | 53.8 -> 28.0 | 52424 -> 26288 | 3.248 -> 3.198 | 257.8 -> 239.6 |
| shd-attr-200-r1 | pan | 1.397 -> 1.143 | 57.7 -> 30.2 | 55536 -> 27576 | 5.226 -> 4.535 | 169.9 -> 193.8 |
| shd-attr-1000-r1 | static | 2.032 -> 1.927 | 157.2 -> 131.2 | 142792 -> 114536 | 8.429 -> 8.983 | 109.2 -> 87.7 |
| shd-attr-1000-r1 | pan | 3.076 -> 2.980 | 165.0 -> 137.4 | 146576 -> 117584 | 12.104 -> 13.434 | 65.6 -> 55.5 |
| shd-attr-200-r2 | static | 2.282 -> 1.980 | 55.4 -> 29.7 | 52248 -> 26304 | 3.639 -> 4.067 | 232.8 -> 197.6 |
| shd-attr-200-r2 | pan | 3.396 -> 2.926 | 61.3 -> 32.9 | 56856 -> 28328 | 5.917 -> 4.990 | 142.4 -> 165.3 |
| shd-attr-1000-r2 | static | 1.878 -> 1.703 | 155.6 -> 128.1 | 142880 -> 114744 | 7.713 -> 7.736 | 117.8 -> 114.0 |
| shd-attr-1000-r2 | pan | 2.519 -> 2.338 | 161.7 -> 133.3 | 146560 -> 117496 | 8.731 -> 9.740 | 100.2 -> 85.9 |

Coarse sections (pooled A -> B, ms/frame). Nested sections overlap; do not add them.
Process CPU cores do not identify driver threading behavior.

| Launch | Phase | Shadows (A) | Shadows drawn | Entity loop | Render | Swap | Process cores |
|---|---|---:|---:|---:|---:|---:|---:|
| shd-attr-200-r1 | static | 0.175 -> 0.000 | 54.000 -> 0.000 | 0.445 -> 0.255 | 0.689 -> 0.509 | 0.553 -> 0.636 | 1.683 -> 1.599 |
| shd-attr-200-r1 | pan | 0.203 -> 0.000 | 57.074 -> 0.000 | 0.547 -> 0.317 | 1.221 -> 0.966 | 0.548 -> 0.597 | 1.734 -> 1.731 |
| shd-attr-1000-r1 | static | 0.215 -> 0.000 | 59.000 -> 0.000 | 1.216 -> 0.993 | 1.683 -> 1.490 | 1.167 -> 1.334 | 1.695 -> 1.789 |
| shd-attr-1000-r1 | pan | 0.249 -> 0.000 | 59.823 -> 0.000 | 1.489 -> 1.259 | 2.621 -> 2.482 | 0.865 -> 1.078 | 1.819 -> 1.838 |
| shd-attr-200-r2 | static | 0.247 -> 0.000 | 54.000 -> 0.000 | 0.910 -> 0.632 | 1.783 -> 1.509 | 0.378 -> 0.433 | 1.109 -> 1.154 |
| shd-attr-200-r2 | pan | 0.286 -> 0.000 | 57.308 -> 0.000 | 1.056 -> 0.700 | 2.865 -> 2.430 | 0.433 -> 0.440 | 1.105 -> 1.119 |
| shd-attr-1000-r2 | static | 0.201 -> 0.000 | 59.000 -> 0.000 | 1.118 -> 0.910 | 1.514 -> 1.333 | 1.099 -> 1.260 | 1.769 -> 1.704 |
| shd-attr-1000-r2 | pan | 0.217 -> 0.000 | 59.821 -> 0.000 | 1.228 -> 1.013 | 2.142 -> 1.987 | 0.883 -> 1.075 | 1.815 -> 1.767 |

Paired summary (pilot-attribution): median of the per-launch changes; the range shows every repeat.

| Mobs | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | B faster than A / A2 |
|---|---|---:|---:|---:|---:|---:|
| 200 | static | 8.5% (7.6 to 9.5%) | 6.7% | +9.3% (+8.2 to +10.5%) | 0.160 | 2/2 / 2/2 |
| 200 | pan | 12.2% (11.9 to 12.6%) | 13.5% | +14.0% (+13.5 to +14.4%) | 0.333 | 2/2 / 2/2 |
| 1000 | static | 0.8% (0.8 to 0.9%) | 2.2% | +0.8% (+0.8 to +0.9%) | 0.026 | 2/2 / 2/2 |
| 1000 | pan | -2.5% (-2.7 to -2.3%) | 1.7% | -2.4% (-2.6 to -2.2%) | -0.094 | 0/2 / 1/2 |

## Pilot-optimization: vanilla shadow queries (A) versus `render.fastEntityShadows` (B)

| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |
|---|---|---:|---:|---:|---:|---:|---:|
| shd-200-r1 | static | 1.283 | 1.314 | 1.296 | -0.2% | -1.4% | 770.2 -> 771.5 |
| shd-200-r1 | pan | 2.002 | 1.888 | 1.967 | +1.2% | +4.1% | 514.5 -> 508.5 |
| shd-1000-r1 | static | 3.152 | 3.233 | 3.397 | +6.4% | +5.1% | 313.3 -> 294.4 |
| shd-1000-r1 | pan | 3.986 | 3.942 | 3.743 | -5.6% | -5.1% | 252.3 -> 267.1 |
| shd-200-r2 | static | 1.436 | 1.470 | 1.342 | -7.6% | -8.7% | 688.2 -> 745.2 |
| shd-200-r2 | pan | 2.035 | 1.946 | 2.090 | +5.0% | +7.4% | 502.6 -> 478.5 |
| shd-1000-r2 | static | 3.304 | 3.394 | 3.354 | +0.2% | -1.2% | 298.7 -> 298.2 |
| shd-1000-r2 | pan | 3.818 | 3.654 | 4.116 | +10.2% | +12.6% | 267.8 -> 242.9 |

CPU, allocation and tails (pooled A -> B):

| Launch | Phase | Client CPU ms/frame | Allocation KB/frame | Non-tick median bytes | p99 ms | 1% low FPS |
|---|---|---:|---:|---:|---:|---:|
| shd-200-r1 | static | 0.824 -> 0.761 | 72.1 -> 53.3 | 71296 -> 52168 | 3.562 -> 3.421 | 250.2 -> 256.1 |
| shd-200-r1 | pan | 1.433 -> 1.397 | 78.0 -> 57.9 | 76432 -> 56088 | 5.846 -> 5.390 | 143.6 -> 147.7 |
| shd-1000-r1 | static | 1.934 -> 1.990 | 177.3 -> 158.0 | 163936 -> 142816 | 7.945 -> 8.628 | 111.8 -> 100.1 |
| shd-1000-r1 | pan | 2.824 -> 2.663 | 185.9 -> 163.4 | 168376 -> 146704 | 10.745 -> 9.660 | 79.6 -> 91.6 |
| shd-200-r2 | static | 0.935 -> 0.792 | 91.2 -> 53.7 | 90504 -> 52344 | 3.894 -> 3.385 | 225.3 -> 266.0 |
| shd-200-r2 | pan | 1.465 -> 1.460 | 98.2 -> 58.1 | 97000 -> 55936 | 5.614 -> 5.951 | 88.4 -> 112.0 |
| shd-1000-r2 | static | 2.211 -> 2.017 | 178.1 -> 157.4 | 163800 -> 142680 | 8.944 -> 9.368 | 94.7 -> 96.5 |
| shd-1000-r2 | pan | 2.780 -> 2.906 | 184.6 -> 165.2 | 168000 -> 146696 | 10.185 -> 10.627 | 90.0 -> 84.6 |

Coarse sections (pooled A -> B, ms/frame). Nested sections overlap; do not add them.
Process CPU cores do not identify driver threading behavior.

| Launch | Phase | Entity loop | Render | Swap | Process cores |
|---|---|---:|---:|---:|---:|
| shd-200-r1 | static | 0.482 -> 0.413 | 0.739 -> 0.667 | 0.469 -> 0.540 | 1.666 -> 1.628 |
| shd-200-r1 | pan | 0.600 -> 0.518 | 1.341 -> 1.243 | 0.450 -> 0.572 | 1.729 -> 1.740 |
| shd-1000-r1 | static | 1.154 -> 1.146 | 1.555 -> 1.563 | 1.244 -> 1.385 | 1.679 -> 1.692 |
| shd-1000-r1 | pan | 1.363 -> 1.258 | 2.381 -> 2.229 | 1.033 -> 1.029 | 1.749 -> 1.841 |
| shd-200-r2 | static | 0.518 -> 0.419 | 0.805 -> 0.687 | 0.546 -> 0.564 | 1.642 -> 1.697 |
| shd-200-r2 | pan | 0.608 -> 0.547 | 1.335 -> 1.320 | 0.467 -> 0.596 | 1.769 -> 1.707 |
| shd-1000-r2 | static | 1.290 -> 1.216 | 1.746 -> 1.659 | 1.149 -> 1.234 | 1.750 -> 1.742 |
| shd-1000-r2 | pan | 1.387 -> 1.371 | 2.404 -> 2.473 | 0.825 -> 1.056 | 1.807 -> 1.808 |

Paired summary (pilot-optimization): median of the per-launch changes; the range shows every repeat.

| Mobs | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | B faster than A / A2 |
|---|---|---:|---:|---:|---:|---:|
| 200 | static | 3.9% (0.2 to 7.6%) | 5.0% | +4.2% (+0.2 to +8.3%) | 0.057 | 2/2 / 2/2 |
| 200 | pan | -3.1% (-5.0 to -1.2%) | -5.8% | -3.0% (-4.8 to -1.2%) | -0.062 | 0/2 / 0/2 |
| 1000 | static | -3.3% (-6.4 to -0.2%) | -1.9% | -3.1% (-6.0 to -0.2%) | -0.105 | 0/2 / 1/2 |
| 1000 | pan | -2.3% (-10.2 to 5.6%) | -3.8% | -1.7% (-9.3 to +5.9%) | -0.080 | 1/2 / 1/2 |
