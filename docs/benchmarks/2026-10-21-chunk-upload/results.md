# Session 27 measurements

Ordinary-profiler controls select regions; intrusive diagnostics are separate. Warmup is archived but unranked.

| Launch | Phase | Mean ms | p99 ms | Slow-1% ms | Upload mean ms | Upload slow-1% ms | Tick p99 ms | CPU ms/frame | Alloc B/frame |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| cu27-high-control-r1 | warmup-pan | 1.277 | 2.878 | 3.585 | 0.031 | 0.633 | 3.959 | 1.257 | 3908 |
| cu27-high-control-r1 | still | 0.985 | 1.869 | 2.164 | 0.000 | 0.002 | 2.284 | 0.925 | 3007 |
| cu27-high-control-r1 | fresh-flight | 1.997 | 5.295 | 6.678 | 0.175 | 1.081 | 6.936 | 1.924 | 33636 |
| cu27-high-control-r2 | warmup-pan | 1.989 | 5.322 | 6.264 | 0.056 | 0.223 | 6.683 | 1.911 | 5486 |
| cu27-high-control-r2 | still | 1.391 | 2.895 | 3.455 | 0.000 | 0.001 | 3.697 | 1.319 | 3685 |
| cu27-high-control-r2 | fresh-flight | 2.054 | 4.990 | 6.273 | 0.182 | 1.176 | 5.277 | 1.989 | 34724 |
| cu27-high-control-r3 | warmup-pan | 1.435 | 3.525 | 4.102 | 0.035 | 0.369 | 4.322 | 1.397 | 4540 |
| cu27-high-control-r3 | still | 0.874 | 1.632 | 1.958 | 0.000 | 0.006 | 2.414 | 0.852 | 3268 |
| cu27-high-control-r3 | fresh-flight | 1.906 | 5.014 | 6.282 | 0.163 | 0.821 | 6.499 | 1.850 | 31288 |
| cu27-high-detail-r1 | warmup-pan | 1.283 | 3.292 | 4.053 | 0.034 | 0.413 | 4.099 | 1.239 | 4439 |
| cu27-high-detail-r1 | still | 0.924 | 2.037 | 2.566 | 0.001 | 0.004 | 2.755 | 0.898 | 2998 |
| cu27-high-detail-r1 | fresh-flight | 1.773 | 4.937 | 6.189 | 0.154 | 0.937 | 5.914 | 1.720 | 29173 |
| cu27-high-detail-r2 | warmup-pan | 1.858 | 4.806 | 5.592 | 0.056 | 0.314 | 5.886 | 1.774 | 4695 |
| cu27-high-detail-r2 | still | 1.225 | 3.066 | 3.917 | 0.001 | 0.002 | 3.937 | 1.194 | 3225 |
| cu27-high-detail-r2 | fresh-flight | 1.971 | 5.234 | 6.618 | 0.169 | 0.845 | 6.494 | 1.909 | 32809 |
| cu27-near-control-r1 | warmup-pan | 1.118 | 2.516 | 3.133 | 0.027 | 0.664 | 2.983 | 1.085 | 6432 |
| cu27-near-control-r1 | still | 0.915 | 1.644 | 1.959 | 0.000 | 0.000 | 2.315 | 0.878 | 2952 |
| cu27-near-control-r1 | fresh-flight | 1.622 | 4.553 | 5.690 | 0.145 | 1.345 | 5.319 | 1.566 | 26515 |
| cu27-near-control-r2 | warmup-pan | 1.276 | 3.243 | 3.855 | 0.032 | 0.655 | 5.066 | 1.232 | 7605 |
| cu27-near-control-r2 | still | 1.004 | 1.971 | 2.215 | 0.000 | 0.001 | 2.488 | 0.970 | 3371 |
| cu27-near-control-r2 | fresh-flight | 1.996 | 5.457 | 6.983 | 0.188 | 1.226 | 7.263 | 1.919 | 32522 |
| cu27-near-control-r3 | warmup-pan | 1.245 | 3.200 | 3.926 | 0.031 | 0.798 | 4.669 | 1.212 | 6711 |
| cu27-near-control-r3 | still | 0.981 | 1.884 | 2.262 | 0.000 | 0.006 | 2.912 | 0.944 | 3003 |
| cu27-near-control-r3 | fresh-flight | 1.757 | 4.817 | 6.043 | 0.161 | 1.070 | 5.964 | 1.704 | 28625 |
| cu27-near-detail-r1 | warmup-pan | 1.224 | 3.578 | 4.789 | 0.032 | 0.444 | 4.882 | 1.168 | 7158 |
| cu27-near-detail-r1 | still | 0.924 | 2.528 | 3.208 | 0.001 | 0.002 | 3.619 | 0.876 | 3350 |
| cu27-near-detail-r1 | fresh-flight | 1.894 | 6.138 | 7.515 | 0.174 | 0.917 | 8.847 | 1.817 | 30791 |
| cu27-near-detail-r2 | warmup-pan | 1.143 | 3.072 | 3.870 | 0.028 | 0.516 | 5.580 | 1.117 | 6934 |
| cu27-near-detail-r2 | still | 1.147 | 2.570 | 3.292 | 0.001 | 0.002 | 3.431 | 1.099 | 3567 |
| cu27-near-detail-r2 | fresh-flight | 1.742 | 4.942 | 6.611 | 0.158 | 0.964 | 6.969 | 1.678 | 28490 |

Full CSV groups (all, tick, nontick, slow95, slow99, tick_slow99), 10 s bins, worst frames, queue gauges, completed intervals, CPU, allocation/second and sparse GPU distributions are in metrics.json.
