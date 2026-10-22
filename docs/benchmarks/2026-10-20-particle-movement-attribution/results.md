# Session 24 movement results

## Startup hook-only minus hooks-absent

| Pair | Phase | Frame | Ordinary frame | CPU/frame | Allocation/frame | Tick allocation | Frame p99 | Tick p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | static | +2.67% | +2.81% | +2.52% | +56.11% | +1.24% | +1.29% | -9.17% |
| 1 | pan | +12.24% | +12.48% | +11.65% | +29.38% | +1.60% | +20.22% | +34.69% |
| 2 | static | -0.26% | -0.34% | -1.54% | +5.48% | +14.81% | +3.74% | -22.56% |
| 2 | pan | -0.35% | -0.47% | +1.05% | +6.97% | +14.70% | +1.21% | +1.71% |

## Active movement census

| Scale | Phase | Moves | noClip | Positive stepHeight | Step queries | Ordinary queries | Mean list length | Gather / move wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | static | 702180 | 131595 | 0 | 0 | 570585 | 0.11 | 40.5% |
| 1 | pan | 1054013 | 200596 | 0 | 0 | 853417 | 0.11 | 41.0% |
| 2 | static | 1072495 | 192604 | 0 | 0 | 879891 | 0.14 | 41.3% |
| 2 | pan | 1614984 | 288778 | 0 | 0 | 1326206 | 0.14 | 41.9% |

Counts are client capture-window observations. Gather/rest times include probe costs; 3N/7N fields are structural scan estimates, not measured clip counts. Two startup pairs cannot certify equivalence. Full values, warnings and classes remain in metrics.json.
