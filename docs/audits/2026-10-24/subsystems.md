# Alacer subsystem audit and roadmap review

Audit target: clean `main`, `91efebe`, including `ebb44be`, `1692a44`, `2f604b4`,
`7a39f1e` and `e364b82`. Started October 23; completed October 24, 2026 (Pacific).
Scope: the requested terrain, entity, worker-cache, traversal, Vector API, raycast,
ambient-tick and sound changes, their hooks/config/tests, and Phase 3.1/3.3/6.1 planning.
No production Java, defaults, historical archives, remote refs or releases changed.
This audit adds a runner, its tests, a mathematical reproducer and these documents.

**Verdict: unit suite passes; current native client fails. Do not treat Phases 1–5
as verified complete or start scored scheduling comparisons from this revision.**

## Verified evidence

| Check | Result |
| --- | --- |
| Java build/test target | Java toolchain 25; compiler `options.release = 25`; Vector module explicitly added; Gradle daemon criteria `toolchainVersion=25` |
| `gradlew.bat test --rerun-tasks --console=plain` | 215 tests, 52 suites, zero failures/errors/skips |
| Requested named suites | VectorFrustum 2, WorkerBlockStateCache 1, FastRayTrace 2, FastRandomDisplayTicks 6, FastSoundThread 3, ChunkFrustum 5, AlacerConfig 11; all green |
| Native startup, unchanged sources | Crashes loading RenderChunkRegion: `InvalidInjectionException: NEWARRAY is not a valid injection point specifier` |
| Explicit Adoptium reference smoke | Same world-load failure; executable receipt confirms installed Adoptium Java 25, RTX 3060 Ti / OpenGL 4.6 NVIDIA 616.92 |
| FMA boundary probe | Vanilla `true`, VectorFrustum `OUTSIDE`; same result with `-XX:UseAVX=0` |
| Probe without Vector module | `NoClassDefFoundError: jdk/incubator/vector/Vector` |
| Reference runner unit validation | Tests cover 16/80-launch plans, every scene's receipts, invalid backend/config/errors/durations, raw hash rejection, zero baselines, exclusive attempt files and paired evaluation |

The first Gradle command launched through Adoptium `JAVA_HOME`, but Gradle's existing
daemon and unconstrained Java-25 toolchains select Microsoft 25.0.3. This is still
Java 25, not proof of an Adoptium client. The initial live crash identifies Microsoft
25.0.3; the reference runner now uses a scoped init-script vendor constraint and
checks the actual game executable. Its later smoke identifies Adoptium
25.0.4.1+1-LTS. See `validation.json` for the test-executor path and retained receipts.

### P0: world load cannot apply the worker cache mixin — reproduced

`src/main/java/net/adiont/alacer/mixin/RenderChunkRegionMixin.java:20,31` uses
`@At("NEWARRAY")` and `@At("ANEWARRAY")`. They are JVM opcode names, not supported
injection point specifiers in the installed Sponge Mixin. Native startup fails while
preparing the first redirect. This mixin is required and unconditional: turning
`render.fastWorkerAo=false` cannot avoid parsing it. The second injection is also
not a valid built-in point; the first failure prevents testing it independently.

The 215 tests instantiate the cache helper directly, without transforming the target
constructor. Repair must use supported bytecode hooks, actually avoid the large
allocations, preserve constructor semantics, and receive real world-load/rebuild
validation. Merely replacing arrays at constructor RETURN retains the allocations
and would not establish the claimed optimization. Retain the crash as before evidence.

### P1: nested SIMD FMA changes frustum decisions — reproduced

`render/VectorFrustum.java:207,235` evaluates `a*x + (b*y + (c*z + d))` with FMA.
Mapped vanilla `FrustumData.discriminant` evaluates `((a*x + b*y) + c*z) + d` with
separate rounded operations. Both regrouping and fused rounding can change signs.
The ordinary random-box tests almost never exercise cancellation boundaries.

The audit probe uses plane `(1, 1, 2^-55, 0)` and point `(1, -1, 1)`; other planes
always accept. Vanilla obtains `2^-55 > 0`; the nested expression rounds the small
term away and obtains zero. Current SIMD incorrectly culls it. This is finite input
well inside the coordinate guard. `FrustumAuditProbe.java` asserts reproduction of
the defect; it is deliberately **not** a correctness test that certifies the candidate.

