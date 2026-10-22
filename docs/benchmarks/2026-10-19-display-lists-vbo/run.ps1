param(
    [ValidateSet('smoke', 'controls', 'diagnostics')][string]$Mode = 'controls',
    [string]$Prefix = 'bvs'
)
# Execute from repository root. Refuse to reuse any evidence label, even after a failed launch.
$ErrorActionPreference = 'Stop'
$series = switch ($Mode) {
    smoke { @(@{ backend='dl'; round='smoke'; vbo='false' }, @{ backend='vbo'; round='smoke'; vbo='true' }) }
    controls { @(
        @{ backend='dl'; round='r1'; vbo='false' }, @{ backend='vbo'; round='r1'; vbo='true' },
        @{ backend='vbo'; round='r2'; vbo='true' }, @{ backend='dl'; round='r2'; vbo='false' },
        @{ backend='vbo'; round='r3'; vbo='true' }, @{ backend='dl'; round='r3'; vbo='false' },
        @{ backend='dl'; round='r4'; vbo='false' }, @{ backend='vbo'; round='r4'; vbo='true' }
    ) }
    diagnostics { @(
        @{ backend='dl'; round='detail1'; vbo='false' }, @{ backend='vbo'; round='detail1'; vbo='true' },
        @{ backend='vbo'; round='detail2'; vbo='true' }, @{ backend='dl'; round='detail2'; vbo='false' }
    ) }
}
foreach ($run in $series) {
    $label = "$Prefix-$($run.backend)-$($run.round)"
    if ((Test-Path "build/$label.log") -or (Get-ChildItem run/alacer/benchmarks -Directory -Filter "*-$label-*")) {
        throw "$label already has evidence; use a new prefix."
    }
}
foreach ($run in $series) {
    $label = "$Prefix-$($run.backend)-$($run.round)"
    $detail = if ($Mode -eq 'controls') { 'false' } else { 'true' }
    $launchArgs = @('runClient', '--console=plain', '-Palacer.benchmark=true',
        "-Palacer.benchmark.label=$label", '-Palacer.benchmark.backendScenario=true',
        '-Palacer.benchmark.entities=0', '-Palacer.benchmark.chests=0', '-Palacer.benchmark.viewDistance=12',
        "-Palacer.benchmark.vbo=$($run.vbo)", "-Palacer.profiler.chunkDetail=$detail",
        '-Palacer.profiler.enabled=true', '-Palacer.profiler.entityDetail=false',
        '-Palacer.profiler.shadowTiming=false', '-Palacer.profiler.frustumTiming=false', '-Palacer.profiler.hud=false',
        '-Palacer.render.fastChunkVisibility=true', '-Palacer.render.fastEntityChunkLookup=true',
        '-Palacer.render.throttleGlErrorChecks=true', '-Palacer.render.avoidMatrixReads=true',
        '-Palacer.render.cacheClouds=true', '-Palacer.render.fastEntityShadows=true',
        '-Palacer.render.fastChunkFrustum=false', '-Palacer.render.batchModelLists=false',
        '-Palacer.render.skipZeroTranslates=false', '-Pwindow=1920x1080', '-Pxmx=2G')
    if ($Mode -eq 'smoke') {
        $launchArgs += '-Palacer.benchmark.timeScale=0.25'
        $launchArgs += '-Palacer.benchmark.screenshots=true'
    }
    if ($Mode -eq 'diagnostics') { $launchArgs += '-Palacer.benchmark.screenshots=true' }
    Write-Output "Starting $label ($Mode)"
    & .\gradlew.bat @launchArgs *> "build/$label.log"
    if ($LASTEXITCODE -ne 0) { throw "$label failed; see build/$label.log" }
    $log = Get-Content "build/$label.log" -Raw
    if ($log -notmatch 'Benchmark: results written' -or $log -match 'Benchmark aborted') {
        throw "$label did not complete"
    }
    Write-Output "Completed $label"
}
