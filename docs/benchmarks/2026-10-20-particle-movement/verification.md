# Movement correctness evidence

Build/unit tests and real client diagnostics are separate gates.

`pm-verify-1` ran Java 25, scale 1, changing particle diagnostic blocks and particles above
and below build height: 16,681,339 block reads compared, zero mismatches. This initial smoke
preceded the additional ordered-list probes; it is supplemental evidence, with its local log
in `build/pm-verify-1.log`, not the final measured source snapshot.

`pm-check-2` ran the final recorded prototype at scale 2, with changing emitter cells
(glowstone, slab, air) and height-limit particles. **23,537,260 movement block reads compared,
zero mismatches.** Verification compares block-state identity and returns vanilla's state.
The candidate caches no block states, so block changes remain visible on the next read.

Before emitting particles, a separate synchronous diagnostic disabled the verification
fallback and compared the candidate itself: **288 direct block-state reads and 200 ordered
collision lists and outside-border flags matched**. It covered negative chunk edges,
unloaded columns, build-height and hard coordinate limits, terrain/air intersections,
both initial border flags, resized/recentered client world borders, a cache reset and a
column-generation increment. The initial stationary border and option values were restored
in `finally`; synthetic particles were never inserted into the game world or manager.
The archive record retains the source snapshot, exact launch command, source/raw/log hashes,
config-file before/after hashes, and verification lines.

The implementation leaves vanilla's collision collection loops, loaded checks, world-border
mutation, entity collision queries, collision-box ordering, axis clipping, step/fall logic,
block callbacks, `onGround`, velocity and position assignments in place. It only substitutes
the block-state reads in `Entity.move`, `Entity.checkBlockCollisions` and `World.getCollisions`.
Only particles in the exact `ClientWorld` class on the owning client thread use cached chunk
references. The existing cache checks world identity, column and load/unload generation and
is cleared by the existing world replacement hook. Every lookup still reads the current
state from that chunk. Other entities, world subclasses and threads call the world method.

Limits: synthetic generation invalidation and cache reset are not actual network chunk
unload/reload or dimension replacement tests. The border checks resize/recenter a stationary
border; continuous time-based interpolation was not tested. There is no paired full movement
state replay, dedicated boat/entity-collision probe, visual gameplay inspection, or other-mod
compatibility certification. Another mod redirecting the same calls would conflict. These
remaining gates are reasons to retain the candidate as an explicit experiment even if a
subset of performance metrics improves. With the experiment disabled at startup, its two
mixins are absent; with the in-process toggle, both A and B contain the dispatch hooks.
