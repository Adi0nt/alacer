# Entity chunk lookup cache, in-process A/B, 0 mobs (3 launches; a=vanilla lookup, b=cache; pan includes warm-up segment a1)

A (option off) vs B (option on) within 3 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 28414 | 29413 | +3.5% |
| avg fps | 947.0 | 980.3 | +3.5% |
| 1% low fps | 494.5 | 474.7 | -4.0% |
| frame p50 ms | 1.016 | 0.977 | -3.9% |
| frame p95 ms | 1.344 | 1.322 | -1.6% |
| frame p99 ms | 1.649 | 1.694 | +2.7% |
| frame p99.9 ms | 2.143 | 2.365 | +10.4% |
| client thread CPU ms/frame | 1.033 | 0.994 | -3.7% |
| process CPU cores | 1.109 | 1.065 | -4.0% |
| render mean ms | 0.849 | 0.813 | -4.3% |
| terrain_setup mean ms | 0.016 | 0.015 | -1.5% |
| visibility mean ms | 0.000 | 0.000 | +159.5% |
| chunk_upload mean ms | 0.000 | 0.000 | +14.9% |
| entities mean ms | 0.106 | 0.065 | -38.6% |
| entity_loop mean ms | 0.089 | 0.050 | -43.6% |
| block_entity_loop mean ms | 0.016 | 0.014 | -12.3% |
| swap mean ms | 0.153 | 0.154 | +0.5% |
| alloc KB/frame | 37.2 | 37.2 | +0.0% |
| alloc MB/s | 34.4 | 35.6 | +3.5% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 26680 | 29882 | +12.0% |
| avg fps | 592.8 | 664.0 | +12.0% |
| 1% low fps | 221.7 | 320.6 | +44.6% |
| frame p50 ms | 1.567 | 1.438 | -8.2% |
| frame p95 ms | 2.603 | 2.118 | -18.6% |
| frame p99 ms | 3.335 | 2.588 | -22.4% |
| frame p99.9 ms | 5.052 | 3.628 | -28.2% |
| client thread CPU ms/frame | 1.659 | 1.484 | -10.5% |
| process CPU cores | 1.257 | 1.061 | -15.6% |
| render mean ms | 1.435 | 1.268 | -11.7% |
| terrain_setup mean ms | 0.235 | 0.210 | -10.8% |
| visibility mean ms | 0.214 | 0.194 | -9.4% |
| chunk_upload mean ms | 0.032 | 0.000 | -98.8% |
| entities mean ms | 0.178 | 0.109 | -39.1% |
| entity_loop mean ms | 0.150 | 0.086 | -42.6% |
| block_entity_loop mean ms | 0.027 | 0.022 | -21.1% |
| swap mean ms | 0.186 | 0.177 | -5.1% |
| alloc KB/frame | 44.5 | 40.6 | -8.7% |
| alloc MB/s | 25.8 | 26.3 | +2.2% |
| visible_chunks | 1482.2 | 1485.2 | +0.2% |
| pending_chunks | 1.761 | 0.001 | -99.9% |
| visibility_searches | 1.000 | 1.000 | +0.0% |


# Entity chunk lookup cache, in-process A/B, 200 mobs (3 launches; a=vanilla lookup, b=cache; pan includes warm-up segment a1)

