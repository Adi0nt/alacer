# Fresh copy of the same base world per launch. No driver changes. Run from repository root.
$ErrorActionPreference = 'Stop'
$series = @(
    @{ label = 'cp-baseline-r2'; detail = 'false'; jfr = $false },
    @{ label = 'cp-detail-age-r4'; detail = 'true'; jfr = $false },
    @{ label = 'cp-baseline-r3'; detail = 'false'; jfr = $false },
    @{ label = 'cp-detail-jfr-r5'; detail = 'true'; jfr = $true }
)
foreach ($run in $series) {
    $label = $run.label
    if ((Test-Path "build/$label.log") -or (Test-Path "run/$label.jfr") -or
        (Get-ChildItem run/alacer/benchmarks -Directory -Filter "*-$label-*")) {
        throw "$label already has evidence; use new labels for another series."
    }
}
foreach ($run in $series) {
    $label = $run.label
    $launchArgs = @('runClient', '--console=plain', '-Palacer.benchmark=true',
        "-Palacer.benchmark.label=$label", '-Palacer.benchmark.entities=0',
        "-Palacer.profiler.chunkDetail=$($run.detail)", '-Palacer.profiler.entityDetail=false',
        '-Palacer.profiler.shadowTiming=false', '-Palacer.profiler.frustumTiming=false', '-Palacer.profiler.hud=false',
        '-Palacer.render.fastChunkVisibility=true', '-Palacer.render.fastEntityChunkLookup=true',
        '-Palacer.render.throttleGlErrorChecks=true', '-Palacer.render.avoidMatrixReads=true',
        '-Palacer.render.cacheClouds=true', '-Palacer.render.fastEntityShadows=true',
        '-Palacer.render.fastChunkFrustum=false', '-Palacer.render.batchModelLists=false',
        '-Palacer.render.skipZeroTranslates=false', '-Palacer.benchmark.vbo=false',
        '-Pwindow=1920x1080', '-Pxmx=2G')
    if ($run.jfr) {
        $launchArgs += "-Pjfr=$label"
        $launchArgs += '-Palacer.benchmark.screenshots=true'
    }
    Write-Output "Starting $label"
    & .\gradlew.bat @launchArgs *> "build/$label.log"
    if ($LASTEXITCODE -ne 0) { throw "$label failed; see build/$label.log" }
    $log = Get-Content "build/$label.log" -Raw
    if ($log -notmatch 'Benchmark: results written' -or $log -match 'Benchmark aborted') {
        throw "$label did not complete its benchmark"
    }
    Write-Output "Completed $label"
}
