# Session 17: configuration isolation and particle movement

Written before candidate measurements. Java 25 throughout. Preserve session 15/16 and
Java 8 archives; do not regenerate historical hashes from changed sources.

Benchmark configuration uses this build's defaults plus explicit JVM properties, including
startup mixin selection. It neither reads nor writes `config/alacer.properties`. Normal
launches retain their file settings. Record the config source and check every phase's
effective options, as well as the shared file's before/after SHA-256.

Movement control: vanilla `Entity.move`, collision gathering and callbacks, with the eight
shipped optimizations on. First collect a fresh intrusive particle-detail/JFR profile.
Candidate: only substitute particle movement's block-state reads with reads through the
existing world/column/generation checked chunk-reference cache. Do not cache block states,
collision shapes, entity lists, loaded flags or world-border state. Preserve every call,
collision ordering, axis resolution, callbacks, velocity and onGround update. Other entities,
world subclasses and non-owner threads use vanilla. Keep the candidate opt-in and outside
`render.defaults` until the gates below establish a dependable improvement.

Correctness gates: query-by-query identity comparison returning vanilla state, zero
mismatches; runtime collision-list and border-flag comparisons covering air, solid and partial
blocks, negative coordinates, chunk edges, build-height limits, hard world limits, moving world
border, unloaded columns, cache reset and column-generation changes. Exercise changing blocks
in the dedicated particle diagnostic. Build/unit tests are separate from client evidence.

Performance gates: warmed in-process A/B/B/A with candidate false/true, Java 25, 1920x1080,
display lists, view distance 12, particle scale 1 and 2, four fresh launches per scale with
scale order alternating by round. No detail timers, verification, diagnostics or JFR in
selection launches. Validate all other phase options are equal. Compare per-launch tick-frame
means/p95/p99, particle tick, client CPU, allocation and ordinary frame means/p95/p99 separately.
Do not pool millions of ordinary frames into a particle-tick claim. Require particle-tick
mean improvement in every launch, median >=2% at each scale, no median frame/tick p95
regression >5%, no median client CPU/allocation regression >2%, and particle populations
within 2%. Report every noisy/regressing metric. Failure or incomplete gates leaves the
experiment disabled; a narrow microbenchmark is not selection evidence.

Logs and raw CSV/JFR stay under ignored `build/` and `run/`. Archive commands, source and raw
hashes, exported summaries, diagnostic counts and analysis separately in this directory.
