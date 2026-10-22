# Scalar table-interface attribution

Exact client-thread allocated bytes around unlit passes; JFR weights are sampled estimates.

| Launch | Phase | Exact B/pass | Pass allocation samples | Sampled MB | Light BlockPos elimination records in whole JIT log |
|---|---|---:|---:|---:|---:|
| lc23-ea-original-r1 | static | 15122.8 | 239 | 127.4 | 7 |
| lc23-ea-original-r1 | pan | 76614.3 | 1117 | 708.5 | 7 |
| lc23-ea-scalar-r1 | static | 79338.2 | 964 | 512.1 | 4 |
| lc23-ea-scalar-r1 | pan | 79173.8 | 851 | 499.7 | 4 |
| lc23-ea-scalar-r2 | static | 25.7 | 0 | 0.0 | 6 |
| lc23-ea-scalar-r2 | pan | 75544.2 | 953 | 599.6 | 6 |
| lc23-ea-original-r2 | static | 78914.8 | 1227 | 651.0 | 4 |
| lc23-ea-original-r2 | pan | 79140.2 | 1145 | 661.9 | 4 |

Stable >10% reduction in both scalar launches, in both phases: False

The original can also avoid many allocations. Removing the position argument did not make elimination dependable. Whole-log elimination records describe particular compilations, not a guarantee that those versions remained active in each phase. Per-launch compilation JSON retains inlining successes/failures and method identities. No timing claim or promotion follows.
