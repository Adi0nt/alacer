# Particle session: raw-frame-derived tables

Dedicated particle scenario (`benchmark.particles`), Java 25, 1920x1080, render distance 12, no mobs.
A pools a1/a2, B pools b1/b2; warmups are excluded. Every number derives from raw frames.
Entity detail and verification are off; the six shipped defaults are on in A and B.

## Attribution: particles emitting (A) versus none (B)

B stops the schedule and clears every particle; it changes the image and is not an option.
These launches ran the attribution build, without the light redirect.

| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |
|---|---|---:|---:|---:|---:|---:|---:|
| pt-attr-r1 | static | 1.275 | 1.445 | 0.553 | -59.2% | -61.7% | 738.1 -> 1808.7 |
| pt-attr-r1 | pan | 1.742 | 1.737 | 0.820 | -52.9% | -52.8% | 574.9 -> 1219.4 |
| pt-attr-r2 | static | 1.407 | 1.229 | 0.534 | -59.3% | -56.6% | 761.9 -> 1874.4 |
| pt-attr-r2 | pan | 1.542 | 1.845 | 0.792 | -52.9% | -57.1% | 595.1 -> 1263.1 |
| pt-attr2-r1 | static | 2.303 | 1.987 | 0.478 | -77.6% | -75.9% | 468.7 -> 2090.7 |
| pt-attr2-r1 | pan | 2.714 | 2.431 | 0.731 | -71.5% | -69.9% | 389.9 -> 1368.3 |

CPU, allocation and tails (pooled A -> B). Tick frames are frames that ran a client tick.

| Launch | Phase | Client CPU ms/frame | Alloc KB/frame | Alloc MB/s | p99 ms | 1% low FPS | Max ms | Tick-frame mean ms |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| pt-attr-r1 | static | 1.304 -> 0.218 | 254.6 -> 3.5 | 184 -> 6 | 5.206 -> 1.339 | 170.7 -> 698.2 | 8.47 -> 4.01 | 5.075 -> 1.226 |
| pt-attr-r1 | pan | 1.683 -> 0.466 | 273.8 -> 3.8 | 154 -> 4 | 5.806 -> 2.230 | 156.2 -> 357.7 | 9.91 -> 9.73 | 5.577 -> 2.121 |
| pt-attr-r2 | static | 1.267 -> 0.191 | 256.8 -> 3.3 | 191 -> 6 | 4.592 -> 1.276 | 189.5 -> 705.0 | 7.51 -> 2.32 | 4.645 -> 1.211 |
| pt-attr-r2 | pan | 1.629 -> 0.476 | 270.3 -> 3.9 | 157 -> 5 | 5.393 -> 2.692 | 162.4 -> 275.5 | 10.28 -> 9.97 | 5.169 -> 2.289 |
| pt-attr2-r1 | static | 2.033 -> 0.185 | 474.1 -> 3.0 | 217 -> 6 | 7.221 -> 1.050 | 119.5 -> 812.5 | 14.61 -> 3.17 | 6.840 -> 1.148 |
| pt-attr2-r1 | pan | 2.465 -> 0.438 | 504.5 -> 3.4 | 192 -> 5 | 8.132 -> 2.003 | 105.3 -> 395.8 | 17.19 -> 4.62 | 7.468 -> 1.950 |

Coarse sections and particle counts (pooled A -> B, ms/frame). Nested sections overlap; do not add them.
Process CPU cores do not identify driver threading behavior.

