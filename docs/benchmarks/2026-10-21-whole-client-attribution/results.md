# Session 26 whole-client results

Hooks-absent 1 ms JFR (DebugNonSafepoints). Shares are of client-thread Java execution samples inside each phase's CSV frames; samples are counts, not times. Native samples use a 20 ms period and are reported separately; CSV times come from the always-installed ordinary profiler. Warmup phases are excluded from scene medians.

## Launches

| Launch | Phase | Frames | FPS | p99 ms | Java samples | Native | Alloc samples | Swap % of frame | Client CPU ms/frame | CPU util | Offset ms | Tick alignment |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| wc26-d0-r1 | static | 71880 | 1797 | 1.49 | 5504 | 489 | 442 | 61.1% | 0.225 | 0.41 | -1.0 | 718/740 |
| wc26-d0-r1 | pan | 66675 | 1111 | 2.85 | 17323 | 532 | 484 | 36.8% | 0.559 | 0.62 | -1.0 | 718/740 |
| wc26-d0-r1 | fly | 68767 | 860 | 3.48 | 18364 | 859 | 999 | 24.5% | 0.674 | 0.58 | -1.0 | 718/740 |
| wc26-d0-r2 | static | 70440 | 1761 | 1.46 | 6086 | 484 | 435 | 58.4% | 0.260 | 0.46 | -0.5 | 893/893 |
| wc26-d0-r2 | pan | 68534 | 1142 | 2.60 | 17574 | 529 | 473 | 36.9% | 0.541 | 0.62 | -0.5 | 893/893 |
| wc26-d0-r2 | fly | 60369 | 755 | 3.80 | 20229 | 808 | 982 | 19.7% | 0.768 | 0.58 | -0.5 | 893/893 |
| wc26-d200-r1 | static | 35961 | 899 | 2.90 | 8227 | 389 | 3633 | 43.9% | 0.636 | 0.57 | -1.0 | 1065/1065 |
| wc26-d200-r1 | pan | 50614 | 844 | 2.96 | 15604 | 500 | 5292 | 33.6% | 0.753 | 0.64 | -1.0 | 1065/1065 |
| wc26-d200-r1 | fly | 76809 | 960 | 2.94 | 17833 | 853 | 955 | 28.8% | 0.559 | 0.54 | -1.0 | 1065/1065 |
| wc26-d200-r2 | static | 40151 | 1004 | 2.39 | 7539 | 409 | 4102 | 46.8% | 0.540 | 0.54 | -0.2 | 994/994 |
| wc26-d200-r2 | pan | 46131 | 769 | 3.30 | 16264 | 505 | 4901 | 33.0% | 0.846 | 0.65 | -0.2 | 994/994 |
| wc26-d200-r2 | fly | 69819 | 873 | 3.10 | 17788 | 847 | 957 | 28.1% | 0.610 | 0.53 | -0.2 | 994/994 |
| wc26-particles-r1 | static | 36323 | 908 | 4.02 | 16395 | 230 | 2867 | 4.0% | 1.030 | 0.94 | -0.6 | 2721/2721 |
| wc26-particles-r1 | pan | 38078 | 635 | 5.31 | 26321 | 300 | 9800 | 2.7% | 1.496 | 0.95 | -0.6 | 2721/2721 |
| wc26-particles-r2 | static | 40394 | 1010 | 3.61 | 15873 | 226 | 3311 | 3.1% | 0.926 | 0.94 | -0.4 | 2564/2564 |
| wc26-particles-r2 | pan | 40012 | 667 | 4.96 | 26417 | 297 | 11001 | 2.7% | 1.413 | 0.94 | -0.4 | 2564/2564 |
| wc26-terrain-r1 | warmup-pan (warmup) | 83000 | 1383 | 1.80 | 15905 | 583 | 675 | 41.1% | 0.425 | 0.59 | -0.4 | 1598/1598 |
| wc26-terrain-r1 | warmup-sort (warmup) | 74565 | 1165 | 2.36 | 17192 | 613 | 1187 | 34.2% | 0.519 | 0.60 | -0.4 | 1598/1598 |
| wc26-terrain-r1 | steady | 63532 | 1588 | 1.61 | 5728 | 477 | 497 | 59.8% | 0.268 | 0.43 | -0.4 | 1598/1598 |
| wc26-terrain-r1 | pan | 66609 | 1110 | 2.53 | 16810 | 562 | 476 | 39.7% | 0.557 | 0.62 | -0.4 | 1598/1598 |
| wc26-terrain-r1 | sort | 79226 | 1238 | 2.23 | 17282 | 612 | 1148 | 34.0% | 0.511 | 0.63 | -0.4 | 1598/1598 |
| wc26-terrain-r1 | fly | 59090 | 739 | 3.98 | 20092 | 837 | 980 | 20.5% | 0.785 | 0.58 | -0.4 | 1598/1598 |
| wc26-terrain-r2 | warmup-pan (warmup) | 71359 | 1189 | 2.44 | 16775 | 504 | 581 | 38.3% | 0.516 | 0.61 | -0.8 | 1588/1588 |
| wc26-terrain-r2 | warmup-sort (warmup) | 62357 | 974 | 3.20 | 18262 | 554 | 1166 | 30.2% | 0.620 | 0.60 | -0.8 | 1588/1588 |
| wc26-terrain-r2 | steady | 65224 | 1631 | 1.59 | 5879 | 463 | 456 | 60.3% | 0.253 | 0.41 | -0.8 | 1588/1588 |
| wc26-terrain-r2 | pan | 70605 | 1177 | 2.34 | 16187 | 546 | 467 | 41.3% | 0.509 | 0.60 | -0.8 | 1588/1588 |
| wc26-terrain-r2 | sort | 79898 | 1248 | 2.03 | 16748 | 630 | 1104 | 37.0% | 0.447 | 0.56 | -0.8 | 1588/1588 |
| wc26-terrain-r2 | fly | 69632 | 870 | 3.33 | 18655 | 849 | 951 | 25.4% | 0.602 | 0.52 | -0.8 | 1588/1588 |

