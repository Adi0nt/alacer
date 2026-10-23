# Culling, optimized math and modern OpenGL: attribution first

Starting source: `d8abd3f`. Java 25 for Gradle, compilation/tests and Minecraft.
Native Windows / RTX 3060 Ti, 1920x1080, distance 12, 2 GiB heap; ordinary
shipped defaults retained, experiments off. No third-party mod implementations.

Before implementing a candidate, profile the default static/pan/fly route and
the existing translucent terrain backend scenario using vanilla VBOs explicitly
(`benchmark.vbo=true`, actual backend checked by the runner). Each launch uses
the same retained seed-1789 base world and a fresh copy. Label these
`mg-attrib-vbo-default` and `mg-attrib-vbo-terrain`. Record 1 ms JFR with the
existing movement-1ms.jfc settings and DebugNonSafepoints, alongside ordinary
profiler JSON/CSV. These are attribution launches, not performance selection.
No overlapping client, build, tests or metric analysis. Preserve every attempt.

Inspect the current hot paths, not just historical percentages. Java samples
at native-call boundaries require corroboration with ordinary section timing.
Potential directions include removing repeated integer division/address math
from the existing culling traversal and reducing repeated VBO vertex-input
setup through capability-gated OpenGL features. Prior closed frustum/camera
experiments remain off; a new design needs its own mechanism and evidence.

Select and document concrete candidate correctness, lifecycle, compatibility,
frame-time, CPU, allocation and tail gates before scored A/B launches. Preserve
the current renderer as control, compare at fixed quality, and keep a fallback
for unsupported capabilities. Math approximations require explicit error bounds
and cannot silently replace exact/default behavior. No production default is
selected by a microbenchmark, a build, or software-rendering timings.
