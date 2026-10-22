# Visibility search: vanilla (A) vs Alacer (B), 3 separate launches each

A: 3 run(s), B: 3 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 27826 | 29327 | +5.4% |
| avg fps | 927.5 | 977.5 | +5.4% |
| 1% low fps | 494.8 | 489.0 | -1.2% |
| frame p50 ms | 1.043 | 0.978 | -6.3% |
| frame p95 ms | 1.376 | 1.322 | -3.9% |
| frame p99 ms | 1.710 | 1.692 | -1.1% |
| frame p99.9 ms | 2.496 | 2.309 | -7.5% |
| client thread CPU ms/frame | 1.051 | 0.989 | -5.9% |
| process CPU cores | 1.283 | 1.057 | -17.6% |
| render mean ms | 0.804 | 0.819 | +1.9% |
| terrain_setup mean ms | 0.017 | 0.017 | -1.8% |
| visibility mean ms | 0.000 | 0.001 | +980.1% |
| chunk_upload mean ms | 0.000 | 0.000 | +19.4% |
| entities mean ms | 0.108 | 0.093 | -14.5% |
| entity_loop mean ms | 0.096 | 0.080 | -16.1% |
| block_entity_loop mean ms | 0.012 | 0.011 | -3.2% |
| swap mean ms | 0.112 | 0.151 | +35.1% |
| alloc KB/frame | 4.899 | 4.811 | -1.8% |
| alloc MB/s | 4.437 | 4.593 | +3.5% |
| visible_chunks | 1374.0 | 1374.0 | -0.0% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 23149 | 28398 | +22.7% |
| avg fps | 514.4 | 631.0 | +22.7% |
| 1% low fps | 187.9 | 247.3 | +31.6% |
| frame p50 ms | 1.837 | 1.473 | -19.8% |
| frame p95 ms | 2.730 | 2.421 | -11.3% |
| frame p99 ms | 3.495 | 3.095 | -11.5% |
| frame p99.9 ms | 11.7 | 4.333 | -62.9% |
| client thread CPU ms/frame | 1.861 | 1.536 | -17.5% |
| process CPU cores | 1.430 | 1.191 | -16.7% |
| render mean ms | 1.552 | 1.353 | -12.8% |
| terrain_setup mean ms | 0.460 | 0.212 | -53.9% |
| visibility mean ms | 0.437 | 0.193 | -55.9% |
| chunk_upload mean ms | 0.032 | 0.029 | -11.6% |
| entities mean ms | 0.183 | 0.167 | -8.7% |
| entity_loop mean ms | 0.158 | 0.142 | -10.2% |
| block_entity_loop mean ms | 0.024 | 0.024 | +1.3% |
| swap mean ms | 0.134 | 0.170 | +26.3% |
| alloc KB/frame | 426.6 | 10.2 | -97.6% |
| alloc MB/s | 214.3 | 6.278 | -97.1% |
| visible_chunks | 1452.1 | 1482.1 | +2.1% |
| pending_chunks | 13.2 | 1.011 | -92.3% |
| visibility_searches | 1.000 | 1.000 | +0.0% |

### fly

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 28690 | 33993 | +18.5% |
| avg fps | 478.2 | 566.5 | +18.5% |
| 1% low fps | 165.2 | 145.1 | -12.1% |
| frame p50 ms | 1.982 | 1.543 | -22.1% |
| frame p95 ms | 3.048 | 2.996 | -1.7% |
| frame p99 ms | 3.903 | 4.050 | +3.8% |
| frame p99.9 ms | 12.4 | 15.1 | +21.8% |
| client thread CPU ms/frame | 2.018 | 1.702 | -15.7% |
| process CPU cores | 1.513 | 1.576 | +4.2% |
| render mean ms | 1.815 | 1.498 | -17.5% |
| terrain_setup mean ms | 0.457 | 0.224 | -51.0% |
| visibility mean ms | 0.425 | 0.200 | -52.9% |
| chunk_upload mean ms | 0.138 | 0.122 | -12.0% |
| entities mean ms | 0.139 | 0.127 | -8.5% |
| entity_loop mean ms | 0.119 | 0.109 | -8.3% |
| block_entity_loop mean ms | 0.018 | 0.016 | -10.6% |
| swap mean ms | 0.198 | 0.195 | -1.6% |
| alloc KB/frame | 441.2 | 23.7 | -94.6% |
| alloc MB/s | 206.0 | 13.1 | -93.6% |
| visible_chunks | 1204.2 | 1199.9 | -0.4% |
| pending_chunks | 68.1 | 29.3 | -57.0% |
| visibility_searches | 1.000 | 1.000 | +0.0% |


# Verification overhead: vanilla (A) vs verify mode (B), 3 vs 2 launches

A: 3 run(s), B: 2 run(s)

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 23149 | 13157 | -43.2% |
| avg fps | 514.4 | 438.6 | -14.7% |
| 1% low fps | 187.9 | 154.2 | -17.9% |
| frame p50 ms | 1.837 | 2.183 | +18.8% |
| frame p95 ms | 2.730 | 3.115 | +14.1% |
| frame p99 ms | 3.495 | 4.033 | +15.4% |
| frame p99.9 ms | 11.7 | 13.1 | +12.1% |
| client thread CPU ms/frame | 1.861 | 2.192 | +17.8% |
| process CPU cores | 1.430 | 1.296 | -9.4% |
| render mean ms | 1.552 | 1.982 | +27.7% |
| terrain_setup mean ms | 0.460 | 0.689 | +49.9% |
| visibility mean ms | 0.437 | 0.661 | +51.2% |
| chunk_upload mean ms | 0.032 | 0.047 | +46.0% |
| entities mean ms | 0.183 | 0.196 | +7.0% |
| entity_loop mean ms | 0.158 | 0.174 | +10.4% |
| block_entity_loop mean ms | 0.024 | 0.020 | -15.9% |
| swap mean ms | 0.134 | 0.218 | +62.1% |
| alloc KB/frame | 426.6 | 420.6 | -1.4% |
| alloc MB/s | 214.3 | 180.1 | -16.0% |
| visible_chunks | 1452.1 | 1381.2 | -4.9% |
| pending_chunks | 13.2 | 71.2 | +439.8% |
| visibility_searches | 1.000 | 1.000 | -0.0% |

