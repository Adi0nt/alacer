# Benchmark configuration isolation

Session 16 found saved `render.fastParticleLight=false` and
`render.fastParticleRemoval=false` in the shared development directory. A file value took
precedence over a newer build's default. The Java 25 launcher helper also enumerated only
the six optimizations that existed when its historical controls were measured.

`AlacerConfig.load` now starts benchmark mode (`alacer.benchmark=true`) from this build's
defaults and explicit JVM properties, without opening or rewriting the shared file.
`AlacerMixinPlugin` follows the same rule before client initialization; otherwise a saved
intrusive timer or experiment could still install hooks even though runtime config ignored it.
The config-source marker is exported as `alacer.configSource=build-defaults-and-jvm-overrides`.
Effective config continues to be captured per phase and guarded against mid-phase changes.

Normal launches retain file precedence and file creation/updating. Saved false values remain
intentional user settings in that mode. To enable the shipped particle options in a normal
launch without editing the shared file, pass both JVM/Gradle overrides explicitly.

Regression evidence: the three new benchmark-isolation tests failed before the implementation
(`build/session17-config-red.log`) and passed after it. They cover old disabled particle
settings, a saved disabled profiler and enabled intrusive detail, explicit option overrides,
byte-for-byte preservation of the existing file, no file creation when missing, normal launch
file precedence, and startup hook selection. The movement experiment also has startup gating
and runtime toggle/preset tests. `build/session17-final-build.log` reports a successful Java 25
build; the suite contains 60 tests, zero failures.

The first real client profile deliberately omitted explicit light/removal overrides. Both
exported `true`, with the new config-source marker. Its before/after shared-file SHA-256
matched. Every new movement launch also verifies this invariant and checks all phase configs.

Historical archive analyzers are not rewritten: some deliberately compare their measured
hashes against the working tree and will fail after subsequent source changes. That failure
does not invalidate their stored results. New movement records include a ZIP snapshot and
per-file hashes of the measured source, and their analyzer verifies those snapshots rather
than current sources. Raw CSVs, logs and JFR remain local and separately hashed.
