# Maintainer default selection (2026-10-28)

After the [completed qualification](resume-1/README.md), the user explicitly
directed: "Ok turn off the bad stuff then". This is a subsequent default-selection
change, separate from the frozen campaign. Its measured inputs, protocols,
results and historical archives remain unchanged.

| Option | New default | Basis |
|---|---|---|
| render.cameraHysteresis | false | Stable-input traversal verifier differences; cause/interaction not isolated to hysteresis alone |
| render.flatOcclusion | false | Native verifier differences |
| render.fastTerrainPipeline | false | Layer-count verifier differences |
| render.fastWorkerAo | false | Frame-tail/CPU gate failures and regressing update-latency observations |
| render.fastModelTransforms | false | Static p99/CPU gate failures; exact skipZeroTranslates overlap |

All five stay off in both variants of `render.defaults`, even after an explicit
per-feature opt-in. The two formerly exposed menu entries (terrain pipeline and
camera caching) were removed; none of the five is in Video Settings. Individual
config/JVM/toggle opt-ins and verifiers remain available for investigation.
Vector frustum and mipmaps retain their defaults pending Extended work. The
selection does not establish a new performance improvement or certify that all
rendering correctness issues are repaired.

The checkout's ignored `run/config/alacer.properties` was also set to false for
these five keys, with its other settings preserved. Backup:
`build/qualification/before-disable-defaults-alacer.properties`. Other users'
existing configs still override shipped defaults and require explicitly setting
these keys to false when updating; there is no automatic config migration.

Validation, explicit Temurin 25.0.4.1 for Gradle/compiler/tests/client:

- `./gradlew build javaRuntimeInfo`: successful, 353 tests, zero failures/errors/skips.
- Existing config/preset tests now cover all five defaults, preserved individual
  opt-ins, and exclusion from the aggregate preset. Catalog tests cover menu exclusion.
- One plain client correctness smoke:
  `./gradlew runClient -Pwindow=1920x1080 -Pxmx=2G -Palacer.benchmark=true -Palacer.benchmark.label=q-defaults-disabled-smoke`.
  Normal exit; static 17,472, pan 12,631 and fly 17,812 frames; zero dropped frames.
  All five keys are false in the build-default config and every phase. Vector
  frustum and mipmaps remain true. No per-option JVM override was used.
- No client crash, mixin-application error or GL error found. The earlier
  Log4j Queue/ServerGuiConsole, Twitch initialization and missing Realms class
  errors persisted. This is a functional smoke, not an A/B performance run.

The [smoke summary](selection-smoke/summary.json) and
[validation record](selection-smoke/validation.json) are retained here. Raw CSVs
remain under ignored `run/alacer/benchmarks/20261028-185630-q-defaults-disabled-smoke-vis_on-ecl_on-ent0/`.
Build/launch logs remain under ignored `build/qualification/defaults-disabled-*.log`.
As with the campaign, `runClient` loads development classes/resources rather than
installing the remapped distributable JAR. No new production-JAR claim is made.

Work remains on `codex/1.0-qualification`; main and the original `v1.0.0` tag are
unchanged. Nothing is pushed or published.
