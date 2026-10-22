# Session 25 movement region results

Hooks-absent JFR sample shares (1 ms sampling, DebugNonSafepoints). Counts are samples, not times.

| Launch | Phase | Move samples | Move / client | pre | gather_bounds | gather | clip_publish | step | profiler_tail | position_flags | block_under | fall_damage | velocity_vertical_callback | step_sounds | block_collisions | water_rain | fire | fire_effects |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| mr25-jfr-1-r1 | static | 573 | 3.5% | 5.4 | 9.1 | 44.2 | 8.2 | 1.4 | 0.0 | 0.2 | 7.2 | 0.2 | 0.2 | 0.7 | 11.2 | 0.3 | 8.7 | 0.2 |
| mr25-jfr-1-r1 | pan | 922 | 3.6% | 2.5 | 10.5 | 46.2 | 7.9 | 0.8 | 0.0 | 0.2 | 4.3 | 0.8 | 0.2 | 0.7 | 11.1 | 0.3 | 11.9 | 0.1 |
| mr25-jfr-1-r2 | static | 550 | 3.3% | 3.6 | 8.4 | 42.5 | 9.6 | 1.5 | 0.0 | 0.4 | 5.3 | 0.0 | 0.4 | 0.2 | 10.5 | 0.5 | 12.9 | 0.0 |
| mr25-jfr-1-r2 | pan | 867 | 3.4% | 2.4 | 9.0 | 43.7 | 9.7 | 0.9 | 0.0 | 0.2 | 5.4 | 0.7 | 0.1 | 0.3 | 14.6 | 0.3 | 9.2 | 0.1 |
| mr25-jfr-2-r1 | static | 924 | 4.9% | 3.9 | 7.9 | 47.9 | 10.5 | 0.1 | 0.0 | 0.2 | 6.8 | 0.8 | 0.4 | 0.5 | 6.0 | 0.0 | 11.9 | 0.2 |
| mr25-jfr-2-r1 | pan | 1433 | 5.0% | 3.1 | 8.0 | 45.3 | 12.6 | 0.3 | 0.0 | 0.4 | 5.8 | 0.3 | 0.2 | 0.4 | 7.5 | 0.6 | 12.0 | 0.1 |
| mr25-jfr-2-r2 | static | 904 | 4.9% | 3.8 | 10.1 | 43.9 | 9.6 | 0.9 | 0.0 | 0.4 | 5.1 | 0.2 | 0.1 | 0.8 | 10.0 | 0.6 | 12.3 | 0.0 |
| mr25-jfr-2-r2 | pan | 1345 | 4.8% | 3.3 | 11.6 | 44.3 | 9.2 | 1.3 | 0.0 | 0.4 | 5.4 | 0.7 | 0.3 | 0.3 | 10.9 | 0.2 | 9.6 | 0.0 |

| Launch | Phase | All block-state reads | Reads with chunk-map frame | Any chunk-map frame |
|---|---|---:|---:|---:|
| mr25-jfr-1-r1 | static | 23.7% | 8.2% | 27.6% |
| mr25-jfr-1-r1 | pan | 22.1% | 6.8% | 29.1% |
| mr25-jfr-1-r2 | static | 26.2% | 7.3% | 27.8% |
| mr25-jfr-1-r2 | pan | 25.1% | 8.2% | 28.3% |
| mr25-jfr-2-r1 | static | 39.3% | 18.9% | 23.7% |
| mr25-jfr-2-r1 | pan | 40.3% | 19.9% | 24.5% |
| mr25-jfr-2-r2 | static | 23.7% | 8.6% | 28.3% |
| mr25-jfr-2-r2 | pan | 23.0% | 7.6% | 28.1% |

## Largest shares over all JFR launch/phase combinations

