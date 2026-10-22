# Session 23 supplemental thread-cycle attribution

Declared after the two main draw companions showed negative wall-minus-CPU residuals
from Windows' coarse ThreadMXBean clocks, before enabling or launching this supplement.
Keep the original source manifest and those raw measurements unchanged. CPU residuals
from those companions are unreliable as quantitative driver wait estimates.

Add a command-line-only `profiler.particleThreadCycles` diagnostic to the already gated
draw diagnostic, using Windows QueryThreadCycleTime through Java 25's foreign function
API. Retain raw cycles and sample counts; never convert cycles to elapsed nanoseconds.
Microsoft documents that relationship as processor-dependent. Own the native arena,
thread handle and buffer on the client thread and close them at diagnostic finish.
Unsupported platforms/API failures must report unavailable. Default/scored clients have
neither draw hooks nor native cycle probes.

Run a small standalone helper check for monotonic CPU-work reads, ownership and closed
state before live use. Then freeze a separate manifest and collect two scale 1 draw
companions in A/B/B/A and B/A/A/B order, same copied-world protocol, all shipped
optimizations on and all independent experiments off. No scored performance result is
replaced or re-evaluated by these companions. Every draw/pass must have a cycle sample,
and GPU query issued/completed/dropped/outstanding counts must remain reported.

Compare per-pass draw host wall, raw thread cycles and GPU-timestamp elapsed time. A host
wall increase with stable cycles and GPU duration is consistent with waiting; increased
cycles indicates client-thread work. Neither cycle counts nor GPU timestamps establish
an exact partition of host time into driver/GPU wait, scheduler delay and driver worker
work. Preserve that limitation if no stable causal pattern appears. No selection or
default change follows from this attribution.

Primary API references:
- https://learn.microsoft.com/en-us/windows/win32/api/realtimeapiset/nf-realtimeapiset-querythreadcycletime
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html
