# Release frame pacing check: protocol (written before any launch)

Question: should `render.fastDisplaySync`, `render.fastSwapBuffers` or `render.suppressThreadYield`
(added 2026-10-25, no archived measurement) be on by default in 1.0.0?

Mode: **Standard** (docs/BENCHMARK_MODES.md). The control is the 1.0.0 candidate build with all
three options off; each run toggles one option in process (static and pan, A = off, B = on).
Build defaults plus explicit JVM overrides, Temurin Java 25, 1920x1080, 2 GiB heap, the seeded
`alacer-benchmark` world, no mobs or chests, verification and detail profiling off.

| Launch | Toggle | Frame limit | Order |
|---|---|---|---|
| 1 | `render.fastDisplaySync` | `benchmark.fpsLimit=120` | A B B A |
| 2 | `render.fastDisplaySync` | `benchmark.fpsLimit=120` | B A A B (`toggleReverse`) |
| 3 | `render.fastSwapBuffers` | unlimited | A B B A |
| 4 | `render.fastSwapBuffers` | unlimited | B A A B |
| 5 | `render.suppressThreadYield` | unlimited | A B B A |
| 6 | `render.suppressThreadYield` | unlimited | B A A B |

Budget: at most these six launches, no automatic retries, stop on a functional failure.

Gates, per option, required in **both** launches and **both** static and pan:

- Unlimited (`fastSwapBuffers`, `suppressThreadYield`): median frame time at least 3% lower with
  B, and B's p99 frame time and client CPU per frame no more than 3% higher.
- Limited (`fastDisplaySync`): B's median frame rate within 2% of the 120 FPS target, B's frame-time
  p99 and standard deviation at least 3% lower, and B's client CPU per frame no more than 3% higher.

Standard mode cannot support a release decision or a small-improvement claim. Whatever the
result, the three options ship **off** in 1.0.0. Passing every gate would only justify an Extended
campaign before a later release enables the option. Any failed gate keeps the option off without
further work in this release.