A (option off) vs B (option on) within 3 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 16905 | 17721 | +4.8% |
| avg fps | 563.4 | 590.7 | +4.8% |
| 1% low fps | 290.6 | 320.2 | +10.2% |
| frame p50 ms | 1.717 | 1.647 | -4.1% |
| frame p95 ms | 2.364 | 2.175 | -8.0% |
| frame p99 ms | 2.791 | 2.559 | -8.3% |
| frame p99.9 ms | 3.874 | 3.057 | -21.1% |
| client thread CPU ms/frame | 1.722 | 1.632 | -5.2% |
| process CPU cores | 1.294 | 1.268 | -2.0% |
| render mean ms | 1.460 | 1.381 | -5.4% |
| terrain_setup mean ms | 0.021 | 0.018 | -15.2% |
| visibility mean ms | 0.000 | 0.000 | -76.9% |
| chunk_upload mean ms | 0.000 | 0.000 | -22.5% |
| entities mean ms | 0.685 | 0.621 | -9.3% |
| entity_loop mean ms | 0.667 | 0.606 | -9.1% |
| block_entity_loop mean ms | 0.017 | 0.014 | -18.9% |
| swap mean ms | 0.157 | 0.153 | -2.7% |
| alloc KB/frame | 77.9 | 76.9 | -1.3% |
| alloc MB/s | 42.9 | 44.4 | +3.5% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 17194 | 18685 | +8.7% |
| avg fps | 382.0 | 415.2 | +8.7% |
| 1% low fps | 160.2 | 238.2 | +48.7% |
| frame p50 ms | 2.490 | 2.349 | -5.7% |
| frame p95 ms | 3.679 | 3.207 | -12.8% |
| frame p99 ms | 4.533 | 3.671 | -19.0% |
| frame p99.9 ms | 9.709 | 4.473 | -53.9% |
| client thread CPU ms/frame | 2.546 | 2.348 | -7.8% |
| process CPU cores | 1.259 | 1.081 | -14.2% |
| render mean ms | 2.302 | 2.106 | -8.5% |
| terrain_setup mean ms | 0.283 | 0.255 | -9.8% |
| visibility mean ms | 0.256 | 0.237 | -7.8% |
| chunk_upload mean ms | 0.049 | 0.001 | -98.9% |
| entities mean ms | 0.880 | 0.797 | -9.5% |
| entity_loop mean ms | 0.854 | 0.776 | -9.2% |
| block_entity_loop mean ms | 0.025 | 0.020 | -19.8% |
| swap mean ms | 0.216 | 0.210 | -2.9% |
| alloc KB/frame | 87.3 | 83.9 | -3.9% |
| alloc MB/s | 32.6 | 34.0 | +4.4% |
| visible_chunks | 1477.4 | 1488.5 | +0.8% |
| pending_chunks | 6.052 | 0.002 | -100.0% |
| visibility_searches | 1.000 | 1.000 | +0.0% |


# Entity chunk lookup cache, in-process A/B, 1000 mobs (3 launches; a=vanilla lookup, b=cache; pan includes warm-up segment a1)

A (option off) vs B (option on) within 3 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 8443 | 8783 | +4.0% |
| avg fps | 281.4 | 292.7 | +4.0% |
| 1% low fps | 137.5 | 145.9 | +6.1% |
| frame p50 ms | 3.361 | 3.248 | -3.4% |
| frame p95 ms | 4.995 | 4.884 | -2.2% |
| frame p99 ms | 5.758 | 5.462 | -5.1% |
| frame p99.9 ms | 9.529 | 7.835 | -17.8% |
| client thread CPU ms/frame | 3.455 | 3.359 | -2.8% |
| process CPU cores | 1.225 | 1.144 | -6.6% |
| render mean ms | 3.144 | 3.031 | -3.6% |
| terrain_setup mean ms | 0.028 | 0.023 | -18.7% |
| visibility mean ms | 0.001 | 0.000 | -58.5% |
| chunk_upload mean ms | 0.001 | 0.001 | -32.7% |
| entities mean ms | 2.227 | 2.143 | -3.8% |
| entity_loop mean ms | 2.199 | 2.117 | -3.7% |
| block_entity_loop mean ms | 0.026 | 0.024 | -7.2% |
| swap mean ms | 0.219 | 0.210 | -4.5% |
| alloc KB/frame | 172.9 | 171.1 | -1.0% |
| alloc MB/s | 47.5 | 48.9 | +3.0% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |
| pending_chunks | 0.004 | 0.001 | -70.0% |
| visibility_searches | 0.002 | 0.001 | -57.9% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 9809 | 10679 | +8.9% |
| avg fps | 217.9 | 237.2 | +8.9% |
| 1% low fps | 98.7 | 129.2 | +30.8% |
| frame p50 ms | 4.340 | 4.060 | -6.5% |
| frame p95 ms | 6.430 | 5.836 | -9.2% |
| frame p99 ms | 7.481 | 6.600 | -11.8% |
| frame p99.9 ms | 19.0 | 8.554 | -55.0% |
| client thread CPU ms/frame | 4.455 | 4.122 | -7.5% |
| process CPU cores | 1.302 | 1.110 | -14.8% |
| render mean ms | 4.116 | 3.781 | -8.1% |
| terrain_setup mean ms | 0.346 | 0.305 | -12.0% |
| visibility mean ms | 0.313 | 0.280 | -10.3% |
| chunk_upload mean ms | 0.085 | 0.001 | -98.7% |
| entities mean ms | 2.482 | 2.329 | -6.1% |
| entity_loop mean ms | 2.446 | 2.301 | -5.9% |
| block_entity_loop mean ms | 0.034 | 0.027 | -21.8% |
| swap mean ms | 0.242 | 0.225 | -7.0% |
| alloc KB/frame | 189.0 | 180.6 | -4.4% |
| alloc MB/s | 40.2 | 41.8 | +4.0% |
| visible_chunks | 1460.8 | 1490.1 | +2.0% |
| pending_chunks | 8.712 | 0.005 | -99.9% |
| visibility_searches | 0.999 | 0.999 | +0.0% |


