# r2 amendment: mod-list ordering in the validator

The first r1 control client completed successfully (exit 0; all three phases,
zero dropped frames). The new harness then rejected the environment because it
compared the raw display string of the loaded mod list. Its ordering varies
between launches. The sorted lists of mod identifiers and versions are exactly
equal, and all phase configuration dictionaries match the archived control.
All 989 r1 frozen entries still match. No other client was running at amendment.

Preserve r1 protocol, tools, freeze, failure, state, launch receipt, raw records
and log verbatim. The r2 wrapper compares sorted mod/version tokens before
delegating every other invariant to the unchanged r1 validator. It records the
original strings in the raw evidence, with no change to the game or its build.

Revalidate and adopt the already completed first control; this decision is based
only on the environment representation error, without inspecting its performance
metrics. Preserve its original failed-harness status in r1 and explicitly record
adoption in r2. Continue the remaining V V A launches, then the conditional F V V F
stage. Maximum total is still eight clients across r1 and r2, with no replacement
launch. The routing criterion, selection gates and all settings are unchanged.

Freeze the wrapper, this amendment, all original r1 frozen entries, the first
control's raw inputs/log, and r1 failure/state/launch metadata before continuing.
r2 writes separate suffixed metadata/evaluation/manifest files. A subsequent
client or validation failure stops this amended attempt; no further automatic
retry or experiment stage is authorized by this runner.
