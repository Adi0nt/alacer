# Complete rerun on the local Docker host

Recorded before the first scored rerun. The original cloud host is unavailable in
this session. Run the full existing protocol in a dedicated local Linux container
with a four-CPU quota and 8 GiB memory limit, using software GL under Xvfb. This is
a new complete series, not a continuation or pooling of the interrupted attempt.
The source under test is `5030c5f` (no changes to `src/`), with the same runtime
sources as the protocol's `9b087aa`. The protocol, evaluator, launch order,
resolution, settings, phase lengths, pairing and gates are unchanged.

The JDK was downloaded from the requested Adoptium endpoint: Temurin 25.0.5+8.
The CPU/kernel and Mesa differ from the original cloud environment: the exact
values are in `rerun-environment.txt` and each launch's exported environment.
Docker's CPU quota is not four dedicated physical cores; the host can add noise.
These results are indicative llvmpipe evidence only, not a replication on the
original machine and not reference-hardware evidence. Both experiments stay off
by default regardless of the outcome.

The three `raw/*-cs-edit-*.summary.json` files remain the original aborted
attempt, excluded from scoring. New launches live under `series/`, and only the
new `run/alacer/benchmarks` results are passed to the unchanged evaluator.
`rerun-freeze.json` records SHA-256 hashes before launch; `run-series.py` checks
them between clients and after the series. A base-world generation launch is
unscored. No build, unit test or metric analysis runs during the scored clients.
