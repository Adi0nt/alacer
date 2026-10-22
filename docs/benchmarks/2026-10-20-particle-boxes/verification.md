# Session 18 correctness evidence

Java 25 build and all 64 unit tests passed (`build/pb18-build-final.log`). The pure guard's
tests include 100,000 seeded IEEE-754 bound pairs and both zero-offset signs; they do not
substitute for a transformed Minecraft client. Startup/config tests cover the independent
disabled option, exclusion from render.defaults, benchmark file isolation and hook gating.

The real client diagnostic `pb18v2-check-2` used the candidate itself (verification returns
the candidate, not a vanilla fallback): **14,447,580 eligible Box operations compared,
391,285 reused, zero mismatches**. Comparisons use all six raw double bit patterns.
Only 2.71% of compared operations reused a value; this is not an allocation saving claim.

Before emission, **5,406 direct checks** covered positive/negative-zero offsets and bounds,
subnormals, extreme finite values, infinity, NaN payloads, inverted BlockPos-constructed
boxes, actual Box.moved/expanded constructor normalization, subclass dispatch, exact identity
reuse, non-particle fallback and a non-owner-thread fallback. Unsupported cases invoke the
original virtual Box method. No new box state, world reference, geometry or pool is retained.

**5,760 paired movement steps** compared every nonstatic, nonfinal primitive Entity/Particle
field, including private cobweb, fire, border and fall state, using raw float/double bits.
They also compared all six final bounds and the ordered trace of intermediate shape
assignments, checkFallDamage inputs and checkBlockCollisions entry/exit. Probe random state
is seeded identically. Both particles execute the actual vanilla method, with the option
false/true respectively; they are not inserted into the manager or world.

The disposable world's synchronous fixture provides stone floor/wall, slab and web, restored
in finally. Cases cover zero/negative-zero and nonzero motion, onGround, stepHeight, noClip,
cobweb, initial border flags, recentered/resized stationary borders, negative chunk edges,
out-of-height and unloaded positions. A temporary real BoatEntity was inserted and removed
synchronously; one explicit collision-list identity assertion confirmed its bounds were
included, followed by the paired steps around it. Border settings and config are restored.

Retained failed attempt: `pb18-check-2` stopped on outsideWorldBorder inequality. Its boat
presence check had queried one replay particle and changed its flag before the pair began.
The correction uses a separate collision probe. Its source ZIP, command and failed log hash
are retained under attempts/; it contributes no passing evidence.

Limits: the callback trace is not instrumentation of every block callback; resulting primitive
state and intermediate shapes are compared, while callback execution remains vanilla.
SyncedData contents and external sound/network effects are not exhaustively compared.
There is no visual gameplay inspection, actual dimension replacement/chunk unload-reload
test, continuously interpolating border test or third-party-mod compatibility certification.
The Box candidate has no chunk cache or per-world retained data; this does not complete
Session 17's separate cached-read lifecycle gates. Collision-list collection/order is left
unchanged; this session adds full movement replay, not a new independent all-list ordering
implementation. These boundaries remain explicit even if performance gates pass.
