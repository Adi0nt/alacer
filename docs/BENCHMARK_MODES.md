# Benchmark effort and evidence

Session 32 workflow guidance. These are time budgets, not new statistical gates.
No rendering default, existing calibration rule, or historical verdict changes.
The targets below are planning targets; this session did not validate new shortened workloads.

| Mode | Target | Existing mechanism | Decision it supports |
|---|---|---|---|
| Quick | 2–5 minutes | One affected scene, shortened explicit `benchmark.phaseSeconds` / `benchmark.timeScale`, optional existing in-process `benchmark.toggle` / `toggleBlocks` for runtime-safe options | Correctness checks and obvious regressions; descriptive feedback only |
| Standard | 10–30 minutes | Two to four complete affected-scene launches, explicit control/candidate order (ABBA when four), existing frame CSVs, references, process/client CPU, environmental monitor | Whether a promising idea deserves further work; show every observation, range, order and uncertainty |
| Extended | Existing longer campaign | Separately versioned smoke, source/artifact/world/config/JDK freeze, calibration, existing paired runner | Release decisions or small improvement claims, only when all predeclared stability and power requirements pass |

The existing `StabilityOptions` supports phase lengths, reference phases, repeat segments and alternating
ABBA/BAAB static toggle blocks. `aa_pilot.py` already supplies quiet checks, fixed affinity, attempt retention,
retry caps, freeze checks and resumable stage boundaries. Reuse these mechanisms; there is no second scoring engine.
In-process toggles cannot evaluate options that need startup mixin selection, and they do not establish fresh-launch
repeatability. Keep a dedicated protocol and labels for shortened/exploratory workloads; never point them at aa31 or aa31w2.

For Quick and Standard, choose the affected scene, complete order, maximum launch count and hard deadline
before starting. Count failed/interrupted attempts against the budget. No automatic retries. Do not start a client
without enough time left to finish it. Stop on a functional failure, large unexplained timing-state change, or deadline.
Inspect failed evidence before spending another launch. Use the same explicit Java 25 JVM for Gradle and client,
build defaults plus explicit JVM overrides, source identity and shared-config/world hashes as the longer workflow.
Do not pool dissimilar configurations or change acceptance after looking at performance. Runtime targets do not
waive correctness checks, environmental controls, or any threshold needed for a claim.

Before a new calibration, inspect existing records rather than repeating all preparation:

```powershell
python tools/aa_diagnose.py docs/benchmarks/2026-10-22-aa-pilot/windows2 build/aa31w2-diagnosis.json --bins
```

The output must be new. The tool reads existing CSVs/JSON/optional thread traces and writes only that new file.
Five-second bins retain tails and weight individual frames correctly. CPU-distribution hints (`low-client-cpu`,
`mixed`, `client-heavy`) are descriptive only, never exclusions or acceptance thresholds. A change in hints across
references warns that warmup alone has not established a stable execution state. It cannot name a driver cause
without independent attribution. Optional `--bins` costs offline analysis time; keep the generated result and
reuse it. Run bulk analysis and tests after measurement, not alongside clients.

Use JFR/native thread collectors only for attribution. Their own CPU and perturbation must be reported.
Routine optimization comparisons should use the existing environmental monitor and lightweight in-client timing.
Disabling environmental monitoring leaves missing acceptance evidence, even though CSV timing still works.

Do not spend another multi-hour calibration/pilot on an unresolved execution-state change. First use the smallest
separate experiment that controls the suspected source. If a new protocol or settling strategy is justified, version
it, run fresh smoke and validate it before scoring. Reuse old diagnostic evidence, never old calibration observations
under a changed protocol. The 5% calibration limit, 3% paired gate and requirements for a 2% power claim stay intact.
