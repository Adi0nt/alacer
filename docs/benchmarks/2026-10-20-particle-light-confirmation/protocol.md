# Session 23: independent particle light reuse confirmation

Predeclared before freezing sources and before any client launch in this session.
Session 22's archive is frozen: none of its launches will be rescored, replaced or pooled
with these. The retained session 22 implementation is the candidate, without changing its
BlockPos interface. Preserve the subsequent NaN verifier fix and optimization 9's write methods.

Java 25.0.4.1 Adoptium, explicit JAVA_HOME, Gradle toolchain and actual launcher executable.
1920x1080, display lists, view distance 12, vsync off, 2 GiB maximum heap. The existing seeded
particle scenario and phase/settling durations remain unchanged. Every launch starts from
the same freshly copied base world; runner hashes every base file before and after launch.
The runner refuses overlapping KnotClient/devlaunchinjector clients and reused labels/logs.
All nine shipped optimizations are explicitly on, all other experiments explicitly off;
all verification/detail/counter/draw diagnostics explicitly off in scored runs. Shared config
SHA256 remains 694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31.
Source, runner, analysis and this protocol are frozen in a hash manifest and ZIP before runs.
Every exported phase config, raw output and actual launcher are validated and retained.

## Correctness

Full Java 25 build/unit suite must pass. Existing table/config/plugin tests remain. New
diagnostic hooks must be absent by default. Two fresh verification launches, scale 2 then 1,
reuse enabled with both particle light and removal diagnostics, must each compare >=10 million
queries including >=5 million reused with zero mismatches; no GL/Mixin/runtime errors.
The existing stale-table negative control remains historical sensitivity evidence, not a fresh
test. Reuse still closes at unlit return, lit head and world replacement and resets at pass head.
No list, draw order, vertex calculation or lighting semantics change. Skyless dimensions,
world mutation by third-party particle renderers and arbitrary mod combinations remain untested.

## Scored confirmation

Eight scale 1 in-process launches, labels lc23-ab-1-r1..r8. Odd launches use A1/B1/B2/A2;
even launches use B1/A1/A2/B2. A = reuse off with hooks installed, B = on. Both paths warm
before each phase, as in session 22. Static and pan are both scored; warmup phases excluded.
No simultaneous builds, tests, profiles or analysis. No stopping early or discarding outliers.

Per phase, pool A1+A2 and B1+B2 within each launch, then take median per-launch percent changes:

1. Particle pass lower in >=6/8 launches and median saving >=3%.
2. Whole frame mean saving >=1%, and ordinary (non-tick) frame mean saving >=1%.
3. Client CPU/frame not higher; process CPU/frame regression <=2%.
4. Allocation/frame regression <=2%; also report allocation/second and its throughput coupling.
5. Frame p95/p99, non-tick p95/p99, tick-frame mean/p99 regressions each <=5%.
6. Mean particles rendered within 2% for every comparison.
7. Flag |A2/A1 frame mean drift| >10%; if median absolute group drift >10%, additionally
   require pooled B pass to beat A1 and A2 separately in >=6/8 launches.

Four startup pairs, labels lc23-startup-{absent,on}-1-r1..r4; odd pairs absent/on,
even on/absent, serial fresh copied worlds. Absent = reuse false and no toggle, so pass
hooks absent. On = reuse true at startup, no toggle. Per phase compare medians of the four
launches per side: pass and whole/non-tick frame mean lower, client CPU/frame not higher,
allocation/frame <=+2%; frame and non-tick p95/p99 and tick-frame mean/p99 each <=+5%;
rendered load within 2%. Report all pair deltas, maxima and individual allocations.

Selection requires correctness, both confirmation phase groups and both startup phase groups,
plus session 22's already-passing scale 2 groups without rescoring them. This confirmation
cannot waive an ordinary-frame, CPU, allocation or tail failure. Any failure keeps reuse off
and excluded from render.defaults; continue roadmap item 1 with fresh movement attribution.

## Separate attribution, never scored

Two scale 1 A/B companions lc23-draw-1-r1/r2 in opposite orders enable only the new draw
diagnostic. Around Tesselator.end, accumulate host wall time, client thread CPU time and
allocation; timestamp pairs measure GPU command-stream elapsed time, polled asynchronously
from a bounded pool with no glFinish, blocking result read or forced flush. Results stay
associated with the phase at issue, not completion. Count missing/dropped/pending queries.
Compare aggregate clocks over each phase: Windows CPU quantization makes per-draw CPU and
individual residuals unreliable. Wall minus client CPU includes driver waiting, descheduling
and other blocking; GPU elapsed is execution on the GPU timeline, not host wait or GPU queue
latency. These diagnostics perturb submission and are causal attribution limits, not new gates.

Four scale 1 startup JFR companions in off/on/on/off order (two per side) attribute pass
samples and getLightLevel BlockPos allocation. Do not infer escape analysis solely from frame
allocation or a sampled absence. Inspect the retained bytecode/inlining; if justified, separately
snapshot a scalar-only table-interface variant that moves verification outside the table.
Compare original/scalar/scalar/original in fresh JFR launches under a separate attribution
manifest. It is not a promotion candidate in this protocol. Preserve the retained control and
report failure to establish a causal allocation difference. No product default is changed by
an attribution-only variant.
