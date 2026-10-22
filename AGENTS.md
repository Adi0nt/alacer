# Alacer development

- Java 25 is the primary development, test, game and benchmark runtime. Keep the
  Gradle JVM, compiler bytecode target and Minecraft launcher JVM explicit.
- Large overhauls and independently developed rendering subsystem replacements
  are allowed. Use profiling to identify the problem, define correctness and
  performance gates, and benchmark the replacement against the retained control.
  Small patches are not a requirement when a larger change is justified.
- Preserve existing working optimizations, profiling infrastructure, verification
  modes and benchmark functionality. Do not rewrite them just because Java changed.
- Do not copy implementations from third-party performance mods.
- Preserve Java 8 benchmark archives as historical evidence. Establish new Java 25
  baselines; do not change optimization defaults automatically from old results.
- Separate build/unit-test evidence from real client rendering, correctness,
  profiling and benchmark evidence. Report limitations and noisy/regressing metrics.
- Preserve unrelated work. Do not commit, push or publish without explicit permission.
- When interacting with Cloudflare, use the cf CLI unless the project has a
  Wrangler configuration file.