## Scene d0: 6 launch/phase combinations, median swap 36.9% of frame time

| Region | Median % | Min % | Max % | Min samples | Per phase median % | Slow-1% frames % | Tick frames % | Native median % | Allocation median % |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| agg:world_render | 95.1 | 92.4 | 96.4 | 5105 | fly 95.1, pan 96.3, static 92.6 | 80.3 | 59.1 |  |  |
| agg:render_pass | 94.9 | 92.0 | 96.3 | 5079 | fly 94.9, pan 96.1, static 92.2 | 80.0 | 58.7 |  |  |
| agg:terrain_all | 72.2 | 36.7 | 73.2 | 2094 | fly 72.8, pan 72.3, static 37.4 | 54.5 | 45.6 |  |  |
| terrain_setup | 59.8 | 7.3 | 60.7 | 404 | fly 60.0, pan 60.1, static 7.3 | 41.6 | 39.1 | 0.0 | 6.6 |
| agg:entities_all | 19.6 | 18.4 | 45.9 | 2490 | fly 18.7, pan 19.6, static 45.6 | 21.9 | 10.6 |  |  |
| entities.loop.global | 15.4 | 0.0 | 32.1 | 0 (low) | fly 14.4, pan 16.3, static 16.1 | 12.8 | 8.1 | 0.0 | 0.0 |
| agg:terrain_layers | 12.3 | 11.8 | 30.6 | 1684 | fly 12.1, pan 12.0, static 29.9 | 12.6 | 6.4 |  |  |
| terrain.solid | 6.1 | 5.3 | 14.1 | 748 | fly 6.1, pan 5.4, static 13.9 | 8.0 | 3.8 | 0.3 | 1.4 |
| terrain.cutout_mipped | 2.6 | 2.3 | 5.1 | 279 (low) | fly 2.5, pan 2.4, static 4.8 | 2.0 | 1.0 | 0.4 | 2.8 |
| terrain.cutout | 2.2 | 1.7 | 5.8 | 315 | fly 1.8, pan 2.2, static 5.5 | 1.1 | 0.7 | 0.4 | 1.6 |
| terrain.translucent | 2.1 | 1.6 | 6.2 | 323 | fly 1.8, pan 2.1, static 5.8 | 1.5 | 0.8 | 0.2 | 1.5 |
| agg:tick | 1.9 | 1.5 | 2.9 | 154 (low) | fly 1.9, pan 1.6, static 2.8 | 16.8 | 39.4 |  |  |
| entities.loop.entities | 1.7 | 1.6 | 4.5 | 190 (low) | fly 1.7, pan 1.7, static 4.0 | 2.7 | 0.8 | 0.0 | 0.6 |
| pass.other | 1.5 | 1.4 | 3.3 | 157 (low) | fly 1.4, pan 1.5, static 3.1 | 1.2 | 0.8 | 0.3 | 5.7 |
| agg:particles_all | 1.3 | 0.3 | 2.5 | 53 (low) | fly 0.3, pan 1.3, static 2.5 | 1.6 | 1.6 |  |  |
| particles.unlit | 1.1 | 0.2 | 2.3 | 35 (low) | fly 0.3, pan 1.1, static 2.2 | 0.8 | 0.4 | 0.6 | 0.0 |
| agg:alacer_frame | 0.9 | 0.8 | 1.6 | 79 (low) | fly 0.9, pan 0.9, static 1.4 | 0.7 | 0.5 |  |  |
| entities.draw | 0.7 | 0.0 | 1.8 | 0 (low) | fly 1.7, pan 0.7, static 0.0 | 2.5 | 0.6 | 0.0 | 8.4 |
| tick.display_ticks | 0.7 | 0.5 | 1.2 | 64 (low) | fly 0.5, pan 0.7, static 1.1 | 5.4 | 13.3 | 0.0 | 0.1 |
| alacer.benchmark_driver | 0.6 | 0.6 | 1.0 | 50 (low) | fly 0.6, pan 0.6, static 0.9 | 0.5 | 0.4 | 0.0 | 1.6 |
| camera_fog | 0.6 | 0.5 | 1.3 | 70 (low) | fly 0.6, pan 0.6, static 1.3 | 0.6 | 0.5 | 0.2 | 3.1 |
| render.hud | 0.6 | 0.5 | 2.0 | 83 (low) | fly 0.6, pan 0.6, static 1.8 | 0.5 | 0.2 | 0.2 | 1.0 |
| tick.world | 0.6 | 0.4 | 0.9 | 37 (low) | fly 0.6, pan 0.5, static 0.8 | 5.5 | 12.1 | 0.0 | 12.7 |
| sky | 0.5 | 0.5 | 1.3 | 64 (low) | fly 0.5, pan 0.5, static 1.2 | 0.4 | 0.5 | 0.2 | 2.1 |
| entities.loop.blockentities | 0.5 | 0.4 | 41.1 | 74 (low) | fly 0.4, pan 0.5, static 25.0 | 3.3 | 0.8 | 0.3 | 2.1 |
| tick.entities | 0.4 | 0.2 | 0.7 | 26 (low) | fly 0.6, pan 0.2, static 0.4 | 3.5 | 9.3 | 0.0 | 0.7 |