| Region or set | Median % | Min % | Max % | Meets 20% median / 15% min |
|---|---:|---:|---:|---|
| gather | 44.2 | 42.5 | 47.9 | yes |
| all_chunk_map_frames | 28.0 | 23.7 | 29.1 | yes |
| all_block_state_reads | 24.4 | 22.1 | 40.3 | yes |
| gather.column_check | 12.8 | 0.7 | 13.8 | no |
| fire | 11.9 | 8.7 | 12.9 | no |
| block_collisions | 10.7 | 6.0 | 14.6 | no |
| gather.block_read | 10.6 | 9.6 | 22.1 | no |
| clip_publish | 9.6 | 7.9 | 12.6 | no |
| clip_publish.all | 9.6 | 7.9 | 12.6 | no |
| gather_bounds | 9.0 | 7.9 | 11.6 | no |
| gather_bounds.all | 9.0 | 7.9 | 11.6 | no |
| block_reads_with_chunk_map_frame | 8.2 | 6.8 | 19.9 | no |
| gather.entity_query | 7.3 | 6.4 | 11.1 | no |
| gather.add_collisions | 7.1 | 5.8 | 7.7 | no |
| fire.block_read | 5.5 | 3.1 | 9.6 | no |
| block_under | 5.4 | 4.3 | 7.2 | no |
| block_under.block_read | 4.3 | 2.9 | 5.8 | no |
| block_collisions.block_read | 4.2 | 3.4 | 6.0 | no |
| pre | 3.5 | 2.4 | 5.4 | no |
| pre.all | 3.5 | 2.4 | 5.4 | no |
| fire.area_check | 3.3 | 0.4 | 4.2 | no |
| block_collisions.area_check | 2.8 | 0.1 | 4.6 | no |
| gather.border | 2.0 | 1.7 | 2.5 | no |
| noclip | 1.9 | 1.4 | 3.5 | no |
| noclip.all | 1.9 | 1.4 | 3.5 | no |
| block_collisions.loop_self | 1.5 | 0.9 | 2.2 | no |
| fire.bounds | 1.5 | 1.2 | 2.0 | no |
| gather.loop_self | 1.3 | 0.9 | 1.7 | no |
| gather.bounds | 1.1 | 0.7 | 1.8 | no |
| gather.entity_query_bounds | 1.0 | 0.5 | 1.2 | no |

## Region census (intrusive; includes mark costs)

| Launch | Phase | Region moves | ns/move raw | ns/move clock-adjusted | pre | gather_blocks | gather_entities | clip_publish | under_flags | block_collisions | water_rain | fire | tail |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| mr25-regions-1 | static | 569418 | 715 | 526 | 14.1% | 30.4% | 13.6% | 7.7% | 9.4% | 11.4% | 0.4% | 12.4% | 0.7% |
| mr25-regions-1 | pan | 857414 | 733 | 543 | 13.0% | 30.7% | 13.9% | 8.1% | 9.4% | 11.5% | 0.5% | 12.1% | 0.8% |
| mr25-regions-2 | static | 880059 | 768 | 573 | 13.8% | 29.8% | 12.8% | 9.2% | 9.7% | 11.6% | 0.6% | 11.8% | 0.6% |
| mr25-regions-2 | pan | 1328987 | 729 | 533 | 13.6% | 30.7% | 13.6% | 7.9% | 9.4% | 11.6% | 0.4% | 12.2% | 0.5% |

## Work per region-timed move (structural counts assume every column loaded)

| Launch | Phase | gather_columns_structural | gather_in_height_cells_structural | gather_block_boxes | entity_query_chunks_structural | entity_query_results | under_block_second_reads | vertical_collision_callbacks | block_collision_cells_structural | fire_cells_structural | fire_sources_found |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| mr25-regions-1 | static | 1.531 | 3.490 | 0.114 | 1.619 | 0.000 | 0.775 | 0.077 | 1.702 | 1.702 | 0.000 |
| mr25-regions-1 | pan | 1.525 | 3.472 | 0.112 | 1.619 | 0.000 | 0.775 | 0.077 | 1.694 | 1.694 | 0.000 |
| mr25-regions-2 | static | 1.551 | 3.550 | 0.144 | 1.620 | 0.000 | 0.740 | 0.098 | 1.696 | 1.696 | 0.000 |
| mr25-regions-2 | pan | 1.550 | 3.546 | 0.144 | 1.620 | 0.000 | 0.738 | 0.099 | 1.695 | 1.695 | 0.000 |

Full per-launch sub-regions, allocation weights and census values are in metrics.json.