Repair should use vector multiply/add operations in vanilla's order, or conservatively
delegate ambiguous inputs to vanilla with a proven error bound. The existing scalar
ChunkFrustum explains the monotone-corner proof. Do not retain FMA and claim bit parity.
Boundary/cancellation, signed-zero, reversed-box and exceptional-input tests must
precede performance retesting.

### P1: hysteresis can indefinitely reuse stale visibility and bypass verification

`render/ChunkVisibility.java:202–220,264–270` skips the search for small motion, then
updates `lastSearchCamera*` in `afterVanillaSearch` even when no search ran. A camera
moving 0.005 blocks each frame remains below the per-frame threshold forever; it is
not compared against the pose that produced the retained list. Sub-threshold rotation
has the same problem. A stationary viewChanged notification from freshly published
compiled chunks can also be suppressed when the dirty set is empty. The guard has no
compiled-data generation, frustum/projection identity, eye-block boundary or camera
identity check. These omissions follow directly from the control flow; visible
missing-terrain extent was not measured because startup is blocked.

The hysteresis branch precedes `config.verifyChunkVisibility`; `!verifyPending` is
normally true at frame entry. It can therefore suppress both searches while verify
is enabled. No `render.cameraHysteresis.verify` option exists. Pose state must advance
only after a real search, invalidation must cover all changing inputs, and verification
must independently compare the skipped result against an actual vanilla search. Exact
vanilla identity for arbitrary nonzero movement cannot be assumed from small deltas.

### P1: advertised verification modes do not establish their contracts

| Option | Inspection result |
| --- | --- |
| `fastWorkerAo.verify` | Configured/exported, never read by the worker mixin. `WorkerBlockStateCache.verifyBlockState` has no callers and would call the same region cache, not an independent uncached reference. No light-cache verification exists. |
| `fastModelTransforms.verify` | Configured/exported, never read by ModelPartMixin or FastModelTransforms. |
| `cameraHysteresis` | No dedicated verify flag; can bypass visibility verification as above. |
| `fastSoundThread` | No verification mode or audio-output/lifecycle comparison. |
| `fastRayTrace.verify` | Compares null/type/face/block position, omits the hit-vector coordinates. It uses a second maintained DDA implementation, not the intercepted vanilla method. |
| `fastRandomDisplayTicks.verify` | Checks some block queries and handler detection, not emitted particles/sounds, RNG transitions, call order or all skipped cases. |
| `flatOcclusion.verify` | Only active when flatOcclusion is also on; logs differences but continues returning the candidate decision. |
| `fastEntityCulling.verify` | Computes/logs vanilla but returns the candidate even on a mismatch, contrary to config text saying vanilla is used. |

`benchmark/BenchmarkRunner.warmsBothPaths` also omits the newly added entity, sound,
worker, model, vector, occlusion and hysteresis options. Do not score these options
using short in-process toggles until both paths have explicitly warmed.

### P1: ambient tick handler discovery depends on development mappings

`render/FastRandomDisplayTicks.java:179` reflectively searches the literal method name
`"randomDisplayTick"`. That works in this named development runtime, explaining the
green torch/redstone tests. Literal strings are not a runtime mapping strategy for
the intermediary production namespace. A rebuilt remapped jar retains that literal;
the local named/official/intermediary mapping row names the target `m_41538657`
in intermediary (official `c`). A failed search is cached as no handler,
potentially suppressing ambient effects in a distributed jar. This is a verified
mapping-dependent implementation; the remapped jar's visible ambient effects were
not exercised in this audit. Resolve the runtime name with Fabric's mapping resolver,
use an original registration strategy, or retain vanilla callbacks.

The shared DISPLAY_RANDOM also differs from vanilla's newly seeded Random per tick;
query comparisons cannot prove byte-identical effect streams. A proper replay needs
identical RNG state and observation of emitted effects without double-executing them.

### P1: entity culling bypasses renderer-specific policies

`render/EntityCulling.java:57–105` substitutes entity distance/box rules for the actual
renderer predicate and consults the renderer's policy only for certain guardian
fallbacks. Mapped vanilla dispatches to `EntityRenderer.shouldRender` virtually.
The implementation explicitly handles vanilla leash/guardian cases, but arbitrary
registered renderer overrides or foreign renderer mixins can choose differently.
An always-rejecting custom renderer can be accepted on the ordinary in-frustum path.
No compatibility restriction establishes that the replacement applies to those types.
The unit tests exercise rejection branches without a dispatcher and reset counters;
they do not exercise a visible entity's renderer policy or a world.
Retain renderer-specific decisions or gate an original fast path to verified compatible
renderer implementations. Verification should return the reference answer on mismatch.