## Scene d200: 6 launch/phase combinations, median swap 33.3% of frame time

| Region | Median % | Min % | Max % | Min samples | Per phase median % | Slow-1% frames % | Tick frames % | Native median % | Allocation median % |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| agg:world_render | 95.5 | 92.8 | 96.2 | 7046 | fly 95.5, pan 96.1, static 93.1 | 68.6 | 52.0 |  |  |
| agg:render_pass | 95.2 | 91.9 | 95.7 | 7000 | fly 95.2, pan 95.7, static 92.4 | 68.2 | 51.7 |  |  |
| agg:terrain_all | 49.4 | 18.3 | 74.2 | 1381 | fly 73.9, pan 49.4, static 18.4 | 42.1 | 34.7 |  |  |
| agg:entities_all | 43.2 | 17.7 | 69.5 | 3156 | fly 17.9, pan 43.2, static 69.0 | 22.9 | 15.1 |  |  |
| terrain_setup | 41.1 | 4.3 | 60.3 | 322 | fly 60.0, pan 41.1, static 4.6 | 31.8 | 29.2 | 0.0 | 0.8 |
| entities.draw | 25.6 | 1.6 | 41.6 | 284 (low) | fly 1.6, pan 25.6, static 39.5 | 9.4 | 7.7 | 3.9 | 88.9 |
| entities.loop.global | 14.8 | 14.2 | 23.2 | 1499 | fly 14.3, pan 14.8, static 21.5 | 11.0 | 6.2 | 0.0 | 0.0 |
| agg:terrain_layers | 13.2 | 8.0 | 14.0 | 1053 | fly 13.2, pan 8.2, static 13.6 | 9.9 | 5.5 |  |  |
| terrain.solid | 5.9 | 3.5 | 6.9 | 469 | fly 5.9, pan 3.5, static 6.6 | 6.3 | 3.3 | 0.3 | 0.1 |
| agg:tick | 2.5 | 1.7 | 4.3 | 296 (low) | fly 1.7, pan 2.5, static 4.1 | 28.8 | 47.0 |  |  |
| terrain.cutout_mipped | 2.4 | 1.5 | 2.9 | 181 (low) | fly 2.9, pan 1.6, static 2.4 | 2.0 | 1.0 | 0.3 | 0.3 |
| terrain.cutout | 2.1 | 1.5 | 2.5 | 180 (low) | fly 2.2, pan 1.5, static 2.3 | 0.9 | 0.7 | 0.2 | 0.1 |
| terrain.translucent | 2.1 | 1.5 | 2.5 | 167 (low) | fly 2.2, pan 1.6, static 2.3 | 0.7 | 0.4 | 0.2 | 0.2 |
| entities.loop.entities | 1.5 | 1.1 | 1.9 | 145 (low) | fly 1.3, pan 1.5, static 1.9 | 1.1 | 0.6 | 0.0 | 0.0 |
| pass.other | 1.5 | 1.1 | 1.9 | 141 (low) | fly 1.5, pan 1.2, static 1.8 | 1.2 | 0.4 | 0.3 | 0.4 |
| tick.entities | 1.2 | 0.5 | 2.1 | 97 (low) | fly 0.6, pan 1.2, static 2.1 | 14.3 | 21.5 | 0.0 | 1.3 |
| entities.visibility | 0.9 | 0.3 | 1.7 | 59 (low) | fly 0.3, pan 0.9, static 1.5 | 0.8 | 0.5 | 0.0 | 0.0 |
| agg:particles_all | 0.9 | 0.3 | 1.3 | 54 (low) | fly 0.3, pan 0.9, static 1.3 | 2.0 | 2.1 |  |  |
| agg:alacer_frame | 0.8 | 0.5 | 1.2 | 84 (low) | fly 0.8, pan 0.6, static 1.1 | 0.6 | 0.5 |  |  |
| particles.unlit | 0.7 | 0.2 | 1.1 | 36 (low) | fly 0.2, pan 0.7, static 1.0 | 0.2 | 0.1 | 0.1 | 0.0 |
| alacer.benchmark_driver | 0.6 | 0.3 | 0.7 | 49 (low) | fly 0.6, pan 0.4, static 0.6 | 0.4 | 0.3 | 0.0 | 0.2 |
| tick.display_ticks | 0.5 | 0.4 | 1.0 | 60 (low) | fly 0.5, pan 0.5, static 0.9 | 5.6 | 10.9 | 0.0 | 0.0 |
| render.hud | 0.5 | 0.4 | 1.1 | 50 (low) | fly 0.5, pan 0.4, static 0.9 | 0.2 | 0.1 | 0.2 | 0.1 |
| camera_fog | 0.5 | 0.4 | 0.8 | 55 (low) | fly 0.5, pan 0.4, static 0.7 | 0.6 | 0.5 | 0.0 | 0.1 |
| sky | 0.5 | 0.3 | 0.7 | 45 (low) | fly 0.5, pan 0.3, static 0.6 | 0.4 | 0.5 | 0.2 | 0.3 |
| tick.world | 0.5 | 0.4 | 0.7 | 42 (low) | fly 0.5, pan 0.4, static 0.6 | 4.9 | 9.3 | 0.0 | 1.5 |
| tick.particles | 0.1 | 0.0 | 0.3 | 3 (low) | fly 0.0, pan 0.1, static 0.2 | 1.7 | 2.0 | 0.0 | 0.3 |
| tasks | 0.0 | 0.0 | 0.9 | 2 (low) | fly 0.9, pan 0.0, static 0.0 | 1.7 | 0.2 | 0.0 | 0.0 |

