# Every resumed launch and command

All use explicit Temurin 25.0.4.1 via launch.py. Times are America/Los_Angeles (PDT, UTC-7).

| Label | Local start/end | Result | Quiet wait s | Background cores mean* | Foreground client fraction* |
|---|---|---|---:|---:|---:|
| q2-manual | 17:51:02–17:52:18 | exit 0 | - | 1.341 | 0.779 |
| q2-smoke | 17:52:38–17:53:55 | exit 0 | 11.00 | 1.089 | 0.000 |
| q2-verify-cameraHysteresis | 17:54:06–17:55:23 | exit 0 | 11.01 | 0.989 | 0.000 |
| q2-verify-vectorFrustum | 17:55:34–17:56:52 | exit 0 | 11.01 | 1.052 | 0.000 |
| q2-verify-flatOcclusion | 17:57:04–17:58:21 | exit 0 | 11.01 | 1.015 | 0.000 |
| q2-verify-fastModelTransforms | 17:58:32–17:59:49 | exit 0 | 11.01 | 1.005 | 0.000 |
| q2-verify-fastTerrainPipeline | 18:00:01–18:01:18 | exit 0 | 11.01 | 0.977 | 0.000 |
| q2-verify-fastWorkerAo | 18:01:31–18:02:53 | exit 0 | 13.02 | 0.982 | 0.000 |
| q2-verify-fastMipmaps | 18:03:05–18:04:22 | exit 0 | 11.01 | 0.965 | 0.000 |
| q2-vectorFrustum-1 | 18:06:12–18:08:15 | exit 0 | 11.01 | 0.882 | 0.000 |
| q2-vectorFrustum-2 | 18:08:27–18:10:33 | exit 0 | 12.01 | 0.928 | 0.000 |
| q2-fastModelTransforms-1 | 18:10:45–18:12:49 | exit 0 | 11.01 | 0.954 | 0.000 |
| q2-fastModelTransforms-2 | 18:13:00–18:15:05 | exit 0 | 11.01 | 0.925 | 0.000 |
| q2-fastWorkerAo-1 | 18:15:17–18:16:40 | exit 0 | 11.01 | 0.931 | 0.000 |
| q2-fastWorkerAo-2 | 18:16:52–18:18:14 | exit 0 | 11.02 | 1.100 | 0.000 |
| q2-fastWorkerAo-3 | 18:18:25–18:19:47 | exit 0 | 11.01 | 0.908 | 0.000 |
| q2-fastWorkerAo-4 | 18:19:59–18:21:21 | exit 0 | 11.01 | 0.896 | 0.000 |
| q2-fastMipmaps-1 | 18:24:42–18:27:18 | exit 0 | 11.01 | 1.044 | 0.345 |
| q2-fastMipmaps-2 | 18:27:30–18:29:26 | exit 0 | 11.01 | 1.062 | 0.000 |
| q2-fastMipmaps-3 | 18:29:40–18:31:22 | exit 0 | 13.02 | 1.031 | 0.009 |
| q2-fastMipmaps-4 | 18:31:34–18:33:37 | exit 0 | 11.02 | 0.905 | 0.000 |

*Whole attempt including preflight/startup; not per-phase environmental acceptance. See each launch JSON for all telemetry and availability.

## q2-manual

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G
```

## q2-smoke

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-smoke
```

Raw result: `run\alacer\benchmarks\20261028-175353-q2-smoke-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-cameraHysteresis

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-cameraHysteresis -Palacer.render.cameraHysteresis.verify=true
```

Raw result: `run\alacer\benchmarks\20261028-175521-q2-verify-cameraHysteresis-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-vectorFrustum

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-vectorFrustum -Palacer.render.vectorFrustum.verify=true
```

Raw result: `run\alacer\benchmarks\20261028-175650-q2-verify-vectorFrustum-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-flatOcclusion

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-flatOcclusion -Palacer.render.flatOcclusion.verify=true
```

Raw result: `run\alacer\benchmarks\20261028-175819-q2-verify-flatOcclusion-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-fastModelTransforms

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-fastModelTransforms -Palacer.render.fastModelTransforms.verify=true -Palacer.benchmark.entities=200
```

Raw result: `run\alacer\benchmarks\20261028-175947-q2-verify-fastModelTransforms-vis_on-ecl_on-ent200`. Archived summary is under `runs/`.

