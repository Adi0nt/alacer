# Session 23 draw and allocation attribution

All draw values sum the unlit pass's Tesselator.end calls, then divide by unlit pass count. GPU elapsed is command-stream time, not host waiting. These companions are intrusive.

## Supplemental raw thread cycles

| Launch | Phase | Host draw us A/B | GPU elapsed us A/B | Draw raw cycles A/B | Pass raw cycles A/B |
|---|---|---:|---:|---:|---:|
| lc23-cycles-draw-1-r1 | static | 36.36/35.85 | 31.19/30.59 | 82347/80766 | 868450/722542 |
| lc23-cycles-draw-1-r1 | pan | 42.17/41.65 | 41.08/40.19 | 95254/93628 | 1067002/913546 |
| lc23-cycles-draw-1-r2 | static | 40.52/39.08 | 31.71/31.54 | 90776/87282 | 1005555/806956 |
| lc23-cycles-draw-1-r2 | pan | 42.59/41.46 | 40.01/36.89 | 96326/93792 | 1084462/901454 |

All timestamp pairs completed, with zero dropped/outstanding in the reported phases. Draw host wall, raw client cycles and GPU elapsed fall slightly with reuse in both companion launches/phases. Pass cycles fall more strongly. The session 22 growth in draw-call waiting was not reproduced here. Do not infer a driver threading-mode change or an exact partition of CPU time into driver/GPU waiting.

The first two draw companions used ThreadMXBean CPU clocks. Even phase sums produced negative wall-minus-CPU residuals in some regions, so they cannot quantify wait time. Their raw fields remain in draw-attribution.json and the run files. The supplement uses QueryThreadCycleTime, retaining cycles without a conversion to nanoseconds, as required by the documented API limitations.

## Escape-analysis interface experiment

[Four-run results](escape-analysis/results.md) retain original/scalar/scalar/original pass allocation and JFR samples. The scalar boundary failed the predeclared stable-allocation threshold in both phases. Static bytes/pass were original 15122.8 / 78914.8 versus scalar 79338.2 / 25.7; pan original 76614.3 / 79140.2 versus scalar 79173.8 / 75544.2. All JIT logs contain light-related BlockPos allocation-elimination records (4-7 per log). Neither interface makes elimination dependable; record presence does not prove a particular version stayed active during a measured phase. Original light sources are restored byte-identically.

The scalar variant passed the full Java 25 unit/build suite and two fresh live reuse diagnostics before attribution. Inspect escape-analysis/validation/runs for exact query counts and zero mismatches. It is an attribution variant, not a newly selected optimization.

## JFR pass attribution

The four off/on/on/off profiles retain scoped text and streaming JSON reports. Most sampled allocations inside the pass are BlockPos from Entity.getLightLevel, and both off/on runs can show little static allocation in particular compilations. No sampled allocation does not itself prove scalar replacement.

The streaming RecordingFile helper validates log/export/JFR hashes and joins exact CSV phase windows to the retained profiler wall epoch. Its execution/pass counts match the existing parser for all eight phase windows from the four main profiles. Earlier parsers and frozen source remain preserved.

Primary API references: [QueryThreadCycleTime](https://learn.microsoft.com/en-us/windows/win32/api/realtimeapiset/nf-realtimeapiset-querythreadcycletime), [Java 25 foreign linker](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html).

Streaming movement/gather/entity-query execution counts also match the retained parser. Its particle-tick sample count intentionally matches the exact tickParticle method; the older parser matches the tickParticle prefix and also includes tickParticles manager frames (1-2 additional samples in some phases). Do not compare those two tick totals as identical scopes.