## Scene terrain: 8 launch/phase combinations, median swap 38.4% of frame time

| Region | Median % | Min % | Max % | Min samples | Per phase median % | Slow-1% frames % | Tick frames % | Native median % | Allocation median % |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| agg:world_render | 95.9 | 91.6 | 96.7 | 5248 | fly 95.4, pan 96.3, sort 96.7, steady 92.2 | 81.6 | 60.0 |  |  |
| agg:render_pass | 95.7 | 90.8 | 96.5 | 5201 | fly 95.2, pan 96.1, sort 96.5, steady 91.5 | 81.4 | 59.6 |  |  |
| agg:terrain_all | 72.7 | 37.0 | 74.6 | 2130 | fly 73.3, pan 72.0, sort 74.0, steady 37.1 | 56.4 | 44.4 |  |  |
| terrain_setup | 59.8 | 8.9 | 62.2 | 507 | fly 60.2, pan 59.9, sort 61.0, steady 9.1 | 44.6 | 37.2 | 0.0 | 22.8 |
| agg:entities_all | 19.7 | 18.2 | 45.3 | 2563 | fly 18.4, pan 19.8, sort 19.1, steady 45.0 | 21.6 | 12.6 |  |  |
| entities.loop.global | 16.2 | 14.4 | 37.1 | 2032 | fly 14.5, pan 16.4, sort 16.0, steady 36.3 | 17.7 | 10.4 | 0.0 | 0.0 |
| agg:terrain_layers | 12.6 | 11.6 | 28.2 | 1615 | fly 12.3, pan 12.1, sort 12.7, steady 27.9 | 11.5 | 7.1 |  |  |
| terrain.solid | 5.7 | 5.1 | 13.7 | 756 | fly 6.2, pan 5.2, sort 5.4, steady 13.4 | 6.9 | 4.2 | 0.4 | 1.3 |
| terrain.cutout_mipped | 2.5 | 2.2 | 5.2 | 271 (low) | fly 2.4, pan 2.4, sort 2.5, steady 4.9 | 1.9 | 1.1 | 0.4 | 2.5 |
| terrain.translucent | 2.3 | 1.9 | 4.4 | 253 (low) | fly 2.0, pan 2.3, sort 2.4, steady 4.4 | 1.8 | 0.9 | 0.2 | 2.3 |
| terrain.cutout | 2.3 | 1.8 | 5.4 | 284 (low) | fly 1.8, pan 2.2, sort 2.4, steady 5.1 | 0.9 | 0.8 | 0.3 | 1.1 |
| agg:tick | 1.7 | 1.3 | 3.6 | 155 (low) | fly 1.8, pan 1.6, sort 1.4, steady 3.1 | 15.1 | 38.6 |  |  |
| entities.loop.entities | 1.6 | 1.3 | 3.9 | 216 (low) | fly 1.4, pan 1.7, sort 1.5, steady 3.8 | 1.5 | 1.0 | 0.0 | 0.0 |
| pass.other | 1.5 | 1.3 | 3.3 | 160 (low) | fly 1.4, pan 1.5, sort 1.4, steady 3.1 | 1.5 | 1.0 | 0.3 | 3.0 |
| entities.draw | 1.4 | 0.7 | 4.0 | 119 (low) | fly 1.7, pan 1.1, sort 1.0, steady 3.6 | 2.0 | 1.0 | 0.4 | 8.2 |
| agg:particles_all | 0.9 | 0.2 | 2.8 | 50 (low) | fly 0.3, pan 1.2, sort 0.6, steady 2.5 | 1.1 | 1.2 |  |  |
| agg:alacer_frame | 0.9 | 0.5 | 1.8 | 92 (low) | fly 0.9, pan 0.9, sort 0.6, steady 1.8 | 1.0 | 0.4 |  |  |
| particles.unlit | 0.8 | 0.2 | 2.6 | 33 (low) | fly 0.2, pan 1.0, sort 0.5, steady 2.3 | 0.6 | 0.4 | 0.3 | 0.0 |
| camera_fog | 0.6 | 0.4 | 1.6 | 75 (low) | fly 0.6, pan 0.7, sort 0.5, steady 1.5 | 0.4 | 0.5 | 0.2 | 0.0 |
| tick.display_ticks | 0.6 | 0.5 | 1.2 | 60 (low) | fly 0.6, pan 0.6, sort 0.6, steady 1.1 | 4.7 | 14.3 | 0.0 | 0.2 |
| alacer.benchmark_driver | 0.6 | 0.3 | 1.3 | 60 (low) | fly 0.6, pan 0.6, sort 0.4, steady 1.3 | 0.8 | 0.3 | 0.0 | 1.1 |
| sky | 0.6 | 0.4 | 1.2 | 63 (low) | fly 0.5, pan 0.6, sort 0.4, steady 1.2 | 0.3 | 0.4 | 0.2 | 1.7 |
| tick.world | 0.6 | 0.3 | 1.4 | 49 (low) | fly 0.6, pan 0.5, sort 0.4, steady 1.1 | 5.2 | 12.7 | 0.0 | 8.6 |
| render.hud | 0.5 | 0.5 | 1.7 | 77 (low) | fly 0.5, pan 0.5, sort 0.5, steady 1.5 | 0.6 | 0.3 | 0.2 | 0.8 |
| tick.entities | 0.4 | 0.3 | 0.7 | 32 (low) | fly 0.6, pan 0.3, sort 0.3, steady 0.6 | 4.0 | 9.1 | 0.0 | 1.2 |
| tasks | 0.2 | 0.0 | 0.9 | 0 (low) | fly 0.9, pan 0.0, sort 0.3, steady 0.0 | 1.1 | 0.3 | 0.0 | 6.1 |

