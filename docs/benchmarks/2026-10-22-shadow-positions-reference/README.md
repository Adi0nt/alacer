# Session 29: `render.reuseShadowPositions` on the reference hardware (run plan)

**Status: prepared, not frozen, not run.** Roadmap item 1 of "Next session (29)". The series, its changes from
session 28 and its pass conditions are in [gates.md](gates.md). This file is the procedure; replace it with the
results archive after the series.

## What was prepared (in a Linux container, October 22, 2026)

- `benchmark.screenshotEndPose` in `BenchmarkRunner` (off by default; requires `benchmark.screenshots`): one
  unrecorded frame at the phase's exact end pose, captured on the next frame. The environment now also exports
  `benchmark.screenshots` and `benchmark.screenshotEndPose`. Build and 105 tests pass on JDK 25.0.4.1.
  Development launches here (llvmpipe, 854x480, d200, time scale 0.5, not evidence): two fixed-pose launches gave pan
  captures at the same angle, differing by 1.78 mean absolute RGB and 13.8% of pixels (mobs, clouds, animation),
  against session 28's 9.2 and 52.8% between its two vanilla pan captures.
- `tools/run_shadow_position_reference.py`: the Windows runner (session 27's launcher, manifest and kill handling;
  session 28's options and log checks).
- [gates.py](gates.py): session 28's evaluator with the tick-free target and gate 5, gates 2-4 and 7 judged, and the
  new labels. Exercised on synthetic data only.

## Procedure on the reference machine

The checkout there is untracked and shared: copy the changed files in (do not commit, push, stash, reset or switch
branches there), keep each file's line endings, and leave other sessions' scratch directories alone. Files:
`src/main/java/net/adiont/alacer/benchmark/BenchmarkRunner.java`, `README.md`, `tools/run_shadow_position_reference.py`,
and this folder (`README.md`, `gates.md`, `gates.py`).

1. Close every other client; nothing else heavy running. Confirm `run/saves/alacer-benchmark` is the seed-1789 base
   world sessions 26-27 used and `run/config/alacer.properties` is unchanged.
2. Build with the explicit JDK (no launch may compile afterwards):
   `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` then `gradlew.bat build`. Expect 105 tests.
3. Smoke (before the freeze; attempts only, about 5 minutes):
   `python tools\run_shadow_position_reference.py smoke`. Both must pass. Open `run\screenshots\alacer-spr29-smoke-verify-*`
   and check the three captures look right. A failure that needs a tooling change means fixing it, rebuilding, and
   repeating the smoke under new labels (the runner refuses reused labels); record why in gates.md.
4. Freeze: `python tools\run_shadow_position_reference.py freeze`. It hashes sources, tooling, gates.md, gates.py,
   compiled classes, the base world and the shared config into `manifest.json` and writes a sources ZIP. From here on
   change nothing those hashes cover.
5. Run the groups serially, in order, without builds or analysis in between (about 1.5 hours in total):
   `verify` (3 launches, about 25 min), `screens` (3, about 8 min), `ab` (4, about 20 min), `startup` (16, about 40 min):
   `python tools\run_shadow_position_reference.py verify` and so on. A rejected launch keeps its attempt file; repeat
   it only after fixing a runner bug, under a new `-r2` label, with the amendment written in gates.md.
6. Evaluate: `python docs\benchmarks\2026-10-22-shadow-positions-reference\gates.py gates.json` (needs numpy and
   Pillow). Inspect the three screenshot pairs.
7. Record: rewrite this README with the verdict, add the session 29 entries to `docs/BENCHMARKS.md` (append only) and
   `docs/ARCHITECTURE.md` ("Done in session 29", "Next session (30)"). If every gate passes, promotion is a separate,
   explicit decision; otherwise `render.reuseShadowPositions` is closed and stays off.

## Expectations stated before running

The allocation target should resolve here: about 98% of frames are tick-free at these frame rates, and session 28's
post hoc tick-free comparison (container, not evidence) saw 17-20 KB per frame. The likely deciding gate is gate 2: a
2% frame-mean saving from 20 KB less allocation per sub-millisecond frame is not expected, and failing it closes the
candidate under the frozen rule. That is an acceptable outcome; the gate is not relaxed.

Out of scope for this series: re-ranking the flood fill and entity brightness positions (roadmap item 1's optional
part) needs a JFR attribution with its own rule written first, and is not prepared here.
