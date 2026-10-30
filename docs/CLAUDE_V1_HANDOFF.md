# Claude continuation prompt: Alacer v1 release qualification

Copy the prompt below into Claude if this work needs to be continued. Read the
latest campaign report and Git state before deciding what remains; this file is
a handoff, not proof that subsequent work has already passed.

---

Continue Alacer v1 release qualification in:
`C:\Users\CodyJ\OneDrive\Documents\GitLab\adiont\aevum\alacer`.

First inspect AGENTS.md, the active checkout/worktree, branch and dirty state.
The last completed commit is the release-check fix `6f60f08` (on `codex/v1-release-checks`,
after hardening `c79ff99` on `codex/v1-hardening`), based
on `dd4a142` (native testing archive). Preserve all later/unrelated edits. Do not
reset or switch to an older main/tag just because it says v1.0.0.

The user wants practical improvements and the remaining pre-v1 checks carried
through, with Minecraft muted. Set Minecraft master volume to 0 before launching
each instance and leave it muted. Do not mute other apps. Preserve original
config/options/worlds; use isolated instances and disposable world copies.

Java 25 is mandatory and explicit for Gradle, compiler, tests, clients and local
test servers. The validated installation is
`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` (25.0.4.1+1-LTS),
classfile 69. Keep native access and `jdk.incubator.vector` JVM arguments.

Completed evidence to read:
- `docs/benchmarks/2026-10-29-defaults-qualification/resume-1/README.md`:
  qualification failures. The maintainer subsequently disabled
  cameraHysteresis, flatOcclusion, fastTerrainPipeline, fastWorkerAo and
  fastModelTransforms by default. Those remain explicit config-only experiments.
- `docs/benchmarks/2026-10-29-remaining-features/README.md`: 14 retained attempts;
  entity/particle/tick/sound/adjacency checks, shader upload readbacks, MDI
  command validation, 12 scheduled resource reloads and actual remapped-JAR
  smoke. Includes a native MDI/fallback crash and three setup/driver failures.
- `docs/benchmarks/2026-10-29-v1-hardening/README.md`: fixes that crash and a
  related startup gate. Resident fallback now binds the arena page and uses
  the slice's first vertex; replacement uploads clear stale slices. MDI alone
  and its benchmark toggle now select buffer/lifecycle mixins. Seven new
  regressions; full Java 25 build passed 360 tests. Four muted native attempts
  passed, including eight runtime toggle segments and the rebuilt production
  JAR under forced fallback. Source/artifact manifests and all attempts retained.
  Final validated JAR SHA-256:
  `214c7be1fd6317638d85ae1f31c11a267d105ee46c3262dbd91b7f9d8dba5da2`.

Those short functional checks are not Extended performance approval. Vector
frustum and fast mipmaps have native verifier evidence but inconclusive/failed
performance results. Do not silently change their defaults. Frame pacing/swap
experiments remain off. GpuComputeCulling and GpuTranslucentSort have no production
callers; sorting is a CPU helper, not an active GPU feature. Do not wire them into
rendering just to make a status table say completed.

Status after the 2026-10-29 release checks (branch `codex/v1-release-checks`):
- `docs/benchmarks/2026-10-29-release-checks/README.md` (c79ff99): official
  Ornithe 0.16.0 installer profile smoke and 8-minute default soak passed
  (profile-driven offline launch; the generated profile itself supplies
  `fabric.fixPackageAccess=true` and lacks the Realms library). Loopback vanilla
  server connect/updates/disconnect/rejoin/stop passed. All production logs
  showed a compatibility-guard failure.
- Commit 6f60f08 fixes it: `RenderCompatibility` used unremapped named-mapping
  strings, so in every production install the shipped `fastRandomDisplayTicks`
  default, and the opt-in chunk neighbour and modern GL paths, silently fell
  back. Java 25 build: 361 tests. Fixed JAR SHA-256
  `588631a2fe3fe61edf9ee551ee42ce4f0affcc5c0895405fc5d9031c9271e261`.
