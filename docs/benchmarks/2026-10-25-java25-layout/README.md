# Java 25 runtime and compact object header check (2026-10-25)

The project already compiled to Java 25 bytecode, but Gradle's unqualified daemon
criteria selected Microsoft JDK 25.0.3 on this machine. After pinning the daemon,
compiler, tests and Loom client to the installed Eclipse Temurin 25.0.4.1, the
`javaRuntimeInfo` task reported the same JDK path for all four roles. The remapped
mod's classfile target remains major version 69. The Gradle build passed; the
control and compact-header test runs each passed 323 tests.

Adoptium published a `jdk-25.0.5+8` release on October 22, but its release had no
Windows x64 JDK ZIP asset when checked on October 25. The Adoptium Windows x64 JDK
API still returned 25.0.4.1. This project therefore uses the newer installed
Temurin 25.0.4.1 rather than the older JDK that Gradle had selected.

## Live client protocol

`python tools/run_java25_layout_comparison.py` ran four fresh client launches in
A/B/B/A order, where A passes `-XX:-UseCompactObjectHeaders` and B passes
`-XX:+UseCompactObjectHeaders`. All used the same Temurin build, base world,
1920x1080 window, 2 GiB heap, distance 12, display lists, and normal enabled
optimizations. The benchmark mode ignored persisted Alacer config. Each launch
recorded 5 seconds static, 7.5 seconds pan, and 10 seconds fly after settling.
The client started on Windows/NVIDIA RTX 3060 Ti and all phases completed without
dropped benchmark frames, benchmark aborts or mixin failures. Exports identified
the effective `UseCompactObjectHeaders` flag and Java installation in each run.

| Phase | A1 FPS / p99 ms / CPU ms | B1 | B2 | A2 |
|---|---:|---:|---:|---:|
| Static | 481.7 / 4.98 / 1.47 | 563.9 / 4.59 / 1.37 | 525.7 / 5.02 / 1.40 | 477.9 / 5.11 / 1.43 |
| Pan | 318.0 / 6.28 / 2.90 | 323.0 / 5.73 / 2.90 | 326.1 / 5.71 / 2.87 | 313.1 / 5.88 / 2.99 |
| Fly | 303.9 / 6.19 / 3.09 | 315.9 / 5.65 / 2.97 | 322.6 / 5.38 / 2.93 | 333.5 / 5.26 / 2.86 |

Measured client allocation per frame was 3.78-3.80 KiB for the static controls
versus 3.22-3.39 KiB with compact headers, and 31.3-32.1 KiB for the pan controls
versus 28.6-29.5 KiB with compact headers. The fly results were mixed. The two
control fly runs themselves shifted from 303.9 to 333.5 FPS, showing substantial
run-to-run noise for that phase. The short phases, two launches per arm and lack
of independent visual inspection do not justify a default change. Compact headers
remain opt-in pending a longer, predeclared performance and visual correctness gate.

`report.json` contains commands, source/JDK/artifact hashes, elapsed time, raw output
locations and per-phase metrics. The four `.summary.json` files and `.log` files
retain the unrounded exports and launch records. Full per-frame CSVs remain under
the report's `raw_path` locations in the ignored `run/alacer/benchmarks/` tree.
Historical Java 8 and earlier Java 25 archives were not pooled into this comparison.
