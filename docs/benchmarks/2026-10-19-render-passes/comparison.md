# Render passes — where the rest of `render` goes, 200 mobs

Generated from the `runs/` summaries. Two normal launches (no toggle), 1920x1080, render
distance 12, fancy graphics and fancy clouds, 200 mobs, NVIDIA Threaded optimization forced on,
every Alacer option at its default. Both launches in the threaded mode (process CPU 1.85-2.33
cores). All values are means in ms per frame.

| Launch | Kind |
|---|---|
| `rp-r1`, `rp-r2` | New pass sections (`world`, `lightmap`, `pick`, `sky`, `clouds`, `terrain_*`, `particles`, `weather`, `hand`, `gui`), no toggle |

## Per phase

`untimed` = `world` minus every section inside it (clear, projection setup, fog, block outline,
mining progress, world border and the profiler calls themselves). `outside world` = `render`
minus `world` minus `gui` (mouse input, post-processing).

| | r1 static | r2 static | r1 pan | r2 pan | r1 fly | r2 fly |
|---|---:|---:|---:|---:|---:|---:|
| frame | 1.614 | 1.681 | 2.301 | 2.320 | 1.951 | 2.024 |
| `render` | 1.308 | 1.280 | 2.030 | 2.046 | 1.717 | 1.767 |
| `world` | 1.295 | 1.268 | 2.016 | 2.031 | 1.702 | 1.750 |
| `entities` | 0.592 | 0.572 | 0.656 | 0.655 | 0.172 | 0.177 |
| **`clouds`** | **0.480** | **0.474** | **0.459** | **0.466** | **0.471** | **0.482** |
| `terrain_setup` | 0.047 | 0.045 | 0.553 | 0.547 | 0.491 | 0.527 |
| `chunk_upload` | 0.001 | 0.001 | 0.137 | 0.153 | 0.379 | 0.378 |
| `terrain_solid` | 0.050 | 0.049 | 0.056 | 0.055 | 0.073 | 0.070 |
| `terrain_cutout_mipped` | 0.016 | 0.016 | 0.021 | 0.020 | 0.019 | 0.019 |
| `terrain_cutout` | 0.015 | 0.015 | 0.018 | 0.018 | 0.017 | 0.017 |
| `terrain_translucent` | 0.013 | 0.013 | 0.017 | 0.018 | 0.013 | 0.014 |
| `particles` | 0.013 | 0.013 | 0.014 | 0.014 | 0.002 | 0.002 |
| `pick` | 0.011 | 0.012 | 0.019 | 0.019 | 0.004 | 0.004 |
| `sky` | 0.011 | 0.011 | 0.010 | 0.009 | 0.011 | 0.009 |
| `lightmap` | 0.004 | 0.004 | 0.005 | 0.005 | 0.004 | 0.004 |
| `camera_setup` + `frustum` | 0.004 | 0.004 | 0.005 | 0.004 | 0.004 | 0.005 |
| `hand` | 0.003 | 0.003 | 0.004 | 0.003 | 0.004 | 0.004 |
| `weather` | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 |
| untimed | 0.037 | 0.036 | 0.042 | 0.042 | 0.038 | 0.041 |
| `gui` | 0.010 | 0.010 | 0.011 | 0.011 | 0.013 | 0.013 |
| outside world | 0.003 | 0.003 | 0.003 | 0.004 | 0.003 | 0.003 |
| `swap` | 0.196 | 0.281 | 0.114 | 0.116 | 0.115 | 0.125 |
| `tick` | 0.076 | 0.085 | 0.118 | 0.118 | 0.077 | 0.087 |
| client thread CPU | 1.324 | 1.392 | 2.028 | 2.040 | 1.437 | 1.567 |
| alloc KB/frame | 55.5 | 55.7 | 65.9 | 65.9 | 29.8 | 30.9 |

`clouds` per frame (from the CSVs): static p10 0.43, p50 0.45, p90 0.54-0.56 ms; pan p10
0.41-0.42, p50 0.44-0.45 ms. It is a steady cost in every frame, not spikes.

## Fancy cloud geometry per frame

`WorldRenderer.renderFancyClouds` (fancy graphics with clouds on, the 1.8.9 default) rebuilds
its geometry in immediate mode every frame: 8 × 8 tiles of 8 × 8 cloud cells, each tile drawn
with its own `Tesselator.end`, and the whole grid drawn twice (a depth-only pass, then the color
pass). Below the cloud layer each tile has 1 bottom face and on average 10 + 10 side strips, so
21 quads, 84 vertices: 64 × 84 × 2 = **10,752 vertices built and 128 immediate-mode draws per
frame**, about 45 ns per vertex for building it in Java and the driver copying the client
arrays. From frame to frame the geometry only moves by the camera's and the drift's fraction of
a cloud cell; its shape changes only when that crosses a whole cell, the camera moves through
the layer height or the cloud color changes.
