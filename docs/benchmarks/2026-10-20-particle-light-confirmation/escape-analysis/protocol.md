# Session 23 escape-analysis attribution (separate from selection)

Declared after the retained confirmation failed its startup allocation and pan tick-tail
gates, before changing either light source file or launching this attribution series.
The option remains disabled regardless of this experiment's result.

Change only the reuse interface: a scalar core receives world, x/y/z and minimum block
light; the original BlockPos goes to a separate verification wrapper only when verification
is requested for that pass. Preserve the same table, brightness, counters, lifecycle,
original vanilla position construction/loaded check, guards and all other render options.
No combination with session 21 or movement experiments. Snapshot both exact source variants;
restore the retained implementation byte for byte afterward.

Build the variant on Java 25 and run both existing live reuse diagnostics (scales 2 and 1,
>=10 million compared and >=5 million reused queries each, zero mismatches) before using
it for attribution. The table itself is unchanged; the full existing test suite must pass.

Four startup-on scale 1 launches in order original-r1, scalar-r1, scalar-r2, original-r2,
fresh identical copied worlds, no runtime toggle. Both variants have the same draw/pass
diagnostic, JFR and HotSpot LogCompilation enabled through a separate Gradle init script.
These are intrusive attribution only; do not use their frame times for selection. The
runner retains the normal provenance/config/world/launcher checks and separately frozen
manifests under original/ and scalar/. It also retains JIT XML and JFR hashes.

Use exact client-thread allocation summed around unlit passes, divided by pass count,
plus whole-frame allocation and scored-window JFR allocation stacks/sample counts. Inspect
getLightLevel, the redirect, ParticleLight.lightColor and the table helper's compilation/
inlining records. GPU timestamps and CPU clocks are interpreted as in the main protocol.

Evidence of a dependable interface allocation benefit requires >10% lower pass allocation
in each scalar launch than the median of the two originals, in both static and pan. Report
all four values and compilation differences. Passing that attribution threshold is not a
performance gate or a reason to promote; failure means a stable benefit was not established.
Do not infer escape-analysis elimination solely from no JFR allocation samples. An explicit
eliminate_allocation record scoped to the relevant compilation, if emitted by this HotSpot
build, is stronger compiler evidence; its absence is not proof of failed elimination.