| Launch | Phase | Particles | Draw | Particle tick | Render | Swap | Rendered | Process cores |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| pt-attr-r1 | static | 0.975 -> 0.008 | 0.096 -> 0.002 | 0.067 -> 0.000 | 1.217 -> 0.160 | 0.031 -> 0.369 | 3301 -> 15 | 1.683 -> 1.225 |
| pt-attr-r1 | pan | 1.072 -> 0.009 | 0.169 -> 0.003 | 0.084 -> 0.000 | 1.575 -> 0.419 | 0.031 -> 0.361 | 3313 -> 13 | 1.639 -> 1.481 |
| pt-attr-r2 | static | 0.960 -> 0.007 | 0.098 -> 0.002 | 0.062 -> 0.000 | 1.184 -> 0.160 | 0.030 -> 0.348 | 3314 -> 11 | 1.521 -> 1.248 |
| pt-attr-r2 | pan | 1.047 -> 0.010 | 0.167 -> 0.003 | 0.081 -> 0.000 | 1.523 -> 0.428 | 0.033 -> 0.322 | 3311 -> 15 | 1.577 -> 1.559 |
| pt-attr2-r1 | static | 1.572 -> 0.007 | 0.147 -> 0.002 | 0.154 -> 0.000 | 1.881 -> 0.165 | 0.037 -> 0.289 | 5135 -> 12 | 1.491 -> 1.396 |
| pt-attr2-r1 | pan | 1.635 -> 0.008 | 0.167 -> 0.002 | 0.189 -> 0.000 | 2.258 -> 0.392 | 0.042 -> 0.303 | 5133 -> 13 | 1.483 -> 1.608 |

Paired summary (attribution): median of the per-launch changes; the range shows every repeat.

| Scale | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | Particle pass ms saved, median (range) | B faster than A / A2 |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | static | 59.3% (59.2 to 59.3%) | 59.2% | +145.5% (+145.0 to +146.0%) | 0.790 | 0.960 (0.953 to 0.967) | 2/2 / 2/2 |
| 1 | pan | 52.9% (52.9 to 52.9%) | 54.9% | +112.2% (+112.1 to +112.2%) | 0.904 | 1.050 (1.037 to 1.063) | 2/2 / 2/2 |
| 2 | static | 77.6% (77.6 to 77.6%) | 75.9% | +346.1% (+346.1 to +346.1%) | 1.655 | 1.565 (1.565 to 1.565) | 1/1 / 1/1 |
| 2 | pan | 71.5% (71.5 to 71.5%) | 69.9% | +250.9% (+250.9 to +250.9%) | 1.834 | 1.626 (1.626 to 1.626) | 1/1 / 1/1 |

| Scale | Phase | Particle pass lower | Client CPU lower (median ms) | Allocation lower (median KB/frame) | p99 lower (median ms) | 1% low higher (median) |
|---|---|---:|---:|---:|---:|---:|
| 1 | static | 2/2 | 2/2 (1.081) | 2/2 (252.3) | 2/2 (3.591) | 2/2 (+290.6%) |
| 1 | pan | 2/2 | 2/2 (1.185) | 2/2 (268.3) | 2/2 (3.138) | 2/2 (+99.3%) |
| 2 | static | 1/1 | 1/1 (1.848) | 1/1 (471.1) | 1/1 (6.172) | 1/1 (+580.0%) |
| 2 | pan | 1/1 | 1/1 (2.028) | 1/1 (501.1) | 1/1 (6.130) | 1/1 (+275.8%) |

## Optimization: vanilla particle light (A) versus `render.fastParticleLight` (B)

| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |
|---|---|---:|---:|---:|---:|---:|---:|
| pt-opt-1-r1 | static | 1.451 | 1.357 | 1.007 | -28.2% | -25.8% | 713.2 -> 992.7 |
| pt-opt-1-r1 | pan | 1.770 | 1.781 | 1.399 | -21.2% | -21.4% | 563.2 -> 714.6 |
| pt-opt-2-r1 | static | 2.208 | 2.277 | 1.965 | -12.4% | -13.7% | 446.0 -> 509.0 |
| pt-opt-2-r1 | pan | 2.173 | 2.271 | 1.939 | -12.7% | -14.6% | 450.3 -> 515.7 |
| pt-opt-1-r2 | static | 1.194 | 1.218 | 0.923 | -23.4% | -24.2% | 829.2 -> 1083.1 |
| pt-opt-1-r2 | pan | 1.529 | 1.514 | 1.234 | -18.9% | -18.5% | 657.4 -> 810.1 |
| pt-opt-2-r2 | static | 1.949 | 1.886 | 1.325 | -30.9% | -29.8% | 521.7 -> 754.9 |
| pt-opt-2-r2 | pan | 2.297 | 2.177 | 1.665 | -25.5% | -23.5% | 447.3 -> 600.5 |
| pt-opt-1-r3 | static | 1.229 | 1.187 | 0.927 | -23.3% | -21.9% | 828.2 -> 1079.2 |
| pt-opt-1-r3 | pan | 1.536 | 1.497 | 1.216 | -19.8% | -18.8% | 659.5 -> 822.5 |
| pt-opt-2-r3 | static | 2.197 | 2.155 | 1.453 | -33.2% | -32.6% | 459.6 -> 688.1 |
| pt-opt-2-r3 | pan | 2.219 | 2.423 | 1.665 | -28.1% | -31.3% | 431.6 -> 600.6 |

CPU, allocation and tails (pooled A -> B). Tick frames are frames that ran a client tick.

| Launch | Phase | Client CPU ms/frame | Alloc KB/frame | Alloc MB/s | p99 ms | 1% low FPS | Max ms | Tick-frame mean ms |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| pt-opt-1-r1 | static | 1.352 -> 0.976 | 256.9 -> 121.4 | 179 -> 118 | 4.950 -> 4.190 | 176.5 -> 220.1 | 10.32 -> 8.89 | 4.884 -> 4.228 |
| pt-opt-1-r1 | pan | 1.710 -> 1.344 | 269.5 -> 137.7 | 148 -> 96 | 5.479 -> 4.966 | 157.3 -> 178.1 | 11.02 -> 12.60 | 5.337 -> 4.955 |
| pt-opt-2-r1 | static | 2.165 -> 1.857 | 479.1 -> 253.4 | 209 -> 126 | 7.173 -> 7.863 | 117.2 -> 107.5 | 12.89 -> 17.83 | 6.967 -> 7.472 |
| pt-opt-2-r1 | pan | 2.163 -> 1.872 | 482.1 -> 251.3 | 212 -> 127 | 6.907 -> 7.473 | 130.3 -> 113.6 | 13.61 -> 15.85 | 6.664 -> 7.049 |
| pt-opt-1-r2 | static | 1.168 -> 0.886 | 245.3 -> 117.6 | 199 -> 124 | 4.221 -> 3.867 | 214.7 -> 232.4 | 7.13 -> 7.43 | 4.258 -> 4.025 |
| pt-opt-1-r2 | pan | 1.472 -> 1.175 | 259.6 -> 130.9 | 167 -> 104 | 4.888 -> 4.470 | 190.9 -> 200.7 | 7.49 -> 8.98 | 4.763 -> 4.518 |
| pt-opt-2-r2 | static | 1.848 -> 1.293 | 441.6 -> 198.4 | 225 -> 146 | 6.524 -> 5.955 | 137.3 -> 158.8 | 11.44 -> 8.87 | 6.418 -> 5.916 |
| pt-opt-2-r2 | pan | 2.179 -> 1.554 | 463.7 -> 218.9 | 203 -> 128 | 6.910 -> 6.395 | 131.0 -> 144.3 | 14.02 -> 11.89 | 6.678 -> 6.293 |
| pt-opt-1-r3 | static | 1.185 -> 0.902 | 247.0 -> 118.3 | 200 -> 125 | 4.222 -> 3.971 | 214.0 -> 230.5 | 7.85 -> 5.79 | 4.266 -> 4.089 |
| pt-opt-1-r3 | pan | 1.464 -> 1.175 | 261.1 -> 130.2 | 168 -> 105 | 4.798 -> 4.734 | 193.7 -> 194.0 | 7.81 -> 9.26 | 4.718 -> 4.701 |
| pt-opt-2-r3 | static | 2.104 -> 1.374 | 479.7 -> 218.6 | 215 -> 147 | 7.234 -> 6.145 | 126.0 -> 139.7 | 10.38 -> 12.74 | 6.864 -> 6.200 |
| pt-opt-2-r3 | pan | 2.244 -> 1.604 | 486.1 -> 233.0 | 205 -> 137 | 7.346 -> 6.358 | 121.2 -> 145.4 | 12.32 -> 10.81 | 6.898 -> 6.264 |