## Scene particles (reference): 4 launch/phase combinations, median swap 2.9% of frame time

| Region | Median % | Min % | Max % | Min samples | Per phase median % | Slow-1% frames % | Tick frames % | Native median % | Allocation median % |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| agg:world_render | 92.6 | 92.2 | 93.0 | 14710 | pan 92.8, static 92.5 | 42.3 | 35.9 |  |  |
| agg:render_pass | 92.5 | 92.1 | 92.9 | 14696 | pan 92.7, static 92.3 | 42.2 | 35.8 |  |  |
| agg:particles_all | 61.2 | 51.8 | 71.4 | 11339 | pan 52.5, static 70.4 | 62.1 | 65.4 |  |  |
| particles.unlit | 55.8 | 46.5 | 65.8 | 10416 | pan 47.3, static 64.7 | 19.0 | 17.0 | 77.0 | 27.4 |
| agg:terrain_all | 22.5 | 10.9 | 33.9 | 1734 | pan 33.6, static 11.3 | 15.4 | 12.7 |  |  |
| terrain_setup | 14.6 | 1.7 | 27.5 | 269 (low) | pan 27.2, static 2.1 | 11.8 | 9.9 | 0.0 | 0.3 |
| agg:entities_all | 11.9 | 9.9 | 14.4 | 2134 | pan 10.1, static 13.9 | 6.5 | 5.0 |  |  |
| entities.loop.global | 8.4 | 0.0 | 10.2 | 4 (low) | pan 8.4, static 5.1 | 3.6 | 3.2 | 0.0 | 0.0 |
| agg:terrain_layers | 7.8 | 6.4 | 9.2 | 1457 | pan 6.4, static 9.2 | 3.6 | 2.9 |  |  |
| agg:tick | 6.2 | 6.0 | 6.5 | 969 | pan 6.2, static 6.3 | 56.6 | 63.4 |  |  |
| tick.particles | 4.7 | 4.5 | 5.0 | 749 | pan 4.6, static 4.9 | 42.8 | 48.2 | 0.0 | 57.5 |
| terrain.translucent | 3.0 | 2.3 | 3.7 | 567 | pan 2.4, static 3.6 | 1.2 | 0.8 | 0.4 | 0.2 |
| terrain.solid | 2.8 | 2.1 | 3.3 | 530 | pan 2.2, static 3.3 | 1.5 | 1.3 | 1.3 | 0.1 |
| entities.loop.blockentities | 1.1 | 0.2 | 12.9 | 50 (low) | pan 0.2, static 7.4 | 1.6 | 1.1 | 0.8 | 0.1 |
| terrain.cutout_mipped | 1.1 | 0.9 | 1.2 | 176 (low) | pan 1.0, static 1.2 | 0.5 | 0.4 | 0.2 | 0.2 |
| entities.loop.entities | 1.0 | 0.8 | 1.2 | 171 (low) | pan 0.9, static 1.1 | 0.9 | 0.5 | 0.0 | 0.0 |
| terrain.cutout | 0.9 | 0.8 | 1.2 | 165 (low) | pan 0.9, static 1.1 | 0.4 | 0.3 | 0.6 | 0.2 |
| particles.lit | 0.7 | 0.5 | 0.9 | 129 (low) | pan 0.5, static 0.9 | 0.2 | 0.2 | 1.1 | 0.0 |
| pass.other | 0.6 | 0.6 | 0.7 | 95 (low) | pan 0.6, static 0.7 | 0.5 | 0.4 | 0.7 | 0.3 |
| agg:alacer_frame | 0.5 | 0.4 | 0.6 | 92 (low) | pan 0.5, static 0.6 | 0.6 | 0.3 |  |  |
| tick.display_ticks | 0.4 | 0.4 | 0.5 | 68 (low) | pan 0.4, static 0.4 | 3.3 | 4.2 | 0.0 | 0.0 |
| tick.alacer.benchmark_driver | 0.4 | 0.3 | 0.5 | 55 (low) | pan 0.5, static 0.4 | 4.1 | 4.5 | 0.0 | 9.2 |
| tick.world | 0.3 | 0.3 | 0.4 | 55 (low) | pan 0.3, static 0.4 | 3.5 | 3.6 | 0.0 | 1.0 |
| tick.entities | 0.2 | 0.1 | 0.2 | 21 (low) | pan 0.2, static 0.1 | 1.8 | 1.7 | 0.0 | 0.0 |

