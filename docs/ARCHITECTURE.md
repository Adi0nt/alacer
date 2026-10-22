# Architecture

## Layout

```
net.adiont.alacer
├── Alacer                  Client entrypoint; owns config and components; the only class mixins call
├── config/
│   ├── AlacerConfig        Properties file + -Dalacer.* overrides; immutable except runtime toggles
│   └── AlacerMixinPlugin   Applies profiling-only mixins only when they are enabled
├── profiler/
│   ├── FrameProfiler       Per-frame sections, phases and counters, allocation, chunk counts (client thread)
│   ├── ConcurrentTimings   Bounded worker completion totals, detached into client frame columns
│   ├── DirtyQueueAge       Per-slot diagnostic residency stamp; resets on origin/observation gaps
│   ├── MetricRing          Fixed-capacity column ring buffer (no allocation per row)
│   ├── SeriesStats         Percentiles / tail means / FPS conversions (pure, unit tested)
│   ├── JvmMonitor          Heap and GC sampling, once per second
│   ├── ProfilerHud         Overlay text, recomputed twice per second
│   ├── ProfileReport       Detached snapshot → JSON summary + CSV
│   └── JsonWriter          Minimal JSON output (no library dependency)
├── benchmark/
│   ├── BenchmarkRunner     Scripted camera paths in a fresh copy of a fixed-seed world; optional
│   │                       mob/chest population or particle scenario; optional in-process A/B segments
│   └── ParticleProbes      Verification-only particles for the removal diagnostic (session 16)
├── render/
│   ├── ChunkVisibility     Allocation-free chunk visibility search (optimization 1)
│   ├── ChunkFrustum        Exact one-corner-per-plane chunk predicate (session 12)
│   ├── ChunkPipelineTimings Opt-in build, queue, scheduling and upload attribution (session 13)
│   ├── EntityRendering     renderEntities phase timing and world-chunk lookup cache (optimization 2)
│   ├── EntityDrawDetails   Optional per-class draw-stage times and call counts
│   ├── EntityChunkSlot     Per-render-chunk cache fields (added to RenderChunk by a mixin)
│   ├── GlErrorChecks       Rate limit for vanilla's per-frame GL error checks (optimization 3)
│   ├── GlStateReads        CPU answers for the world render's matrix and viewport reads (optimization 4)
│   ├── CloudCache          Display list for fancy cloud geometry (optimization 5)
│   ├── EntityShadows       Entity shadow world queries through cached chunk references (optimization 6)
│   ├── ParticleLight       Particle light queries through cached chunk references (optimization 7)
│   ├── ParticleRemoval     Dead-particle removal by network id (optimization 8)
│   ├── KeyedRemoval        Single-pass, order-preserving removeAll for int-keyed equality (pure, unit tested)
│   ├── ParticleVertices    Direct particle vertex element writes (optimization 9)
│   ├── ParticleVertexWriter Vanilla's per-element ByteBuffer puts for the particle format (pure, unit tested)
│   ├── ParticleLightEntry  Disabled particle light-entry experiment: chunk check and position copy (session 21)
│   ├── ParticleLightReuse  Per-pass table of particle light answers by block position (session 22)
│   ├── ParticleLightRepeats Opt-in counter of particle light queries and distinct positions per pass (session 22)
│   ├── ParticleTimings     Particle tick/draw sections and counts; opt-in per-particle detail (session 15)
│   ├── ParticleMovement    Disabled cached block-read movement experiment (session 17)
│   ├── ParticleBoxes       Disabled immutable zero-offset movement Box reuse (session 18)
│   ├── ZeroOffsetBox       Pure IEEE-754/constructor guard for immutable value reuse
│   ├── ModelListBatch      Experimental face-upload batching within model display lists
│   ├── ModelListVerification Native feedback comparison of original and alternate lists
│   ├── RenderPasses        Timing for the world render passes (terrain layers, sky, clouds, ...)
│   ├── MatrixTracker       CPU copy of GL's modelview and projection stacks (pure, unit tested)
│   ├── ZeroTranslates      Skips model parts' translations by zero (measured, off by default)
│   └── GlCallStats         Counts GL calls made while drawing entities (profiling only)
└── mixin/                  One-line hooks, wrappers and accessors only
```

Rules the code follows:

- **Mixins stay thin.** They time a region, forward to a component, or expose a field. All
  logic lives in ordinary classes that can be read and reasoned about without Mixin knowledge.
- **No renderer replacement.** Optimizations replace specific hot paths, keep vanilla's data
  structures as the interface to the rest of the game, and pre-empt or wrap single calls rather
  than cancel whole methods, so other mods' injections into the same methods still run.
- **Every optimization has a toggle** (`render.*`), defaults to the measured-better setting, and
  ships with a `.verify` mode that checks it against vanilla while the game runs.
- **Client thread only** for everything that touches rendering state. The frame profiler is
  deliberately single-threaded. Opt-in chunk diagnostics publish worker duration/count pairs
  into fixed-size synchronized totals, drained only by the client; workers never access the
  frame ring. Exported files are written from detached snapshots.
- **Bounded memory.** Histories are fixed-size rings; per-chunk arrays are sized to the render
  distance and reallocated only when the renderer rebuilds its chunk storage.
- **Profiling costs nothing when off.** Per-call timing and GL call counting live in separate
  mixins that the mixin plugin does not apply unless `profiler.entityDetail=true` (the coarse
  shadow timer: `profiler.shadowTiming=true`; chunk diagnostics: `profiler.chunkDetail=true`;
  per-particle timers: `profiler.particleDetail=true`).
- **Hooks on hot paths do not allocate.** MixinExtras' `@WrapOperation` passes arguments to the
  original through `Operation.call(Object...)`, which boxes primitives and allocates an array
  per call unless the JIT removes it. For calls made thousands of times per frame, use
  `@WrapWithCondition` (or another form without the `Operation`) and check allocation per frame.

## Profiler design notes

- *Frame* = start of `Minecraft.runGame()` to the next start. This includes the frame-rate
  limiter's sleep, which is what the player perceives. Sections inside the frame are wall time
  on the client thread (CPU time unless the thread blocks in the driver). `swap`
  (`Display.update`) is where the driver blocks when the GPU is behind.
- Sections nest (for example everything under `entities` is inside `render`) and must not be
  added together. *Phases* are consecutive, non-overlapping sections inside one method; the
  stages of `renderEntities` are phases, bounded by vanilla's own profiler section changes.
- *Detail* timing wraps individual calls (`world.getChunk`, `shouldRender`, entity and
  block-entity draws) with `nanoTime()`. It costs roughly 30 ns per wrapped call, which is
  significant next to a 35-130 ns lookup, so detail runs are used for attribution and counts.
  Real savings are measured with detail off.
- Allocation is read with `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` once per
  frame. It is exact per thread and cheap on HotSpot; when unavailable the column is -1.
- Phase-level CPU time (client thread and whole process) is sampled at benchmark phase
  boundaries only, because Windows thread CPU clocks have about 15.6 ms resolution.
- "1% low" is the frame rate implied by the mean of the slowest 1% of frames, not the 99th
  percentile, because it reflects how bad stutters are rather than where they begin. Both are
  reported.

## Optimization 1: chunk visibility search

**Evidence.** A Java Flight Recorder profile of the scripted benchmark (Temurin 8u492,
1920x1080, render distance 12) showed `WorldRenderer.setupRender` at 51% of client-thread CPU
samples and roughly 80% of client-thread allocation: `Direction.values()` copies 33%,
`LinkedList` nodes 22%, the camera-chunk flood fill (`ChunkOcclusionGraph.getFaces`) 16%,
`RenderChunkInfo`/`EnumSet` 13%. The profiler's own counters agreed: about 430 KB allocated per
frame while the camera turns, versus 6 KB standing still.

