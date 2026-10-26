# Phases 1–5 audit fixes

Local follow-up to `57ecab64f400736fb8666f9c7ca8ae2a00e0721d`, October 24, 2026.
The earlier audits and benchmark archives are preserved unchanged.

## Corrections

- **TerrainShader promotion / benchmark isolation:** restore the shader and legacy
  alias to off by default and outside `render.defaults`. Both defaults benchmark arms
  now agree with the startup gate. Explicit shader/alias/modern-pipeline benchmark
  toggles still request startup hooks for both runtime arms. Tests cover these
  combinations and verify that benchmark config loading does not create or rewrite
  a persisted settings file. Existing explicit user settings remain explicit settings.
- **Terrain positions:** replace the expansion around the wrong pivot with the
  affine coefficients of vanilla's `T(-8) * S(1.000001F) * T(8)`. A CPU regression
  compares against the existing matrix tracker, including the opposite-sign origin
  counterexample and boundary/subnormal coordinates. Both shader variants use these
  coefficients. GPU arithmetic and the reordered camera projection remain unqualified.
- **Fog:** select radial, signed-depth or absolute-depth fog from the active NVIDIA
  fog-distance mode at each shader pass. The CPU reference distinguishes a 3/4/5
  radial-distance case from planar depth. The shader remains opt-in; this is not a
  native pixel-identity certification of a driver's fog interpolation.
- **GL hygiene:** save and restore incoming program, VAO (decoupled path) and array
  buffer bindings with reusable state and `finally` cleanup. Classic pointers keep
  the incoming VAO/client-array enable state. Foreign vertex-buffer subclasses retain
  the entire vanilla layer before any shader draws. Initialization releases temporary
  shaders/programs/VAOs on failure, restores the setup VAO and caches unsupported
  results until lifecycle cleanup. Telemetry distinguishes requested from successfully
  initialized shader state. Injected-driver tests exercise binding rollback and draw
  exceptions; they do not exercise a real OpenGL context or driver compilation failures.
- **Frustum qualification:** correct column-major projection/model multiplication;
  add independent known forward/backward/far-plane checks. Rename the synthetic sweep
  so it does not advertise a live-client camera qualification.
- **Sound waits:** retry raw permits/spurious park returns until the remaining
  monotonic deadline expires or an interrupt arrives. Timeout conversion saturates
  instead of overflowing. The legacy `unpark` helper now requests an interrupt, matching
  the sound library's wake-and-clear contract; these wakes count as interruptions.
  Deterministic tests cover early returns, remaining time, clock wrap, interruptions
  and timeout overflow. The 16-thread exercise performs 3,200 real waits with verification
  enabled and asserts all 3,200 verified completions, including 1,600 interrupt wakes.
- **Worker leases:** synchronize four concurrent workers over 800 simulated leases,
  compare live array identities and marker contents, check reuse initialization, and
  propagate executor failures. Remove the machine-speed/GC-pressure assertion.
- **Evidence claims:** replace tautological coordinate checks and arbitrary-matrix
  certification with scoped coordinate/offset/counterexample tests. State the remaining
  camera-face/look-vector allocations explicitly. Scheduling tests assert actual
  deadline arithmetic (1.042/2.083 ms at 60 FPS), not elimination of real upload tails.

## Local validation

With `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`:

```powershell
.\gradlew.bat test remapJar --console=plain --info
git diff --check
```

- Full suite: **275 tests, 60 suites, zero failures/errors/skips**.
- Compilation and test executor: **Temurin Java 25.0.4.1**; the explicit executable
  and compiler toolchain are recorded in
  `build/roadmap-audit-fixes/full-tests-and-package-final.log`.
- Remapped JAR builds; all packaged classes have major version **69** (Java 25).
  The new shader math/state classes are included.
- JAR SHA-256: `99badda1f91576d80de4185cba83e27212902772bc71ff38bbe4fc33bffb9490`.
- An earlier focused run passed 44 tests; its log is retained separately as
  `build/roadmap-audit-fixes/focused-tests-01.log`.

No new Minecraft launch, rendered-image comparison, listening-based audio check,
native fallback/context-loss qualification, allocation profile or performance
benchmark was performed. Real lighting queries, whole-search allocation and upload
tail performance retain their separate native validation requirements. No performance
benefit, universal matrix bit identity or completion of all roadmap runtime gates is
claimed. Changes remain local on `main`; no commit or push was performed.
