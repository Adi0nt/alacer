# Longer benchmark heap amendment

The installer-profile smoke completed normally at 2 GiB. Before starting the
longer client, source preflight found BenchmarkRunner.startRecording allocates
`seconds * 4000 + 1000` rows per FrameProfiler column and retains all phases until
finish. The planned 60/120/300-second phases therefore retain about 1.92 million
rows per column, before world state and report/export scratch allocations.

Use an explicit 4 GiB heap for the second client, keeping source/artifact,
generated metadata, libraries, scenarios and all functional gates unchanged.
This is a functional eight-minute soak with benchmark-retention overhead, not
proof of eight-minute stability within a 2 GiB heap or a performance comparison
with the short smoke. No game default or benchmark allocation implementation
is changed. Preserve both launcher-tool hashes. There is no extra client/retry.
