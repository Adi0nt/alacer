# F3 overlay and performance graph

The debug display keeps Minecraft's pixel font and gray text backgrounds, with
colored FPS, coordinates and light information. Text is refreshed at 200 ms intervals;
graph statistics refresh every 400 ms. Columns are clipped to their own half of the
screen. Compact layouts keep key world information above a shorter, docked graph;
hiding the graph restores space for the full debug lists.

Vanilla still supplies game information, target-block properties and reduced-debug
filtering. Disabling `profiler.enabled` retains the vanilla debug overlay and charts.

| Control | Action |
| --- | --- |
| F3 | Toggle the debug display |
| Shift+F3 or Alt+F3 | Open F3 with the performance graph |
| F8, or the configured **Toggle performance graph** binding | Toggle the standalone graph |
| F4, while the graph is visible | Cycle frame milliseconds, subsystem milliseconds, subsystem percentages, and top subsystem detail |
| Ctrl+F4 | Cycle automatic, 50 ms, 33.3 ms and 100 ms scales |
| Shift+F4 or Shift+graph binding | Freeze or resume the history |
| F7 / F9 | Existing performance HUD / full profile export |

`profiler.graph=false` controls startup visibility. `profiler.graphMode=0|1|2|3` selects
the startup mode. Keyboard changes affect the running client. F4 does nothing while
the graph is hidden, while a screen is open, or for repeated keyboard events. F8 has
one handler, using the registered binding rather than a second hard-coded toggle.

## Reading the display

- Statistics use up to 480 recent frames. The scrolling plot shows the newest frames
  that fit its horizontal pixel count.
- Average FPS is the reciprocal of mean frame duration. The 1% low is the reciprocal
  of the mean of the slowest `ceil(count * 0.01)` durations. P99 uses nearest rank.
  F3 marks the low statistic as warming up before 100 frames are available.
- Subsystem rows stay in a fixed order. Inputs are bounded so the stack cannot exceed
  the measured frame duration; negative measurements cannot create negative areas.
- **Other / wait** is unmeasured frame wall time, not a diagnosis of a stall or GC
  pause. JVM collection observations mark frames, but their durations are not added
  on top of section wall times that may already include them. GC totals are JVM bean
  totals, not measured stop-the-world pauses.
- **Client alloc** uses the profiler's client-thread byte counter and measured elapsed
  frame time, in MiB/s. A zero-byte sample is valid; unavailable counters show `n/a`.
  Heap growth is never substituted for allocation. Other threads are not included.
- Backend labels distinguish actual VBO/display-list selection from shader state;
  merely requesting a shader does not make it active.

Display sampling stops when neither F3 nor the graph is visible, when menus are open,
or when HUD rendering is hidden. Frozen history survives temporary hiding; world
changes reset it. Benchmark launches disable this additional display sampling while
retaining the original profiler ring, listener and export paths.

## Validation boundary

Automated tests cover layout bounds at several GUI sizes, disjoint accounting,
percentiles and tail means, allocation availability, ring wrap, frozen snapshots,
scale selection and preservation of the original profiler/listener when display
sampling is disabled. The Java 25 suite and remapped JAR build are recorded under
`build/f3-finish/`.

An isolated Java 25 native client reached the title screen. Desktop automation was
stopped at the user's request before world entry. In-game F3 layout, controls and GL
rendering were not visually confirmed. The preview client was closed, and hashes
confirmed that shared `run/options.txt` and `run/config/alacer.properties` were unchanged.
No performance improvement, benchmark certification or final visual approval is claimed.
