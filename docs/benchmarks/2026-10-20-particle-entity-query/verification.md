# Session 19 correctness and scope

The independent candidate is `render.scalarParticleEntityQuery=false`, outside
`render.defaults`. Its one World mixin is absent unless the option, verification or its
benchmark toggle requests it at startup. All eight shipped defaults are preserved. Sessions
17 and 18 stay false and their correctness/selection decisions remain separate.

The two adjacent redirects cover only `World.getCollisions`' grown bounds and nearby-entity
query. Returning the original input identity records that the pure `grown(.25,.25,.25)` was
scalarized. The immediately following query consumes that identity, independent of a toggle
change. It is never installed as an entity shape or returned to another caller. Scalar
bounds reproduce grown arithmetic and constructor Math.min/max. Vanilla loaded checks and
world chunk lookups execute in the same X/Z order; exact WorldChunk sections execute in Y
order with the original iterators. Shape tests precede exclude, NOT_SPECTATOR precedes
getParts, and parts preserve duplicates and order. Chunk/shape subclasses lazily materialize one shared immutable
bounds object per query and call their original virtual methods. No list pooling or retained geometry exists.

The owner is the client thread captured at Alacer initialization. Query inputs must be exact
Box, finite/ordered within +/-30,000,000 and attached to a particle in this same exact
ClientWorld. Other inputs/worlds/threads keep the original grown and getEntities calls.
No per-world state is retained; actual chunk/dimension transitions are nevertheless an
uncompleted integration gate. Geometry and entity data are always read live. No GL state
or resource ownership changes. The original block gathering, world-border behavior, final
collision appends, movement, intermediate shape setters, stepping and callbacks are intact.

Java 25 build passes all 65 tests. Config tests exercise disabled defaults, aggregate-preset
exclusion, independent startup gating and benchmark file isolation. These are build evidence,
not client, visual or performance evidence.

`pe19-check-2` (earlier diagnostic source) completed 3,412,892 ordered list/bounds comparisons
with zero mismatches, 10,302 direct checks and 5,760 paired movement steps. It is supplemental;
the final fixture adds positive multi-entity/duplicate-part/virtual-call trace comparisons.

`pe19v2-check-2` (superseded source) completed **3,588,226 ordered list/bounds comparisons,
zero mismatches**, **10,306 direct checks**, **5,760 paired movement steps** and one
explicit real boat collision-list assertion. It exercised 7,207 chunk-subclass fallbacks
and one direct shape-subclass predicate fallback; four additional live positive-list
fixtures compared subclass call traces with verification disabled. This version missed shared
query-object identity across multiple fallback calls and is not final correctness evidence.

The dedicated `pe19-identity-negative-check-2` regression launch failed on the original
implementation with `shared query Box identity across virtual fallbacks`. The corrected
implementation lazily materializes one local Box shared by every chunk/shape fallback in
a query; it keeps no bounds between queries. `pe19v3-check-2` passed the strengthened
fixture: **3,405,003 runtime comparisons, zero mismatches**, **10,306 direct checks**,
**5,760 movement steps**, one boat check, 7,207 chunk fallbacks and one direct shape fallback.
Four positive queries separately checked shared identity across two subclass callbacks,
result order/duplicates and call traces with verification disabled. The failed command,
source ZIP and log are retained in attempts/. See correction.md for superseded controls.

Final fixture direct checks include growth decisions at signed zero, subnormal, extreme,
NaN/infinity and inverted inputs; actual vanilla subclass dispatch and non-owner/non-particle
fallbacks; 10,000 actual scalar-predicate comparisons against Box.intersects; loaded/unloaded,
negative chunk, height and hard-coordinate query edges. It installs and removes temporary
entities synchronously in the disposable world. Positive list checks assert exact insertion
order, duplicate parts, exclude behavior, and recorded getShape/getParts/subclass-intersects
call order, with verification disabled so extra oracle calls cannot mask trace differences.

Paired movement reuses the Session 18 fixture design independently for this option: 5,760
steps compare all nonstatic/nonfinal primitive Entity/Particle fields using raw float/double
bits, all final bounds and the ordered intermediate setShape/fall/checkBlockCollisions trace.
Both execute the actual transformed Entity.move with the option false/true. A synchronous
floor/wall/slab/web fixture, step/noClip/cobweb variants, stationary resized/recentered borders,
initial border flags, negative chunk edges, unloaded/out-of-height positions and a real boat
collision are exercised. Cells, entities, border and config are restored in finally. Random
state is identically seeded and replay particles are not registered with the manager.

Limits: SyncedData contents and external sound/network/block effects are not exhaustively
observed. The traces do not instrument every block callback. No continuous border interpolation,
actual network unload/reload, dimension replacement or visual gameplay inspection is claimed.
Synthetic part entities establish the query algorithm's part behavior, not a full live dragon
integration. No arbitrary third-party mixin compatibility certification: other redirects of
the same calls conflict, and modifications inside the replaced World/WorldChunk query region
need integration. These limits prohibit selection even if a narrow performance gate passes.
