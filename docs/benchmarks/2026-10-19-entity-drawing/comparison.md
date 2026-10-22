# Entity drawing — GL call counts and `render.skipZeroTranslates`, 200 and 1000 mobs

Generated from the `runs/` summaries and the game log. All launches 1920x1080, render distance
12, NVIDIA Threaded optimization forced on, every other Alacer option at its default.

| Launch | Kind |
|---|---|
| `edc-det200` | Detail counting (`profiler.entityDetail=true`), no toggle, 200 mobs |
| `edc-detab` | Detail counting, toggle `render.skipZeroTranslates`, 200 mobs |
| `edc-verify` | `render.skipZeroTranslates.verify=true`, 200 mobs |
| `edc-r1` to `edc-r3` | A/B, first build (a `@WrapOperation` that boxed the arguments; not used for the result) |
| `edc-r4` to `edc-r6` | A/B, final build (`@WrapWithCondition`); `edc-r5` with 1000 mobs |

## GL calls per drawn entity (`edc-det200`, static phase, 58 entities drawn per frame)

`calls` = all counted GL calls; `matrix` = matrix operations (push, pop, translate, rotate,
scale), of which `zeroTranslate` translate by (0, 0, 0); `callList` = display lists (one per
model part); `stateChanges` = `GlStateManager` setters that reached OpenGL, out of
`stateRequests`; `draws` = immediate-mode draws (shadows), each about 14 GL calls.

```
CowEntity      n=62881 calls=66.00 matrix=32.00 zeroTranslate=12.00 callList=6.00 stateChanges=13.00 draws=1.00 stateRequests=18.00
WolfEntity     n=77392 calls=71.69 matrix=41.00 zeroTranslate=12.00 callList=8.00 stateChanges=12.06 draws=0.69 stateRequests=17.13
PigEntity      n=62881 calls=64.46 matrix=32.00 zeroTranslate=12.00 callList=6.00 stateChanges=12.54 draws=0.92 stateRequests=17.46
SheepEntity    n=77392 calls=94.94 matrix=58.00 zeroTranslate=24.00 callList=12.00 stateChanges=13.44 draws=0.75 stateRequests=19.56
all            n=280546 calls=75.21 matrix=41.66 zeroTranslate=15.31 callList=8.21 stateChanges=12.76 draws=0.83 stateRequests=18.07
state changes per entity: cull=2.00 rescale_normal=2.00 blend=1.86 active_texture=2.00 bind_texture=2.21 color=0.83 depth_mask=1.86
```

Per frame (static): 4,362 counted GL calls, 2,416 matrix operations (888 zero translations), 476
display lists, 740 state changes out of 1,048 requests, 58 lightmap coordinates, 48 shadow draws.
Pan: 4,819 calls, 976 zero translations. Fly: almost no entities in view.

With the option on (`edc-detab`, segment `static-b1`), only the zero translations disappear:

```
all            n=128296 calls=59.90 matrix=26.34 zeroTranslate=0.00 callList=8.21 stateChanges=12.76 draws=0.83 stateRequests=18.07
```

Per frame 4,362 → 3,474 calls (static) and 4,840 → 3,841 (pan); every other counter is
identical between A and B.

## Verification (`edc-verify`)

500+ zero translations compared (the log line is written every 500), 0 changed the matrix.
This run used the first build, whose comparison logic is identical to the final one: read the
current matrix, issue the translation, read again, compare all 16 elements.

## A/B (`edc_analyze.py`)

Segments A, B, B, A per launch; "vs a2" compares the B segments with the second A segment. The
driver mode is judged from the process CPU of the A segments (above 1.5 cores = threaded); with
all current defaults on, threaded A segments already wait in the swap (0.2-0.3 ms), so the
earlier swap criterion no longer applies. All six launches landed in the threaded mode.

`ent` = `entities`, `loop` = `entity_loop`, `cpu` = client thread CPU ms per frame (all
frame-weighted means of a1+a2 and b1+b2).

