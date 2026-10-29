# Muted native feature follow-up, October 29, 2026

**Result:** entity/tick/sound/adjacency/particle verifiers, native shader upload
checks, MDI command checks, twelve scheduled reloads and the actual remapped-JAR
smoke completed. Forced MDI fallback **crashed**. Fourteen attempts are retained:
ten functional successes, one native fallback failure, one failed reload driver,
and two installation-assembly failures. No feature receives performance approval.

This is a new **functional** campaign on source `0277cbd7647fad8302ef96e47593b984cd093267`,
branch `codex/remaining-feature-testing`. It follows the earlier defaults
qualification without pooling its results. Minecraft master volume was set to
zero before every client. Other applications' audio was not changed.

No rendering implementation or optimization default was changed. The five
options rejected by the earlier qualification remain default-off. See the
[protocol](PROTOCOL.md), [instrumentation](INSTRUMENTATION.md),
[additional fallback check](FALLBACK-CHECK.md), complete [results](results.json),
per-attempt [launch records](launches/) and [logs](logs/).

## Completed diagnostic observations

| Feature / workload | Observation | Scope |
|---|---|---|
| Entity pipeline, 200 mobs + 50 chests | At least 30,000 compared entity passes, zero mismatches | Rendered counts/order verifier; naturally spawned entity counts vary |
| Entity lighting | 4,254,883 queries compared, zero mismatches | Same populated workload |
| Entity culling | 6,467,684 tested, zero mismatches | Same populated workload |
| Entity renderer cache | 14,324,497 queries, 14,320,727 hits, zero mismatches | Same populated workload |
| Ray tracing | 36,904 traces, zero mismatches | Dedicated verification enabled |
| Ambient display ticks | 729 ticks, 251,363 blocks, zero mismatches | Block/query checks, not random-stream or effect identity |
| Sound-thread waits | 37,035 verified snoozes, zero mismatches | Sound engine running with master volume zero; no listening check |
| Chunk adjacency | 49,216,392 indexed comparisons, zero mismatches | Dedicated ticks/sound/adjacency launch |
| Terrain shader / named uploads | Shader and attribute binding active; 6,833 upload checks, 207,723,840 bytes compared, zero failures | OpenGL 4.5 direct upload verification; no automatic performance promotion |
| MDI / resident arena | 5,190,068 batches and 19,508,294 verified commands/origins, zero failures; three resident pages created | Struct validity/finite origins; not a framebuffer or geometry-order reference verifier |
| Particle light | 33,692,864 comparisons, zero differences | Dedicated scale-2 particle diagnostic |
| Particle removal | 4,584 compared removals, 101,434 removed particles, 47 vanilla fallbacks, zero differences | Existing adversarial mutation/equality probes |
| Particle vertex writer | 447,373,568 compared elements, 111,843,392 complete vertices, zero differences/incomplete vertices | Existing standard-layout verifier |

The particle run exercised 4,277 probe particles, 19,471 layer moves during
ticking (1,823 on the dying tick), 20,038 additions to a full list, 462 deaths
with a live equal-ID twin, 470 duplicated probes and 47 equality-overriding
probes. These are actual native observations, separate from prior unit tests.

Manual visual inspection of live entity/MDI views and retained shader, MDI and
particle screenshots found terrain, cutouts, mobs and particle rendering present.
This limited inspection does not establish exact image equality or arbitrary
scene correctness. Each summary retains individual phase frame/drop counts;
raw CSVs remain in the recorded ignored game directories.

## Resource reload methodology and failed driver attempt

The original `q3-reload-1` client exited normally and completed benchmark phases,
but **its resource test failed** before any scheduled reload. The first test
driver selected an uninitialized Minecraft class instance and raised a
NullPointerException. This was test instrumentation, not a game rendering crash.
Its original [driver source](logs/ResourceReloadDriver-v1.java.txt), log,
observer hash and launch record remain retained. It is not a reload pass.

