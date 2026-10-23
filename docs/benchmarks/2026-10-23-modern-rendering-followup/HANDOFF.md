# Antigravity continuation

## Start here

Use `C:/Users/CodyJ/.codex/worktrees/alacer-chunk-camera/alacer`, branch
`claude/magical-planck-7t9612`. The measured shipping source/build is commit
`95cceb6b1e7a513cf94ed8a206abc49d04813a8f`. Later commits on this branch only
add this follow-up's protocol, records, analysis/replay tools and handoff.
Read `AGENTS.md` first. Java 25 is required for Gradle, compilation and the game.
The separate primary checkout at
`C:/Users/CodyJ/OneDrive/Documents/GitLab/adiont/aevum/alacer` remains clean main
at `0de17a5aa557c78f9f394800a1ef23a8dbbe08d1`; do not switch or modify it.

The human lifted the testing hold: "Ok start testing so antigravity can continue
where you leave off". This completed stage had an explicit eight-client cap.
No further test is running or queued. Define and freeze any next experiment and
its new bounded budget before executing it; do not rerun these attempts or the
old full selection series automatically. No fix, default change, PR or release
has been made. Keep every existing optimization and diagnostic intact.

## What is established

Read [README](README.md), [protocol](protocol.md), [amendment](amendment-r2.md),
`stage1-evaluation-r2.json`, `stage2-evaluation-r2.json`, `launches-r2.json` and
`state-r2.json`. All eight ordinary-route native GPU clients completed.

- A V V A: active VAOs increased pan mean frame time 67.30% / 61.11% and
  CPU/frame 111.40% / 115.12% in the two opposite-order pairs.
- F V V F: with GL hooks installed in both arms, active VAOs increased pan
  frame time 66.32% / 59.01% and CPU/frame 110.11% / 102.15%.
- Static and flight regressed too. Four terrain layer wall sections nearly
  tripled. All pairwise visible-chunk means stayed within 1%; submitted draw and
  vertex identity were not instrumented. No new correctness/image certification.
- F keeps the original GL path by returning early from `ModernGl.beginArrays`.
  It removes the active checks/query/binds together and may change JIT behavior.
  Its result implicates the active path, not an individual GL call or driver.
- Neighbor math and named uploads were OFF throughout. No JFR/detail/verification
  ran; there was no implementation change or new build/test suite invocation.
  runClient used seven up-to-date prerequisite tasks and the client task; all
  archived compiled-class/config hashes remained identical.

The first r1 validator rejected only mod display-string order after a successful
control client. The sorted mod/version list was equal. Original failure and
records are immutable. r2 revalidated/adopted that control (no metrics-based
exclusion) and ran seven additional clients. This was eight total, not nine.

Both earlier frozen manifests still match, as do all 1,004 r2 protected entries.
Archive replay verifies 56 raw files, eight logs, and identical evaluation
objects. No Minecraft client or benchmark runner is left running; an idle Gradle
daemon may remain. The final verification report records process/repository
checks and helper-tool hashes.

## Remaining mechanism question

Inspect `ModernGl.beginArrays` (binding query per render-list entry),
`TerrainVaoMixin` (active per-buffer checks and skipped bind/pointer calls),
`TerrainVertexArray.bind` (VAO per buffer), and local LWJGL2 `GL30` / StateTracker
binding bookkeeping. `RenderCompatibility` caches mixin inspection.
Existing pointer counters show caching worked; avoid treating initialization
as the main explanation without new evidence.

The next useful diagnostic should separate active Java work, binding-query work,
and bind/draw work with the original guard retained. Prefer native CPU-stack
attribution and actual GL operation/draw/upload counts on a separately frozen
diagnostic build. Intrusive timing/counters need separate controls and must not
be mixed into ordinary selection data. API time can include deferred driver work;
Java samples after native calls cannot establish driver latency. If native
profiling is unavailable, state that limit explicitly before choosing a bounded
alternative. No approximate math, geometry/order change, batching redesign or
private LWJGL-state replacement is justified by this stage alone.

The prior query bypass improved static, improved pan only 12%, and worsened
flight about 4%; it failed its predeclared replacement criterion. Preserve that
outcome. The full earlier combination still failed its original frozen gates;
this follow-up does not change or supersede them. Any proposed fix needs its
own correctness/lifecycle and repeated native performance evidence.

## Reproduce analysis without a game

From the worktree root, choose a NEW directory each time:

```powershell
python -B docs/benchmarks/2026-10-23-modern-rendering-followup/replay.py build/modern-rendering-followup-replay-antigravity
```

This restores only this archive's inputs and compares both evaluation objects;
it launches no game. The original uncompressed records also remain in ignored
`run/alacer/benchmarks/*-mg-follow-*`.

The earlier large JFRs remain local/ignored in `run/mg-attrib-vbo-default-r2.jfr`,
`run/mg-attrib-vbo-terrain.jfr`, and `run/mg-attrib-candidate-v1.jfr`. No new JFR
was collected. Earlier scored source snapshots, logs, images, gates and query
diagnostic are in `docs/benchmarks/2026-10-23-modern-rendering/`; preserve them.
Never execute its `archive.py` to collect this follow-up: it would rewrite the
earlier archive. Run neither existing `run_diagnostic*.py` again; they preserve
their freezes and refuse reuse. Create any future attempt under a new identifier.