- `docs/benchmarks/2026-10-29-release-checks-2/README.md` (6f60f08): production
  guard default/verifier smokes pass (ambient fast path active, 0 mismatches;
  35.8M chunk neighbour comparisons, 0 mismatches). The modern GL verifier was
  active with byte-exact uploads but failed a stale VAO gate (AMENDMENT-1). The
  zero-chunk multiplayer frame is reproduced by a no-Alacer control, so it is vanilla.
  Singleplayer world A→B, Video Settings Cancel/Apply, resource-pack
  enable/disable and Quit Game passed through the real UI.

- `docs/benchmarks/2026-10-29-mod-compatibility/README.md`: Lomka, Ksyxis and Async
  Logger pass with Alacer defaults and with 8 verifiers (0 mismatches). Legacy Skin
  Layers 1.0.1 crashes without a Kotlin runtime (undeclared; also without Alacer); with
  Fabric Language Kotlin 1.14.2 it passes both clients, including Alacer's opt-in
  ModelPart paths beside its ModelPart hook. Visual player skin layers not drawn.

Remaining release gates, in priority order:
1. Ornithe installer/profile metadata is covered; still not covered: an official
   launcher or Prism frontend launch and Microsoft authentication (needs the
   user), and a normal multiplayer disconnect/exit with 6f60f08 (the harness
   close request was ignored by both control and Alacer clients).
2. Lifecycle covered as above. Still open: experimental MDI lifecycle checks,
   physical `F3+T` delivery, resource packs that change textures/models followed
   by world rendering, and a longer soak with 6f60f08 (where ambient ticks now run).
3. Small follow-ups: correct the stale `render.modernGl.verify` description (no
   terrain VAOs remain); consider keeping per-option comments when the Video
   Settings Apply rewrites `config/alacer.properties`.
4. Mod compatibility: four mods pass (see above). Optional: third-person/visual
   check of player skin layers with Alacer, and broader mod combinations.
5. Prepare/run Extended performance qualification only under a separately frozen
   protocol using the existing aa_pilot/monitor/calibration infrastructure.
   Native functional success or a microbenchmark cannot approve whole-client
   gains. Do not pool Java 8 archives, dissimilar source/artifact versions or noisy
   foreground/background conditions. Report regressions, uncertainty and limits.

An earlier manually assembled production installation needed matching Calamus
Gen 2 mappings and `fabric.fixPackageAccess=true`. Test the actual installer's
metadata before adding workarounds; a generated profile missing a needed flag
is a result to diagnose, not permission to silently alter it. Retain failed
attempts under unique labels. The UI automation `F3+t` opened debug/chat rather
than holding the chord; twelve actual client-thread reload calls passed, but
physical shortcut delivery remains unverified. Do not label those equivalent.

For every campaign: inspect failures, stop the affected campaign on functional
errors, avoid automatic retries, retain logs/summaries/screenshots and actual
commands/JVM/class origins. Separate build/unit, real rendering, native verifier,
installation, multiplayer, compatibility and performance evidence. Preserve
known optional Realms/Twitch/Log4j errors as limitations. Restore options/config
after clients close, with master volume still zero. Archive reports outside old
frozen directories. Do not run builds or heavy analysis beside scored clients.

Fix concrete defects with targeted regressions and native retests; preserve
working optimizations, profiler/benchmark tools and historical evidence. Do not
copy third-party performance-mod implementations. Keep Minecraft-native UI.
Use byte-safe edits for docs/BENCHMARKS.md, which contains non-UTF-8 bytes.

Finished, verified work may be committed on the assigned working branch. Do not
push other branches, rewrite shared history, open PRs or publish a release without
explicit permission. Do not change version tags merely to match new local fixes.
Give concise progress updates and deliver a self-contained status with exact
remaining release gates. Do not stop just after planning or writing another
handoff when independent useful work is possible.
