# Java 25 compatibility

Java 25 is the primary supported runtime for development, tests, Minecraft 1.8.9 and
performance benchmarking. This migration preserves the independently authored renderer,
all six enabled optimizations, three disabled experiments, profiler and benchmark protocols.
Large independently implemented subsystem replacements remain allowed when measurements
justify them; see [ARCHITECTURE.md](ARCHITECTURE.md) and the root `AGENTS.md`.

## Three Java selections

| Component | Selection | Verification |
|---|---|---|
| Gradle daemon | Installed JDK 25 via `gradle/gradle-daemon-jvm.properties` | `./gradlew --version` |
| Compiler and unit tests | Java 25 toolchain; `--release 25`, classfile major 69 | `./gradlew build`; inspect the remapped jar |
| Minecraft | Independent Java 25 `javaLauncher` on Loom run tasks | Exported `environment.java.version`, VM version and JVM arguments |

`JAVA_HOME` controls the wrapper bootstrap JVM, independently of daemon criteria. Install a
JDK 25 locally and set `JAVA_HOME` to it for the simplest setup. Gradle can discover installed
JDKs; a custom installation can be listed in `org.gradle.java.installations.paths`. No JDK
download repository or machine-specific absolute path is committed.

Runtime migration was tested first with unchanged Java 8 classfiles. That launch completed
successfully before raising the compiler target. Java 25 bytecode was then accepted by
javac, Loom's jar/source remappers, access-widener validation, Mixin and the live client, so
the final default is Java 25 bytecode. Existing Minecraft/dependency classes retain their
original bytecode. The new Alacer jar requires Java 25 and declares it in `fabric.mod.json`;
Java 8 results remain historical references rather than current compatibility claims.

## Toolchain audit (2026-10-20)

| Component | Existing resolved version | Migration action |
|---|---|---|
| Gradle wrapper | 9.7.1 | Retained; already runs on JDK 25 |
| Fabric Loom | 1.18.4 from `1.18-SNAPSHOT` | Retained; Java 25 compilation/remapping verified |
| Ploceus | 1.18.1 from `1.18-SNAPSHOT` | Retained; Minecraft/dependency preparation verified |
| Ornithe Gen 2 | Feather 1.8.9 build 2, Calamus intermediary | Retained; mapped hooks/access widener verified |
| Fabric Loader | 0.20.0 | Retained; metadata minimum aligned with tested loader |
| Fabric Mixin | 0.17.5+mixin.0.8.7 | Retained; inspected enum exposes `JAVA_25`; config now selects it |
| ASM | 9.11 | Retained; classfile 69 accepted through build and client transformation |
| MixinExtras | 0.5.5 | Retained |
| OSL | 0.22.2 selected modules | Retained; client initialization, tick and keybinding registration work |
| LWJGL / lwjgl_util | 2.9.4+legacyfabric.15 | Retained; OpenGL, OpenAL, window and rendering initialize |
| JUnit Jupiter | 5.13.4 | Retained; existing tests plus two management API tests pass |

No dependency version upgrade or performance-mod code import was necessary. The two plugin
coordinates remain snapshots, as before; the exact resolved dependencies are archived with
this migration. Re-resolving snapshots in a later environment warrants another compatibility
check. Gradle's [compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
documents Java 25 support starting at Gradle 9.1.0. Local results establish compatibility of
this particular dependency graph, not every version published under those snapshot names.

## Launching and profiling

```sh
./gradlew --version
./gradlew build
./gradlew runClient -Pwindow=1920x1080 -Pxmx=2G
./gradlew runClient -Palacer.benchmark=true -Pjfr=java25-profile -Pwindow=1920x1080 -Pxmx=2G
```

Loom run tasks grant `--enable-native-access=ALL-UNNAMED` for the classpath's native LWJGL,
OpenAL and input libraries. External launchers should select Java 25 and add that argument
too. Broad reflective `--add-opens`, preview features and GC tuning are not required by the
tested configuration. JFR uses `-XX:StartFlightRecording` directly; the obsolete
`-XX:+FlightRecorder` switch is removed. `-PjfrSettings=path` remains supported.

The profiler still uses `com.sun.management.ThreadMXBean` to measure client-thread allocated
bytes, and MXBeans for heap, collector names, GC counts/time and thread/process CPU clocks.
No sampling or render hot path was rewritten. Exports additionally identify the runtime/VM
build and whether allocation counters are available. Two tests exercise retained allocation
and real heap/GC counter sampling; the live suite checks positive allocation/CPU measurements
and JFR recording. Unsupported JVMs still report unavailable allocation rather than inventing
zero allocation. The added allocation test intentionally requires HotSpot-compatible counters.

Use `python tools/run_java25_validation.py diagnostics` or `controls` to reproduce the client
series. They run serially, use fresh world copies and retain raw phase CSVs under
`run/alacer/benchmarks/`. Summaries, commands, logs and raw-file hashes are archived separately
under [2026-10-20-java25](benchmarks/2026-10-20-java25/README.md). Successful archived labels
are skipped; use new labels/a new archive for an independent later series.

## Evidence boundaries and known limitations

- Java 25 uses G1 by default here; historical Java 8 used Parallel GC. Heap ergonomics,
  JIT, allocation rates and pause behavior can differ. The historical results are not a
  controlled measurement of the causal effect of changing Java alone. Do not pool them.
- The old Netty version reports that complete low-level direct-buffer access is unavailable
  and selects its heap-buffer fallback. Client/integrated-server operation succeeds without
  opening JDK internals. A network throughput benchmark has not been performed.
- The development classpath still logs absent Realms notification classes, optional Twitch
  native streaming initialization and the optional `ServerGuiConsole` Log4j appender. These
  also occur in retained Java 8 launch logs; benchmark/world/render completion succeeds.
  Realms and Twitch streaming are not validated features of this migration.
- OpenAL initializes and the sound engine starts; audible output has not been listening-tested.
- Verification compares geometry, GL state and guarded vanilla answers; screenshots provide
  visual inspection. Neither proves framebuffer identity for every view or arbitrary other mods.
- The tested platform is Windows 11 x64 with NVIDIA RTX 3060 Ti. Other OS/native bundles,
  multiplayer servers, external production installers and third-party mod combinations need
  their own live checks. The remapped release jar is built and inspected; live scenario
  validation uses Loom's development client.
- Loom reports existing Gradle deprecations on run tasks; Gradle 10 is not claimed compatible.
  OneDrive also triggers Loom's existing workspace/performance warning. No driver or global
  Java installation settings are changed by this migration.

All existing Java 8 benchmark archives are preserved byte-for-byte. The migration does not
automatically enable/disable an optimization or force a terrain backend based on either
historical results or a narrow fresh win. New subsystem work should use current Java 25
profiles and predeclared correctness and performance gates.
