# V1 hardening: resident VBO fallback and MDI startup

**Both repairs pass their functional regression checks.** The full amended
Java 25 build passed **360 tests**, including seven new regressions. Four serial,
muted native clients completed without crashes, GL/mixin errors, retries or
dropped profiler frames. The rebuilt remapped JAR also rendered the formerly
crashing forced-fallback combination. No optimization default was changed and
no performance approval is inferred.

Source parent: `dd4a142`, branch `codex/v1-hardening`, plus the exact working-tree
source hashes in the manifests. The first attempt used the initial fix and
[freeze-v1.json](freeze-v1.json); the remaining three used the amended startup
gate and [freeze.json](freeze.json), as predeclared in [AMENDMENT.md](AMENDMENT.md).
The [protocol](PROTOCOL.md) capped the work at four launches within twelve minutes.
Actual native execution was October 29, 14:51–14:57 America/Los_Angeles.

## Repairs and regression coverage

The earlier [muted follow-up](../2026-10-29-remaining-features/README.md) crashed
in native glDrawArrays because resident chunk vertices lived in shared arena
pages while fixed-function fallback bound an empty per-chunk VBO and drew from
vertex zero. ModernVertexBufferMixin now routes the existing bind/draw operations
to the resident page and its first vertex whenever a slice exists, independent
of the current MDI/shader flags. Ordinary VBO mode, first vertex and vertex count
are preserved when no slice exists. Replacement uploads release old slices
before ordinary upload binding; deletion shares the same release operation.

Five tests execute the actual handlers with captured GL operations, covering
nonzero resident offsets, ordinary VBO passthrough, additional draw offsets,
resident-to-ordinary replacement and idempotent deletion. These tests do not
apply Mixins or call OpenGL; the native clients test that separate boundary.

A related startup inconsistency was found before the remaining clients:
ModernGl.pipelineActive already treated MDI as a request, but AlacerMixinPlugin
did not install modern buffer/lifecycle hooks for MDI alone. The startup gate
now includes the MDI flag and its benchmark toggle. Two tests cover standalone
MDI selection and hook installation before the toggle's initial off segment.
The final three native clients omit a separate terrainShader flag, checking
this gate rather than relying on an unrelated option to install the hooks.

## Native observations

| Attempt | Workload | Duration | Result |
|---|---|---:|---|
| v1h-fallback | Exact old crash flags: MDI + shader requested + forced fallback; initial repair | 53.90 s | Static 3,021 / pan 2,659 / fly 5,721 frames; terrain visible, shader inactive as expected |
| v1h-mdi | MDI alone + native command verifier; amended source | 48.75 s | Static 3,176 / pan 2,608 / fly 3,576 frames; shader active; 17,673,235 verified commands/origins, zero mismatches |
| v1h-storage-toggle | MDI off/on/on/off in static and pan, shader fallback forced | 65.63 s | All eight toggle segments plus pan warmup completed; arena created during pan-B1; terrain present after returning to pan-A2 |
| v1h-production-fallback | Rebuilt production JAR, MDI alone + forced fallback, 200 spawned mobs | 46.54 s | Static 2,071 / pan 2,328 / fly 6,055 frames; normal exit 0, shader inactive, resident terrain and entities visible |

Every recorded phase has positive frames and zero dropped profiler frames.
Eighteen native phase screenshots are retained. All eight static/pan toggle
screenshots were inspected, along with the live development fallback and the
production static/flight views. This is limited visual review, not exact image
parity across every scene or third-party mod.

The toggle setter does not reload terrain for this option. The arena is created
during pan-B1 and the final off segment remains valid, exercising fallback with
previously resident data. The focused handler tests additionally cover stale
slice release during replacement; the native run does not isolate every upload
transition or allocation-failure condition.

Production class-origin logging confirms Alacer loaded from the actual
`mods/alacer-1.0.0+mc1.8.9.jar`, SHA-256
`214c7be1fd6317638d85ae1f31c11a267d105ee46c3262dbd91b7f9d8dba5da2`.
There are no development Alacer directories or observer agent in that launch.
Its isolated Loader installation uses cached official Minecraft, matching Calamus
Gen 2 mappings and fabric.fixPackageAccess=true, with fabric.development=false.
This does not establish external installer/profile compatibility.

## Build, environment and preserved state

Temurin 25.0.4.1+1-LTS is explicit for Gradle, compiler, tests and Minecraft;
bytecode target 25/classfile 69. Windows 11, i7-12700F, RTX 3060 Ti, driver 616.92,
1280x720, VBOs on, distance 12, 2 GiB G1 heap, native access and Vector module.
The initial build executed 358 tests, the amended build executed 360, all with
zero failures/errors/skips. Build logs and every final JUnit XML are retained
under logs/ and build-evidence/. No builds ran beside these clients.

Each client started from master volume 0 and a fresh benchmark world copy.
The original persisted Alacer config and Minecraft options are restored exactly
to their pre-hardening bytes, which already had master volume zero. The clients
are closed. Defaults, historical archives, shared main and release tags remain
unchanged. Raw CSV/file hashes and state checks are in final-state.json and the
launch records. Archived log text has trailing whitespace normalized; original
raw logs remain in ignored run/build directories.

The known optional Twitch/Realms and development Log4j startup errors remain
visible in the retained logs. No new native/GL/mixin/benchmark failure occurred.
The observer reads counters periodically in the development diagnostics and has
overhead; these short runs have no environmental acceptance or performance score.

## Documentation and remaining release work

README, CHANGELOG, JAVA25 and ROADMAP now distinguish actual packaged-JAR/reload
coverage from untested external launchers and F3+T delivery. The roadmap no longer
claims a whole-frame speedup from moving waits out of swap. GPU compute-culling
and translucent-sort helpers are marked unintegrated; the sorting helper is CPU
code. Default-off options rejected by qualification are labeled accordingly.

Before broad release claims, the outstanding gates are an unmodified Ornithe
installer/profile smoke, longer gameplay/world-switch/resource-pack stability,
multiplayer and representative mod compatibility, and Extended performance
qualification for defaults whose current results remain inconclusive. Other
OS/GPU and audible sound fidelity remain untested. These fixes do not promote
the experimental renderer or implement the unintegrated GPU helpers.

## Reproduction

launch.py and analyze.py explicitly reuse the previous archive's retained helper
implementations with this directory as the evidence destination. The observer
build instructions remain in that archive's INSTRUMENTATION.md; its exact JAR
hash is frozen here. remaining.py verifies frozen source/artifact identities,
deadline and functional gates before each of the last three attempts. It refuses
failed/incomplete evidence. production.py assembles the isolated production test.
Existing labels/directories are refused. Use new labels and a fresh protocol for
another campaign; do not overwrite either source manifest or previous evidence.