# Entity chunk lookup cache, separate launches, 0 mobs (6 vs 6)

A: 6 run(s), B: 6 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 55272 | 58857 | +6.5% |
| avg fps | 921.1 | 980.9 | +6.5% |
| 1% low fps | 463.5 | 531.8 | +14.7% |
| frame p50 ms | 1.044 | 0.981 | -6.1% |
| frame p95 ms | 1.396 | 1.307 | -6.4% |
| frame p99 ms | 1.764 | 1.637 | -7.2% |
| frame p99.9 ms | 2.482 | 2.165 | -12.8% |
| client thread CPU ms/frame | 1.058 | 0.986 | -6.8% |
| process CPU cores | 1.151 | 1.075 | -6.6% |
| render mean ms | 0.856 | 0.811 | -5.2% |
| terrain_setup mean ms | 0.017 | 0.015 | -10.4% |
| visibility mean ms | 0.000 | 0.000 | -10.8% |
| chunk_upload mean ms | 0.000 | 0.000 | -14.4% |
| entities mean ms | 0.113 | 0.062 | -45.3% |
| entity_loop mean ms | 0.097 | 0.043 | -55.9% |
| block_entity_loop mean ms | 0.015 | 0.018 | +21.9% |
| swap mean ms | 0.144 | 0.154 | +7.2% |
| alloc KB/frame | 4.940 | 4.858 | -1.7% |
| alloc MB/s | 4.444 | 4.654 | +4.7% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |
| pending_chunks | 0.000 | 0.000 | -28.2% |
| visibility_searches | 0.000 | 0.000 | -12.0% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 54142 | 56979 | +5.2% |
| avg fps | 601.6 | 633.1 | +5.2% |
| 1% low fps | 219.7 | 235.6 | +7.2% |
| frame p50 ms | 1.544 | 1.455 | -5.7% |
| frame p95 ms | 2.551 | 2.506 | -1.7% |
| frame p99 ms | 3.398 | 3.257 | -4.2% |
| frame p99.9 ms | 5.433 | 4.668 | -14.1% |
| client thread CPU ms/frame | 1.614 | 1.525 | -5.5% |
| process CPU cores | 1.228 | 1.238 | +0.8% |
| render mean ms | 1.419 | 1.336 | -5.8% |
| terrain_setup mean ms | 0.223 | 0.224 | +0.3% |
| visibility mean ms | 0.202 | 0.205 | +1.2% |
| chunk_upload mean ms | 0.032 | 0.030 | -5.3% |
| entities mean ms | 0.190 | 0.113 | -40.6% |
| entity_loop mean ms | 0.163 | 0.088 | -45.9% |
| block_entity_loop mean ms | 0.026 | 0.023 | -9.5% |
| swap mean ms | 0.177 | 0.178 | +0.2% |
| alloc KB/frame | 10.5 | 10.5 | -0.2% |
| alloc MB/s | 6.185 | 6.498 | +5.1% |
| visible_chunks | 1481.1 | 1482.1 | +0.1% |
| pending_chunks | 1.899 | 1.375 | -27.6% |
| visibility_searches | 1.000 | 1.000 | +0.0% |

### fly

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 67265 | 69183 | +2.9% |
| avg fps | 560.5 | 576.5 | +2.8% |
| 1% low fps | 168.3 | 164.2 | -2.4% |
| frame p50 ms | 1.589 | 1.543 | -2.9% |
| frame p95 ms | 2.836 | 2.770 | -2.3% |
| frame p99 ms | 3.731 | 3.676 | -1.5% |
| frame p99.9 ms | 15.1 | 15.7 | +3.9% |
| client thread CPU ms/frame | 1.722 | 1.667 | -3.2% |
| process CPU cores | 1.598 | 1.602 | +0.2% |
| render mean ms | 1.525 | 1.476 | -3.2% |
| terrain_setup mean ms | 0.225 | 0.227 | +0.9% |
| visibility mean ms | 0.201 | 0.203 | +1.3% |
| chunk_upload mean ms | 0.126 | 0.122 | -2.5% |
| entities mean ms | 0.134 | 0.085 | -36.2% |
| entity_loop mean ms | 0.117 | 0.070 | -40.4% |
| block_entity_loop mean ms | 0.015 | 0.014 | -6.3% |
| swap mean ms | 0.187 | 0.187 | +0.2% |
| alloc KB/frame | 24.1 | 23.2 | -3.7% |
| alloc MB/s | 13.2 | 13.0 | -1.0% |
| visible_chunks | 1203.6 | 1202.1 | -0.1% |
| pending_chunks | 28.9 | 28.1 | -2.8% |
| visibility_searches | 1.000 | 1.000 | +0.0% |