Coarse sections and particle counts (pooled A -> B, ms/frame). Nested sections overlap; do not add them.
Process CPU cores do not identify driver threading behavior.

| Launch | Phase | Particles | Draw | Particle tick | Render | Swap | Rendered | Process cores |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| pt-opt-1-r1 | static | 1.008 -> 0.695 | 0.106 -> 0.114 | 0.066 -> 0.047 | 1.263 -> 0.905 | 0.032 -> 0.027 | 3328 -> 3320 | 1.589 -> 1.708 |
| pt-opt-1-r1 | pan | 1.086 -> 0.800 | 0.179 -> 0.190 | 0.082 -> 0.065 | 1.609 -> 1.266 | 0.035 -> 0.030 | 3304 -> 3316 | 1.634 -> 1.686 |
| pt-opt-2-r1 | static | 1.635 -> 1.297 | 0.150 -> 0.159 | 0.169 -> 0.165 | 1.968 -> 1.680 | 0.041 -> 0.050 | 5132 -> 5143 | 1.534 -> 1.607 |
| pt-opt-2-r1 | pan | 1.492 -> 1.130 | 0.150 -> 0.155 | 0.151 -> 0.146 | 1.977 -> 1.693 | 0.036 -> 0.040 | 5141 -> 5131 | 1.583 -> 1.641 |
| pt-opt-1-r2 | static | 0.902 -> 0.646 | 0.089 -> 0.093 | 0.052 -> 0.040 | 1.097 -> 0.830 | 0.025 -> 0.025 | 3301 -> 3311 | 1.517 -> 1.656 |
| pt-opt-1-r2 | pan | 0.982 -> 0.731 | 0.154 -> 0.170 | 0.065 -> 0.055 | 1.390 -> 1.122 | 0.027 -> 0.026 | 3315 -> 3319 | 1.621 -> 1.694 |
| pt-opt-2-r2 | static | 1.449 -> 0.953 | 0.130 -> 0.111 | 0.134 -> 0.091 | 1.701 -> 1.168 | 0.030 -> 0.027 | 5130 -> 5127 | 1.487 -> 1.587 |
| pt-opt-2-r2 | pan | 1.494 -> 1.021 | 0.147 -> 0.142 | 0.152 -> 0.114 | 1.994 -> 1.476 | 0.032 -> 0.030 | 5142 -> 5138 | 1.510 -> 1.591 |
| pt-opt-1-r3 | static | 0.899 -> 0.649 | 0.089 -> 0.098 | 0.053 -> 0.041 | 1.098 -> 0.833 | 0.026 -> 0.025 | 3320 -> 3326 | 1.586 -> 1.645 |
| pt-opt-1-r3 | pan | 0.974 -> 0.715 | 0.151 -> 0.157 | 0.067 -> 0.054 | 1.384 -> 1.102 | 0.028 -> 0.026 | 3316 -> 3318 | 1.615 -> 1.673 |
| pt-opt-2-r3 | static | 1.592 -> 1.028 | 0.152 -> 0.122 | 0.160 -> 0.104 | 1.914 -> 1.272 | 0.040 -> 0.033 | 5139 -> 5129 | 1.364 -> 1.517 |
| pt-opt-2-r3 | pan | 1.518 -> 1.029 | 0.161 -> 0.142 | 0.162 -> 0.115 | 2.055 -> 1.475 | 0.038 -> 0.030 | 5134 -> 5133 | 1.450 -> 1.562 |

