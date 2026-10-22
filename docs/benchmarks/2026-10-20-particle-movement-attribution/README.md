# Session 24: particle movement census and startup controls

Roadmap item 1 after session 23 rejected light reuse. Work began October 20; launches finished
October 21. No movement replacement is selected. Experiments 17-19 stay false and outside
render.defaults. All nine shipped defaults, original light sources and the NaN fix are preserved.

## Decision

The benchmark-only census observed **4,443,672 moves**, with **zero positive step heights and
zero actual step queries**, across scales 1/2 and static/pan. The unpublished step-trial region
has no demonstrated opportunity here. About 18% of moves take noClip. Ordinary collision lists
average **0.11 / 0.14 entries** at scales 1/2: a structural 3N estimate of about 0.33 / 0.42
clip iterations per ordinary move, not measured per-element invocations. An ordinary scalar
clipping kernel has little demonstrated loop work to remove in this scenario.

Gathering consumes **40.5-41.9% of instrumented move wall time**. The rest includes intermediate
shape publications, clipping, callbacks, block/fire checks and probes. Removing the three
virtual setShape publications changes observable intermediate state. No independently justified
replacement is implemented. The next movement target is to separate block gathering and
callbacks/fire scans while retaining virtual calls, ordering and intermediate state.

Four nonintrusive startup controls compare session 19's nearby-entity-query hooks absent versus
installed with its option false. Both hook-only launches executed **zero scalar queries and zero
verification comparisons**. Median paired allocation/frame rose **30.79% static / 18.17% pan**.
Pan also exceeded frame/ordinary/CPU/tail warning limits; one pair differed 12.24% in frame mean,
triggering the drift flag. Tick-frame allocation changes range +1.24% to +14.81%. Two pairs
cannot establish causation, equivalence or production benefit. Installed-but-off is not a
dependable allocation control for expanding session 19. No launch was discarded.

## Implementation and evidence

`profiler.particleMovementCensus=true` installs two mixins only in benchmark mode with the
profiler enabled. Entity hooks observe move entry/return and ordinary/step query results;
World hooks time gathering. They return the original list, never change movement/shape/world
data and add no per-element timer. Storage is bounded and client-thread owned. Capture starts
after setup-frame discard and closes at stopRecording; warmups/probes stay excluded.
`benchmark.movementCensusInactive=true` installs the same hooks without arming capture.

Both active launches matched transformed injectors and passed all invariants: started/returned/
timed moves agree, query calls/returns agree, ordinary-query counts match collision-enabled
moves; zero unknown lists, nesting, overflow, incomplete scopes or unmatched returns. Both
inactive companions captured no phase and counted excluded moves. Java 25 build: **83 tests
passed**, including default-off and benchmark/profiler gating. This is diagnostic-runtime and
build evidence, not new optimization/gameplay compatibility certification.

Two census-absent JFR/detail companions measured movement at **1.37-2.22 ms per tick frame**,
within particle ticks of 1.89-3.07 ms. Whole tick-frame allocation is 2.12-3.09 MB, not allocation
scoped solely to movement. Only 31-66 movement execution samples per phase were collected;
fine CPU rankings remain uncertain. Sampled Box sites include three ordinary moved shapes,
expanded query, grown nearby-entity bounds and contracted fire query. Weights are estimates;
debug line numbers refer to transformed classes, not unchanged source lines.

Active/inactive census runs are intrusive and cannot select a replacement. Future work needs
separate correctness/lifecycle/ordering, throughput, ordinary/tick tails, CPU and allocation
gates. No third-party performance-mod implementation was copied.

## Files and reproduction

- [protocol.md](protocol.md): declared before integration and launches.
- [results.md](results.md), [metrics.json](metrics.json): every pair, warning, phase and class.
- `manifest.json`, `sources-*.zip`: frozen source, tools, protocol and analysis.
- `runs/`, `attempts/`: ten launches, commands, actual JVM, configs and raw hashes.
- `runs/*.stream-jfr.json`: validated, phase-scoped JFR; `staging/`: original source investigation.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_movement_attribution.py freeze
python tools/run_particle_movement_attribution.py census
python tools/run_particle_movement_attribution.py controls
python tools/run_particle_movement_attribution.py profiles
python docs/benchmarks/2026-10-20-particle-movement-attribution/analyze.py
```

Existing manifests/labels refuse reuse. For another series copy protocol/analyzer to a new
archive, adjust its mc24 literals to the new prefix before freezing, and pass --archive/--prefix
to the runner. Compile the preserved JfrPassAttribution.java with build/classes/java/main on
its classpath; use adjacent stream_jfr.py for JFR records. Both helper sources are frozen.
Raw CSV/JFR remain in ignored run/, logs in ignored build/. No shared alacer.properties,
driver setting, historical archive, commit, branch or remote changed.