The renderer MRU cache is cleared at world change, and its four slots are present;
95%+ hits are not established by these tests. It caches by class without a map mutation
epoch or dispatcher identity. Runtime renderer registration/replacement needs a test.

## Concurrency and confinement review

**WorkerBlockStateCache:** `ThreadLocal.withInitial` and final per-instance arrays
isolate workers and the client-thread synchronous fallback from each other. The test
confirms two thread instances differ. Mapped `ChunkRenderWorker.runTask` calls compile
synchronously; RenderChunk.compile keeps its RenderChunkRegion local while producing
geometry. Upload futures use compiled meshes, not the region, so normal sequential
vanilla compilation does not require region arrays after compile returns. However,
two live regions on the same thread alias the arrays: a nested/reentrant constructor
clears the outer region's cache. There is no lease/depth/release mechanism. Foreign
renderer callbacks and unusual region lifetimes are not certified; the invalid mixin
currently prevents testing pooling at all. Verification static longs would race across
workers if the dead helper were wired. Use per-worker or atomic diagnostic counters.

**ChunkVisibility:** the mutable queues, stamps, reused info list, plane buffers and
packed masks belong to one WorldRenderer and are accessed through client setupRender,
markDirty and setWorld hooks. Worker compilation does not call search. This supports
normal client-thread confinement; no explicit thread assertion protects misuse.
CompiledChunk publication is separately concurrent. Existing verification snapshots
references and reports changes as inconclusive, which is appropriate and must remain.
Flat occlusion repacks 36 visibility queries when the CompiledChunk reference changes.
Any concurrent mutation of the same object or subclassed/mixin-altered predicate needs
additional invalidation/compatibility analysis. Reference equality is not a general
thread-safety guarantee.

**FastSoundThread:** the mixin replaces SimpleThread.snooze only. Production code
contains no LockSupport.unpark; Paulscode callers still use Thread.interrupt, which
wakes park, then Thread.interrupted clears the flag. Avoiding InterruptedException
allocations is plausible; saying interrupt was replaced is false. The snooze wrapper
does not acquire a logger monitor. In inspected Paulscode source, command sleeps
occur outside source-management calls; stream sleeps occur after leaving listLock.
No new lock-order cycle with SoundSystemLogger was found. This is static review plus
one interrupted-park unit test, not proof of deadlock freedom, clean audio, source
shutdown/restart or repeated concurrent command delivery. A one-shot park permits
spurious returns, duration multiplication can overflow for arbitrary long inputs,
nonpositive durations differ from Thread.sleep, and counters are unsynchronized across
command/stream threads. Test preexisting interrupts, park wake races, stop/restart,
looping/streaming sounds and enable/disable transitions before certifying the subsystem.

## SIMD conformance and fallback

SPECIES_256 is **four double lanes**, so this code performs two batches (four planes,
then two with padding), not six lanes simultaneously. Coefficients are widened exactly
from float; sign-selected corners and two padded positive lanes are laid out correctly.
The nested FMA is exactly present, but is the demonstrated correctness problem.

With the Vector module resolved, the API has functional implementations when a
hardware vector shape is not efficiently supported. `-XX:UseAVX=0` successfully executes
this probe, while retaining its numerical defect. That does not prove speed on SSE-only,
ARM or a different JVM. Alacer has no explicit scalar dispatch based on SIMD capability.
See the [JDK 25 Vector API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.incubator.vector/jdk/incubator/vector/package-summary.html)
and [DoubleVector](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.incubator.vector/jdk/incubator/vector/DoubleVector.html).

Without `--add-modules=jdk.incubator.vector`, eager class references fail; ChunkVisibility
constructs a VectorFrustum unconditionally. Setting render.vectorFrustum=false is not
a module-absence fallback. If module-optional launch is required, isolate vector types
behind lazy capability loading and use the scalar/culler path without linking them.
VectorFrustum.prepare also lacks ChunkFrustum's foreign-frustum-mixin compatibility
guard, so exact runtime classes alone do not establish unchanged vanilla semantics.

## Roadmap claim review