## q2-verify-fastTerrainPipeline

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-fastTerrainPipeline -Palacer.render.fastTerrainPipeline.verify=true
```

Raw result: `run\alacer\benchmarks\20261028-180116-q2-verify-fastTerrainPipeline-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-fastWorkerAo

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-fastWorkerAo -Palacer.render.fastWorkerAo.verify=true -Palacer.benchmark.chunkEdit=true -Palacer.benchmark.vbo=true -Palacer.profiler.chunkLatency=true
```

Raw result: `run\alacer\benchmarks\20261028-180252-q2-verify-fastWorkerAo-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-verify-fastMipmaps

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-verify-fastMipmaps -Palacer.render.fastMipmaps.verify=true
```

Raw result: `run\alacer\benchmarks\20261028-180420-q2-verify-fastMipmaps-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-vectorFrustum-1

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-vectorFrustum-1 -Palacer.benchmark.toggle=render.vectorFrustum
```

Raw result: `run\alacer\benchmarks\20261028-180813-q2-vectorFrustum-1-vis_on-ecl_on-ent0-ab`. Archived summary is under `runs/`.

## q2-vectorFrustum-2

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-vectorFrustum-2 -Palacer.benchmark.toggle=render.vectorFrustum -Palacer.benchmark.toggleReverse=true
```

Raw result: `run\alacer\benchmarks\20261028-181031-q2-vectorFrustum-2-vis_on-ecl_on-ent0-ab`. Archived summary is under `runs/`.

## q2-fastModelTransforms-1

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastModelTransforms-1 -Palacer.benchmark.toggle=render.fastModelTransforms -Palacer.benchmark.entities=200
```

Raw result: `run\alacer\benchmarks\20261028-181248-q2-fastModelTransforms-1-vis_on-ecl_on-ent200-ab`. Archived summary is under `runs/`.

## q2-fastModelTransforms-2

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastModelTransforms-2 -Palacer.benchmark.toggle=render.fastModelTransforms -Palacer.benchmark.toggleReverse=true -Palacer.benchmark.entities=200
```

Raw result: `run\alacer\benchmarks\20261028-181504-q2-fastModelTransforms-2-vis_on-ecl_on-ent200-ab`. Archived summary is under `runs/`.

## q2-fastWorkerAo-1

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastWorkerAo-1 -Palacer.render.fastWorkerAo=false -Palacer.benchmark.chunkEdit=true -Palacer.benchmark.vbo=true -Palacer.profiler.chunkLatency=true
```

Raw result: `run\alacer\benchmarks\20261028-181638-q2-fastWorkerAo-1-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastWorkerAo-2

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastWorkerAo-2 -Palacer.render.fastWorkerAo=true -Palacer.benchmark.chunkEdit=true -Palacer.benchmark.vbo=true -Palacer.profiler.chunkLatency=true
```

Raw result: `run\alacer\benchmarks\20261028-181812-q2-fastWorkerAo-2-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastWorkerAo-3

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastWorkerAo-3 -Palacer.render.fastWorkerAo=true -Palacer.benchmark.chunkEdit=true -Palacer.benchmark.vbo=true -Palacer.profiler.chunkLatency=true
```

Raw result: `run\alacer\benchmarks\20261028-181946-q2-fastWorkerAo-3-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastWorkerAo-4

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastWorkerAo-4 -Palacer.render.fastWorkerAo=false -Palacer.benchmark.chunkEdit=true -Palacer.benchmark.vbo=true -Palacer.profiler.chunkLatency=true
```

Raw result: `run\alacer\benchmarks\20261028-182119-q2-fastWorkerAo-4-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastMipmaps-1

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastMipmaps-1 -Palacer.render.fastMipmaps=false -Palacer.benchmark.resourceTiming=true -Palacer.benchmark.exit=false
```

Raw result: `run\alacer\benchmarks\20261028-182559-q2-fastMipmaps-1-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastMipmaps-2

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastMipmaps-2 -Palacer.render.fastMipmaps=true -Palacer.benchmark.resourceTiming=true -Palacer.benchmark.exit=false
```

Raw result: `run\alacer\benchmarks\20261028-182846-q2-fastMipmaps-2-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastMipmaps-3

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastMipmaps-3 -Palacer.render.fastMipmaps=true -Palacer.benchmark.resourceTiming=true -Palacer.benchmark.exit=false
```

Raw result: `run\alacer\benchmarks\20261028-183055-q2-fastMipmaps-3-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.

## q2-fastMipmaps-4

```text
cmd.exe /d /c gradlew.bat runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q2-fastMipmaps-4 -Palacer.render.fastMipmaps=false -Palacer.benchmark.resourceTiming=true -Palacer.benchmark.exit=false
```

Raw result: `run\alacer\benchmarks\20261028-183250-q2-fastMipmaps-4-vis_on-ecl_on-ent0`. Archived summary is under `runs/`.
