# Session 18 source and allocation investigation

The actual `run/pm-profile-1.jfr` was decoded with JDK 25 jfr print, stack depth 64, and
filtered to the client thread and stacks containing ParticleManager.tickParticle. Its full
run had 1,358.58 MiB sampled particle-tick weight out of 4,362.87 MiB client weight. Box sites
under that scope included moved at Entity.move lines 493/501/507 (137.34/147.56/126.31 MiB),
expanded at line 486 (140.58 MiB), grown in World.getCollisions line 974 (141.75 MiB), and
contract at Entity.move line 684 (132.15 MiB). These include warmup/startup, are sampled weights,
and have no original source snapshot. Session 17 identities and that limitation are preserved.

Before changing game sources, `pb18-profile` captured a fresh source ZIP and recording. The
new analyzer maps scored CSV start_ns windows to JFR event wall time using the exported
profiler epoch (including its recorded uncertainty), instead of reporting full-run samples
as though they were all steady state. See baseline-jfr.json for all sites/counts and hashes.

| Fresh baseline, intrusive | Static | Pan |
|---|---:|---:|
| Tick frames | 199 | 300 |
| Particles/tick frame | 3,517.33 | 3,519.02 |
| Movement ms/tick frame | 1.428 | 1.366 |
| Particle tick ms/tick frame | 1.909 | 1.821 |
| Box share of sampled particle-tick allocation weight | 59.1% | 61.5% |
| Client execution samples | 513 | 736 |
| Particle tick execution samples | 36 | 40 |
| Entity.move inclusive samples within particle tick | 28 | 32 |
| World.getCollisions inclusive samples within particle tick | 11 | 15 |
| World.getEntities inclusive samples within particle tick | 5 | 1 |

The mapped vanilla sources were read from the checkout's Loom Minecraft source JAR, not a
third-party performance mod. Entity.move creates a query expansion, moves its immutable shape
separately on Y/X/Z, optionally performs stepping, and contracts the final shape for fire
checks. World.getCollisions grows its query for nearby entities and collects block/entity
boxes in order. Nonzero grown/contract operations change bounds and cannot reuse the same
immutable value. Position objects, lists/iterators, block callbacks and chunk/entity queries
also remain. No list, callback, world-border or loaded check is skipped by this candidate.

The bounded candidate tests whether redundant zero-offset operations occur often enough to
matter, without a mutable shape or collision/movement replacement. Its guard also checks
constructor normalization: the BlockPos constructor can produce inverted bounds, while
the double constructor orders bounds with Math.min/max. Adding +0 to a -0 coordinate can
change its raw bits. Exact class guards preserve subclass virtual dispatch; unsupported
values retain vanilla allocation. No Box fields are made mutable.

Dense verification reused only 2.71% of eligible moved/expanded calls. The first candidate
JFR is supplemental: it overlapped a Java build/test command, so its timings cannot establish
a paired saving/regression. It also sampled contract allocations in scored phases, where
the fresh baseline had none. Differences in JIT optimization or sampling are plausible;
absence of samples alone does not prove elimination, nor does this identify a precise
causal allocation regression. Nonintrusive A/B/B/A controls decide the performance gates.

The remaining nonzero movement boxes are a larger opportunity than zero-offset identities,
but reducing them would require a separately justified scalar collision-resolution region
or another replacement with intermediate-shape/callback/step/border/entity ordering gates.
These profiles do not authorize a mutable Box pool or a renderer default change.
