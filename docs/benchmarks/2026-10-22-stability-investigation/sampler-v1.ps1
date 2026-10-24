param([int]$ClientId, [string]$OutputPath)
# Session 32 attribution only. No process/thread state is changed.
$ErrorActionPreference = 'Stop'
$selfProcess = [Diagnostics.Process]::GetCurrentProcess()
$writer = [IO.StreamWriter]::new($OutputPath, $false)
$writer.AutoFlush = $true
try {
    while ($true) {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $selfProcess.Refresh()
        $cpuStart = $selfProcess.TotalProcessorTime.TotalSeconds
        try { $client = [Diagnostics.Process]::GetProcessById($ClientId) } catch { break }
        try {
            if ($client.HasExited) { break }
            $modules = @($client.Modules | ForEach-Object {
                @{ name = $_.ModuleName; base = $_.BaseAddress.ToInt64(); size = $_.ModuleMemorySize }
            })
            $threads = @($client.Threads | ForEach-Object {
                try {
                    $address = $_.StartAddress.ToInt64()
                    $module = @($modules | Where-Object { $address -ge $_.base -and $address -lt ($_.base + $_.size) })
                    @{ id = $_.Id; cpu_s = $_.TotalProcessorTime.TotalSeconds;
                       user_s = $_.UserProcessorTime.TotalSeconds; kernel_s = $_.PrivilegedProcessorTime.TotalSeconds;
                       start = $address; module = ($module.name -join ',') }
                } catch { @{ id = $_.Id; error = $_.Exception.Message } }
            })
            $selfProcess.Refresh()
            $row = @{ t = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0;
                      process_cpu_s = $client.TotalProcessorTime.TotalSeconds; threads = $threads;
                      self_cpu_s = $selfProcess.TotalProcessorTime.TotalSeconds - $cpuStart;
                      self_wall_s = $watch.Elapsed.TotalSeconds }
            $writer.WriteLine(($row | ConvertTo-Json -Depth 5 -Compress))
        } finally { $client.Dispose() }
        Start-Sleep -Milliseconds ([Math]::Max(1, 1000 - [int]$watch.ElapsedMilliseconds))
    }
} finally { $writer.Dispose() }
