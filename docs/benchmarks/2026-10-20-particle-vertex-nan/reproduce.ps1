$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
Push-Location $repoRoot
try {
    $outputRoot = Join-Path $repoRoot 'build/particle-vertex-nan-repro'
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    & ./gradlew.bat test --tests net.adiont.alacer.render.ParticleVertexWriterTest --rerun-tasks -I (Join-Path $PSScriptRoot 'test-runtime.init.gradle') *> (Join-Path $outputRoot 'gradle.log')
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed; see $outputRoot/gradle.log" }
    $runtime = Get-Content (Join-Path $outputRoot 'test-runtime.json') -Raw | ConvertFrom-Json
    $compiler = Join-Path (Split-Path $runtime.java) 'javac.exe'
    $classes = Join-Path $outputRoot 'classes'
    & $compiler -Xlint:none --release 25 -cp $runtime.classpath -d $classes (Join-Path $PSScriptRoot 'ParticleVertexWriterProbe.java') *> (Join-Path $outputRoot 'javac.log')
    if ($LASTEXITCODE -ne 0) { throw "Probe compilation failed; see $outputRoot/javac.log" }
    $probeClasspath = $classes + [IO.Path]::PathSeparator + $runtime.classpath
    $modes = @(
        @{ Name = 'default'; Flags = @(); Repetitions = 100 },
        @{ Name = 'c1'; Flags = @('-XX:TieredStopAtLevel=1'); Repetitions = 100 },
        @{ Name = 'c2'; Flags = @('-XX:-TieredCompilation', '-Xbatch'); Repetitions = 100 },
        @{ Name = 'interpreter'; Flags = @('-Xint'); Repetitions = 10 }
    )
    foreach ($mode in $modes) {
        $vmFlags = $mode.Flags
        $log = Join-Path $outputRoot ($mode.Name + '.log')
        & $runtime.java @vmFlags -cp $probeClasspath net.adiont.alacer.render.ParticleVertexWriterProbe $mode.Repetitions *> $log
        if ($LASTEXITCODE -ne 0) { throw "Probe failed; see $log" }
        Write-Output $mode.Name
        Get-Content $log -Tail 2
    }
} finally {
    Pop-Location
}
