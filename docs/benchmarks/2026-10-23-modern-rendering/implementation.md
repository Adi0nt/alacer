# Mechanism and boundaries

Starting control: d8abd3f (Java 25). No third-party performance mod implementation
was used. Existing optimizations, frustum/camera experiments, chunk scheduling,
quality settings, profiling and benchmark scenarios remain intact.

`ChunkNeighbours` precomputes six toroidal storage addresses per slot, using the
same floor-division/remainder result as the retained calculation. It saves those
operations on every traversed edge; bounds, occlusion, frustum decisions, visited
stamps and output order are unchanged. At distance 12 the table is 240,000 bytes
(25*16*25*6 ints), allocated once per storage replacement, not per search/frame.
It is guarded by ordinary camera coordinates/interpolation and vanilla storage
type/known mixins. Its verify mode compares every used address with the original
calculation. Whole-search comparison remains an independent existing diagnostic;
concurrent compiled-data changes remain inconclusive.

`TerrainVertexArray` lazily captures vanilla's four conventional BLOCK-format
arrays (position, color, texture and lightmap; stride 28) once per drawn VBO.
Later draws bind its VAO and retain the original draw call/matrix/order. Extra
VAOs are removed with their buffer and on renderer world transitions, including
the title-screen boundary where vanilla retains its VBO storage. A changed
context drops old numeric names without deleting those names in the new context.

`ModernVertexBufferMixin` uses OpenGL 4.5 `glCreateBuffers` and
`glNamedBufferData`. Creation must materialize a buffer object: a name reserved
by `glGenBuffers` alone is insufficient for a named operation. The upload retains
vanilla's byte range, STATIC_DRAW usage, vertex-count calculation, and final
ARRAY_BUFFER=0. Verification reads back and compares every byte; it is excluded
from performance runs.

The implementation uses core OpenGL 3.0/4.5 availability checks; it does not
enable extension-only variants. Incoming nonzero VAOs, buffer subclasses, detected
foreign mixins on the buffer/list classes and unavailable APIs retain the original path.
Affected classes are resolved before inspecting/caching their applied mixins,
so an early constructor cannot prematurely accept an unloaded draw-list class.
`modernGl.forceFallback` is a command-line diagnostic, not a saved setting.
These guards are not certification of arbitrary transformers, mods or context
recovery. The ordinary display-list path remains available. All three options
default off; `render.defaults` keeps them off. GL mixins are absent at default
startup. `benchmark.toggle=render.modernPipeline` can exercise their combination;
it is not a saved preset. Each option also has an independent benchmark toggle.

Primary API references: [Khronos VAO specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_vertex_array_object.txt)
and [Khronos direct-state-access specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_direct_state_access.txt).
The conventional array state is captured in a VAO; client-active-texture and
global ARRAY_BUFFER are separate state and are handled explicitly.

Deferred: approximate trig/vector math (no measured target/error budget), a new
frustum/occlusion policy, shaders, persistent mapping, indirect batching and
draw-call reordering. Those require their own profiling mechanism and correctness,
lifecycle and performance gates. Existing closed experiments are not reopened by
this work. Other vendors/driver versions and third-party integration remain
separate follow-up validation.
