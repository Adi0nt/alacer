# Attempts and protocol records

- `mg-attrib-vbo-default` did not launch a client: unquoted PowerShell properties
  were split into Gradle task names. `mg-attrib-vbo-default-r2` and
  `mg-attrib-vbo-terrain` completed; neither is a selection run.
- The first lifecycle harness changed render distance inside a benchmark. Its
  configuration guard correctly aborted `mg-verify-lifecycle-r1`; do not score it.
  The revised standalone harness completed reload/distance/detach checks, and is
  archived with its log and PASS receipts in `validation/`. It is absent from src.
- A clean build mistakenly redirected its log into the directory being cleaned.
  Temporary build logs and derived JFR JSON were removed before Windows rejected
  deletion of the open log. Original JFRs and benchmark outputs in run remain;
  archived artifacts remain. The failure and successful clean build logs are
  archived. Verification default/terrain/edit launches are repeated for full logs.
- `correctness` was interrupted by the conversation stopping at approximately
  02:36 on October 23. `mg-shot-a1` has partial screenshots but no summary; retain
  these as an aborted attempt. No scored performance launch ran. Resume as
  `correctness-r2` with distinct labels and a new manifest. Only the runner's
  attempt suffix support changed; shipping source and gates are unchanged.
- After all eight `correctness-r2` diagnostics and image comparisons passed,
  review tightened `RenderCompatibility`: resolve the affected classes before
  caching applied-mixin metadata, since the public query reports applied mixins
  only. This is a compatibility guard change, not a layout/math change. A new
  build/test and `guard-r3` live terrain verification precede the performance
  freeze. Earlier images/lifecycle receipts retain their original source identity;
  no previous attempt or gate is overwritten.
- All 16 scored launches and four individual ablations completed without a
  replacement; the frozen combination failed. The additional v1 JFR and
  two-launch query diagnostic are attribution only. Skipping the query did not
  meet its predeclared threshold in pan and regressed flight, so no private
  LWJGL-state replacement was pursued. The bypass was removed; shipping sources
  returned to the exact scored version. Its temporary source is retained only
  in `query-diagnostic-source.zip`. No selection gate was relaxed.
