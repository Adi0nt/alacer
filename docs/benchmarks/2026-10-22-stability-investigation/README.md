# Session 32: focused stability investigation

Exploratory evidence, not another calibration or A/A campaign. Start with RESULTS.md when complete.

- PROTOCOL.md: diagnostic v1 registered before the first client.
- AMENDMENT-v2.md: sampler repair registered before v2; original deadline retained.
- protocol.json: explicit existing launcher parameters; never used to rescore aa31w2.
- diagnostic-state.json: original/final power plan, total elapsed wall time and every launch outcome.
- diagnostic-sources/: exact runner/native sampler used by completed v2 launches.
- sampler-v1.ps1: failed initial collector preserved; its incomplete data are never a monitor comparison.
- attempts/ and records/: original diagnostic outcomes and compact metrics; monitor-off is never environmental acceptance.
- preservation-before.json / preservation-after.json: original Session 31 archive and raw-file hash comparison.
- historical-analysis.json / diagnostic-analysis.json: read-only descriptive analysis, not new scores.
- raw results remain in this checkout under run/alacer/benchmarks; thread/JFR/monitor traces under run/alacer/stability/diag32; client/setup logs under build.

Reproduce the offline analysis with tools/aa_diagnose.py ARCHIVE NEW_OUTPUT --bins. Source identity, diagnostic-only overhead and unmeasured native-stack/driver controls limit interpretation. Do not rerun the one-off launch driver against this completed archive or reuse its labels.
