# Session 18: immutable particle Box allocations

Predeclared before implementation/measurements. Java 25 for Gradle, bytecode, tests and
launcher; 1920x1080, display lists, view distance 12, copied fixed-seed worlds. Preserve
shared config, Session 17 evidence/isolation and all eight shipped optimization defaults.
Session 17 cached movement reads stay false in both variants.

First inspect the actual Session 17 JFR with stacks scoped to ParticleManager.tickParticle,
then record a fresh intrusive baseline with source snapshot. Candidate scope: reuse immutable
vanilla Box values only at Entity.move's moved/expanded call sites when offsets are all
zero and constructor normalization/addition leave every coordinate bit unchanged. No mutable
Box pool, geometry cache, movement replacement or skipped callbacks. Restrict to client-owned
particles in exact ClientWorld and exact Box class; unsupported/unsafe inputs call vanilla.
Keep a new startup-gated, disabled option outside render.defaults.

Correctness: compare candidate to real vanilla Box results using raw double bits; include
positive/negative zero, nonfinite/extreme/subnormal coordinates, inverted BlockPos boxes,
and subclass fallback. Replay full movement in paired unregistered particles with identical
initial states and compare position, bounds, velocity, ground/collision/border flags and
callback-visible state. Exercise zero/negative-zero offsets, walls/floors/partial blocks,
step/noClip/cobweb paths, border changes, negative chunk edges, unloaded positions and boats.
Runtime verification allocates vanilla boxes for comparison but returns the candidate;
selection measurements disable verification/detail/JFR. Preserve collision ordering and
vanilla callback execution. State lifecycle/visual gaps explicitly; no promotion with gaps.

Performance: eight fresh launches, four per particle scale 1/2, alternating scale order by
round. Warm both paths, then A/B/B/A static and pan segments, all other effective options
equal. Report every launch, all-frame and tick-frame means/p95/p99/max, particle tick tails,
CPU/frame, allocation/frame and allocation/tick frame, populations and A1/A2 drift.
Require every launch particle-tick mean to improve, median improvement >=2% per density and
phase, no median ordinary-frame/tick/tick-frame p95 regression >5%, CPU/allocation-frame
regression <=2%, populations within 2%. Require median tick-frame allocation reduction >=2%.
Failure or missing correctness gates retains disabled experiment; no tick-only selection.

Archive exact commands, source ZIP/hash, raw exports/log/JFR hashes and config hashes.
Raw recordings/CSVs remain under ignored run/, launch logs under ignored build/.