## CSV section means (ms per frame; ordinary profiler)

| Launch | Phase | Frames | frame | swap | tick | world | terrain setup | upload | solid | cutout m. | cutout | transl. | entities | particles | sky | clouds | gui | slow-1% frame | slow-1% swap | slow-1% upload | slow-1% tick |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| wc26-d0-r1 | static | 71880 | 0.556 | 0.340 | 0.012 | 0.181 | 0.013 | 0.000 | 0.027 | 0.014 | 0.016 | 0.010 | 0.065 | 0.006 | 0.006 | 0.001 | 0.004 | 1.905 | 0.779 | 0.000 | 0.477 |
| wc26-d0-r1 | pan | 66675 | 0.900 | 0.331 | 0.026 | 0.511 | 0.258 | 0.016 | 0.038 | 0.022 | 0.021 | 0.014 | 0.093 | 0.008 | 0.007 | 0.002 | 0.006 | 3.761 | 0.533 | 0.335 | 0.983 |
| wc26-d0-r1 | fly | 68767 | 1.163 | 0.285 | 0.035 | 0.805 | 0.270 | 0.272 | 0.046 | 0.027 | 0.021 | 0.014 | 0.099 | 0.013 | 0.007 | 0.004 | 0.007 | 4.723 | 0.335 | 1.229 | 0.776 |
| wc26-d0-r2 | static | 70440 | 0.568 | 0.331 | 0.013 | 0.197 | 0.014 | 0.000 | 0.030 | 0.015 | 0.017 | 0.010 | 0.072 | 0.006 | 0.006 | 0.001 | 0.005 | 1.876 | 0.373 | 0.005 | 0.922 |
| wc26-d0-r2 | pan | 68534 | 0.875 | 0.323 | 0.025 | 0.496 | 0.251 | 0.016 | 0.037 | 0.021 | 0.021 | 0.014 | 0.089 | 0.008 | 0.007 | 0.002 | 0.006 | 3.307 | 0.258 | 0.379 | 1.025 |
| wc26-d0-r2 | fly | 60369 | 1.325 | 0.261 | 0.044 | 0.975 | 0.334 | 0.338 | 0.054 | 0.029 | 0.022 | 0.015 | 0.121 | 0.013 | 0.008 | 0.005 | 0.009 | 5.142 | 0.273 | 1.233 | 0.956 |
| wc26-d200-r1 | static | 35961 | 1.112 | 0.488 | 0.043 | 0.546 | 0.022 | 0.000 | 0.036 | 0.016 | 0.018 | 0.012 | 0.388 | 0.007 | 0.008 | 0.002 | 0.006 | 3.561 | 0.415 | 0.009 | 1.920 |
| wc26-d200-r1 | pan | 50614 | 1.185 | 0.398 | 0.043 | 0.716 | 0.211 | 0.022 | 0.032 | 0.019 | 0.019 | 0.013 | 0.354 | 0.006 | 0.006 | 0.002 | 0.005 | 3.463 | 0.102 | 0.127 | 1.756 |
| wc26-d200-r1 | fly | 76809 | 1.042 | 0.300 | 0.030 | 0.679 | 0.240 | 0.217 | 0.041 | 0.026 | 0.020 | 0.013 | 0.080 | 0.004 | 0.006 | 0.003 | 0.006 | 3.994 | 0.276 | 1.268 | 0.666 |
| wc26-d200-r2 | static | 40151 | 0.996 | 0.466 | 0.034 | 0.467 | 0.016 | 0.000 | 0.029 | 0.014 | 0.016 | 0.010 | 0.336 | 0.006 | 0.007 | 0.002 | 0.005 | 2.877 | 0.199 | 0.004 | 1.667 |
| wc26-d200-r2 | pan | 46131 | 1.301 | 0.430 | 0.051 | 0.789 | 0.241 | 0.024 | 0.034 | 0.020 | 0.020 | 0.014 | 0.383 | 0.007 | 0.007 | 0.002 | 0.005 | 4.087 | 0.195 | 0.101 | 1.954 |
| wc26-d200-r2 | fly | 69819 | 1.146 | 0.322 | 0.035 | 0.753 | 0.257 | 0.256 | 0.043 | 0.026 | 0.021 | 0.014 | 0.089 | 0.005 | 0.006 | 0.004 | 0.006 | 4.249 | 0.325 | 1.316 | 0.733 |
| wc26-particles-r1 | static | 36323 | 1.101 | 0.044 | 0.059 | 0.966 | 0.022 | 0.000 | 0.034 | 0.015 | 0.016 | 0.034 | 0.111 | 0.692 | 0.006 | 0.002 | 0.007 | 5.041 | 0.247 | 0.003 | 2.815 |
| wc26-particles-r1 | pan | 38078 | 1.576 | 0.042 | 0.086 | 1.411 | 0.306 | 0.008 | 0.040 | 0.023 | 0.022 | 0.039 | 0.132 | 0.791 | 0.006 | 0.002 | 0.008 | 6.612 | 0.097 | 0.017 | 3.115 |
| wc26-particles-r2 | static | 40394 | 0.990 | 0.031 | 0.049 | 0.881 | 0.017 | 0.000 | 0.030 | 0.014 | 0.016 | 0.031 | 0.094 | 0.643 | 0.005 | 0.001 | 0.007 | 4.184 | 0.071 | 0.000 | 2.649 |
| wc26-particles-r2 | pan | 40012 | 1.500 | 0.040 | 0.079 | 1.346 | 0.282 | 0.009 | 0.038 | 0.022 | 0.021 | 0.037 | 0.123 | 0.768 | 0.006 | 0.002 | 0.008 | 6.008 | 0.080 | 0.029 | 2.957 |
| wc26-terrain-r1 | warmup-pan | 83000 | 0.723 | 0.297 | 0.017 | 0.384 | 0.188 | 0.010 | 0.030 | 0.019 | 0.019 | 0.012 | 0.070 | 0.006 | 0.006 | 0.001 | 0.004 | 2.217 | 0.234 | 0.200 | 0.887 |
| wc26-terrain-r1 | warmup-sort | 74565 | 0.858 | 0.293 | 0.023 | 0.513 | 0.226 | 0.066 | 0.035 | 0.020 | 0.021 | 0.013 | 0.091 | 0.003 | 0.007 | 0.002 | 0.005 | 2.985 | 0.232 | 0.611 | 0.798 |
| wc26-terrain-r1 | steady | 63532 | 0.630 | 0.377 | 0.015 | 0.213 | 0.017 | 0.000 | 0.030 | 0.015 | 0.017 | 0.009 | 0.084 | 0.006 | 0.007 | 0.001 | 0.004 | 2.070 | 0.731 | 0.004 | 0.648 |
| wc26-terrain-r1 | pan | 66609 | 0.901 | 0.357 | 0.027 | 0.486 | 0.248 | 0.005 | 0.036 | 0.021 | 0.021 | 0.014 | 0.095 | 0.007 | 0.008 | 0.002 | 0.005 | 3.183 | 0.444 | 0.065 | 1.227 |
| wc26-terrain-r1 | sort | 79226 | 0.808 | 0.274 | 0.020 | 0.486 | 0.220 | 0.058 | 0.034 | 0.020 | 0.020 | 0.013 | 0.083 | 0.003 | 0.006 | 0.002 | 0.005 | 2.799 | 0.213 | 0.573 | 0.685 |
| wc26-terrain-r1 | fly | 59090 | 1.354 | 0.278 | 0.044 | 0.988 | 0.343 | 0.337 | 0.055 | 0.029 | 0.023 | 0.015 | 0.121 | 0.012 | 0.008 | 0.005 | 0.008 | 5.427 | 0.313 | 1.296 | 0.972 |
| wc26-terrain-r2 | warmup-pan | 71359 | 0.841 | 0.322 | 0.023 | 0.466 | 0.232 | 0.013 | 0.034 | 0.020 | 0.020 | 0.013 | 0.088 | 0.008 | 0.007 | 0.002 | 0.005 | 3.152 | 0.442 | 0.198 | 0.943 |
| wc26-terrain-r2 | warmup-sort | 62357 | 1.026 | 0.310 | 0.031 | 0.649 | 0.290 | 0.094 | 0.043 | 0.022 | 0.022 | 0.014 | 0.113 | 0.005 | 0.008 | 0.003 | 0.007 | 4.092 | 0.241 | 0.676 | 1.054 |
| wc26-terrain-r2 | steady | 65224 | 0.613 | 0.370 | 0.014 | 0.205 | 0.017 | 0.000 | 0.030 | 0.014 | 0.017 | 0.009 | 0.077 | 0.006 | 0.006 | 0.001 | 0.005 | 2.016 | 0.721 | 0.000 | 0.566 |
| wc26-terrain-r2 | pan | 70605 | 0.850 | 0.351 | 0.024 | 0.447 | 0.225 | 0.005 | 0.034 | 0.020 | 0.020 | 0.013 | 0.085 | 0.008 | 0.007 | 0.002 | 0.005 | 2.962 | 0.384 | 0.154 | 1.056 |
| wc26-terrain-r2 | sort | 79898 | 0.801 | 0.297 | 0.019 | 0.458 | 0.211 | 0.050 | 0.033 | 0.019 | 0.020 | 0.013 | 0.075 | 0.003 | 0.006 | 0.002 | 0.005 | 2.487 | 0.204 | 0.496 | 0.673 |
| wc26-terrain-r2 | fly | 69632 | 1.149 | 0.292 | 0.033 | 0.786 | 0.273 | 0.259 | 0.045 | 0.026 | 0.021 | 0.014 | 0.095 | 0.011 | 0.007 | 0.004 | 0.007 | 4.530 | 0.262 | 1.290 | 0.761 |