**What vanilla does.** Whenever the camera moves or rotates at all, `setupRender` rebuilds the
list of visible render chunks with a breadth-first flood fill from the camera's chunk, crossing
chunk faces that the chunk's occlusion data says are connected, within view distance and the
frustum. Per visited chunk it allocates a queue node, an info object, an `EnumSet` (copied from
the parent's) and a fresh `Direction[]`. It also flood-fills all 4096 blocks of the camera's
chunk section every time.

**What Alacer does** (`render/ChunkVisibility`):

- Queue → `int[]` of storage slots (each slot is enqueued at most once per search).
- Visited → per-slot `int` stamp compared with a search counter, so nothing is cleared per
  search. `RenderChunk.setFrame` is still called so the chunk's own frame marker matches vanilla.
- Travelled directions → 6-bit mask per slot; source direction → one byte per slot.
- `RenderChunkInfo` → one pooled instance per slot, created once per chunk storage. Outside the
  search, the game only reads `info.chunk`; source direction and step are only read by the
  search itself. An access widener makes the class constructible.
- The camera section's open faces are cached and invalidated by `WorldRenderer.markDirty`. All
  block, light, chunk load and chunk unload notifications go through that method. The cache is
  also invalidated when the camera enters another block or the world chunk object changes, and
  it is never used for chunks that are not yet populated (see the audit below).
- The result is installed and `viewChanged` is cleared *before* vanilla's search, so vanilla
  skips only its search. Frustum capture, rebuild scheduling and other mods' hooks in
  `setupRender` all still run.

Traversal order, direction order, range checks and the point where a chunk is marked visited
(before the frustum test) all mirror vanilla, so the output list is identical, including its
order (which translucent sorting and rendering depend on).

### Audit (second session)

| Area | Finding |
|---|---|
| Open-faces cache invalidation | **Gap found and fixed.** `World.setBlockState` notifies render listeners (and therefore `markDirty`) only when the chunk `isPopulated()`, which requires its first client tick. A block change arriving in the roughly one-tick window after a chunk loads would not invalidate the cache, while vanilla re-reads blocks every search. The cache is now bypassed for unpopulated chunks. Every other client block change path (`setBlockStateFromPacket`, chunk data, chunk unload) ends in `markDirty`. |
| Concurrent occlusion publishing | Alacer reads `RenderChunk.compiled` exactly as vanilla does (a plain field also written by builder threads), so it introduces no new race. Verification now snapshots every slot's compiled-data reference before Alacer's search and compares after vanilla's. A comparison counts as inconclusive only if a reference actually changed. This is more precise than the previous publish counters (15 instead of 98 inconclusive comparisons in comparable runs) and needs no extra mixin. `RenderChunk.reset()` writes `compiled` directly, but only on the client thread outside the comparison window. |
| Pooled object lifetime | Per-slot state and pooled infos are rebuilt whenever `RenderChunkStorage.chunks` changes identity (render distance change, VBO toggle, F3+A, world change) and dropped on `setWorld`. A `RenderChunk` never changes slot within a storage. After a storage rebuild the installed list still holds the old storage's infos until the next search. Vanilla keeps its stale list too, and `reload()` sets `viewChanged`, so the next `setupRender` replaces it before anything renders. |
| World change, teleport, spectator, render distance | World change: `setWorld` resets all state. Teleport: `updateCameraPos` runs before the hook, and the slot mapping is recomputed from chunk origins exactly like `RenderChunkStorage.getChunk`. Spectator: `loadChunks` (`isSpectator()`) is honoured identically, including disabling smart culling inside opaque blocks. Render distance: `reload()` runs at the top of `setupRender`, before the hook. |
| Ordering | Unchanged. Verified element by element. |

**Verification.** With `render.fastChunkVisibility.verify=true`, both searches run and their
outputs are compared chunk by chunk. Results this session: 12,000+ and 16,000+ searches in two
runs, **0 differences with stable input**. Every difference observed (4 and 10) had a compiled
chunk reference change during the comparison.

## Optimization 2: world-chunk lookup cache in the entity loop

**Evidence.** Phase and detail timing of `renderEntities` (see BENCHMARKS.md):

- At low entity density the entity loop is two equal costs: one `ClientWorld.getChunk`
  hash-map lookup per visible render chunk per frame (about 1,400 lookups at render distance 12,
  67-132 ns each), and walking each chunk's entity section.
- With entities in view, drawing them dominates (61-81% of the loop at 200-1,000 mobs; about
  6.6-10.5 µs per drawn entity, of which the OpenGL calls themselves are about 1.3 µs, as
  counted in session 5). Visibility checks are minor.
- Block-entity gathering, the outline pass and global entities are negligible.

The lookup is the only per-chunk cost with a small, local fix, so it was chosen. Entity
bucketing was not implemented: it would address only the section walk (0.14-0.31 ms) at the
cost of tracking every entity's movement, and drawing dominates whenever entities are visible.

**Design** (`render/EntityRendering`, `mixin/EntityRenderPhasesMixin`):

- Each `RenderChunk` remembers the world chunk it last got, the column it was looked up for, and
  that column's load/unload generation.
- `ClientChunkCache.loadChunk` and `unloadChunk` bump the column's generation. A load can replace
  a chunk without an unload first, and that is covered too. Generations live in a 64x64 table
  indexed by the low bits of the chunk coordinates; collisions only cause extra, correct lookups.
- A cached reference is used only if the column, generation and world all match. The shared
  empty placeholder for unloaded columns is cached as well, because loading a chunk there changes
  the generation.
- Everything runs on the client thread, where client chunk loads and unloads also happen.
- Only the single `getChunk` call in the entity loop is wrapped (MixinExtras `@WrapOperation`),
  so other mods' hooks around it still compose.

**Verification.** With `render.fastEntityChunkLookup.verify=true`, vanilla's lookup also runs and
the results are compared by identity: **26 million lookups, 0 mismatches**. That run included
world load, 1,000 mobs, 200 chests and the flying phase with continuous chunk loading and
unloading.

## Optimization 3: rate-limited GL error checks

**Evidence.** `Minecraft.runGame` calls `logGlError("Pre render")` and `logGlError("Post
render")` every frame, each one `glGetError`. When the NVIDIA driver runs in its threaded mode,
each call makes the client thread wait for the driver thread: 0.50-0.79 ms per frame, 32-41% of
the frame. In the single-threaded mode the same checks cost about 0.001-0.01 ms.

**Design** (`render/GlErrorChecks`, `mixin/MinecraftMixin`, `mixin/GameOptionsMixin`):

- The two `logGlError` calls in `runGame` are wrapped (MixinExtras `@WrapOperation`, which also
  times them for the profiler). The startup checks in other methods are untouched. With
  `render.throttleGlErrorChecks=false` the wrapper calls vanilla's method unchanged.
- When on, both checks run in one frame per second (decided at the start of the frame) and are
  skipped otherwise. GL error flags stay set until queried, so an error raised in a skipped frame
  is reported by the next check. Up to 8 flags are read per check, in vanilla's three-line format
  on vanilla's logger, with the location extended by "checked about once per second by Alacer;
  the error may come from any frame since the previous check". Vanilla's `logGlErrors` switch is
  respected.
- **Pending errors and LWJGL.** LWJGL 2 calls `glGetError` and throws `OpenGLException` whenever
  the swap interval is set, including on display mode and fullscreen changes. Vanilla's
  every-frame checks normally consume errors first. The runtime call sites (the vsync option in
  `GameOptions.set`, `Minecraft.toggleFullscreen`, and the benchmark's own vsync change) now
  read pending errors first, if a check was skipped since the last read.
- **Fallback.** After the first real error is reported, checks run every frame again for the rest
  of the session (logged once). Setups that produce GL errors therefore keep vanilla's behavior
  and log output exactly.
- Runtime-toggleable, so it can be measured with in-process A/B.

**Verification.** With `render.throttleGlErrorChecks.verify=true`, a harmless `GL_INVALID_ENUM`
is raised in a skipped frame and the next read must report it (it does not trigger the
fallback): **68 injected, 68 reported, 0 missed** in a full benchmark run, one of them through
the vsync guard. The first verify run, before the guard existed, crashed in
`Display.setVSyncEnabled`, which is how the LWJGL behavior above was found.

**Result.** In single-threaded driver mode (50 in-process A/B launches) the checks drop from
0.002-0.01 ms to under 0.001 ms per frame, with no frame-time effect. In threaded mode (7
launches, with NVIDIA's Threaded optimization forced on) they drop from 0.8-1.3 ms to under
0.01 ms, but most of that wait (median 85%) moves into `render`: the client thread still waits
for the driver thread, at the next call that synchronizes. What remains is a small, consistent
saving: against the second A segment, frame time is 2.8% (static) and 1.6% (pan) lower in the
median, about 0.06 ms, with B faster in 10 of 14 comparisons. On by default since it costs
nothing measurable in either mode (see BENCHMARKS.md). The wait it moved sat in
`Camera.setup`; optimization 4 removes it.

## Optimization 4: CPU-side camera matrices

**Evidence.** With the error checks rate-limited, the wait they no longer pay lands in
`Camera.setup` (0.10 → 0.8-1.3 ms per frame in the threaded driver mode; see BENCHMARKS.md
session 4). Each world render reads GL state five times: `Camera.setup` reads the modelview,
projection and viewport (for `gluUnProject` of the screen centre), `Frustum.getInstance()` reads
projection and modelview, and `new FrustumCuller()` calls `Frustum.getInstance()` again from
unchanged state. Whichever read comes first waits for the driver thread to finish everything
queued; the later ones are nearly free (about 0.01 ms together). `RenderChunk.glTransform`
(every chunk reposition) builds a constant matrix in GL and reads it back.

**Design** (`render/GlStateReads`, `render/MatrixTracker`, `mixin/GlStateManagerMixin`,
`mixin/GameRendererMixin`, `mixin/CameraMixin`, `mixin/FrustumMixin`, `mixin/RenderChunkMixin`):

- `GameRenderer.setupCamera` loads both matrices with the identity before building them, so
  their earlier contents never matter. From its start until the frustum culler exists,
  `MatrixTracker` follows every `GlStateManager` matrix operation (mode, identity, push/pop,
  translate, rotate, scale, multiply, ortho; float and double variants) with the same arithmetic
  and order as the GL specification. The `gluPerspective` call in `setupCamera`, which calls
  `glMultMatrix` directly, is wrapped and replayed with LWJGL's own float arithmetic. Outside
  that window each hook is one static field check. The viewport is the last one set through
  `GlStateManager.viewport` (which the world render does right before).
- The individual reads inside `Camera.setup` and `Frustum.compute` are wrapped, and answered
  from the tracker only during the world render, so `gluUnProject`, the frustum plane
  arithmetic and any other mod's hooks in those methods still run unchanged. A matrix the tracker
  does not know (popped past the start of tracking, or pushed deeper than 32 levels) is read
  from GL as in vanilla.
- `new FrustumCuller()` in the world render becomes `new FrustumCuller(<the frustum computed
  just before>)`: the same instance with the same values, without computing it again.
- `RenderChunk.glTransform`'s read gets the matrix computed on the CPU (identity, translate −8,
  scale 1.000001, translate 8). The push, transforms and pop still run.
- **Periodic check and fallback.** Code that changes the matrices with direct `GL11` calls
  inside `setupCamera` would not be seen. In the frame where the rate-limited error checks run
  (about once per second; that frame waits for the driver anyway), the real values are read,
  used and compared. After a difference beyond a relative 1e-4, the mismatch is logged and the
  reads go to OpenGL every frame for the rest of the session. Wrong matrices can therefore last
  at most about a second, as wrong frustum culling and a slightly offset camera position for
  sounds and particles.
- Runtime-toggleable, so it can be measured with in-process A/B.

**Verification.** With `render.avoidMatrixReads.verify=true`, every read also goes to OpenGL,
is compared and the real value used: **80,000+ camera and frustum reads, 0 mismatches**, largest
relative difference 9.5e-7; **38,000 render chunk matrices, bit-identical**; no frame with an
unknown matrix. The fallback was exercised with a build that deliberately skipped the
`gluPerspective` replay: detected at the first check, logged, and reads returned to OpenGL.

**Result.** In the threaded driver mode (9 in-process A/B launches) frame time drops by 35%
(static) and 38% (pan) against the second A segment (medians; B faster in all 18
comparisons), for example 2.61 → 1.63 ms. The wait is gone rather than moved: client-thread CPU
time falls by about as much (the driver waits by spinning), and the client thread now spends its
remaining wait in the swap (0.08 → 0.25 ms), bounded by the driver thread's throughput. p99 and
1% lows do not improve; the slowest frames are those with a client tick. In the single-threaded
mode (2 launches) there is no measurable effect, as expected for reads that cost about 0.01 ms
there. On by default.

**What still synchronizes.** In a steady frame with both options on, no call that returns GL
data is left: the error checks and the matrix check run in one frame per second. Remaining
synchronizing calls are occasional: `glGenLists` (followed by `glGetError` in
`MemoryTracker`) when display lists are allocated (models drawn for the first time, chunk
storage rebuilds), `glGenTextures` when textures are created (skins, maps), framebuffer status
checks on resize, `glReadPixels` for screenshots, and three `glGetString` calls per frame while
the F3 screen is open (not measured).

## Measured, off by default: translations by zero in model parts

**Evidence.** Detail runs now count every OpenGL call made while the entity loop draws an
entity (`render/GlCallStats`, see BENCHMARKS.md session 5). At 200 mobs: 75 calls per drawn
entity, 4,400 per frame. More than half are matrix operations, because `ModelPart.render`
brackets every part with transforms; 2 of them per part translate by the part's
`translateX/Y/Z` offset and back, which is zero for every part of the benchmark's mobs: 15 of
75 calls per entity do nothing. `GlStateManager` already skips about 30% of state requests;
most remaining state changes are toggles the next entity undoes (cull, rescale normal, active
texture unit, the shadow's blend, depth mask and texture), and each shadow is an immediate draw
of about 14 calls.

**Design** (`render/ZeroTranslates`, `mixin/ModelPartMixin`): every `GlStateManager.translatef`
call in `ModelPart.render`, `renderForceTransform` and `transform` gets a condition
(MixinExtras `@WrapWithCondition`) that skips it when all three arguments are zero. A
translation by zero leaves every element of a finite matrix unchanged (`m[12+i] + 0`), so the
result is exactly vanilla's. Other callers of `translatef`, and other mods' conditions on the
same calls, are unaffected. Runtime-toggleable. A first version used `@WrapOperation`, whose
argument boxing added 65-107 KB of allocation per frame at 200 mobs, in A and B alike.

**Verification.** With `render.skipZeroTranslates.verify=true` every translation is issued, and
in the frame where the rate-limited error checks run up to 16 zero translations are bracketed
by reads of the current matrix: **500+ compared, 0 changed it**.

**Result.** The option removes exactly the zero translations (20% of entity GL calls) and
nothing else, but frame time does not change measurably in the threaded driver mode (3 clean
in-process A/B launches: +1.7%/+1.7% against a2 at 200 mobs, −1.8%/−0.7% at 1000 mobs; one
more launch disturbed). The client thread saves about 17 ns per skipped call (0.07 ms per frame
at 1000 mobs), and the swap wait at 1000 mobs (0.7 ms) does not shrink, so the driver thread
is not limited by these calls either. No launch landed in the single-threaded mode. **Off by
default**; kept as a verified, exact building block. Entity drawing costs 6.6-10 µs per entity,
and the GL calls themselves account for only about 1.3 µs of it (75 × 17 ns); the rest is
Java work in the renderers.

## Optimization 5: cached fancy cloud geometry

**Evidence.** The pass sections added in session 6 put fancy clouds at 0.47-0.48 ms per
frame: 64 tiles, 5,376 vertices per pass, two passes and 128 `Tesselator.end` calls. The
fractional cloud cell and camera height translate those vertices; only the integer cloud cell,
the top/bottom face predicates and the color change their shape or vertex attributes.

**Design** (`render/CloudCache`, `mixin/CloudsMixin`, `render.cacheClouds`): after vanilla's
cloud scale, build the geometry with fractional offsets and height zero, batch its tiles into
one display list, then draw it with `translatef(-fracX, height, -fracZ)` in each of the two
passes. A primitive `@ModifyConstant` hook replaces the outer tile loop's initial value with
a value past its end after drawing the list. Vanilla's texture binding, blend state, scale,
depth/color masks, pass loop and cleanup still run. Flat clouds use vanilla. No whole-method
cancellation or primitive-argument `@WrapOperation` is used.

The cache key is the wrapped integer cloud cell, both height predicates (`j > -5`, `j <= 5`),
the exact post-anaglyph color floats, cloud mode and anaglyph pass. Camera movement within a
cell and height band only changes the translation. Drift crosses a cell every 20 seconds when
stationary. Color transitions can rebuild every frame; stereo changes passes every eye and
also rebuilds. World changes/disconnect delete the list and invalidate it. The bound cloud
texture is not recorded in the list, so resource reloads use the new texture normally.

The cache reuses vanilla's cloud-color local and tesselator buffer. Its fixed vertex array
(at most 8,704 vertices / 244 KB), tile offsets and four-byte comparison scratch buffer are
allocated once. No new per-frame objects or containers are needed. `cloud_rebuilds` counts
compilations in every profiler export, independent of entity detail profiling.

**Verification.** `render.cacheClouds.verify=true` maintains the list but lets vanilla draw
both passes. Before every vanilla tile upload it compares the vertex count and all attributes
against the corresponding cached vertices plus the current translation. Positions allow
0.0001 cloud units (two float ULPs at extreme heights); texture coordinates, color bytes and
normal bytes must match exactly. Unwritten padding is ignored. Reports include vertex/size
differences, largest position error, rebuilds, and masks of exercised height bands and stereo
passes. `benchmark.cloudSweep=true` adds diagnostic paths through all three height bands,
positive/negative texture wraps, dusk/rain, stereo and cloud-mode transitions. See session 7
in BENCHMARKS.md for runtime verification and measured A/B results.

The diagnostic sweep compared 275,956,224 vertices with zero differences beyond tolerance,
including all three height bands and all stereo passes; maximum position error was
0.000003815 cloud units. Three paired 200-mob launches reduced the cloud section by 97-98%
to about 0.01 ms. Median frame reductions against pooled controls were 16% static and 21%
pan, with substantial segment drift. Session 8 confirmed the global driver setting was On
before those tests; low process CPU use alone did not establish a single-threaded mode. Their
wall-time baseline still differs substantially from session 6, so cross-session comparisons
remain inappropriate.
**On by default**, based on the consistent section reduction and positive paired results.

The shape expressions intentionally match vanilla. Mods that alter its local variables or
tile-loop geometry need integration; verification exposes those differences. This comparison
checks generated geometry and attributes, not framebuffer identity.

## Entity draw-stage profiling (session 8)

`profiler.entityDetail=true` now enables `render/EntityDrawDetails` through three thin,
profiling-only mixins: `LivingEntityDrawMixin`, `EntityModelAnimationMixin` and
`EntityShadowDetailMixin`. `profiler.entityStages=false` at startup omits these extra mixins
for an instrumentation control. Both switches are recorded in exports; with entity detail
off, none of their hooks is applied. Invalid startup flags use the same defaults as config
parsing, so plugin decisions and exported settings agree.

The new `entity_*` sections measure living-renderer setup, model preparation and animation,
primary `renderModel` and its `Model.render`, layers, overlay state, name-tag eligibility/draw,
shadow work, per-block shadow geometry and shadow world queries. Animation call sites cover
the living renderer and the quadruped/wolf models used by the benchmark, including layer
models. This is not universal animation coverage for every possible modded model.

These durations are **inclusive**. Model draw is inside renderModel; layer animation and
overlay work are inside layers; shadow queries can also be inside per-block shadow work.
Only setup + preparation + renderModel + layers + shadow + name-tag are disjoint; their sum
is below entity draw, whose remainder includes dispatcher work, first animation setup and
renderer cleanup. The analysis checks these parent/child and top-level invariants on every
recorded frame, and every recorded draw phase reported zero incomplete spans.

The component uses fixed timer/depth arrays and one primitive row per observed entity class,
cleared between benchmark phases. New hooks forward primitive state directly without
`Operation.call` boxing. Exceptions that vanilla catches clear unfinished spans before the
next entity; partial spans are counted, not silently merged into a later entity. Per-class
microseconds and call counts are logged only at phase boundaries. Existing entity-detail
wrappers still have allocation/JIT effects, so allocation results and timer controls must be
considered alongside attribution; detail measurements are not normal-play FPS estimates.

At 200 mobs, instrumented ground-level shadow work averages 6.4-6.7 us/draw and the primary
model 5.2-5.6 us. At 1,000 mobs, most visible mobs are beyond the shadow distance and the
primary model dominates (5.2-5.8 us versus 1.6-1.7 us shadows). Layers average 1.3-1.6 us,
mostly sheep fur. Animation setup and name checks are each about 0.1 us; skipping a duplicate
animation setup is not the next substantial saving. BENCHMARKS session 8 records controls,
class breakdowns and the sampling cross-check before selecting a concrete optimization.

## Model-part display-list experiment (session 9)

`render.batchModelLists` retains the original display list and builds an alternate with one
quad-array draw for the part. `ModelPartBatchMixin` observes `compile` and selects only the
argument of existing `callList` sites in `render` and `renderForceTransform`. It does not
replace render methods. Animation, matrix operations, visibility, children, layers and the
original `glList` field remain vanilla. A changed list argument/source identifier is respected.

`ModelUploadMixin` captures the buffers that vanilla actually uploads, before they are cleared.
Only exact `ModelPart`, `Box` and `Polygon` classes qualify. The observed stream must contain
exactly six four-vertex `ENTITY` quads per box, with the expected format and draw mode. Other
streams use the original list. Both original compilation and its hooks still execute. The
alternate concatenates unchanged raw vertex words; padding is carried along but not used.

The hot selector uses primitive arguments and allocates nothing. Capture storage is fixed at
1,024 faces (96 KiB). A fixed 512-slot table weakly owns parts; alternate lists belonging to
collected parts are reclaimed on the next compilation. Beyond the cap, compilation falls
back to vanilla. Recompilation reuses the alternate identifier, or deletes it when the new
stream is unsupported or verification fails. Vertex storage is shared, not retained per part.
No new model or upload hooks are applied when the option and verification are disabled at
startup, except when `benchmark.toggle` explicitly requests this experiment. Runtime A/B
then switches between already-compiled lists without adding compilation to B segments.

`render.batchModelLists.verify` sends both lists through native OpenGL feedback under three
rotated/nonuniform transforms, including a negative scale. It compares every returned float
(position, lit color, texture coordinates and polygon records) exactly, plus final current
normal and texture coordinates. Feedback overflow, empty output or any difference rejects
the alternate. Matrix stacks, matrix mode and GL attributes are restored. This independently
exercises the driver list contents; it is more than comparing the copied CPU array to itself.
`benchmark.modelListFault=true`, only with verification, perturbs one uploaded vertex as a
negative control. All verification/sampling runs are excluded from performance selection.

The comparison establishes tested native geometry/attribute parity, not framebuffer identity,
all lighting states or compatibility with arbitrary mixins that add non-upload commands to
vanilla compilation. Subclass/stream guards cannot detect every such injection. The retained
original list and disabled default provide an explicit fallback. See BENCHMARKS session 9
and its archive for measurements and the final selection. The six paired launches did not
show a dependable overall frame-time benefit, so the option stays off. Two full native
verification runs compared 192 outputs / 98,280 floats with zero differences; the deliberately
perturbed vertex was detected in three views, and disabled-startup smoke compiled no alternates.

## Phase 10: combined comparison harness

The benchmark-only `render.defaults` selector sets the five shipped optimizations together:
visibility, entity chunk lookup, GL error-check throttling, matrix reads and fancy clouds.
A is all five off; B is the shipped defaults. Both experimental features stay off. A unit test
compares B with freshly loaded defaults and checks every non-verification render option is
false for A, catching future drift between the preset and the defaults. The selector is not
persisted as a new feature and does not alter normal-play defaults or render algorithms.

Both paths warm up before the scored static and pan A/B/B/A segments. Toggles apply before
settling. Each phase stores a detached configuration snapshot, used in both its profile and
the combined summary. A config change during capture aborts the benchmark. Recorder setup
allocations/logging occupy one excluded frame; raw-row capture and CPU/wall clocks start at
the following completed-frame boundary. Metadata reports the discarded setup count, target
and observed duration. Phase-end log output is outside the sampled CPU interval.

This compares vanilla render implementations with Alacer defaults in the same instrumented
client, avoiding separate-JVM confounding. Hooks and common profiling remain present on both
sides; it is not a completely unmodified-client performance measurement. Phase 10 uses fresh
runs and does not reinterpret the unchanged older archives. Across nine launches (0, 200 and
1,000 added mobs, three each), B had a lower frame time than A and than the later A2 control
in every static and pan pair. Median FPS gains were +15.7% to +68.9%, smallest with 1,000 mobs
and largest with none. See BENCHMARKS and the `2026-10-19-phase10-vanilla` archive for the
per-population numbers, one launch's different GL timing state and the limits.

Session 11 added `render.fastEntityShadows` (on by default) to the preset, so A/B with
`render.defaults` now switches six optimizations. Phase 10's archive measured five; its
`analyze.py` checks the Phase 10 sources and option set and reproduces only against them.
Session 15 added `render.fastParticleLight` (on by default), so the preset switches seven;
session 16 added `render.fastParticleRemoval`, so it switches eight; session 20 added
`render.fastParticleVertices`, so it switches nine.

## Optimization 6: entity shadow world queries

**Evidence.** With entity detail off, an in-process A/B that turns the game's Entity Shadows
option off (benchmark-only `benchmark.disableEntityShadows`) lowered frame time by a median
6.7-17.1% across 200 and 1,000 mobs. A coarse timer (`profiler.shadowTiming`) put all of
`renderShadow` at 0.19-0.25 ms per frame for 54-60 shadows; only mobs within 16 blocks get one,
so the count barely depends on population. For every block position under a shadow (8 for a
wolf, 18 for a pig, cow or sheep standing on a block centre) vanilla calls
`World.getBlockState` for the block below and `World.getRawBrightness`, and for each drawn quad
`World.getBrightness`, which repeats the raw brightness. Each starts with a `ClientChunkCache`
hash lookup (`getRawBrightness` does two, since it reads the block state first): three to five
per position. Blocks with light opacity 0 (tall grass, flowers), translucent blocks, slabs,
stairs and farmland are neighbor-lit: their raw brightness reads five neighbors, allocating a
`BlockPos` for each.

**Design** (`render/EntityShadows`, `mixin/EntityShadowMixin`, `render.fastEntityShadows`):

- The three calls are `@Redirect`ed to `EntityShadows`, which computes vanilla's answers with
  vanilla's range checks (±30,000,000 horizontally, build height 0-255 with the same clamp and
  out-of-range values) and calls the same chunk methods: `WorldChunk.getBlockState`,
  `WorldChunk.getLight(pos, world.getAmbientDarkness())` and the dimension's brightness table.
  Neighbor-lit blocks get vanilla's maximum of up, east, west, south and north, each with
  vanilla's non-neighbor rules; one reused mutable position replaces the five copies (the chunk
  only reads its coordinates).
- Only the chunk lookup is replaced: a four-entry reference cache indexed by the low bit of each
  chunk coordinate, so the at most 2x2 columns under one shadow never evict each other. An entry
  is used only for the same world object and column and only while the column's client
  load/unload generation, the table optimization 2 maintains, is unchanged. The shared empty
  chunk of unloaded columns is cached like any other (a load changes the generation). Entries
  are dropped on `WorldRenderer.setWorld`, so no old world is retained.
- Nothing else is cached. Block states and light are read from the chunk on every query, so
  moving entities and block or light changes (which update chunk sections in place) are seen
  exactly when vanilla sees them. The client sets `ambientDarkness` only when its world is
  created; it is read on every query anyway.
- Only the exact `ClientWorld` class is answered this way; subclasses keep their own
  behavior. Everything runs on the client thread, where client chunks load and unload too.
- `@Redirect` rather than `@WrapOperation`: about 2,000 calls per frame at 200 nearby mobs, and
  in pilot launches `Operation.call`'s argument arrays and `Float` boxing were not always
  removed by the JIT, adding up to 38 KB per frame to the disabled path. A second `@Redirect` of
  the same three calls by another mod would conflict; wrappers and injections around them still
  compose (session 8's stage timers attach unchanged). Runtime-toggleable.

**Verification.** `render.fastEntityShadows.verify=true` also runs vanilla's query for every
answered one, uses vanilla's result and compares (block states by identity, light exactly,
brightness by float bits): **23.3 million queries at 1,000 mobs and 19.1 million with walking
AI mobs while cells inside the shadows cycled through glowstone, slab and air
(`benchmark.shadowDiagnostic=true`), 0 mismatches.**

**Result.** In-process A/B (six launches, detail off): entity loop lower in 12/12 pairs by
0.04-0.19 ms, allocation lower by 19-22 KB per frame, shadow section 20-36% lower with the timer
on. Frame time was lower in all six 200-mob pairs (median 5.7% static, 6.9% pan), with no
dependable change at 1,000 mobs, where the swap wait absorbed the saving; in a launch with a
different GL timing state the entity loop did not change at all. The A path ran slower in that
series than in the attribution launches (the JIT did not remove vanilla's `BlockPos` copies),
so against the attribution's A the shadow section saving is about 0.05 ms per frame (25-30%).
**On by default**: exact, better on every CPU-side measure, never dependably worse. See
BENCHMARKS session 11. What remains of a shadow is the chunk reads themselves, the quad
geometry, state changes and one immediate-mode draw per mob (not split further); batching
shadows into one draw would change their order against later entities and is not exact.

## Session 12: chunk frustum predicate

`render/ChunkFrustum` replaces only the box predicate inside Alacer's existing visibility
search. It snapshots the six float planes (widened exactly to doubles) and camera offsets once
per search. For each plane it chooses the box corner that maximizes vanilla's left-to-right
discriminant, preserving the strict `> 0.0` boundary and sorting reversed box coordinates. It
does not use a center/extents approximation or move the camera subtraction into the plane
constant, either of which can change floating-point decisions near a plane.

The finite-coefficient/coordinate guard prevents overflow; unsafe inputs call the original
culler. Only an exact `FrustumCuller` over vanilla `FrustumData`/`Frustum` is eligible. Unknown
cullers, subclasses and foreign mixins detected on those three classes keep their own tests.
The accessor is registered in `alacer.mixins.json`; none of the earlier traversal, occlusion,
frame marking, result ordering or terrain render hooks change.

`render.fastChunkFrustum.verify` checks each predicate against vanilla and returns vanilla's
answer. Separately, enabling `render.fastChunkVisibility.verify` with the candidate on and
frustum verification off checks the entire candidate traversal against vanilla. The live Java
8 diagnostics compared 17.7 million boxes with no differences and at least 11,000 traversals
with no stable-input differences. Five differences among nine changing-input comparisons
are inconclusive. Focused unit tests compare another 1.1 million boxes against the game's
actual method and cover boundary, malformed-input and subclass cases.

`profiler.frustumTiming` is attribution only: it repeats the actual tested boxes through both
predicates after the search, alternating order, with one timer around each batch and a consumed
result. It is off by default, outside the visibility section, and rejected in the warmed
selection protocol. The diagnostic replay saved only about 1-3 microseconds per frame:
pan 0.0423 → 0.0412 ms, fly 0.0391 → 0.0360 ms. Full-run selection and limitations are in
`benchmarks/2026-10-19-chunk-frustum/`.

## Session 13: chunk-build responsiveness attribution

`profiler.chunkDetail` is a startup-only attribution option, default false. Its three mixins
are gated by both this flag and `profiler.enabled`; warmed selection rejects the option.
There is no new render toggle or pipeline algorithm. Vanilla retains its two workers,
100-task queue, five shared buffers plus the local worker's buffers, FIFO upload jobs, locks,
task cancellation and compiled-data publication. GL remains on the client thread.

The original `chunk_upload` section is all of `WorldRenderer.compileChunksUntil`: upload
draining **and** rebuild scheduling. Diagnostics split its drain and `rebuildAsync` calls,
time `uploadGlList` (glNewList through glEndList) and VBO upload separately, and time
`rebuildSync` inside terrain setup. `RenderChunk.compile` and transparency sorting have
separate client and worker totals. Task timestamps measure construction to COMPILING and
UPLOADING to successful DONE; cancellation writes DONE directly and is excluded from the
second timer. Worker buffer waits are measured separately from geometry building.

Workers publish duration/count pairs into `ConcurrentTimings`, a fixed-size synchronized
accumulator. The client drains it once per frame into its own profiler. Worker, buffer-wait,
queue-wait and upload-latency columns are **completion totals**, potentially crossing frame
and phase boundaries; they are neither client CPU time nor parts to add to `render`.
`chunk_sync`, client geometry building and GL upload are inclusive/nested timers too.

Separate end-of-frame gauges expose the renderer's dirty set, queued builds, per-layer upload
jobs and available buffers. The legacy `pending_chunks` is dirty + queued + upload jobs;
entries can represent the same chunk, and running builds are absent. It cannot diagnose
worker saturation. Each diagnostic render chunk owns one small `DirtyQueueAge` stamp, with
no world/chunk reference. It records **first observed** dirty-set residency, resetting on an
origin change, missing sampled frame or accepted enqueue. Accepted rebuilds report observed
wait before task creation. Ages are lower bounds; sub-frame entries are unobserved, and
coalesced invalidations are not individually tracked. Snapshot ages/maxima are not work time.
World/storage replacement drops the stamps with their chunks; no static map retains them.

The fresh copied-world series rechecks the historical fly assumptions with the frustum
experiment off. Display-list compilation is measurable, but most pending entries are in the
dirty set, not the build queue; observed pre-enqueue residency is much larger than queued
task-start latency. Fly performs no synchronous near builds in these samples. JFR/CSV
alignment uses `profiler.epochWallMillis` and its exported sampling/resolution uncertainty;
GC pauses can land inside arbitrary wall-timed sections. All JFR/detail runs are attribution,
never optimization-selection evidence. Exact tables, worst-frame sections, manifests,
diagnostic limits and commands: `benchmarks/2026-10-19-chunk-pipeline/`.

**Decision:** investigation completed, no production pipeline change selected. These results
do not justify more workers, larger queues, moving GL work or changing rebuild priorities or
frame budgets. The tested helpers preserve time/count pairs under concurrent publication,
and dirty ages reset across missing observations/repositioning/enqueue. All six selected
render defaults stay on; frustum, zero translations and model-list batching stay off.

## Session 14: terrain backend scenario and attribution

`benchmark.backendScenario` adds an opt-in fixed scene and camera protocol. It places 364
white stained-glass blocks in four overlapping walls in the copied world, discards full
rotation/orbit warmups, and separates steady drawing, rotating visibility, a local translucent
orbit and a single fresh flight. Ordinary glass uses CUTOUT in this version; the scene uses
StainedGlassBlock's TRANSLUCENT layer, verified from the mapped client bytecode. Other
scenario toggles and added populations are rejected. No game runtime or render default changes.

The existing `benchmark.vbo` option selects vanilla's backend before world load. Inspection
confirmed that `setWorld` calls `reload`, replacing storage/factories and, when the backend
changes, rebuilding the sky. Phase snapshots check the requested option, active GLX selection,
actual chunk implementation and fixed visual options. After export, automatic exit restores the prior VBO
option through vanilla reload, keeping storage and sky consistent for the last unrecorded
frame (`stop` only clears the outer loop flag). Separate fresh launches avoid rebuild warmup and second-flight reuse; balanced order
and repeated controls expose launch drift rather than assuming identical JIT/GL state.

One additional startup-gated `profiler.chunkDetail` mixin times `renderLastChunks` as
`terrain_submit` and counts submitted chunks per layer from `WorldRenderer.render`'s result.
Submission is inside the terrain-layer timers and includes driver waits; it is not GPU time.
Returned chunk counts are neither unique chunks across layers nor driver draw-call counts.
The RETURN hook can box its result and is intrusive; it is absent from performance controls.
Existing list/VBO upload timers and worker-sort/build completion pairs supply separate
attribution. Workers never access the frame profiler. No scheduling, GL state, draw ordering,
storage ownership, cancellation or publication algorithm changes.

Protocol, frozen implementation, raw-data validation, complete measurements and limits:
[Session 14 archive](benchmarks/2026-10-19-display-lists-vbo/README.md).

Eight fresh-world interleaved controls favor vanilla VBOs for throughput on the tested Java 8
profile: median paired mean frame reductions 51–61% and client CPU/frame reductions 67–70%,
with p99/slowest-1% improvements in every pair/phase. Allocation per second rises, fly maxima
regress in every pair, and detail task-start wait rises about 22% at a higher task rate. No
unqualified responsiveness/smoothness claim or global backend change is selected. The scene
and diagnostic hooks are retained; production rendering/scheduling and all option defaults
stay unchanged. Both Java 8 backend controls form the frozen comparison baseline.

## Session 15: particle scenario and attribution (Java 25)

`benchmark.particles` is an opt-in scenario: 16 stationary emitters around the anchor, at fixed
heights over the copied world's terrain, emit a fixed schedule at the start of every client
tick (before the world and particle manager tick, where vanilla's own sources emit): a 64-particle
stone block break, smoke, flame, redstone dust, small and large explosions, scaled by
`benchmark.particleScale`. Positions, jitter and explosion sizes come from a generator seeded from
the world seed; each particle's lifetime and motion keep vanilla's unseeded randomness, and the
always-on counters (`particles_ticked`, `particles_rendered`, `particles_spawned`) record the load.
The Particles option is fixed at All. It uses static/pan or warmed A/B/B/A segments with a 5 s
settle between segments; `benchmark.disableParticles` (attribution only) stops the schedule and
clears the lists through vanilla's `setWorld`. `benchmark.particleDiagnostic` is verification
only (see optimization 7).

`ParticleManagerMixin` keeps its pass timer and adds `particle_tick` (the manager's tick),
`particle_draw` (the unlit pass's `Tesselator.end` calls) and the counters: a few list sizes per
frame and tick, no per-particle work. `profiler.particleDetail` installs two startup-gated mixins:
`ParticleDetailMixin` (each `tickParticle`, the `removeAll` of dead particles, and a rendering
flag) and `EntityParticleDetailMixin` (`Entity.move` while a particle updates, `getLightLevel`
while particles render; other entities are ignored). They cost two `nanoTime` calls per particle
and operation and are rejected by the warmed selection protocol.

On Java 25, with about 3,300 particles, switching particles off lowered frame time by 53-59%.
JFR put the unlit pass at 64% of client-thread samples: per-particle light queries 27.5% (mostly
client chunk cache hash lookups, plus neighbor position copies that are 56% of sampled
allocation), vertex building 22%, the draw itself under 1%. Ticking costs about 2.7 ms per client
tick, so it shows in the frames that tick: movement and collisions about 1.4 ms, and vanilla's
`ArrayList.removeAll` of dead particles about 0.9 ms (it compares every live particle with every
dead one). See BENCHMARKS session 15 and the
[particle archive](benchmarks/2026-10-20-particles/README.md).

## Optimization 7: particle light queries

**Evidence.** Above: the light queries are the largest bounded part of the particle pass whose
answer can be reproduced exactly. Every particle asks for its light every frame while drawn:
`Entity.getLightLevel` calls `World.getLightColor`, and block-break particles call it again for
their source block. `getLightColor` computes sky then block brightness; each starts with two
`ClientChunkCache` hash lookups (the block state, to decide whether the block takes its light from
its neighbors, then the light). Neighbor-lit blocks (non-air blocks with light opacity 0 such as
tall grass, translucent blocks, slabs, stairs, farmland) read five neighbors instead, each copying
a `BlockPos` and looking up its chunk again.

**Design** (`render/ParticleLight`, `mixin/EntityParticleLightMixin`,
`mixin/BlockParticleLightMixin`, `render.fastParticleLight`):

- The two `getLightColor` calls are `@Redirect`ed. In `Entity.getLightLevel` the handler passes
  every non-particle entity straight to vanilla's call. For particles in the exact `ClientWorld`
  class, on the client thread captured at startup, `ParticleLight` computes vanilla's answer:
  `hasNoSky` for sky light, the y < 0 clamp, the ±30,000,000 / 0-255 range check with the light
  type's default, the chunk source's `hasChunk`, `WorldChunk.getBlockState` and
  `usesNeighborLight`, then `WorldChunk.getLight(type, pos)` or vanilla's maximum of up, east,
  west, south and north (each with `World.getLight`'s own clamp and checks), sky before block, and
  the minimum block light. One reused mutable position replaces the neighbor copies; the chunk only
  reads its coordinates.
- Only the chunk lookup is cached: 16 entries indexed by the low two bits of each chunk
  coordinate, so any 4x4 group of columns, such as the area particles usually occupy, never evicts
  itself. An entry is used only for the same world object and column while the column's client
  load/unload generation (the table of optimization 2) is unchanged; the shared empty chunk of
  unloaded columns is cached like any other. Entries are dropped on `WorldRenderer.setWorld`.
- Nothing else is cached. Block states and light are read on every query, so block and light
  changes are seen exactly when vanilla sees them. Particle order, the particles drawn, vertex data
  and draw calls are untouched; only the integer light value's computation changes.
- Thread ownership: the cache and mutable position belong to the client thread; any other caller
  takes vanilla's path. No GL objects are created. Runtime-toggleable.
- `@Redirect` rather than `@WrapOperation`, for thousands of calls per frame (see the rule above).
  The `BlockPos` vanilla allocates before the query is unchanged. Another mod's `@Redirect` of the
  same two calls would conflict; injections around them compose.

**Verification.** `render.fastParticleLight.verify=true` also runs vanilla's query for every
answered one, uses vanilla's result and compares: **66.7 million queries, 0 mismatches**, with
`benchmark.particleDiagnostic` cycling the blocks under the emitters through glowstone, slab and
air and emitting particles above the build height and below y = 0. Skyless dimensions were not
exercised.

**Result.** Six warmed in-process A/B launches at about 3,300 and 5,130 particles: frame time lower
in 12/12 pairs against pooled A and A2, median 23.4% (static) / 19.8% (pan) at 3,300 and 30.9% /
25.5% at 5,130; particle pass 0.25-0.50 ms lower, client CPU and allocation (about half) lower in
every pair, p99 lower in 10/12. One dense launch gained only 12% with worse tails. **On by
default.** The pass still costs 0.65-0.80 ms at 3,300 particles, mostly vertex building inside
each particle class's own render method.

## Optimization 8: dead-particle removal

**Evidence** (session 16). Every client tick, `ParticleManager.tickParticles(List)` ticks the
particles of each of its eight lists, collects the removed ones in a new `ArrayList` and calls
`particles.removeAll(dead)`. Java 25's `ArrayList.removeAll` asks `dead.contains(live)` for every
live particle, which calls `live.equals(dead[i])` until a match: O(live x dead), about 3,500 x 210
`Entity.equals` calls per tick in the particle scenario. Detail launches put it at 0.87-0.89 ms per
tick at about 3,500 ticked particles and 1.49-1.59 ms at about 5,400, all in the frame that ticks.

**Design** (`render/ParticleRemoval`, `render/KeyedRemoval`, `mixin/ParticleRemovalMixin`,
`render.fastParticleRemoval`):

- `Entity.equals` is true exactly for another entity with the same `networkId`, and
  `Entity.hashCode` returns that id. For classes that inherit both from `Entity` (every vanilla
  particle; checked once per class with a `ClassValue`, by the declaring class of the two methods,
  which never depend on mappings), `e.equals(d)` is `e.hashCode() == d.hashCode()`.
- A `@Redirect` replaces that one `removeAll` call (eight calls per tick, so not a per-particle
  hot path; a redirect like optimization 7's keeps the handler free of `Operation` arrays).
  `KeyedRemoval` fills a reused open-addressing set with the dead ids, checks every live element,
  then moves survivors down with `set` and removes the tail from the end. Survivors keep their
  order, the list object is the same, and the backing array, size and modification count end as
  `batchRemove` leaves them (it adds the number removed; `set` adds nothing, each tail `remove`
  one). Equal-id particles (`setNetworkId`, id wrap-around) and duplicates are removed exactly
  as `removeAll` removes them; an empty dead list returns at once, as vanilla changes nothing then.
- Vanilla's call runs when the option is off, off the client thread captured at startup, when
  either list is not exactly `java.util.ArrayList`, or when any particle in either list is null or
  of a class overriding `equals` or `hashCode`. That decision is made before the list is touched.
- Only the `removeAll` changes. The ticking loop is vanilla's: particles added while a list ticks
  (and the 4,000-per-list cap's `remove(0)`, which makes the loop skip one particle), layer moves
  through `setAlpha` -> `changeLayer` (a particle can leave a list after joining its dead list, and
  is then removed from the other list later, as in vanilla), and the dead list itself.
- No allocation: the id set grows only when a larger dead list than any before appears and is
  re-stamped, not cleared, per call. Thread ownership: the set and the per-class cache belong to
  the client thread; no GL objects. Runtime-toggleable. Another mod's `@Redirect` of the same call
  would conflict; injections around it compose (`profiler.particleDetail`'s removal timer does).

**Verification.** `render.fastParticleRemoval.verify=true` runs the fast path on a copy of the list
and vanilla's `removeAll` on the real one, uses vanilla's result and compares the survivors by
identity, count and return value: **0 mismatches** in 14,376 removals (354,907 particles removed)
at both densities. `benchmark.particleRemovalDiagnostic` (`ParticleProbes`) adds particles that move
layers while ticking (including on the tick they die), add particles to a full list while it ticks,
share a network id with a live particle, appear twice in a list, or override `equals` (80 vanilla
fallbacks). Unit tests compare against `ArrayList.removeAll` on random lists, including the
modification count.

**Result.** Eight warmed in-process A/B launches with the shipped defaults (plus eight with
`render.fastParticleLight` off): particle tick 0.74-1.51 ms lower per ticking frame and tick-frame
mean 11-28% lower in every primary pair; tick-frame maximum and all-frame p99 lower in 16/16.
Frames without a tick are unchanged and frame means move by a few percent at most, inside this
scene's launch-to-launch noise. **On by default**, and in `render.defaults`. Particle movement
(`Entity.move`, about 1.3-2.3 ms per tick) is now the largest particle tick cost.

## Session 17: benchmark isolation and movement experiment

Benchmark launches now ignore and never rewrite `config/alacer.properties`, starting from
the running build's defaults plus explicit JVM properties. Startup mixin selection follows
the same rule. Normal launches retain saved settings. Exported environments identify the
config source; regression tests and every new control check effective options and file hashes.

`render.fastParticleMovement` is an explicit **disabled** experiment. It replaces only
particle movement/collision gathering's block-state reads with reads through the existing
world/column/generation checked chunk-reference cache. No block states or shapes are cached;
vanilla collision ordering, entity queries, callbacks and movement calculations remain.
Its two mixins are absent unless requested at startup, including by its benchmark toggle.
It is excluded from `render.defaults`.

The final dense diagnostic compared 23.5 million reads with zero mismatches, plus 288
boundary reads and 200 ordered collision-list/border checks. Eight warmed Java 25 A/B/B/A
launches reduced particle tick cost by 10.8–24.8% in every comparison, but **failed selection**:
normal-density static medians regressed ordinary-frame p95 10.56%, CPU/frame 2.88% and
allocation/frame 3.22%. Keep the experiment disabled. Actual lifecycle transitions and full
movement/visual checks remain incomplete. See the
[session 17 archive](benchmarks/2026-10-20-particle-movement/README.md) for all results,
regressions, source snapshots and limitations. Historical archives are preserved.

## Session 18: immutable movement boxes

The actual Session 17 JFR was inspected with particle-tick stack filtering, then a fresh
source-snapshotted Java 25 baseline was recorded. Its scored phases attributed 59.1–61.5%
of sampled particle-tick allocation weight to immutable `Box` values; movement averaged
1.428 ms/static and 1.366 ms/pan per ticking frame with intrusive detail enabled. Full-run
samples, scored windows, sampled allocation and exact per-frame allocation remain distinct.

`render.reuseParticleBoxes` is a separate **disabled** experiment, excluded from
`render.defaults` and startup-gated independently of Session 17. It only reuses an exact
immutable vanilla Box for zero-offset moved/expanded calls with finite, ordered, nonzero
coordinates. Other values/classes/worlds/threads call vanilla. No Box is mutated, pooled or
retained; all collision queries, shape setters, axis clipping and callbacks still execute.

Java 25 build and all 64 tests pass. Dense live verification compared 14,447,580 operations
with zero raw-bit mismatches, plus 5,406 direct checks and 5,760 paired movement steps that
compare primitive movement state, intermediate bounds and callback traces. Boat collisions,
step/noClip/cobweb settings, border changes, unsafe coordinates and thread/subclass fallbacks
are covered within the documented scope. Only 2.71% of compared operations reused a box.
Visual inspection, actual world/chunk lifecycle transitions and exhaustive external callback
effects remain unverified; this does not complete Session 17's cached-read lifecycle gates.
See the [Session 18 archive](benchmarks/2026-10-20-particle-boxes/README.md).

Eight warmed A/B/B/A launches **failed all four density/phase gate groups**. Allocation per
tick frame decreased only 0.42–1.22% (below the 2% minimum), while particle tick regressed in
7/16 comparisons. Static median particle-tick changes were -0.68%/scale 1 and +0.66%/scale 2;
pan medians were -2.38%/-2.05%, with inconsistent individual improvements. Ordinary-frame
medians are not a selection claim: control frame-mean drift ranged -20.23% to +49.97%, six
comparisons regressed ordinary-frame mean/p95/CPU, and nine regressed tick-frame p99 (up to
18.07%). Keep the experiment disabled; all launches and regressions remain archived.

## Session 19: scalar nearby-entity query bounds

`render.scalarParticleEntityQuery` is a third independent **disabled** experiment, outside
`render.defaults` and startup-gated separately. It scalarizes only the grown nearby-entity
query bounds in `World.getCollisions`. Loaded checks, chunk/section iteration, predicates,
parts and result order stay exact; subclass calls share one lazily materialized immutable
Box per query. It retains no geometry or world/entity references. Vanilla nonzero Y/X/Z
movement boxes, intermediate setters, clipping/stepping, borders and callbacks remain intact.

Java 25 build passes 65 tests. Final client diagnostics compare 3,405,003 ordered queries
with zero mismatches, 10,306 direct checks and 5,760 paired movement steps. A targeted
regression failed on the first implementation's separate fallback Box identities and passed
after correction; five completed superseded controls and the failure are retained.

Eight fresh corrected-source A/B/B/A launches **failed all four density/phase groups**.
Allocation/tick frame fell 12.32-13.97% in every comparison, but particle tick regressed in
6/16. Dense static medians regressed frame mean 2.75%, frame p95 6.18% and CPU/frame 4.07%.
Tick-frame p99 regressed in 7/16, up to 23.53%. A1/A2 frame-mean drift ranged -16.42% to
+44.17%. These instrumented controls do not establish an allocation benefit against a
startup-disabled client: grown allocation sampling varied across baseline profiles. See
[Session 19](benchmarks/2026-10-20-particle-entity-query/README.md) for profile distinctions,
all regressions, the correction, source snapshots and remaining visual/lifecycle/effect gates.
All eight shipped defaults and both earlier experiments' decisions are preserved.

## Optimization 9: particle vertex writing

**Evidence** (session 20). Fresh Java 25 JFR of the shipped defaults in the particle scenario put
the unlit particle pass at 49-76% of client-thread samples and the whole particle tick at 3-9%
(`Entity.move` 2.5-6.1%); the pass runs every frame, the tick 20 times a second. Every drawn
particle emits a quad of four vertices with four elements each: sixteen `BufferBuilder` calls per
particle per frame. For each element `BufferBuilder` reads the element's byte offset from the
format's `List<Integer>` (bounds check and unboxing), switches on the current element's type,
writes, and `nextElement` advances the index modulo the element count, fetches the next element
from the format's list and skips padding. That bookkeeping was 39-53% of the pass's samples
(`Objects.checkIndex` and `nextElement` the top leaves), light queries 27-34%, the draw about 1%.

**Design** (`render/ParticleVertices`, `render/ParticleVertexWriter`, `mixin/ParticleVertexMixin`,
`mixin/ParticleBufferAccessor`, `render.fastParticleVertices`):

- The four element calls (`vertex(DDD)`, `texture(DD)`, `color(FFFF)`, `texture(II)`) inside
  `Particle.render` and `BlockParticle.render` are `@Redirect`ed. `Particle.render` is also what
  every `super.render` caller reaches (smoke, flame, redstone, cloud, crit, explosion, ...), so
  each particle class's own prelude (size, alpha, UVs) and its virtual `getLightLevel` still run.
- At the vertex call the direct path is taken when the option is on, the call is on the client
  thread captured at startup, the buffer's format is the `DefaultVertexFormat.PARTICLE` object,
  that object's layout was confirmed at the start of the pass (position FLOAT x3 at 0, UV0 FLOAT
  x2 at 12, color UBYTE x4 at 20, UV1 SHORT x2 at 24, 28 bytes, no padding; vanilla's
  `VertexFormat` is mutable, so this is rechecked every frame), the element index is 0 and the
  buffer is not `uncolored` (whose color call writes nothing and does not advance).
- Each element is then written with exactly the absolute `ByteBuffer` puts of vanilla's branch for
  that element type, at the element's fixed offset in the current vertex: position as
  `(float)(x + offset)` floats, UV0 as `(float)` floats, color as `(int)(c * 255.0F)` bytes in
  native-order layout, light as two shorts with v first. The buffer's own position, limit, byte
  order and bounds checks apply as in vanilla. `nextVertex` (vertex count and growth) is vanilla's.
- The element index stays 0 through the four direct writes; vanilla's ends the vertex at 0 too
  (four elements, no padding), and `currentElement` stays element 0. When a vertex call does not
  qualify it and the rest of the vertex take vanilla's methods: vanilla's vertex call leaves the
  element index at 1, and the three later handlers write directly only at element index 0 of the
  confirmed particle format.
- Unchanged: particle lists and order, each particle's render method, light queries, the pass,
  its GL state, `Tesselator.end` and the draw. Nothing is retained between frames but the layout
  flag; no world, chunk or geometry references and no GL objects, so world changes, resource
  reloads, render distance and backend changes do not interact. Client thread only.
- Hooks are startup-gated: installed when the option (default on), its verification or a
  benchmark toggle of it or of `render.defaults` asks for them, so a client started with the
  option off runs vanilla's element path without redirects. Runtime-toggleable when installed.
  `@Redirect` rather than `@WrapOperation`: about 16 calls per particle per frame. Another mod's
  `@Redirect` of the same four calls would conflict; injections around them compose. Particle
  classes that build their own vertices without `super.render` keep vanilla's element path.

**Verification.** `render.fastParticleVertices.verify=true` lets vanilla write every element of
qualifying vertices, computes the direct bytes for the same call into a scratch buffer of the same
byte order and compares them with what vanilla wrote (NaN encodings in FLOAT lanes compare by
value; every other bit must match), and checks every verified vertex runs
through its four elements in order: **950,264,304 elements, 0 mismatches**, 237.6 million complete
vertices, no fallbacks or layout changes, at scale 2 with the light and removal diagnostics
(light-changing blocks, particles beyond the build limits, layer-moving, cap-evicting, equal-id,
duplicated and `equals`-overriding probes). Unit tests compare the writer with a real vanilla
`BufferBuilder` for 24,000 cases of three vertices with random and edge values (signed zero, NaN,
infinities, subnormals, out-of-range colors and light, nonzero and NaN offsets) and check the layout.

The session 22 intermittent edge-test failure was a comparison error: byte 35 is the high byte of
vertex 1's Y position, and `0x7fc00000` versus `0xffc00000` are both NaNs. With `y = Float.NaN`
and `offsetY = -Double.NaN`, identical `(float)(y + offsetY)` expressions can select different NaN
encodings under interpreted/C1/C2 execution. Raw bytes can therefore differ for pathological NaN
inputs in the game too; there is no cross-tier NaN sign/payload guarantee. The direct writer is
unchanged. Tests and the opt-in verifier share `ParticleVertexWriter.firstMismatch`, which ignores
only NaN-to-NaN encoding differences in the five FLOAT lanes (position and UV0), while finite values,
signed zero, infinities, color bytes and light shorts remain bit exact. Negative controls cover both
byte orders, NaN-to-finite/infinity differences and every single-byte corruption. No finite-value
mismatch was observed; this is not new framebuffer or performance evidence. See the
[NaN investigation](benchmarks/2026-10-20-particle-vertex-nan/README.md) for raw captures, JVM modes
and a reproduction script. Buffer values/bytes, not the framebuffer, are compared.

**Result.** Eight warmed in-process A/B/B/A launches at about 3,300 and 5,130 particles, all four
predeclared groups passed: median particle pass -24.7% to -37.3%, frame mean -20.4% to -32.7%,
client CPU per frame -21.6% to -33.0%, frame p95/p99 and tick-frame mean/p99 lower; pass lower in
16/16 comparisons, frame mean in 15/16 (one disturbed launch regressed frame mean 2.9% and
tails). Part of the saving reappears at scale 1 as a longer wait in the draw call. Because the
A segments run vanilla through the installed hooks, separate startup-on and hooks-absent launches
were compared as well: median frame mean -23.5% to -33.5% and particle pass -27.6% to -33.6%,
with large cross-launch variance. **On by default**, and in `render.defaults`. With it on, light
queries are 34-43% of the remaining pass. See BENCHMARKS session 20 and the
[session 20 archive](benchmarks/2026-10-20-particle-vertices/README.md).

## Session 21: particle light entry (measured, off by default)

**Evidence.** Fresh JFR of the shipped defaults charged 7.5-11.5% of the particle pass to
`World.isChunkLoaded(pos)` in `Entity.getLightLevel` (and `BlockParticle.getLightLevel`), as an
inlined `ServerChunkCache.hasChunk` hash lookup, and showed `getLightLevel`'s `BlockPos` copy as the
pass's only sampled allocation in pan. On the client that check cannot hash: the client world's
chunk source is `ClientChunkCache`, whose `hasChunk` is a constant `true`, and vanilla passes
`allowEmpty = true`, so `isChunkLoaded` is `World.contains` plus that call. The lookup in the
profile belongs to `World.isChunkLoadedAt`'s compiled interface call, which the integrated server's
worlds share; session 20's reading of it as a per-particle client lookup was wrong.

**Design** (`render/ParticleLightEntry`, `mixin/EntityParticleLightEntryMixin`,
`mixin/BlockParticleLightEntryMixin`, `render.fastParticleLightEntry`): for particles in the exact
`ClientWorld` class whose chunk source is exactly `ClientChunkCache`, on the client thread captured
at startup, the two `isChunkLoaded` calls are `@Redirect`ed to the same range test on the same
coordinates; when this option answers the check and optimization 7 answers the light query (both
on, neither verifying), a `NEW` redirect replaces `getLightLevel`'s `new BlockPos(x, y + eyeHeight,
z)` with one reused mutable position holding the same `MathHelper.floor` coordinates, read only by
those two answers before `getLightLevel` returns. Everything else takes vanilla's calls. No caches,
no GL objects; hooks startup-gated like optimization 9. Another mod's `@Redirect` of the same calls
would conflict, and an injection capturing the position local would see a reused object.

**Verification.** `render.fastParticleLightEntry.verify` keeps vanilla's position, compares the
reused position's coordinates and runs vanilla's check for every answered one: **181.5 million
checks (8.0 million not loaded) and 152.1 million positions, 0 mismatches** with both particle
diagnostics; unit tests compare the floored coordinates with `new BlockPos(x, y, z)` for edge and
2 million random/bit-pattern doubles. The reused-position path itself does not run in verify mode.

**Result.** All four predeclared in-process A/B groups failed (medians: particle pass -1.7% / +1.2%
at scale 1 static/pan, +1.1% / -3.5% at scale 2; frame mean -1.9% to +1.7%, -4.0% at scale 2 pan;
CPU per frame higher in two groups) and one of four startup-on versus hooks-absent comparisons
failed (scale 1 static pass +0.1%; the others -2.8% to -8.6% frame, with cross-launch spread as large
as the differences). Allocation per frame fell 57-68% in process and 22-57% against hooks-absent
launches, without a matching time saving. Candidate profiles show why: the entry samples disappear
but the light share of the pass (36-43%) does not shrink; the time JFR charged to the inlined entry
reappears in optimization 7's light query that follows it. The installed-but-off hooks also made
vanilla's path allocate more than a client without them (they defeat C2's escape analysis of the
`BlockPos`), so in-process allocation savings overstate the real ones. **Off by default**, kept off
by `render.defaults`, retained as an experiment whose hooks are absent unless requested. See
BENCHMARKS session 21 and the [session 21 archive](benchmarks/2026-10-20-particle-light-entry/README.md).

## Session 22: per-pass particle light reuse (measured, off by default)

**Evidence.** `profiler.particleLightRepeats` (startup constant in `render/ParticleLightRepeats`, read
at first use and folded away when off; two hash-set inserts per query when on) counts, per unlit
`ParticleManager.render` pass, the queries reaching optimization 7's redirect and their distinct
(x, y, z, blockLight) arguments. In the particle scenario about 3,910 (scale 1) / 6,355 (scale 2)
queries per pass hit only 395 / 416 distinct positions: 89.9% / 93.5% repeat a position already
queried in the pass. `blockLight` is always 0, and only 6-7% repeat the previous query.

**Why reuse within a pass is exact.** `Minecraft.runGame` drains the client task queue (every world
packet handler is posted there by `PacketUtils.ensureOnSameThread`), runs the client ticks (block,
light and chunk changes, `setWorld`), then renders; the unlit particle pass only runs particle render
code. The handlers that stay on the network thread (disconnect, compression, tab list, keep-alive,
resource pack) do not touch the world, chunk-build workers only read, and the integrated server has
its own worlds. So optimization 7's answer for one world and position cannot change between the
pass's head and return.

**Design** (`render/ParticleLightReuse`, `mixin/ParticleManagerLightReuseMixin`,
`render.reuseParticleLight`): startup-gated `@Inject`s at `ParticleManager.render`'s head and return
and at `renderLit`'s head open and close a pass for the manager's world when the option is on and
optimization 7 answers without verifying (exact `ClientWorld`, client thread). Inside it,
optimization 7's existing `@Redirect` answers its queries for that world from a 4,096-slot
open-addressing table keyed by (x, y, z) holding the sky and block brightness computed earlier in
the pass; a new position is computed by optimization 7's code (sky, then block) and stored; the
minimum block light is applied after the lookup as in `World.getLightColor`. The table is emptied by
a generation stamp at every pass head and closed at return, at the lit pass's head and on
`WorldRenderer.setWorld`; past 3,072 positions new ones are computed unstored. A startup constant
mirrors the plugin's gating so the check folds away when the hooks are absent. No allocation, no GL
objects; particle order, vertices and draws are untouched.

**Verification.** `render.reuseParticleLight.verify` runs vanilla's `World.getLightColor` for every
answer the table gives (reused or computed) and uses vanilla's: **169.6 million queries compared,
158.9 million reused, 0 mismatches** with both particle diagnostics at scales 2 and 1. A
negative-control build whose table is never emptied produced 51.5 million mismatches under the same
check once the diagnostic changed blocks.

**Result.** Three of four predeclared in-process A/B groups passed (group medians: pass -11.8% at
scale 1 pan, -13.8% / -9.3% at scale 2 static/pan; frame -9.1%, -12.7%, -5.5%; CPU per frame lower);
scale 1 static failed (pass +3.9%, frame +6.8%: two launches regressed 11-19% in the pass while two
improved). The pass excluding its draw call fell in 15 of 16 comparisons, but the draw call took
longer in all 16, so part of the saving becomes waiting at the next synchronizing call, most at the
lightest load (frame about 0.8 ms). Startup-on versus hooks-absent launches lowered frame, pass and
CPU in all four comparisons, but scale 1 static allocation rose 51% (scale 2 static fell 46%; the
pass's sampled allocations are all vanilla's `getLightLevel` `BlockPos`, whose scalar replacement
varies by compilation). Candidate JFR puts the light work at 21-28% of the pass (37-40% before); what
remains is mostly `getLightLevel`'s own work and its `isChunkLoaded` entry. **Off by default**, kept
off by `render.defaults`, retained with its hooks absent unless requested. See BENCHMARKS session 22
and the [session 22 archive](benchmarks/2026-10-20-particle-light-reuse/README.md).

## Roadmap (recommended order)

Done in session 11: nearby-mob shadow queries (optimization 6). With it on, shadows still cost
about 0.14-0.16 ms per frame at 200 mobs (chunk reads, geometry, state changes and the draw);
no further exact, bounded change was identified there (see optimization 6). Session 9's model-list batching stays an explicit experiment,
off by default; keep that evidence instead of expanding into matrix or renderer rewrites. Do
not prioritize duplicate animation setup or unnamed-mob name checks on the current measurements.

Done in session 12: **frustum test cost** investigated and runtime-verified (above). The
one-corner predicate is exact within its guarded range, but repeated warmed runs did not
establish a dependable saving. `render.fastChunkFrustum` stays **off by default**, including
in `render.defaults`. The attribution replay saved only 1-3 microseconds/frame, so the previous
assumption that generic box testing was a substantial part of the remaining traversal cost
was not supported. Retain the experiment and evidence; do not prioritize further frustum
arithmetic changes on these measurements.

Done in session 13: **chunk-build pipeline responsiveness investigated**, with opt-in queue,
build, scheduling and GL compilation attribution. The old 6 ms worst-frame / 0.11 ms terrain
estimates did not describe the fresh series. No scheduling change was selected; retain the
measurements and diagnostics instead of treating the aggregate pending counter as worker
saturation. See the session archive for controls and intrusive attribution separately.

Done in session 14: **display lists versus VBO investigated** using a dedicated warmed terrain,
translucent-orbit and fresh-flight scenario. The existing VBO setting is a measured throughput
preference on this hardware, with retained tail/allocation/queue-wait limits. No renderer
rewrite, backend forcing or default change was justified. Preserve both Java 8 controls.

Done in session 15: **particles**, with a dedicated seeded scenario, Java 25 attribution and
optimization 7 (particle light queries, on by default). Vertex building (22% of client samples in
the scenario) is spread over every particle class's render method and has no thin exact hook.

Done in session 16: **particle removal** (optimization 8, on by default): the dead-particle
`removeAll` is now a single exact pass; ticking frames are 0.8-1.5 ms shorter in the scenario.

Done in session 20: **particle vertex writing** (optimization 9, on by default). Fresh profiling
showed the per-frame render pass, not movement, dominating the particle scenario (49-76% of
client samples versus 3-9% for the particle tick); direct element writes cut the pass by a median
25-37% and frame time by 20-33%.

Done in session 21: **particle light entry** investigated (above). The `isChunkLoaded` check is a
range test on the client; answering it directly and reusing the position removed the attributed
samples and most of the pass's allocation but no dependable time. `render.fastParticleLightEntry`
stays **off by default** and out of `render.defaults`.

Done in session 22: **per-pass particle light reuse** measured (above). 90-94% of particle light
queries in a pass repeat a position; the exact per-pass table verified with 0 mismatches and passed
three of four A/B groups (pass -9% to -14%), but scale 1 static and one startup comparison failed.
`render.reuseParticleLight` stays **off by default** and out of `render.defaults`.

Done in session 23: **independent light-reuse confirmation**. Eight scale 1 launches alternated
A/B/B/A and B/A/A/B, with four balanced startup pairs and fresh copied worlds. Both in-process
groups passed (pass -15.4% static / -14.4% pan), but startup static allocation rose 65.6% and pan
tick-frame p99 rose 5.66%, above the 5% limit. Reuse stays off and outside `render.defaults`.
Separate asynchronous GPU timestamps and raw Windows thread cycles did not reproduce session 22's
draw-time growth; coarse thread-CPU residuals were unreliable. An original/scalar/scalar/original
table-interface experiment did not make BlockPos elimination dependable; the original light files
were restored byte-identically. Session 22 was neither changed nor rescored. See the
[session 23 archive](benchmarks/2026-10-20-particle-light-confirmation/README.md).

Light-reuse confirmation (session 23) is complete: keep the retained experiment disabled; no
further table promotion or repeat confirmation is justified by those results.

Done in sessions 17-19, 24 and 25: **particle movement**. `Entity.move` for particles costs about 1.3-2.3 ms per client tick in the
scenario (collision box queries against the world for every particle that moves), now the
largest part of the particle tick, but only 2.5-6.1% of client samples in session 20's
profiles because it runs once per tick; it mainly affects tick-frame tails. Any change must keep vanilla's collision results, ordering
and `onGround`/velocity effects exactly; measure with tick-frame means and tails.
Session 17 investigated cached block reads and retained an opt-in experiment, but its
normal-density static ordinary-frame gates failed. Do not promote from tick savings alone.
Session 18 verified and measured immutable zero-offset Box reuse, but it failed every
density/phase gate group: less than 1.23% tick-frame allocation reduction and inconsistent
tick time. Keep it disabled as well; do not expand or rebrand it from favorable frame medians.
Session 19 scalarized the nearby-entity query bounds and reduced instrumented tick-frame
allocation about 13%, but all selection groups failed; keep that independent option disabled.
Establish nonintrusive startup-absent/hook-only controls and current JIT-sensitive allocation
behavior before expanding it. Continue profiling nonzero axis boxes and block/axis loops; a
separately gated scalar resolution region remains possible if justified. Preserve intermediate bounds, collision
ordering, step/border/entity behavior and callbacks; retain ordinary-frame, CPU, allocation
and tail gates. Complete remaining visual/lifecycle/external-effect checks before selection.

Session 24 continued with a benchmark-only movement census, startup dispatch controls and
fresh JFR/detail. Across 4.44 million moves, stepHeight and actual step-query counts were zero;
ordinary collision lists averaged 0.11 / 0.14 entries at scales 1/2. The unpublished step-trial
region has no opportunity in this scenario and ordinary clipping has little demonstrated scan
work. Gathering took 40.5-41.9% of instrumented move wall time; the rest includes intermediate
shape publications and callbacks/block/fire checks. Two startup hook-only/absent pairs showed
allocation and pan timing/tail warnings with zero scalar queries, so installed-but-off cannot
stand in for an unmodified allocation control. No replacement was selected. Next separate
block gathering and callbacks/fire scans while retaining the three intermediate setShape calls,
collision ordering and existing ordinary/tick/CPU/allocation gates. See the
[session 24 archive](benchmarks/2026-10-20-particle-movement-attribution/README.md).

Session 25 separated those regions with hooks-absent 1 ms JFR (DebugNonSafepoints, lines
mapped from the runtime jar's own bytecode) and an intrusive region census. Median shares of
movement samples: gathering 44% (column check 13%, block reads 11%, entity query 7%,
`addCollisions` 7%), query-bounds Box 9%, fire scan 12%, `checkBlockCollisions` 11%, clipping
and publications 10%, block under 5%. The entity query returned no entities; the block-collision
and fire scans cover the same 1.7 cells per move. No exact transformation's removable set reached
the predeclared 20%: re-reads after gathering are 19.7% and would cross callbacks (needing a
world-mutation epoch); column-check time moved into the block reads in one launch (session 21's
lesson). No replacement was implemented. See the
[session 25 archive](benchmarks/2026-10-21-particle-movement-regions/README.md). Particle movement
needs no further attribution on this scenario; revisit it only for a design that removes whole
reads or callbacks exactly (for example a client world-mutation epoch), with the session 25 gates.

Done in session 26: **whole-client attribution of the shipped defaults**. Eight hooks-absent launches with 1 ms JFR
and DebugNonSafepoints (default path at 0 and 200 mobs, the terrain backend scenario under display lists, the particle
scenario as a reference; two each, alternating), classified by a whole-frame stack-key streamer with line tables from
the runtime jar's bytecode, beside native samples and the ordinary profiler's CSV sections. Whenever the camera moves,
the visibility search (`terrain_setup`, optimization 1) is about 60% of client-thread Java samples in every ranked
scene (18-29% of frame time); its pieces are the search loop (41-45%), `RenderChunk.setFrame` (14-19%), occlusion-bit
lookups (10-13%) and frustum tests (11-13%). Terrain layers are 12-13% (half of it the per-chunk, per-layer emptiness
check); 200 mobs add entity draws (40% still, 26% turning, 89% of their allocation). Ticking frames are 0.9-1.9 ms
longer, almost all client tick; in fresh flight, native chunk upload averages 1.23-1.32 ms of the 4.0-5.4 ms slow-1%
frames. Another 14-16% of samples sit on one line of `renderEntities` that the ordinary profiler times at 0.000 ms (an
attribution artifact, rejected by the protocol's CSV corroboration gate). No exact transformation's removable set met
the predeclared rule, so no candidate was named and nothing was implemented. See the
[session 26 archive](benchmarks/2026-10-21-whole-client-attribution/README.md).

Done in session 27: **chunk upload/compile in fresh-flight tails measured** with a dedicated stationary-warmup,
still-reference and single 40 s/800-block flight at two fixed altitudes, each launch from a byte-identical copied
world. Six ordinary-profiler controls and four separate chunk-detail/GPU diagnostics passed on Java 25; protocol,
sources ZIP, tooling and runtime artifacts were frozen first. Upload medians were 0.161 / 0.175 ms per frame and
1.226 / 1.081 ms in slow-1% frames (near / high). Its shares were 9.15 / 8.76% of ordinary frames and 17.70 / 16.18%
of slow-1% time. The frozen size/repeatability gate failed (near 1/3, high 0/3; needed 2/3 in both), although
94-95% list-compilation attribution was corroborated. No exact removable subset was established, no candidate was
named, and no optimization was implemented or promoted. Sparse GPU command-stream intervals are not GPU busy time.
Near diagnostic tails worsened; current draw/CPU timing differs from session 26 for an unresolved reason. Preserve
both observations, all defaults and prior evidence. See the
[session 27 archive](benchmarks/2026-10-21-chunk-upload/README.md).

### Next session (28): recommended order

Sessions 26-27 found no qualifying candidate under their separate frozen rules. Do not select a chunk scheduling,
upload budget, list replacement or backend change from these aggregate costs. Revisit upload only with a design
that identifies exact removable operations and separately frozen gates; never use a warmed second flight as a
control. Missing per-list vertex/byte and obsolete-work identity, exact block/light-change-to-visible latency,
lifecycle/order/cancellation stress and driver attribution remain explicit gaps, not a selected follow-up. Later
10 s bins sometimes exceed the session 27 size threshold; selecting those bins after measurement would change its
predeclared whole-flight unit. The next independent target is allocation, with a rule written before measurement.

1. **Allocation-based selection (predeclare a rule for it).** With 200 mobs, entity draws allocate 53-58 KB per frame,
   61% `BlockPos` (mostly `BlockPos.offset` in shadow and brightness code) and 8% regex `Matcher`; without mobs the
   per-frame crosshair ray trace (`world.pick`: `Vec3d`, `HitResult`) is 23% (d0) and 18% (terrain) of client
   allocation, and vanilla's camera-section flood fill allocates `Direction.values()` copies in flight. Any candidate needs hooks-absent
   startup controls (installed-but-off is not an allocation control, session 24) and the session 26 gates.

2. **Parked: particle movement block re-reads through a client world-mutation epoch.** Session 26 confirmed that
   particle ticks do not matter in ordinary scenes (at most 0.1% of client samples and 0.4-1.7% of slow-1% samples in
   d0, d200 and terrain). Revisit only with a design that removes whole reads or callbacks exactly, under the session 25
   protocol's gates (an epoch covering `WorldChunk.setBlockState`, section and chunk data replacement,
   `ClientChunkCache` load/unload and `setWorld`, verification of every reused answer, and a stated policy for skipping
   `containsFireSource` calls).

3. **Closed; do not reopen without new evidence:** per-pass light reuse (sessions 22-23), light entry (21), the
   one-corner frustum predicate (12), model-list batching (9), zero translations, movement experiments 17-19, the step
   trial and ordinary axis clipping (24), entity-query bounds (19), shadow queries beyond optimization 6 (11), and
   (session 26) the visibility search's per-chunk pieces beyond optimization 1 (`setFrame` stamps, occlusion and frustum
   lookups, the per-layer chunk filter: each below 10% of samples and absent while the camera is still) and entity draws
   as a whole (spread over renderers, models, layers and shadows). Also checked on 2026-10-21: the 35,241 exception
   events in a 130 s recording are almost all `InterruptedException`s on paulscode's sound `CommandThread` (34,394),
   off the client thread, so they are not a frame-time target.

When ranking with JFR again, keep session 26's CSV corroboration test: in this client, 14-16% of Java samples can land
on a cheap line right after native GL calls (and move between such lines across launches), so a region's share needs an
ordinary-profiler section or independent timing that agrees before it can select anything.

Session setup, every time: explicit `JAVA_HOME` (Adoptium 25.0.4.1) for Gradle, tests and the client,
and the runner's check of the actual launcher; no overlapping `KnotClient`/devlaunchinjector client;
serial launches with unique `build/<label>.log` paths and fresh copied worlds; no builds or analysis
during scored launches; freeze protocol, sources and tooling before measuring; keep every attempt.
The checkout is untracked and shared: never commit, push, stash, reset or switch branches. Preserve
`docs/BENCHMARKS.md`'s legacy bytes and each file's line endings (this file is CRLF), and leave other
sessions' scratch directories (for example `build/pv-nan`) alone.

The Java 8 baseline above is frozen historical evidence. The Java 25 migration selects
Java 25 independently for the Gradle daemon, compilation/tests and Minecraft. Its client
verification, profiling and fresh scenario controls are documented in [JAVA25.md](JAVA25.md)
and the [migration archive](benchmarks/2026-10-20-java25/README.md). Optimization and backend
defaults are preserved; JVM migration alone does not select a renderer change.

Large independently developed rendering subsystem replacements are allowed. A replacement
should start with current Java 25 profiling and a representative scenario, retain a control,
and define correctness, lifecycle, ordering, allocation, throughput and tail-latency gates
before selection. There is no requirement to restrict justified work to small patches.
Third-party performance mod implementations must not be copied. The particle scenario
(session 15) is now available for particle work; stronger profiling evidence may justify
another subsystem first.

Retain the existing startup-gated mixins and runtime toggles as comparison boundaries while
developing a replacement. Declare which thread owns its mutable data and OpenGL objects,
how reload/world replacement/backend changes dispose or rebuild them, and which ordering
and visual invariants it preserves. Keep vanilla/control and candidate paths independently
measurable until selection; compare fresh identical moving worlds rather than a warmed
second flight. Intrusive attribution and verification remain separate from performance
controls. Prefer explicit experiments over automatic selection from an old JVM's results.
