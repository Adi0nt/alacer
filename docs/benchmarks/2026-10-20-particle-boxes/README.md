# Session 18: immutable particle movement boxes

This session investigates the remaining Entity.move allocations with Java 25 and the eight
shipped optimizations enabled. Session 17's cached movement reads remain false in both paths.
The new `render.reuseParticleBoxes` experiment is **false**, outside `render.defaults`, and
its Entity mixin is absent unless explicitly requested at startup.

The candidate reuses an exact immutable vanilla Box only when Entity.move calls moved or
expanded with three zero offsets and finite, ordered, nonzero coordinates. Both zero-offset
signs are accepted; coordinate zeros, inverted/nonfinite bounds, subclasses, other entities,
world implementations and threads fall back to the original virtual method. Vanilla still
executes all movement, collision gathering/ordering, axis clipping, shape setters and
callbacks. No mutable Box pool or retained geometry is introduced.

## Decision: keep Box reuse disabled

All eight warmed Java 25 A/B/B/A launches completed with matching source identities and
unchanged shared config. **All four density/phase groups failed the predeclared gates.**
Allocation per ticking frame fell in all 16 comparisons, but only **0.42–1.22%**; every group
missed the required 2% median reduction. Particle tick regressed in **7 of 16** comparisons
(range -8.04% to +7.67%); neither static density achieved a 2% median improvement.

| Density / phase | Median particle tick | Median allocation/tick frame | Median all-frame mean | Median all-frame p95 | Median client CPU/frame | Gates |
|---|---:|---:|---:|---:|---:|---|
| 1 / static | -0.68% | -1.04% | -7.52% | -13.03% | -7.21% | FAIL |
| 1 / pan | -2.38% | -0.78% | -6.27% | -5.74% | -6.21% | FAIL |
| 2 / static | +0.66% | -0.91% | +1.44% | +2.85% | +0.26% | FAIL |
| 2 / pan | -2.05% | -0.88% | -5.27% | -7.15% | -5.21% | FAIL |

Favorable all-frame medians do not establish an improvement: A2/A1 frame-mean drift ranged
from **-20.23% to +49.97%**, and B-versus-A2 frame changes are retained separately. All-frame
means, p95 and CPU/frame each regressed in 6/16 comparisons. Tick-frame p99 regressed in
9/16 (up to **18.07%**); dense-static median tick-frame p99 rose **9.58%**. Tick tails contain
roughly 200 static/300 pan frames per variant. No launch is excluded as an outlier. Particle
population differences stayed within 0.36%. Non-tick allocation is essentially unchanged.

The guard is correct within the tested scope but too narrow to select on these measurements.
Keep `render.reuseParticleBoxes=false`, outside `render.defaults`. Session 17's independent
cached-read experiment also remains disabled, with its failed selection and lifecycle gaps
unchanged. All eight shipped optimization defaults are preserved.

Evidence and decision are kept separately:

- [Predeclared protocol](protocol.md) defines correctness and full frame/tick/CPU/allocation
  gates before implementation and selection measurements.
- [Correctness](verification.md) records direct raw-bit comparisons, full movement replay,
  boat/entity collision evidence, the corrected diagnostic setup failure and remaining gaps.
- [Source/profile investigation](profile.md) and [baseline JFR](baseline-jfr.json) scope actual allocation stacks to particle ticks and
  scored phase windows, with source ZIP and recording identities. Intrusive timings and
  sampled weights are attribution, not selection evidence.
- [Candidate JFR](candidate-jfr.json) is supplemental attribution. The first candidate
  profile overlapped a build/test command and is unsuitable for paired timing claims.
- [Comparison](comparison.md) and [metrics](metrics.json) retain all scored launches and
  drift/allocation/populations. [Tails/CPU](tails.md) includes complete percentile and process
  CPU comparisons. [Selection](selection.json) records the final decision.
- [Implementation patch](implementation.patch) is relative to the pre-Session-18 source ZIP;
  source snapshots preserve both versions in this largely untracked checkout.
- [Validation/provenance](validation.json) checks current source identity, all control builds,
  environment/config preservation, Java 25 bytecode, tests and recording hashes. Test/build
  logs and the final launcher/analyzer sources are retained in separate ZIPs.

Reproduction (installed Java 25; no shared config edits):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build --console=plain
python tools/run_particle_boxes.py profile --prefix fresh --archive docs/benchmarks/fresh-boxes
python tools/run_particle_boxes.py diagnostics --prefix fresh --archive docs/benchmarks/fresh-boxes
python tools/run_particle_boxes.py controls --prefix fresh --archive docs/benchmarks/fresh-boxes
python docs/benchmarks/2026-10-20-particle-boxes/analyze.py --archive docs/benchmarks/fresh-boxes
```

The runner refuses reused raw labels/logs, overlapping game clients and source/config changes
during a launch. It checks Java/backend/scenario identity, build-defaults-and-jvm-overrides,
every effective option and complete phases. Source ZIPs, commands, exports/log/JFR hashes and
config hashes remain reviewable. Raw data stays in ignored run/ and build/.

Historical Session 17/Java 8 archives and the shared config are preserved. No optimization
defaults or driver settings changed. No implementation was copied from another performance
mod. Nothing was committed, pushed or published.