| Claim | Verdict |
| --- | --- |
| TerrainShader uniform cache and scalar origin dispatch exist | Verified in source; cache resets in begin, camera-relative float conversion remains in setOrigin |
| Shader archive passed its 16 frozen native gates | Archived evaluation says gates_pass=true, failed_gates=[]; not a new replay/measurement of ebb44be's changes |
| Shader is the universally promoted default | Refuted: the roadmap itself still lists promotion pending; historic series does not cover every vendor or subsequent change |
| Model part matrix flattening/push-pop removal | Refuted: current hook only skips zero translations; recordSavedPush has no call site. This overlaps the earlier closed skipZeroTranslates mechanism |
| Worker AO optimization is complete | Refuted by native Mixin crash; no live pooling/verification evidence |
| FastChunkUnpack explicitly uses Vector API | Refuted: it bulk-decodes via little-endian ByteBuffer/CharBuffer and scans a lazily resolved 65,536-entry byte flag table. JVM intrinsic/SIMD lowering was not measured |
| Raycast is allocation-free | Too broad: collideable steps still allocate Vec3d/BlockPos and block.rayTrace may allocate results; intermediate empty-cell allocations are reduced |
| Camera hysteresis preserves vanilla and is verified | Not established; invalidation and verification defects above |
| Sound replaces interrupts and is verified | Refuted as stated; interrupt wakes park, no production unpark path and no strict verifier |
| 215 green tests prove rendering parity | Refuted by the native startup crash and targeted mathematical counterexample |

## Phase 3.1: prepared reference runner

Use [the new protocol](../../benchmarks/2026-10-24-chunk-scheduling-reference/protocol.md)
and `tools/run_chunk_scheduling_reference.py`. Planning is offline and produces 16
core or 80 full **serial** launches. Full includes independent floor/feed contrasts,
edit, near/high flight, default and terrain controls. The original Linux study's
16 launches cannot cover that expanded three-arm/scene request without extra runs.

The runner reserves attempts exclusively, captures actual launchers, refuses concurrent
clients, preserves logs/raw data, enforces hashes/config/backend/phase validity, and
scores normalized adjacent pairs. It uses existing ctypes process inspection instead
of starting PowerShell every sampling interval. A passing unchanged smoke is required
before freeze; no successful smoke/manifest or scored output exists for this audit.
The first runner smoke rejected the wrong Java vendor; the second used the selected
Adoptium runtime and rejected the actual startup crash. Source/tool amendments happened
between these unscored attempts, never during a scored series. Full visual/hardware
stability gates remain separate; timing evaluation cannot promote defaults on its own.

## Phase 3.3: evaluate staging as an opt-in design, not an assumed DMA win

The proposed PBO terminology obscures ownership: pixel pack/unpack targets serve pixel
transfers; terrain needs vertex-buffer storage. A buffer can be rebound, but calling it
a PBO does not make vertex uploads asynchronous. Persistent mapping requires immutable
buffer storage (OpenGL 4.4 or ARB_buffer_storage), with compatible map flags; ordinary
GL30.glMapBufferRange and UNSYNCHRONIZED alone do not create persistent storage.
See [ARB_buffer_storage](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_buffer_storage.txt).

Original design proposal:

1. Retain vanilla upload/control and current off-by-default experiments. Add
   render.pboChunkUploads (or a clearer stagedVertexUploads name) and `.verify` only
   when an implementation exists; do not add a nonfunctional toggle now.
2. Client owns all GL objects/context calls. Workers publish immutable mesh payloads
   through the existing completion queue. Initially retain worker CPU buffers to
   isolate staging effects; do not silently replace their lifecycle too.
3. Allocate three bounded staging ranges with explicit FREE -> CPU_WRITING ->
   COPY_SUBMITTED -> FENCED -> FREE ownership. CPU writes to persistent mapped storage;
   GPU copies into **resident mesh storage**. Never draw long-lived terrain from a
   transient three-frame ring that will overwrite it.
4. Fence after copies; reuse a staging range only after its fence signals. Retirement
   of resident mesh ranges needs last-use draw fences separately. Three slots are a
   capacity choice, not proof the GPU is at most three frames behind. Poll without
   blocking the client; when full, bounded deferral/control upload and metrics apply.
5. Coherent mapping or explicit flush supplies CPU-write visibility before GPU use.
   Nonpersistent UNSYNCHRONIZED mapping is a separate capability path and must unmap
   before GPU access. In either path, the application proves disjoint ownership;
   UNSYNCHRONIZED suppresses implicit synchronization rather than providing it.
6. Unload/reload, resize, cancellation, stale mesh generations, low memory, fence
   failures and capability/context loss must retire safely and select the retained
   path. Account for staging and resident bytes, high-water mark and fragmentation.

