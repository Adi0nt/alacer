# Session 19 predeclared protocol

Written before implementation and Session 19 measurements. Java 25 is explicit for Gradle,
compiler/tests and launcher. Preserve all eight shipped defaults and shared config SHA-256
694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31.
Session 17 cached movement reads and Session 18 Box reuse stay false throughout.

Investigation: source tracing shows nonzero Y/X/Z boxes cross virtual setShape boundaries;
removing them would change intermediate observable bounds without a larger replacement.
Session 18 scored JFR attributes 40/48 MB sampled weight to the collision query's grown Box.
Record a fresh source-snapshotted baseline before implementation. Investigate a scalar
nearby-entity query region instead: avoid materializing its grown Box while preserving the
world loaded checks, chunk lookup order, entity section iteration, predicates/parts and result
identity/order. Retain vanilla movement, block collision gathering, world-border behavior,
Y/X/Z setters, steps and callbacks. Do not combine prior failed candidates.

Candidate render.scalarParticleEntityQuery defaults false, excluded from render.defaults,
independent startup hooks. Only client owner thread, exact ClientWorld, exact finite ordered
Box with bounded coordinates. Fallback to original virtual methods for unsafe values, other
entities/worlds/threads, chunk subclasses and shape subclasses. Scalar arithmetic must match
grown's additions and Math.min/max constructor normalization. No pool, geometry cache or
world/chunk/entity references retained between calls. No GL ownership changes. Guards and
fallbacks must preserve subclass dispatch; list/section/predicate ordering stays vanilla.

Correctness before selection: raw-bit bounds/predicate comparisons including signed zero,
NaN/infinity/extreme/subnormal/inverted inputs; ordered live entity lists compared against
real vanilla, returning candidate; paired full movement probes with intermediate setShape
and fall/block callback traces and primitive Entity/Particle state; partial blocks, walls,
steps, noClip/cobweb, border flags and boat collisions, unloaded/negative/chunk/height edges.
Owner-thread and non-particle fallbacks must be checked. Explicitly report limits for
SyncedData/external effects, continuously moving borders, actual unload/reload/dimension
replacement, visuals and arbitrary mod injections. Incomplete correctness retains disabled.

Performance: eight fresh copied-world launches, four per scale 1/2, alternating scale order
by round. Warm both paths before A/B/B/A static/pan. Java 25, 1920x1080, display lists,
view distance 12. All other options identical. No detail/JFR/verification during scored
selection. No builds/analysis competing with measurements. Retain every launch and failure.
Require every launch particle tick mean improves; group median saving >=2%; median
tick-frame allocation saving >=2%; median ordinary-frame mean/CPU/allocation regression
<=2%; median frame/tick/tick-frame p95 and p99 regression <=5%; populations within 2%.
Report ordinary/tick-frame/particle-tick means,p95,p99,max, CPU, allocation/frame and /second,
non-tick metrics and A1/A2 drift. Any group with absolute A1/A2 frame-mean drift >10% is
flagged noisy and cannot support an unqualified selection. Failure/missing gates retains
false; no promotion or default changes are authorized in this session.

Archive source ZIPs, command/environment/config snapshots, export/log/JFR hashes, implementation
and tooling. Never reuse historical labels. Raw CSV/JFR in ignored run/, logs in build/.
