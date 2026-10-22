# Full-duration selection series. Run from the repository root; leaves driver settings alone.
$ErrorActionPreference = 'Stop'
foreach ($round in 1..3) {
    foreach ($population in @(0, 200)) {
        $label = "fr-$population-r$round"
        if ((Test-Path "build/$label.log") -or
            (Get-ChildItem run/alacer/benchmarks -Directory -Filter "*-$label-vis_*-ab")) {
            throw "$label already has evidence. Archive the existing logs/results before rerunning this series."
        }
    }
}
foreach ($round in 1..3) {
    foreach ($population in @(0, 200)) {
        $label = "fr-$population-r$round"
        $launchArgs = @('runClient', '--console=plain', '-Palacer.benchmark=true',
            "-Palacer.benchmark.label=$label", "-Palacer.benchmark.entities=$population",
            '-Palacer.benchmark.toggle=render.fastChunkFrustum',
            '-Palacer.profiler.entityDetail=false', '-Palacer.profiler.shadowTiming=false',
            '-Palacer.profiler.frustumTiming=false', '-Palacer.profiler.hud=false',
            '-Palacer.render.fastChunkVisibility=true', '-Palacer.render.fastEntityChunkLookup=true',
            '-Palacer.render.throttleGlErrorChecks=true', '-Palacer.render.avoidMatrixReads=true',
            '-Palacer.render.cacheClouds=true', '-Palacer.render.fastEntityShadows=true',
            '-Palacer.render.fastChunkVisibility.verify=false', '-Palacer.render.fastChunkFrustum.verify=false',
            '-Palacer.render.batchModelLists=false', '-Palacer.render.skipZeroTranslates=false',
            '-Palacer.benchmark.vbo=false', '-Pwindow=1920x1080', '-Pxmx=2G')
        Write-Output "Starting $label"
        & .\gradlew.bat @launchArgs *> "build/$label.log"
        if ($LASTEXITCODE -ne 0) {
            throw "$label failed: see build/$label.log"
        }
        Write-Output "Completed $label"
    }
}
