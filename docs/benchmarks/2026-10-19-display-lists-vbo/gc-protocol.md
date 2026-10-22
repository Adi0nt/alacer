# Post-hoc GC companions

The eight scored controls already completed and passed validation before these companions
were added. Their fly maxima contain isolated 133–392 ms frames in multiple wall-timed
sections, with little upload time. No GC log exists for the controls. Do not assign their
causes from the companions or retroactively add companions to selection.

Run one fresh copied-world DL launch and one VBO launch using the same frozen game sources,
Java 8, camera/scene/settings and detail-off profiler. Add only Java 8 GC details/date/uptime
and application-stopped-time logging through `gc.init.gradle`. No JFR or screenshots. These
logs can perturb execution too. No performance selection or diagnostic-overhead estimate.

`analyze_gc.py` reuses the strict CSV/settings/backend/log validator, exports GC/safepoint
events and aligns the worst frames with the exported wall-clock/frame anchor. GC event
stamps are start times. Application-stopped-time lines are emitted at safepoint end; their
total includes synchronization, and the separate stopping-threads field records synchronization
time. Semantics checked against the primary
[OpenJDK 8 RuntimeService source](https://github.com/openjdk/jdk8u/blob/master/hotspot/src/share/vm/services/runtimeService.cpp).
Do not add the overlapping GC and total-safepoint intervals or infer which thread delayed entry.

Alignment allowance = exported anchor uncertainty + 2 ms log rounding/formatting allowance
+ observed full-range spread of (date stamp minus JVM uptime). Reject spread >=50 ms for
manual clock/log review. Report the observed spread and allowance; small clock/log latency
and no unobserved clock jump are assumptions, not precision guarantees. Conservative overlap
shrinks each frame by that allowance at both ends. Whole event duration totals include
events intersecting a phase and are not exact clipped phase pause totals. The per-frame
overlap is clipped. Sources and protocol for selection remain frozen and unchanged.

```powershell
& docs/benchmarks/2026-10-19-display-lists-vbo/run_gc.ps1 -Prefix new-gc
python docs/benchmarks/2026-10-19-display-lists-vbo/analyze_gc.py --prefix new-gc
```

The final companion labels are `bvgc-dl` and `bvgc-vbo`; raw `.gc.log` files stay ignored
under `run/`. `gc.json` retains parsed events, raw event lines, file hashes, frame overlaps
and complete companion metrics. Other prefixes write into `series/<prefix>/`.
