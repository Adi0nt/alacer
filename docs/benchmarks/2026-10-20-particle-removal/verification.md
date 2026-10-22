# Verification: `render.fastParticleRemoval.verify`

Both launches ran the frozen build (`implementation.json`), Java 25, static 10 s and pan 15 s, with
`render.fastParticleRemoval=true -Palacer.render.fastParticleRemoval.verify=true`. In verify mode every
dead-particle removal runs the fast path on a copy of the list and vanilla's `ArrayList.removeAll` on
the real list (whose result is used), then compares the survivors element by element by identity,
the survivor count and the return value. `render.fastParticleLight` was off in both (the persisted
`run/config` value; see the README); it does not touch removal.

| Launch | Scale | Removal probes | Removals compared | Particles removed | Vanilla fallbacks | Mismatches |
|---|---:|---|---:|---:|---:|---:|
| `pr-verify-1` | 1 | no | 7,200 | 165,751 | 0 | **0** |
| `pr-verify-2` | 2 | yes, 7,253 probes | 7,176 | 189,156 | 80 | **0** |

"Removals compared" counts `removeAll` calls (eight per client tick, one per atlas/layer list,
most of them on empty lists); "particles removed" sums what the fast path removed in the compared
calls. Fallbacks are calls where the fast path declined and vanilla's call ran alone: in
`pr-verify-2` exactly the 80 ticks on which the `equals`-overriding probe's list had dead particles
(80 such probes were added).

`pr-verify-2` adds `benchmark.particleRemovalDiagnostic` (`ParticleProbes`, verification only), which
every tick adds particles that exercise the cases where a removal could differ from `removeAll`.
The log's case counts:

| Case | Count |
|---|---:|
| Layer changes made while ticking (`setAlpha` -> `changeLayer`), by switching probes | 32,894 |
| ... of which on the tick the probe died (it is in one list's dead list but already in the other list) | 3,119 |
| Particles added to a full (4,000) list while that list was ticking, so vanilla's `add` evicted index 0 mid-loop | 43,960 |
| Probe deaths while an equal-id twin (`setNetworkId`) was alive, so `removeAll` also removes the live twin | 787 |
| Probes added twice to the same list | 797 |
| Probes overriding `equals` (fast path must fall back for their list) | 80 |

Scale 2 also keeps the misc-atlas list at the cap from the scenario's own emission. All cases gave
identical survivors. Unit tests (`KeyedRemovalTest`, `ParticleRemovalKeysTest`) additionally compare
against `ArrayList.removeAll` on random lists with duplicates, shared and negative ids, nulls, empty
and unmatched dead lists, and check the modification count, the refusal paths and real particle
classes' `Entity` equality.

An earlier probe build (`pr-verify-r1`, `pr-smoke-verify`; before the probe counters and the bounded
switching probe) also reported 0 mismatches; it is not used. With probes that switch layers on
every tick even after dying, vanilla never removes them (they leave each list after being added to
its dead list): a vanilla quirk, preserved by both paths, that made the probe population grow.