The corrected driver selects Minecraft from Fabric's Knot class loader, checks
for a live instance and schedules reloads on the client thread. Four fresh labels
`q3r2-reload-1` through `4` use mipmaps off/on/on/off; none overwrites the first
attempt. Each successful launch requires three begin/completion pairs. Reloads
restart OpenAL and rebuild the atlas while master volume remains zero.
Controls with fast mipmaps off intentionally have zero fast-mipmap comparisons.
Positive pixel comparisons are required for the two enabled launches.

These reloads run inside ordinary benchmark phases and use an observer agent.
Their timings include sound/resource/atlas work, cache/JIT effects and uncontrolled
background activity. They are **not** isolated mipmap cost or a performance gate.
Startup reloadResources timing precedes atlas listener registration and must not
be called complete startup-resource time. Full per-event times are in results.json.

All four corrected clients completed three reloads each. The two enabled clients
each finished with 3,544 sprites / 350,200 pixels compared and zero differences
(counts include startup plus the three reloads). Full reload wall times in ms:

| Launch | Fast mipmaps | Reload 1 | Reload 2 | Reload 3 |
|---|---|---:|---:|---:|
| q3r2-reload-1 | Off | 1087.451 | 1047.922 | 1164.639 |
| q3r2-reload-2 | On | 1034.459 | 1198.439 | 1063.157 |
| q3r2-reload-3 | On | 1038.813 | 1035.226 | 1009.474 |
| q3r2-reload-4 | Off | 1079.468 | 1087.730 | 1042.776 |

These observations close the functional reload gap; they do not approve mipmap
performance or change its existing default.

## Production JAR and fallback follow-up

**Forced MDI fallback fails.** `q3-mdi-fallback` entered the copied world, created
a resident mesh arena, then crashed in native `GL11.nglDrawArrays` at 14:38:36
America/Los_Angeles. Gradle returned 1 after 11.77 seconds. No summary or final
verifier counts exist; zeros in the early observer snapshot are not a pass.
The full [JVM fatal-error report](logs/hs_err_pid9364.log) is retained. It records
an access violation reading address zero on the client thread through
`VertexBuffer.draw` → `VboRenderChunkListMixin.alacer$fastRender`.

Source evidence supports an MDI/fallback integration defect: resident upload
interception in ModernVertexBufferMixin checks MDI enabled/supported, while
ModernGl.pipelineActive rejects forced fallback. The fallback VBO draw path
does not select resident slices. This explains a plausible invalid-buffer path;
it is a source-based diagnosis, not a native driver root-cause proof. The
combination was not retried or repaired. MDI remains default-off.

The first production attempt, `q3-production-jar`, failed during Loader setup
because the hand-assembled classpath omitted mappings.jar. Loader chose the
official namespace and rejected Alacer's intermediary access widener. Alacer
never initialized: this is an installation-assembly failure, not evidence of a
broken distributable. The failed record/log and [original launcher](logs/production-v1.py.txt)
remain preserved. A fresh `q3r2-production-jar` uses the exported mappings JAR
and original cached Minecraft client JAR so Loader can perform its ordinary
official-to-intermediary remap. It has a separate isolated game directory.

That second installation reached Alacer initialization and the title screen but
failed world entry with an IllegalAccessError between remapped ProgressRenderer
and Minecraft.running. The locally inspected Loader MappingConfiguration source
requires `fabric.fixPackageAccess=true` to enable package-access correction in
an intermediary runtime. A fresh `q3r3-production-jar` adds that property; it
does not alter the mod JAR or use broad JVM reflective opens. The second crash
and [launcher source](logs/production-v2.py.txt) remain retained separately.