## Share eligibility of regions and aggregates (protocol.md)

| Region | Scenes >= 10% | Scenes >= 20% | Slow-1% scenes >= 20% | Meets share or tail rule | CSV group | JFR > CSV in |
|---|---|---|---|---|---|---|
| agg:entities_all | d0, d200, terrain, particles | d200 | d0, d200, terrain | yes |  |  |
| agg:particles_all | particles |  |  | no |  |  |
| agg:render_pass | d0, d200, terrain, particles | d0, d200, terrain | d0, d200, terrain | yes |  |  |
| agg:terrain_all | d0, d200, terrain, particles | d0, d200, terrain | d0, d200, terrain | yes |  |  |
| agg:terrain_layers | d0, d200, terrain |  |  | yes |  |  |
| agg:tick |  |  | d200 | yes |  |  |
| agg:world_render | d0, d200, terrain, particles | d0, d200, terrain | d0, d200, terrain | yes |  |  |
| entities.loop.global | d200, terrain |  |  | yes | entities.global | d0, d200, particles, terrain |
| particles.unlit | particles |  |  | no | particles |  |
| terrain_setup | d0, terrain | d0, terrain | d0, d200, terrain | yes | terrain_setup |  |

## CSV corroboration (JFR estimate vs ordinary-profiler section, ms per frame; flagged when > 3x + 0.02 ms)

