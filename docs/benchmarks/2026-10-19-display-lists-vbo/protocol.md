# Session 14 protocol (declared before scored launches)

Compare vanilla display lists and VBOs on the actual Java 8 client, keeping Java 8 bytecode,
JDK 25 build/test, six selected Alacer defaults, all three experiments off, and identical
visual settings. Do not change driver settings. No global backend override is a candidate.
No renderer rewrite is proposed. Particles remain the next independent scenario.

Each launch copies the byte-hashed existing base world and creates the same 364 white stained-glass blocks:
four walls in the TRANSLUCENT layer, 13 blocks across, seven high, at anchor-relative z=10/14/18/22. These overlap in
view, expose translucent faces and sorting, and retain the surrounding natural terrain/water.
No mobs/chests are added; natural animals remain. The base world is never changed.

`benchmark.backendScenario=true` requires explicit `benchmark.vbo=true/false` and rejects
other scenario toggles/populations. Set the backend before world load. Vanilla setWorld/reload
recreates storage/factories and the sky backend. Check actual GLX selection and storage class
in phase snapshots, not just the requested option. No in-process switches/reload samples.

Phases: discarded warmup-pan 15 s (two complete turns), discarded warmup-sort 16 s (two
orbits), steady 10 s, pan 15 s, sort 16 s, fresh fly 20 s at 20 blocks/s. Orbit radius 12,
center (anchorX, anchorZ+16), height ground+5, looks at the glass. Every phase first waits for
two pending-free seconds (first minimum 10 s, others 3 s); 90 s settle timeout is rejected
by analysis. Exclude one recorder setup frame per phase. Warmups are archived but never scored.
Only one flight per process/copied world. Fresh here means identical saved input/client build
state per launch; it does not imply world generation or every flight chunk was initially unseen.

Smoke runs are quarter-duration diagnostics, excluded. Freeze implementation after smoke.
Score eight separate launches in order DL/VBO/VBO/DL/VBO/DL/DL/VBO, pairing adjacent
unlike backends (four comparisons, balanced order). Report every run, paired effects,
same-backend drift and late controls. Frames are correlated: launches, not frames, are repeats.
Keep detailed diagnostics/JFR/screenshots off for selection. Then collect four full diagnostic
launches DL/VBO/VBO/DL with chunkDetail on and screenshots outside recorded intervals.

Primary evidence: per-phase mean, p99/p99.9, slowest-1% mean, maximum, threshold counts/rates,
client CPU, process CPU, client allocation; terrain layers, sky, visibility, swap and upload.
Responsiveness proxies: pending time-weighted/mean/max and completion-time dirty/queue/upload
ages in diagnostics. No exact invalidation-to-visible latency or worker saturation inference.
Diagnostics separate draw submission, per-layer submitted chunk counts, backend upload time,
worker sorts/builds and task completions. Native GL wall time is not GPU execution time;
backend selection changes sky as vanilla does, so inspect it separately from terrain.

Selection gates for a setting recommendation: all four phases improve median paired mean
frame and client CPU by >=5%, at least 3/4 pairs agree per phase, median paired p99 and
slowest-1% mean do not regress >10%, and no persistent pending-work/latency regression >10%
without explained work-count differences. Inspect maxima and drift; do not discard bad runs
without a documented protocol failure. Correctness/log/settings/CSV gates must pass. These
are machine/scenario setting gates, not universal backend superiority. Any code optimization
would require a separately frozen candidate and new controls; do not invent one to claim a win.

Validate block placement, actual backend storage, phase durations/config/CSV continuity,
allocation availability, no dropped frames, no GL/task/render errors, and screenshots of both
backends, especially overlapping glass and the final flight. Screenshots do not establish
pixel identity or arbitrary-mod compatibility. No GPU timing, driver-threading inference,
Java 25 client validation or particle benchmark is part of this phase.

Before scored runs, bytecode inspection confirmed ordinary GlassBlock uses CUTOUT and
StainedGlassBlock uses TRANSLUCENT. Initial quarter-duration `bv-*-smoke` used ordinary
glass, so are development pilots only. Corrected stained-glass smoke uses new labels.

An initial control collection was stopped before comparison: `bv-dl-r1` completed and
`bv-vbo-r1` was deliberately interrupted. Inspection of Minecraft.stop confirmed it clears
the loop flag while the current frame continues. The benchmark now restores the prior VBO
option with vanilla reload after exporting results, so backend/storage/sky agree for the
last unrecorded frame. Final smoke/controls/diagnostics use fresh `bvs` labels and a new
implementation freeze; development identities/logs are retained separately. Selection
gates, camera paths, durations, visual settings and comparison order did not change.
