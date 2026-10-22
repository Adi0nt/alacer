# Session 23: particle light reuse confirmation

Independent follow-up to session 22. See [protocol.md](protocol.md) for the gates
declared before any launch and [manifest.json](manifest.json) for the measured source.
The retained light implementation and the subsequent vertex NaN verifier fix are preserved.

## Decision: keep reuse off, outside render.defaults

Both fresh eight-launch in-process groups passed. Static median particle pass fell 15.37%,
frame mean 6.35% and CPU/frame 6.33%; pan fell 14.37%, 5.04% and 4.91%. Static pass was
lower in 7/8 launches; pan in 8/8. Pan's median absolute A2/A1 drift was 11.33%, so it also
had to pass the additional noisy-group gate. Every launch remains in the results.

The startup comparison still fails: static allocation rises **65.56%** (74.5 to 123.3 KB/frame)
and pan tick-frame p99 rises **5.66%**, above the declared 5% limit. Startup medians otherwise
show lower pass, whole/ordinary frame and CPU times. The complete gates, rather than a narrow
throughput win, determine selection. See [correctly labelled results](results.md),
[all scored metrics and checks](metrics.json) and [tails](tails.md).

Fresh live verification compared **134,999,655 queries**, including **126,410,060 reused**,
with **zero mismatches**, at scales 2 and 1 with changing blocks and both particle diagnostics.
The Java 25 build passed **82 tests**, no failures/skips, and all 136 main classes target
classfile major 69 (see build-validation.json for the actual class count). The captured
scale 2 static frame was inspected and visibly renders the scene; this is not framebuffer
identity or arbitrary-mod compatibility evidence. Build/tests, live query verification,
visual inspection and measured performance remain separate evidence.

Continue roadmap item 1 (particle movement) after completing the separate light attribution.

That continuation is completed as [session 24](../2026-10-20-particle-movement-attribution/README.md):
fresh branch census, startup dispatch controls and movement JFR, without selecting another
unsubstantiated replacement.

## Separate attribution completed

[Attribution](attribution.md) retains the original draw and supplemental raw-thread-cycle
companions. CPU-clock quantization prevents interpreting the original pair's wall-minus-CPU
residuals as wait time. Cycle companions show lower pass cycles; draw wall/cycles/GPU elapsed
also fall slightly. They do not reproduce session 22's draw growth or establish a driver-mode
explanation. Cycles are not converted to time.

[Escape-analysis results](escape-analysis/results.md) compare original/scalar/scalar/original
with exact pass allocation, JFR and compilation logs. Removing the position argument does not
make elimination dependable: scalar static runs allocate about 79 KB/pass and almost nothing;
pan stays about 76-79 KB/pass. Both interfaces have light-related BlockPos elimination records.
Original light sources are restored byte-identically; the scalar variant remains only in its
separate snapshot. These attribution runs do not waive or repair the failed allocation gate.

## Reproduction

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_light_confirmation.py freeze
python tools/run_particle_light_confirmation.py diagnostics
python tools/run_particle_light_confirmation.py controls
python tools/run_particle_light_confirmation.py startup
python tools/run_particle_light_confirmation.py draws
python tools/run_particle_light_confirmation.py profiles
python docs/benchmarks/2026-10-20-particle-light-confirmation/analyze.py
python docs/benchmarks/2026-10-20-particle-light-confirmation/report.py
```

Frozen manifests and labels cannot be reused. For an independent reproduction copy the
analysis/protocol to a new archive, use `--archive` and `--prefix` on the runner/analyzer,
and freeze that archive before launching. The source identity includes this session's
runner, shared snapshot helper, analysis and protocol. Raw CSV/JFR remain under ignored
`run/`; logs remain under ignored `build/`. Records archive their hashes and actual
launcher executable/command, plus base-world file hashes and shared-config preservation.

`benchmark.toggleReverse=true` changes the scored order to B1/A1/A2/B2. A and B retain
their meanings; both variants receive the existing warmups. Without the flag, the
retained A1/B1/B2/A2 order is unchanged. `profiler.particleDrawDiagnostics=true` adds
benchmark-only attribution hooks; they are absent from ordinary and scored clients.

`retained-evidence-sha256.json` seals session 22 and the NaN investigation at the start
of this follow-up. No archived run is rescored or substituted into these results.

The frozen analyzer's startup Markdown header omits its added ordinary-frame columns.
`report.py` formats unchanged `metrics.json` into correctly labelled `results.md`; it does
not recompute metrics or gates. The frozen analyzer and source identity remain unchanged.
