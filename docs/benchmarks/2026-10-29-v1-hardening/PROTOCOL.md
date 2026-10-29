# V1 resident-buffer fallback regression checks

Functional campaign only, source parent dd4a142 on codex/v1-hardening plus the
exact working-tree source hashes in freeze.json. Runtime, compiler, Gradle and
tests use Temurin 25.0.4.1. Full build/unit tests precede all clients. No change
to optimization defaults, historical archives, shared main or release tags.

At most four new client attempts, serially, within twelve minutes of the first
launch; no retries. Stop the campaign on a native/mixin/GL/functional failure,
missing benchmark summary or dropped profiler frames. Every attempt is retained.
All clients use master volume zero, fresh benchmark world copies, VBOs, view
distance 12, 1280x720, 2 GiB heap and existing static/pan/fly paths or toggle paths.

Order:
1. v1h-fallback: exact former crash flags (MDI + terrainShader + forced fallback),
   with GL-error throttling off. Terrain must render; shader inactive is expected.
2. v1h-mdi: normal MDI, shader on, command verification and GL-error checks on.
   Require positive verified commands and zero mismatches.
3. v1h-storage-toggle: benchmark toggles MDI off/on/on/off while shader fallback
   is forced; no terrain reload is induced by the option change. Inspect terrain
   in all eight static/pan toggle segments, covering resident storage after MDI
   is disabled and replacement uploads as chunks continue to build.
4. v1h-production-fallback: actual rebuilt remapped JAR under the corrected
   isolated production Loader installation, with the formerly crashing flags.
   No development Alacer directories or observer agent. Require class-origin
   evidence, complete phases, normal exit and visible terrain.

Use the archived counter observer for development diagnostics, not timing claims.
Native screenshots are a limited visual inspection, not full framebuffer parity.
No performance scoring/promotion, unsupported-GPU claim, third-party-mod
compatibility or Extended release qualification follows from these checks.
Preserve source/artifact/protocol hashes and all failed observations. Restore
options/config after clients close, preserving master volume zero.