# Detail instrumentation overhead, 0 mobs, cache on (A=off, B=on; 2 vs 2)

A: 2 run(s), B: 2 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 18291 | 17926 | -2.0% |
| avg fps | 914.5 | 896.3 | -2.0% |
| 1% low fps | 461.8 | 492.5 | +6.7% |
| frame p50 ms | 1.067 | 1.074 | +0.7% |
| frame p95 ms | 1.384 | 1.429 | +3.2% |
| frame p99 ms | 1.768 | 1.757 | -0.6% |
| frame p99.9 ms | 2.559 | 2.402 | -6.1% |
| client thread CPU ms/frame | 1.070 | 1.084 | +1.3% |
| process CPU cores | 1.400 | 1.113 | -20.5% |
| render mean ms | 0.764 | 0.904 | +18.4% |
| terrain_setup mean ms | 0.018 | 0.017 | -5.3% |
| visibility mean ms | 0.000 | 0.000 | -67.5% |
| chunk_upload mean ms | 0.000 | 0.000 | -28.5% |
| entities mean ms | 0.061 | 0.151 | +147.3% |
| entity_loop mean ms | 0.044 | 0.133 | +199.9% |
| block_entity_loop mean ms | 0.016 | 0.017 | +8.6% |
| swap mean ms | 0.095 | 0.156 | +63.8% |
| alloc KB/frame | 5.009 | 5.390 | +7.6% |
| alloc MB/s | 4.473 | 4.718 | +5.5% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 17811 | 16847 | -5.4% |
| avg fps | 593.7 | 561.5 | -5.4% |
| 1% low fps | 201.5 | 170.2 | -15.5% |
| frame p50 ms | 1.539 | 1.589 | +3.2% |
| frame p95 ms | 2.663 | 2.968 | +11.5% |
| frame p99 ms | 3.479 | 4.164 | +19.7% |
| frame p99.9 ms | 5.397 | 6.754 | +25.1% |
| client thread CPU ms/frame | 1.594 | 1.712 | +7.4% |
| process CPU cores | 1.505 | 1.292 | -14.1% |
| render mean ms | 1.214 | 1.523 | +25.5% |
| terrain_setup mean ms | 0.243 | 0.236 | -2.7% |
| visibility mean ms | 0.221 | 0.214 | -2.8% |
| chunk_upload mean ms | 0.026 | 0.036 | +35.8% |
| entities mean ms | 0.113 | 0.246 | +118.1% |
| entity_loop mean ms | 0.085 | 0.220 | +158.0% |
| block_entity_loop mean ms | 0.026 | 0.025 | -6.7% |
| swap mean ms | 0.112 | 0.189 | +68.6% |
| alloc KB/frame | 10.8 | 13.6 | +26.8% |
| alloc MB/s | 6.241 | 7.483 | +19.9% |
| visible_chunks | 1478.5 | 1478.4 | -0.0% |
| pending_chunks | 4.227 | 3.119 | -26.2% |
| visibility_searches | 1.000 | 1.000 | -0.0% |

### fly

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 21451 | 21096 | -1.7% |
| avg fps | 536.2 | 527.4 | -1.7% |
| 1% low fps | 145.8 | 154.3 | +5.8% |
| frame p50 ms | 1.655 | 1.691 | +2.2% |
| frame p95 ms | 3.055 | 3.048 | -0.2% |
| frame p99 ms | 4.178 | 3.890 | -6.9% |
| frame p99.9 ms | 15.1 | 14.7 | -2.9% |
| client thread CPU ms/frame | 1.689 | 1.819 | +7.7% |
| process CPU cores | 1.826 | 1.625 | -11.0% |
| render mean ms | 1.309 | 1.635 | +24.8% |
| terrain_setup mean ms | 0.250 | 0.233 | -6.7% |
| visibility mean ms | 0.222 | 0.209 | -5.8% |
| chunk_upload mean ms | 0.099 | 0.132 | +33.1% |
| entities mean ms | 0.091 | 0.217 | +138.9% |
| entity_loop mean ms | 0.072 | 0.198 | +176.3% |
| block_entity_loop mean ms | 0.018 | 0.016 | -9.7% |
| swap mean ms | 0.116 | 0.189 | +62.0% |
| alloc KB/frame | 25.2 | 26.3 | +4.4% |
| alloc MB/s | 13.2 | 13.5 | +2.7% |
| visible_chunks | 1201.2 | 1197.9 | -0.3% |
| pending_chunks | 40.8 | 31.9 | -21.9% |
| visibility_searches | 1.000 | 1.000 | -0.0% |

