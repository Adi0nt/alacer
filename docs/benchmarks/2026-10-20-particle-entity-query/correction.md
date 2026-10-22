# Session 19 virtual-fallback correction

The initial candidate preserved coordinates and virtual dispatch but materialized a separate
query Box for each chunk/shape fallback. Vanilla shares one grown Box across its query.
A subclass could observe that identity difference. This was found by source review during
the first measurement batch, before selection analysis.

The control runner was stopped after four archived launches; its already-running fifth
client was allowed to finish normally. That complete fifth raw export/log was recovered
into a record, explicitly marking the unavailable runner elapsed clock. All five completed
v2 labels, source ZIPs, raw exports and hashes remain intact. Their per-phase metrics are
retained separately as superseded_comparison in metrics.json. They are not final selection
evidence, regardless of whether any timing happens to look favorable.

Before changing the implementation, a strengthened two-subclass fixture reproduced the
failure in `pe19-identity-negative-check-2`: `shared query Box identity across virtual fallbacks`.
Its source snapshot, exact command and failed log hash are retained under attempts/.
The fix keeps a local initially-null Box and materializes it once when a virtual fallback
first needs it, sharing that object for the rest of the query. The common exact-class path
still uses scalars and there is no pooled/retained mutable geometry or per-world state.
The direct predicate fixture passes an actual immutable bounds object to its virtual case.

Java 25 build and all 65 tests pass after the fix. `pe19v3-check-2` passes the new shared-
identity assertion, positive list/callback checks and full dense runtime/movement diagnostics.
All final performance selection uses the new `pe19v3-ab-*` labels: a complete fresh eight-
launch batch on one corrected source identity. v2 profile/diagnostic evidence is supplemental;
new v3 control/candidate profiles use the corrected source. No gate threshold was relaxed.
