# Final native verification

All checks ran the real development client on Temurin 8, 1920x1080, distance 12,
VBO off, cloud caching on and entity detail off. No base-world generation or dropped
frames. All static/pan/fly phases completed. These runs are excluded from performance selection.

| Launch | Batch | Verify | Lists | Original face draws | Feedback checks | Floats compared | Differences |
|---|---|---|---:|---:|---:|---:|---:|
| ml-verify1000 | true | true | 32 | 234 | 96 | 49140 | 0 |
| ml-verify200-off | false | true | 32 | 234 | 96 | 49140 | 0 |
| ml-fault200 | true | true | 32 | 234 | 96 | 49140 | 3 |
| ml-default-smoke | false | false | 0 | 0 | 0 | 0 | 0 |

The two full verification runs compare transformed native list output under three views,
including nonuniform and negative scales. Final current normal/texture coordinates are
also compared. Zero empty comparisons: the native output was actually exercised.

The negative control adds 0.125 to one uploaded position in the first alternate list.
All three views detect it. The failed alternate is deleted and the part uses its retained
vanilla list. Later lists pass; the benchmark completes without a GL error or rendering exception.

The disabled-startup smoke check reports zero alternate compilations and comparisons,
confirming the startup gate in the actual client. Unit tests also cover startup flag
selection, system-property/config parsing, defaults and runtime toggle acceptance.

Static and fly screenshots from `ml-verify1000` were visually inspected: models, fur,
textures, terrain and cached clouds were present without an obvious geometry defect.
Screenshots remain at `run/screenshots/alacer-ml-verify1000-vis_on-ecl_on-ent1000-{static,pan,fly}.png`.
This is sampled visual inspection plus native geometry/attribute parity; framebuffer identity,
all lighting states, every model species and arbitrary mod injection compatibility were not established.
The 512-list cap and GC reclamation were reviewed in source but were not stress-tested here.

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=ml-verify1000 -Palacer.benchmark.entities=1000 -Palacer.render.batchModelLists=true -Palacer.render.batchModelLists.verify=true -Palacer.benchmark.screenshots=true -Palacer.profiler.entityDetail=false -Pwindow=1920x1080 -Pxmx=2G
# ml-verify200-off: entities=200, batchModelLists=false, verify=true, screenshots=false.
# ml-fault200: entities=200, batchModelLists=true, verify=true, timeScale=0.15, benchmark.modelListFault=true.
# ml-default-smoke: entities=200, batchModelLists=false, verify=false, timeScale=0.15.
python docs/benchmarks/2026-10-19-model-lists/verify.py
```
