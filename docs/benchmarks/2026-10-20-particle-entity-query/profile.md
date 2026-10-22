# Session 19 source and JFR attribution

The mapped vanilla source JAR and inspected regions are identified in source-investigation.json.
Nonzero Y/X/Z movement boxes cross virtual setShape boundaries between ordered axis clips.
This session leaves those boxes and all step/border/callback behavior intact. The separate
candidate instead scalarizes the grown bounds used only by World.getCollisions' entity query.

Before implementation, pe19-profile sampled the grown site at 35.18 MB/static and 53.63 MB/pan
within scored particle-tick windows. Movement was 1.351/1.268 ms per tick frame with intrusive
detail. These observations justified an independent query-region experiment, not a default change.

## Final corrected-source profiles

All runs have matching final game-source snapshots, Java 25, scale 1, display lists and
1920x1080. All eight shipped optimizations are on; Sessions 17/18 experiments are off.
Detail/JFR are intrusive. Phase windows are aligned from CSV start_ns and the exported
profiler epoch, with its raw recorded uncertainty. Weights below are decimal MB estimates,
not exact bytes. Allocation/tick frame is an exact client-thread frame counter, not pure query bytes.

| Profile | Phase | Movement ms/tick frame | Particle tick ms | Allocation/tick frame KB | Grown sampled MB (samples) | Box sampled MB |
|---|---|---:|---:|---:|---:|---:|
| startup absent | static | 1.261 | 1.694 | 1865.6 | 0.00 (0) | 230.58 |
| startup absent | pan | 1.313 | 1.832 | 1871.8 | 0.00 (0) | 308.29 |
| hook installed, candidate off | static | 1.441 | 1.968 | 2176.6 | 41.08 (73) | 247.39 |
| hook installed, candidate off | pan | 1.370 | 1.798 | 2262.5 | 56.26 (86) | 364.67 |
| candidate on | static | 1.398 | 1.866 | 1898.4 | 0.00 (0) | 215.10 |
| candidate on | pan | 1.354 | 1.814 | 1973.7 | 0.00 (0) | 288.44 |

The startup-disabled final control had no grown samples, whereas the hook-only follow-up
sampled 41.08/56.26 MB at Box.grown <- ParticleEntityQuery.grow:45. The candidate also had
no grown samples. This is consistent with the redirect/dispatch boundary changing JIT inlining
or escape analysis, but neither that mechanism nor exact bytes eliminated is established.
Earlier startup-disabled baselines did sample grown, so its allocation behavior is not stable
across these launches. Absence of JFR samples alone is not proof of zero allocation.

The hook-only recording uses scalarParticleEntityQuery=false and its .verify=true solely
to install the startup hook. Eligibility is false, no scalar verification executes, and all
query results are vanilla. Its environment/config/command/source/JFR identities are archived.
This is attribution, not another correctness or performance selection control.

The final eight nonintrusive A/B/B/A launches compare A and B with the startup hook installed
in both. Their 12.32-13.97% tick-frame allocation reduction is real within that instrumented
comparison, but must not be presented as the same reduction against startup-disabled vanilla.
Cross-JVM intrusive timings above are not a paired speedup estimate. No causal overhead or
unmodified-client saving is claimed without separate nonintrusive startup controls.

## Remaining costs and decision

Expanded and nonzero axis moved boxes, final contract boxes, block positions, result lists
and iterators remain in the recorded particle-tick allocation stacks. Source tracing preserves
World.getCollisions block loops and border mutation, getEntities chunk loaded/lookups, Y section
order, NOT_SPECTATOR, getParts ordering and duplicate results. All callbacks and collision
append/clip order remain in the original movement method. No geometry/entity list cache exists.

Five completed v2 controls and its profiles are supplemental after the shared-fallback Box
identity defect was reproduced and fixed. Final v3 selection failed every group; it is not
rescued by the allocation profile. Next investigation should include startup-disabled and
hook-only nonintrusive controls and JIT-sensitive allocation behavior before a broader scalar
movement region. Such a replacement needs explicit intermediate-shape/step/callback gates.