```
edc-r1 static threaded cores 1.87  frame 1.558->1.694 (+8.7%)  vs a2 +5.6%  ent 0.572->0.591  loop 0.516->0.533  render 1.258->1.318  swap 0.197->0.257  cpu 1.329->1.373
edc-r1 pan    threaded cores 1.88  frame 2.175->2.171 (-0.2%)  vs a2 +3.4%  ent 0.655->0.634  loop 0.613->0.594  render 1.845->1.799  swap 0.177->0.209  cpu 1.944->1.915
edc-r2 static threaded cores 1.95  frame 3.821->3.892 (+1.9%)  vs a2 +5.2%  ent 1.536->1.502  loop 1.453->1.420  render 2.459->2.437  swap 0.811->0.873  cpu 2.865->2.882
edc-r2 pan    threaded cores 1.91  frame 4.298->4.659 (+8.4%)  vs a2 +6.5%  ent 1.597->1.718  loop 1.527->1.639  render 3.297->3.430  swap 0.428->0.515  cpu 3.581->3.843
edc-r3 static threaded cores 1.91  frame 1.677->1.729 (+3.1%)  vs a2 +11.8%  ent 0.622->0.614  loop 0.559->0.551  render 1.351->1.347  swap 0.209->0.258  cpu 1.413->1.445
edc-r3 pan    threaded cores 1.92  frame 2.193->2.192 (-0.0%)  vs a2 -1.0%  ent 0.660->0.651  loop 0.616->0.609  render 1.860->1.849  swap 0.180->0.185  cpu 1.964->2.009
edc-r4 static threaded cores 1.92  frame 1.610->1.673 (+3.9%)  vs a2 +1.7%  ent 0.558->0.570  loop 0.504->0.515  render 1.247->1.277  swap 0.250->0.277  cpu 1.331->1.357
edc-r4 pan    threaded cores 1.91  frame 2.362->2.162 (-8.5%)  vs a2 +1.7%  ent 0.701->0.649  loop 0.636->0.590  render 1.996->1.831  swap 0.192->0.175  cpu 2.079->1.959
edc-r5 static threaded cores 1.88  frame 3.281->3.197 (-2.5%)  vs a2 -1.8%  ent 1.329->1.259  loop 1.224->1.160  render 2.156->2.040  swap 0.709->0.761  cpu 2.480->2.273
edc-r5 pan    threaded cores 1.89  frame 4.027->3.917 (-2.8%)  vs a2 -0.7%  ent 1.424->1.411  loop 1.352->1.338  render 2.992->2.855  swap 0.511->0.531  cpu 3.251->3.214
edc-r6 static threaded cores 1.86  frame 1.884->1.781 (-5.5%)  vs a2 +10.7%  ent 0.641->0.619  loop 0.575->0.556  render 1.402->1.364  swap 0.335->0.287  cpu 1.519->1.436
edc-r6 pan    threaded cores 1.88  frame 2.233->2.634 (+18.0%)  vs a2 +19.6%  ent 0.625->0.722  loop 0.589->0.681  render 1.841->2.128  swap 0.227->0.288  cpu 2.016->2.318
```

`edc-r1` to `edc-r3` passed the arguments through MixinExtras' `Operation.call(Object...)`,
which boxed three floats and allocated an array for every model part translation, in A as well
as B: 122-164 KB allocated per frame at 200 mobs instead of 57 KB. A was therefore not
vanilla-equivalent, and these three launches are listed only for completeness.

## A/B, final build (`edc_table.py`, `edc-r4` to `edc-r6`)

| Launch | Phase | Mode | Mobs | `entities` A → B (ms) | Swap A → B (ms) | Client CPU A → B (ms/frame) | Frame a2 → B (ms) | B vs a2 | p99 a2 → B (ms) |
|---|---|---|---|---|---|---|---|---:|---|
| `edc-r4` | static | threaded | 209 | 0.558 → 0.570 | 0.25 → 0.28 | 1.33 → 1.36 | 1.65 → 1.67 | +1.7% | 4.36 → 4.60 |
| `edc-r4` | pan | threaded | 209 | 0.701 → 0.649 | 0.19 → 0.18 | 2.08 → 1.96 | 2.12 → 2.16 | +1.7% | 5.50 → 5.65 |
| `edc-r5` | static | threaded | 1009 | 1.329 → 1.259 | 0.71 → 0.76 | 2.48 → 2.27 | 3.26 → 3.20 | −1.8% | 8.15 → 8.23 |
| `edc-r5` | pan | threaded | 1009 | 1.424 → 1.411 | 0.51 → 0.53 | 3.25 → 3.21 | 3.94 → 3.92 | −0.7% | 10.19 → 10.39 |
| `edc-r6` | static | threaded | 209 | 0.641 → 0.619 | 0.34 → 0.29 | 1.52 → 1.44 | 1.61 → 1.78 | +10.7% | 4.05 → 4.67 |
| `edc-r6` | pan | threaded | 209 | 0.625 → 0.722 | 0.23 → 0.29 | 2.02 → 2.32 | 2.20 → 2.63 | +19.6% | 5.79 → 7.49 |

`edc-r6` was disturbed: a1 and b1 (static) and b1 (pan) are 15-30% slower than the other
segments, with higher tick and swap times, which no rendering option explains.

Allocation per frame (static, 200 mobs): A 56.7-58.5 KB, B 56.9-57.6 KB, the same as before
this session.

## Section breakdown, threaded mode, second A segment (static)

| Launch | Mobs | Frame | `render` | `entities` | `terrain_setup` + `chunk_upload` + `camera_setup` | Rest of `render` | Swap | Tick |
|---|---|---|---|---|---|---|---|---|
| `edc-r4` | 209 | 1.646 | 1.249 | 0.564 | 0.046 | 0.639 | 0.279 | 0.086 |
| `edc-r5` | 1009 | 3.257 | 2.137 | 1.333 | 0.077 | 0.727 | 0.717 | 0.348 |