This adds a CPU copy and GPU copy; existing glBufferData may already stage well on
NVIDIA. No speed claim is justified. First attribute ordinary upload wall/CPU, queue
age, slow-1% frames and driver/GPU copy timing separately. Freeze unchanged scenes,
quality and an original retention policy; verify uploaded bytes/generation/layer
counts and visual output, then require roadmap mean/tail/CPU gates. Preserve the
rejected directBufferUploads evidence; this is not a reason to re-enable DSA uploads.

## Phase 6.1: an actionable MDI architecture

MDI cannot select a different VBO, VAO or u_ChunkOrigin uniform for every command.
Current chunks own separate buffers and TerrainShader updates one uniform per chunk.
The prerequisite is shared mesh storage plus per-draw origin data; replacing a loop
with one GL call without these changes would draw incorrect geometry.
The [ARB_multi_draw_indirect specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_multi_draw_indirect.txt)
defines an array of commands over bound state, not arbitrary per-command GL state.

Proposed layout, one 16-byte command per currently visible chunk/layer in the
**existing order**, with four unsigned 32-bit words in native buffer order:

| Byte offset | Field | Meaning |
| --- | --- | --- |
| 0 | count | vertex count, validated against resident allocation |
| 4 | instanceCount | 1 |
| 8 | first | resident byte offset divided by the unchanged vertex stride |
| 12 | baseInstance | origin-record slot |

Use one shared arena/VAO per compatible layer/storage page, preserving block vertex
bytes and atlas/lightmap bindings. Align allocations to vertex stride, check offsets
and command arithmetic, and keep generation-tagged live allocations until all draws
using them retire. Frame command/origin storage uses fenced ranges; resident meshes
retain their independent lifetime. Empty draws emit no command; allocation failure
uses the direct path or another bounded page.

For an OpenGL 4.3-compatible route, supply camera-relative origin as an instanced vec3
attribute with divisor 1. With instanceCount=1, baseInstance selects its origin record;
the shader applies the same expansion/translation arithmetic as the direct candidate.
This avoids depending on gl_DrawID/gl_BaseInstance shader builtins, which require
additional shader-draw-parameters support (or newer core GLSL). Alternatively gate a
buffer-table route on that support; do not assume GL4.3 alone supplies those builtins.
See [ARB_shader_draw_parameters](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_shader_draw_parameters.txt).

Submit once per compatible layer/page/pass. **Three to four calls is a target only
for one page, one view and no state splits**, not a bound for stereo, shadows or
multiple arenas. Translucent command order and intra-chunk CPU sorting must match
vanilla; GPU sorting is a separate Phase 6.2 experiment. Explicitly validate primitive
mode on the compatibility context (including existing quads); any triangulation must
separately preserve interpolation, provoking vertex and sort semantics.

Capability gate MDI, base-instance attribute fetch and compatible shader/VAO state;
foreign shaders/renderers, unsupported contexts or failures use TerrainShader direct
dispatch, then vanilla as appropriate. Preserve external program/VAO/buffer state.
Add render.multiDrawIndirect and `.verify` with a CPU reference command/origin manifest,
ordered chunk/layer/count comparisons, byte checks, zero GL errors and fixed-end-pose
images. Validate unload/rebuild/cancel/sort/near-world-limit lifetimes before timing.

Benchmark stages must distinguish shared-arena direct dispatch from MDI over the same
arena, so submission savings are not confused with storage changes. Freeze Java 25,
runtime/config/hardware and default/terrain/near/high/edit scenes. Require >=5% primary
improvement and the roadmap's mean <=+1%, p99/slow-1% <=+2%, CPU <=0% gates, plus visual
and allocation limits. Driver/GPU diagnostics remain separate from ordinary scored
frames. No implementation, performance measurement or MDI promotion occurred here.

## Recommended order

1. Repair required Mixin application; demonstrate native world entry, rebuild and
   shutdown with the retained before-crash evidence and all Java 25 tests.
2. Repair/restrict exactness and verification paths, including FMA, hysteresis,
   production-mapped ambient handlers and renderer policies. Run strict world-based
   verification and audio lifecycle checks before performance claims.
3. Create a new unchanged smoke/freeze for Phase 3.1. Run core for scheduling viability;
   use the explicitly larger full suite and independent visual/calibration gates for
   any later promotion decision.
4. Attribute remaining upload/submit cost. Develop Phase 3.3 staging or Phase 6.1 arenas
   only if that attribution justifies their memory/lifetime complexity; retain controls.

This order preserves every historical result while making the roadmap's completed
labels conditional on the evidence they actually have.
