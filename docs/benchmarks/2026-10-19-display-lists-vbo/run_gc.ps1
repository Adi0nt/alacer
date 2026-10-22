param([string]$Prefix = 'bvgc')
# Post-hoc attribution companions, excluded from selection. Run after the frozen series.
$ErrorActionPreference = 'Stop'
foreach ($backend in @('dl', 'vbo')) {
    $label = "$Prefix-$backend"
    if ((Test-Path "build/$label.log") -or (Test-Path "run/$label.gc.log") -or
        (Get-ChildItem run/alacer/benchmarks -Directory -Filter "*-$label-*")) { throw "$label already exists" }
}
foreach ($backend in @('dl', 'vbo')) {
    $label = "$Prefix-$backend"
    $vbo = if ($backend -eq 'vbo') { 'true' } else { 'false' }
    $launchArgs = @('runClient', '--console=plain', '-I', 'docs/benchmarks/2026-10-19-display-lists-vbo/gc.init.gradle',
        "-PbackendGcLabel=$label", '-Palacer.benchmark=true', "-Palacer.benchmark.label=$label",
        '-Palacer.benchmark.backendScenario=true', '-Palacer.benchmark.entities=0', '-Palacer.benchmark.chests=0',
        '-Palacer.benchmark.viewDistance=12', "-Palacer.benchmark.vbo=$vbo",
        '-Palacer.profiler.enabled=true', '-Palacer.profiler.chunkDetail=false', '-Palacer.profiler.entityDetail=false',
        '-Palacer.profiler.shadowTiming=false', '-Palacer.profiler.frustumTiming=false', '-Palacer.profiler.hud=false',
        '-Palacer.render.fastChunkVisibility=true', '-Palacer.render.fastEntityChunkLookup=true',
        '-Palacer.render.throttleGlErrorChecks=true', '-Palacer.render.avoidMatrixReads=true',
        '-Palacer.render.cacheClouds=true', '-Palacer.render.fastEntityShadows=true',
        '-Palacer.render.fastChunkFrustum=false', '-Palacer.render.batchModelLists=false',
        '-Palacer.render.skipZeroTranslates=false', '-Pwindow=1920x1080', '-Pxmx=2G')
    Write-Output "Starting $label (GC attribution; excluded from selection)"
    & .\gradlew.bat @launchArgs *> "build/$label.log"
    if ($LASTEXITCODE -ne 0) { throw "$label failed" }
    if ((Get-Content "build/$label.log" -Raw) -notmatch 'Benchmark: results written') { throw "$label incomplete" }
    Write-Output "Completed $label"
}