Paired summary (optimization): median of the per-launch changes; the range shows every repeat.

| Scale | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | Particle pass ms saved, median (range) | B faster than A / A2 |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | static | 23.4% (23.3 to 28.2%) | 24.2% | +30.6% (+30.3 to +39.2%) | 0.283 | 0.256 (0.251 to 0.313) | 3/3 / 3/3 |
| 1 | pan | 19.8% (18.9 to 21.2%) | 18.8% | +24.7% (+23.2 to +26.9%) | 0.301 | 0.259 (0.251 to 0.286) | 3/3 / 3/3 |
| 2 | static | 30.9% (12.4 to 33.2%) | 29.8% | +44.7% (+14.1 to +49.7%) | 0.592 | 0.496 (0.338 to 0.564) | 3/3 / 3/3 |
| 2 | pan | 25.5% (12.7 to 28.1%) | 23.5% | +34.2% (+14.5 to +39.1%) | 0.570 | 0.473 (0.362 to 0.489) | 3/3 / 3/3 |

| Scale | Phase | Particle pass lower | Client CPU lower (median ms) | Allocation lower (median KB/frame) | p99 lower (median ms) | 1% low higher (median) |
|---|---|---:|---:|---:|---:|---:|
| 1 | static | 3/3 | 3/3 (0.283) | 3/3 (128.7) | 3/3 (0.354) | 3/3 (+8.2%) |
| 1 | pan | 3/3 | 3/3 (0.297) | 3/3 (130.9) | 3/3 (0.418) | 3/3 (+5.2%) |
| 2 | static | 3/3 | 3/3 (0.554) | 3/3 (243.1) | 2/3 (0.569) | 2/3 (+10.9%) |
| 2 | pan | 3/3 | 3/3 (0.625) | 3/3 (244.8) | 2/3 (0.515) | 2/3 (+10.1%) |

## Detail and JFR launches (static 10 s, pan 15 s; no toggle)

`pt-detail-r1` adds per-particle timers (two `nanoTime` calls per particle and operation); they
inflate what they time and are for attribution only. Detail columns are zero in `pt-jfr-r1`.
Section values are means over all frames; the particle tick runs only in frames with a client tick.

| Launch | Phase | Frame ms | p99 ms | Client CPU ms/frame | Alloc KB/frame | Render | Particles (pass) | Draw (Tesselator.end) | Light (detail) | Tick | Particle tick | Update (detail) | Move (detail) | Remove (detail) | Spawn | Swap | Particles rendered | Ticked/s | Removed/s (detail) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pt-detail-r1 | static | 1.636 | 5.524 | 1.547 | 265.708 | 1.467 | 1.211 | 0.107 | 0.447 | 0.115 | 0.089 | 0.054 | 0.046 | 0.030 | 0.005 | 0.033 | 3323 | 70313 | 4189 |
| pt-detail-r1 | pan | 2.148 | 6.832 | 2.036 | 291.106 | 1.930 | 1.302 | 0.191 | 0.440 | 0.157 | 0.119 | 0.071 | 0.059 | 0.040 | 0.007 | 0.036 | 3312 | 70501 | 4210 |
| pt-jfr-r1 | static | 1.289 | 4.624 | 1.269 | 250.788 | 1.167 | 0.946 | 0.090 | 0.000 | 0.078 | 0.060 | 0.000 | 0.000 | 0.000 | 0.004 | 0.026 | 3315 | 70499 | 0 |
| pt-jfr-r1 | pan | 2.046 | 6.534 | 1.984 | 286.132 | 1.838 | 1.167 | 0.190 | 0.000 | 0.142 | 0.104 | 0.000 | 0.000 | 0.000 | 0.007 | 0.042 | 3313 | 70454 | 0 |
