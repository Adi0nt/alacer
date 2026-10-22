# Session 30: `render.fastCameraSectionFaces` on the reference hardware (run plan)

**Status: prepared, not frozen, not run.** The series, its mechanism and its pass conditions are in [gates.md](gates.md).
This file is the procedure; replace it with the results archive after the series.

## What was prepared (in a Linux container, October 22, 2026)

- **The candidate on this line.** Session 28b's camera-section open faces (`render/SectionFaces`,
  `render.fastCameraSectionFaces`, off by default, not in `render.defaults`) was developed in parallel on
  `claude/vibrant-bardeen-9adcot` from `main`. It is merged here on top of sessions 28-29 and the video settings work,
  with one test conflict resolved by keeping both sides. Build and 123 tests pass on JDK 25.0.5.
- **Live correctness on the merged line** (container: Xvfb, Mesa llvmpipe, 854x480, view distance 12; not evidence for
  selection): see "Container verification" below.
- `tools/run_section_faces_reference.py`: the Windows runner (session 29's launcher, manifest, freeze and rejection
  checks; session 27's fresh-flight checks; separate launches only).
- [gates.py](gates.py): the evaluator. Checked on synthetic pair data (pass, no-benefit closure, regression, counter
  limits, unresolved pairs, failed verification) and its CSV reader on real container CSVs.

## Procedure on the reference machine

The checkout there is untracked and shared: copy the changed files in (do not commit, push, stash, reset or switch
branches there), keep each file's line endings, and leave other sessions' scratch directories alone. Files:
`src/main/java/net/adiont/alacer/render/SectionFaces.java`, `src/main/java/net/adiont/alacer/render/ChunkVisibility.java`,
`src/main/java/net/adiont/alacer/config/AlacerConfig.java`, `src/main/java/net/adiont/alacer/benchmark/BenchmarkRunner.java`,
the two `SectionFaces*Test.java` files and `AlacerConfigTest.java`, `tools/run_section_faces_reference.py`, and this
folder (`README.md`, `gates.md`, `gates.py`). Alternatively build from this branch in a separate clone, with the base
world and `run/config/alacer.properties` copied in.

1. Close every other client; nothing else heavy running. Confirm `run/saves/alacer-benchmark` is the seed-1789 base
   world sessions 26-29 used and `run/config/alacer.properties` is unchanged.
2. Build with the explicit JDK (no launch may compile afterwards):
   `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` then `gradlew.bat build`.
3. Smoke (before the freeze; attempts only, about 5 minutes):
   `python tools\run_section_faces_reference.py smoke`. Both must pass. Open `run\screenshots\alacer-sf30-smoke-verify-*`
   and check the captures look right. A failure that needs a tooling change means fixing it, rebuilding, and repeating
   the smoke under new labels (the runner refuses reused labels); record why in gates.md.
4. Freeze: `python tools\run_section_faces_reference.py freeze`. From here on change nothing the manifest covers.
5. Run the groups serially, in order, without builds or analysis in between (about 2 hours in total):
   `verify` (4 launches), `screens` (3), `startup` (40): `python tools\run_section_faces_reference.py verify` and so on.
   A rejected launch keeps its attempt file; repeat it only after fixing a runner bug, under a new `-r2` label, with the
   amendment written in gates.md.
6. Evaluate: `python docs\benchmarks\2026-10-22-section-faces-reference\gates.py gates.json` (needs numpy and Pillow).
   Inspect the three screenshot pairs.
7. Record: rewrite this README with the verdict, add the session 30 entries to `docs/BENCHMARKS.md` (append only) and
   `docs/ARCHITECTURE.md`. If every gate passes, promotion is a separate, explicit decision; otherwise the option is
   closed and stays off.

## Expectations stated before running

The candidate is exact (verified answers), so the counter and screenshot gates should pass. The allocation direction
should hold. The open question is size: at reference frame rates the flood fill runs in only a few percent of frames,
so the pooled `terrain_setup` saving may be clear while the frame-level benefit gate (gate 2) is not met. Closing the
option as "exact, no demonstrated benefit" is an acceptable outcome; the gate is not relaxed.

## Container verification (correctness only)

Linux container, Temurin 25.0.5+8, Xvfb, Mesa llvmpipe (software GL, about 20 FPS), 854x480, view distance 12, this
branch's merged build, options passed as the runner passes them (`-Palacer.*`), `render.fastCameraSectionFaces=true`
with `.verify`. Frame times here judge nothing: about 80% of frames contain a client tick at this frame rate.

| Launch | Scene, time scale | Section reads | Flood fills | Reused answers | Compared with vanilla | Mismatched |
|---|---|---|---|---|---|---|
| base-world generation (discarded results) | d0, 0.5 | 27 | 27 | 2,915 | 2,942 | 0 |
| verify | d200, 1 | 170 | 170 | 3,394 | 3,564 | 0 |
| verify | terrain, 1 | 183 | 183 | 6,203 | 6,386 | 0 |
| verify | near flight, 1 | 208 | 208 | 2,454 | 2,662 | 0 |
| verify | high flight, 1 | 164 | 164 | 2,415 | 2,579 | 0 |
| option off | d0, 0.5 | 0 | 0 | 0 | 0 | 0 |

Total with the option on: 18,133 answers compared, 0 mismatched. Both flights logged the byte-identical fresh world
copy, so the runner's flight options are accepted by `BenchmarkRunner`. No GL error, mixin/injection failure, abort or
timeout in any launch. The off launch confirms the runner's "0 reads, fills and reuses when off" check.
