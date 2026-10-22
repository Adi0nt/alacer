# Session 24: particle movement attribution and startup controls

Predeclared after session 23 rejected light reuse, before integrating or launching the new
movement census. Preserve all nine shipped defaults and the NaN verifier correction;
experiments 17-19, light entry/reuse and other experiments remain disabled.

This session selects a justified movement region, not another default-on optimization.
Source inspection found that ordinary Y/X/Z movement publishes all three intermediate Boxes
through virtual setShape; removing those publications changes observable behavior. The
unpublished step-trial region could be scalarized independently, but current step coverage
is unestablished. Historical allocation savings alone did not select experiments 17-19.

## Correctness and attribution

The new census is startup-gated, benchmark-only and absent by default. It must return the
original collision list unchanged, preserve every original move/clip/setter/callback and
perform no world write. Counters and clocks use bounded state owned by the client thread.
Unit/build checks and a live transformed launch are both required. No source is copied from
third-party performance mods. Original Minecraft source identity remains the locally cached
JAR SHA256 9a8846594ccc8ac1b4f0913caecd5dd8fcbdbc256dc9784d89b8eec0d06ff78b.

Two active-census JFR launches, scales 1 and 2, plus two installed-but-inactive census
companions, one per scale. Active capture starts only after the setup frame is discarded,
ends at stopRecording, and excludes warmups/probes. Installed-but-inactive uses the same
hooks with capture never armed. Every active phase must have complete move and collision
query scopes, zero depth/phase overflow and zero unknown-list anomalies. Reject anomalous
captures; retain their logs/sources and diagnose before replacement labels.

Report noClip, nonzero entry axes, positive stepHeight, ordinary and actual step-query
returns, collision list lengths/class coverage, gather/rest wall time and explicitly
estimated 3N/7N scan counts. Entry values precede cobweb scaling. Timing is intrusive and
cannot select a replacement. A zero step-query count rules out a useful step-trial
allocation candidate for this scenario; do not invent a stepping speedup. Sampled
allocation weights are estimates; zero sampled objects do not prove scalar replacement.

Two additional JFR+existing particle-detail launches, scales 1 and 2, with census hooks
absent, will attribute shipped movement under the current configuration. Separate scored
phase windows from startup/warmup; report sample counts and current tick allocation.

## Nonintrusive startup controls (scale 1)

Two paired startup comparisons, absent/hook-only then hook-only/absent, fresh copied worlds,
no runtime toggle, no JFR/detail/census/draw/cycle probes. The retained session 19 nearby-entity
query is false on both sides. Hook-only requests its existing .verify gate while the option
is false: its mixin is installed, but the scalar path and verifier never run. Require final
zero compared/scalar queries; other options and .verify flags identical and explicitly set.
This tests dispatch/JIT sensitivity against absent hooks before any expansion of that work.

Before inspecting these measurements, define equivalence warnings (not promotion gates):
median paired whole/ordinary frame or CPU/frame regression >2%, allocation/frame >2%, or
frame/non-tick p95/p99 and tick-frame mean/p99 >5%; flag |pair frame drift| >10%. Report
every pair, tick-frame allocation, maxima, rendered/ticked loads and allocation/second.
Two pairs cannot certify equivalence or production benefit; no failed historical candidate
can be promoted from them.

Any future replacement requires its own correctness, lifecycle, collision order, intermediate
state, throughput, ordinary/tick tails, CPU and allocation gates before selection. This session
may conclude that the evidence does not justify a replacement; it must report measured branch
coverage and the remaining CPU/allocation limits rather than stop at a proposed plan.

Java 25 is explicit for Gradle/tests/client. Retain existing source snapshots, actual launcher
path/arguments, unique logs, phase configurations, raw hashes and every failed attempt.
Refuse overlapping clients/reused labels and use fresh identical copied worlds at fixed
1920x1080, display lists, view distance 12, vsync off. No builds/analysis compete with the
nonintrusive controls. Do not commit, push, publish or change shared config/driver settings.