| Launch | Phase | Flagged groups (estimate / CSV ms) |
|---|---|---|
| wc26-d0-r1 | static | block_entities 0.089/0.021 |
| wc26-d0-r1 | pan | entities.global 0.095/0.000 |
| wc26-d0-r1 | fly | entities.global 0.123/0.000 |
| wc26-d0-r2 | static | entities.global 0.076/0.000 |
| wc26-d0-r2 | pan | entities.global 0.089/0.000 |
| wc26-d0-r2 | fly | entities.global 0.156/0.000 |
| wc26-d200-r1 | static | entities.global 0.145/0.000 |
| wc26-d200-r1 | pan | entities.global 0.114/0.000 |
| wc26-d200-r1 | fly | entities.global 0.105/0.000 |
| wc26-d200-r2 | static | entities.global 0.105/0.000 |
| wc26-d200-r2 | pan | entities.global 0.131/0.000 |
| wc26-d200-r2 | fly | entities.global 0.118/0.000 |
| wc26-particles-r1 | static | block_entities 0.137/0.029 |
| wc26-particles-r1 | pan | entities.global 0.133/0.000 |
| wc26-particles-r2 | static | entities.global 0.098/0.000 |
| wc26-particles-r2 | pan | entities.global 0.119/0.000 |
| wc26-terrain-r1 | warmup-pan | entities.global 0.062/0.000 |
| wc26-terrain-r1 | warmup-sort | entities.global 0.093/0.000 |
| wc26-terrain-r1 | steady | entities.global 0.090/0.000 |
| wc26-terrain-r1 | pan | entities.global 0.088/0.000 |
| wc26-terrain-r1 | sort | entities.global 0.087/0.000 |
| wc26-terrain-r1 | fly | entities.global 0.158/0.000 |
| wc26-terrain-r2 | warmup-pan | entities.global 0.083/0.000 |
| wc26-terrain-r2 | warmup-sort | entities.global 0.123/0.000 |
| wc26-terrain-r2 | steady | entities.global 0.090/0.000 |
| wc26-terrain-r2 | pan | entities.global 0.083/0.000 |
| wc26-terrain-r2 | sort | entities.global 0.079/0.000 |
| wc26-terrain-r2 | fly | entities.global 0.123/0.000 |

Sub-regions, tail excess, allocation objects, anchor exact/prefix counts and other threads are in metrics.json.