### fly

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 28690 | 16926 | -41.0% |
| avg fps | 478.2 | 423.1 | -11.5% |
| 1% low fps | 165.2 | 154.8 | -6.3% |
| frame p50 ms | 1.982 | 2.231 | +12.6% |
| frame p95 ms | 3.048 | 3.310 | +8.6% |
| frame p99 ms | 3.903 | 4.216 | +8.0% |
| frame p99.9 ms | 12.4 | 12.7 | +2.4% |
| client thread CPU ms/frame | 2.018 | 2.293 | +13.6% |
| process CPU cores | 1.513 | 1.444 | -4.5% |
| render mean ms | 1.815 | 2.068 | +13.9% |
| terrain_setup mean ms | 0.457 | 0.664 | +45.3% |
| visibility mean ms | 0.425 | 0.628 | +47.8% |
| chunk_upload mean ms | 0.138 | 0.146 | +5.4% |
| entities mean ms | 0.139 | 0.152 | +9.7% |
| entity_loop mean ms | 0.119 | 0.134 | +11.9% |
| block_entity_loop mean ms | 0.018 | 0.017 | -5.1% |
| swap mean ms | 0.198 | 0.211 | +6.3% |
| alloc KB/frame | 441.2 | 453.0 | +2.7% |
| alloc MB/s | 206.0 | 187.2 | -9.1% |
| visible_chunks | 1204.2 | 1171.9 | -2.7% |
| pending_chunks | 68.1 | 134.4 | +97.2% |
| visibility_searches | 1.000 | 1.000 | -0.0% |


# Visibility search, in-process A/B (2 launches, segments a=vanilla, b=Alacer; pan includes the warm-up segment a1, see BENCHMARKS.md)

A (option off) vs B (option on) within 2 run(s)

### static

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 19891 | 19677 | -1.1% |
| avg fps | 994.4 | 983.7 | -1.1% |
| 1% low fps | 502.1 | 482.4 | -3.9% |
| frame p50 ms | 0.962 | 0.968 | +0.6% |
| frame p95 ms | 1.300 | 1.350 | +3.9% |
| frame p99 ms | 1.612 | 1.754 | +8.8% |
| frame p99.9 ms | 2.157 | 2.335 | +8.2% |
| client thread CPU ms/frame | 0.961 | 0.993 | +3.3% |
| process CPU cores | 1.086 | 1.073 | -1.2% |
| render mean ms | 0.799 | 0.806 | +0.9% |
| terrain_setup mean ms | 0.016 | 0.016 | +3.2% |
| visibility mean ms | 0.000 | 0.000 | -22.1% |
| chunk_upload mean ms | 0.000 | 0.000 | +13.8% |
| entities mean ms | 0.058 | 0.060 | +3.0% |
| entity_loop mean ms | 0.039 | 0.041 | +3.5% |
| block_entity_loop mean ms | 0.018 | 0.018 | +1.7% |
| swap mean ms | 0.153 | 0.157 | +2.4% |
| alloc KB/frame | 4.983 | 4.839 | -2.9% |
| alloc MB/s | 4.839 | 4.649 | -3.9% |
| visible_chunks | 1374.0 | 1374.0 | +0.0% |
| pending_chunks | 0.000 | 0.000 | +135.9% |
| visibility_searches | 0.000 | 0.000 | +127.4% |

### pan

| metric | A | B | change |
|---|---:|---:|---:|
| frames | 15498 | 19947 | +28.7% |
| avg fps | 516.5 | 664.8 | +28.7% |
| 1% low fps | 170.0 | 360.3 | +111.9% |
| frame p50 ms | 1.785 | 1.447 | -19.0% |
| frame p95 ms | 2.916 | 2.102 | -27.9% |
| frame p99 ms | 3.694 | 2.487 | -32.7% |
| frame p99.9 ms | 13.0 | 3.019 | -76.8% |
| client thread CPU ms/frame | 1.879 | 1.470 | -21.8% |
| process CPU cores | 1.323 | 1.068 | -19.3% |
| render mean ms | 1.663 | 1.266 | -23.9% |
| terrain_setup mean ms | 0.469 | 0.208 | -55.7% |
| visibility mean ms | 0.446 | 0.189 | -57.5% |
| chunk_upload mean ms | 0.038 | 0.001 | -98.2% |
| entities mean ms | 0.125 | 0.100 | -20.2% |
| entity_loop mean ms | 0.096 | 0.075 | -21.4% |
| block_entity_loop mean ms | 0.028 | 0.024 | -16.3% |
| swap mean ms | 0.201 | 0.176 | -12.2% |
| alloc KB/frame | 425.4 | 6.012 | -98.6% |
| alloc MB/s | 214.6 | 3.904 | -98.2% |
| visible_chunks | 1446.6 | 1485.7 | +2.7% |
| pending_chunks | 12.7 | 0.002 | -100.0% |
| visibility_searches | 1.000 | 1.000 | +0.0% |

