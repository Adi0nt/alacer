# Amendment 1: modern GL gate specification error (written after client 3)

`rc2-guard-modern-verify` failed the frozen gate "named uploads and VAO checks
> 0": `modernGl.vaoChecks` and `vaosCreated` were 0. Every other gate passed:
normal exit, zero dropped frames, no GL/mixin/native errors, no compatibility
inspection failure, forced fallback false, terrain shader active with attribute
binding, 18,413,093 decoupled draws, 6,463 named uploads each read back
(191,298,144 bytes compared), zero upload failures, zero fallback draws.

Diagnosis: the gate was written from the stale `render.modernGl.verify` config
description. The current development reference with the same verifier
(`../2026-10-29-remaining-features/runs/20261029-142914-q3-modern-gl-*`) also
reports `vaosCreated 0`, `vaoChecks 0`, shader active and upload checks only;
VAO counters appear only in the 2026-10-23 archives before the shader pipeline
replaced terrain VAOs. This is a protocol error, not a functional failure, and
the client is retained as a declared-gate failure, not relabelled a pass. No
extra client or retry is run. The production modern GL path is therefore
evidenced as active with byte-exact uploads; VAO validation is not exercised by
the current pipeline in either environment. Part 2 is independent of this gate
and proceeds as declared. The config description should be corrected later.
