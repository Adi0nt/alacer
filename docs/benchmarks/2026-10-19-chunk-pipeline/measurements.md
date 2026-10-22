# Session 13 measurements

Fresh copied world per launch; diagnostics are attribution only. No candidate A/B or FPS gain is claimed.

| Run | Fly mean ms | p99 ms | Max ms | CPU ms/frame | Allocation B/frame | Pending mean/max | Terrain ms | Upload ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| cp-baseline-r1 | 3.632 | 7.095 | 26.036 | 3.402 | 46271 | 108.8/333 | 1.298 | 0.292 |
| cp-baseline-r2 | 2.936 | 5.986 | 22.860 | 2.865 | 37679 | 72.4/325 | 1.075 | 0.233 |
| cp-baseline-r3 | 3.009 | 6.578 | 23.685 | 2.860 | 37494 | 70.5/336 | 1.097 | 0.239 |
| cp-detail-r1 | 3.446 | 6.630 | 26.055 | 3.227 | 43523 | 99.7/327 | 1.231 | 0.281 |
| cp-detail-jfr-r2 | 3.003 | 5.454 | 151.590 | 2.846 | 37532 | 66.2/298 | 1.142 | 0.256 |
| cp-detail-age-r3 | 3.078 | 5.600 | 23.288 | 2.958 | 38543 | 74.2/308 | 1.148 | 0.255 |
| cp-detail-age-r4 | 3.491 | 6.555 | 27.017 | 3.247 | 44396 | 101.8/336 | 1.256 | 0.287 |
| cp-detail-jfr-r5 | 3.364 | 6.689 | 181.295 | 3.148 | 42147 | 85.6/327 | 1.217 | 0.270 |
