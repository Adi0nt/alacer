# Entity pipeline follow-up (2026-10-25)

The recent default-on entity pipeline was inspected on Temurin Java 25.0.4.1 and
Windows/NVIDIA with 200 benchmark mobs and 200 chests. A client comparison toggled
`render.fastEntityPipeline` off (A) and on (B) in A/B/B/A order. Each static
segment lasted 1.5 seconds and each pan segment 2.25 seconds. Both runs used
1920x1080, a 2 GiB heap and the same seeded benchmark world. The first launch
reported 209 client entities; the second reported 210.

| Run | Phase | Entity loop mean, A -> B | Frame p99, A -> B | CPU/frame, A -> B |
| --- | --- | ---: | ---: | ---: |
| Before | Static | 0.818 -> 0.757 ms | 3.745 -> 4.444 ms | 1.994 -> 1.924 ms |
| Before | Pan | 1.045 -> 0.909 ms | 6.335 -> 7.398 ms | 3.755 -> 3.845 ms |
| After | Static | 0.682 -> 0.417 ms | 6.314 -> 5.332 ms | 1.464 -> 1.116 ms |
| After | Pan | 0.644 -> 0.386 ms | 7.866 -> 5.368 ms | 2.263 -> 1.724 ms |

The entity loop was faster with the pipeline in both launches. Whole-frame
latency, CPU and other sections changed direction between launches, so these
short runs cannot qualify a new default or prove a speedup from the follow-up
code change. The latter removes a `BlockPos` allocation only when the entity
chunk cache misses; settled benchmark frames rarely exercise that path. No
performance magnitude is attributed to it.

The verification mode previously passed a literal `true` for entity order.
It now compares the identity and order of every entity in the fast traversal
against the vanilla traversal. A separate live-client run with 200 mobs and
200 chests recorded over 10,000 verified passes and zero reported mismatches.
It completed static, pan and fly phases. This is a traversal check, not a
rendered-image comparison. Build validation passed 323 tests in 69 suites,
`remapJar`, and `git diff --check`.

Raw reports (ignored by Git) are under `run/alacer/benchmarks/` with labels
`gemini-entity-baseline`, `gemini-entity-verify` and `gemini-entity-after`.
The benchmark runs did not independently inspect screenshots or certify
visual identity, graphics-driver portability or a whole-client performance
benefit from the follow-up edit. Optimization defaults remain unchanged.