**The third production installation succeeded.** It loaded Alacer from
`mods/alacer-1.0.0+mc1.8.9.jar`, with the exact artifact SHA-256 above, no development
Alacer class/resource directories, no observer or reload driver, and
`fabric.development=false`. It rendered 200 spawned mobs and completed static,
pan and flight phases, then remained open for manual checks and exited 0 after
Alt+F4. The launch record retains class-origin evidence and every JVM/classpath
argument. This is a local manually assembled Loader installation, not a test of
an external launcher installer or release download.

The production sound menu displayed **Master Volume: OFF**; its native F2
[screenshot](master-volume-off.png) is retained. After the benchmark completed,
one Computer Use `F3+t` attempt again opened debug/chat instead of causing a
reload. No extra atlas/reload timing appeared. Chat was closed; no repeated
shortcut attempts were made. **Physical F3+T delivery remains unverified.**
The successful twelve client-thread reload calls test the reload entrypoint,
not keyboard automation. The production client still logged the missing
RealmsNotificationsScreen; this is not an error-free installation certification.

## Environment, build and evidence boundaries

Windows 11, Intel i7-12700F, NVIDIA RTX 3060 Ti, 1280x720, 2 GiB heap, G1,
VBOs on, render distance 12. Gradle, compiler, test launcher and Minecraft all
explicitly select Temurin 25.0.4.1+1-LTS. `build javaRuntimeInfo` succeeded; its
build and test tasks were **up-to-date**, so this is not a new execution of the
353-test suite. Full output is in [build.log](logs/build.log).

The remapped artifact has classfile major 69 and both license texts, SHA-256
`efb44aa3b583ca0a5a8c6d48089cab44ff7f88973619fc6596a559c257b0d1a2`.
The first development launches load development classes, not this JAR. The
isolated production launch has its own record and class-origin evidence.

Development startup retains known Log4j Queue/ServerGuiConsole, optional Twitch
and missing Realms-notification errors. They are not omitted or used as proof
that the production installation is error-free. The diagnostic logs/summaries
must be read for actual GL/mixin/timeout failures and verifier activity.

No controlled performance campaign, foreground/environment acceptance,
Extended stability/power/repeatability testing, multiplayer/network throughput,
third-party mod compatibility, external launcher distribution, other OS/GPU,
custom resource-pack coverage or audible sound fidelity was established here.

`GpuComputeCulling` and `GpuTranslucentSort` have no production callers in the
current source tree. They therefore remain **unintegrated and untested in normal
game rendering**. The latter currently implements CPU distance/index sorting;
its name and roadmap status do not demonstrate an active GPU sorting pipeline.
No helper was wired into rendering merely to change its test status.

## Final preservation checks

All completed benchmark phases reported zero dropped profiler frames. Thirty-two
phase screenshots and the muted sound-menu screenshot are retained. This does
not mean there were no slow frames: these are functional, instrumented runs and
their frame timings are not scored for performance acceptance.

The original persisted Alacer config is byte-identical to its backup. Original
Minecraft options were restored byte-for-byte except `soundCategory_master:0.0`,
which remains muted as requested. Game clients were closed. No Java source,
build configuration, optimization default, historical archive, shared main
history or release tag changed. Work is retained on the separate test branch.

Archived log/crash text has trailing whitespace normalized for Git checks.
Original raw files remain in ignored run/build directories; crash-file and raw
benchmark-file hashes are retained in final-state.json and launch records.
The archive is a functional record with explicit failed attempts, not a fully
frozen performance campaign. The helper sources compiled, Python syntax checks
and staged Git whitespace validation passed.

## Reproduction

Build the non-shipped observer as described in INSTRUMENTATION.md, then invoke
`python <archive>/launch.py LABEL key=value ...`. Full property lists are in each
launch JSON. `resources.py` runs the four fresh reload labels serially and stops
on missing reloads or differences. `production.py` assembles a separate local
game directory and launches Loader using the actual remapped artifact.
`analyze.py` reconstructs results from retained records/logs/summaries. Existing
labels are refused; another campaign needs new labels and its own observations.
