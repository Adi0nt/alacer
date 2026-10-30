# Production guard fix, loopback teleport diagnosis and UI lifecycle (6f60f08)

Follow-up to `../2026-10-29-release-checks/README.md`. Source 6f60f08 (fix on
top of c79ff99), JAR SHA-256
`588631a2fe3fe61edf9ee551ee42ce4f0affcc5c0895405fc5d9031c9271e261`, built
from a working tree byte-identical to that commit. Same untouched Ornithe 0.16.0
generated profile (metadata hashes rechecked before each client), libraries,
natives, assets and four OSL Gen 2 modules. Java 25.0.4.1+1-LTS explicit, master
volume 0 in all seven instances, isolated game directories and world copies.
Protocols declared before their clients: `PROTOCOL.md` (parts 1–2),
`PROTOCOL-UI.md` (part 3, declared after part 2); `AMENDMENT-1.md` written
after client 3. Hashes in `freeze.json`. Serial, one attempt per label, no
retries. Harness: `rc2.py`, `ui.ps1`.

## Build and unit evidence (separate from client evidence)

`gradlew build` on Java 25: 361 tests, 0 failures (one new
`RenderCompatibilityTest`, which rejects hard-coded `net.minecraft.` string
constants in the guard and requires remappable class constants). `javap` of
the remapped JAR shows the guard referencing `net/minecraft/unmapped/C_*`
class constants.

## Part 1: production guard (installer profile, benchmark smoke)

| Attempt | Gate | Evidence |
|---|---|---|
| `rc2-guard-default` (shipped defaults) | Pass | Exit 0; 1,996/1,540/5,285 frames, 0 dropped; no fallback line; `Ambient tick ... 730 ticks, 251,245 blocks` (c79ff99 production: 0 ticks). |
| `rc2-guard-verify` (ambient + chunk neighbour verifiers) | Pass | 730 ticks, 251,208 blocks, 0 mismatches; chunk neighbours 35,834,501 compared, 0 mismatches. |
| `rc2-guard-modern-verify` (`render.modernGl.verify`) | Fail (declared gate) | Terrain shader active, 18,413,093 decoupled draws, 6,463 named uploads all read back (191,298,144 bytes), 0 upload failures, 0 fallback draws, 0 dropped frames. Failed only `vaoChecks > 0`: see `AMENDMENT-1.md`. |

The modern-verify gate came from the stale `render.modernGl.verify` comment
("validate terrain VAO state"). The current dev-environment reference with the
same verifier also has 0 VAOs/checks; VAO counters exist only in the
2026-10-23 archives before the shader pipeline replaced terrain VAOs. It stays
a declared-gate failure, not a functional one, and was not rerun.

Short verifier smokes are correctness/activation evidence on one map, not
Extended performance qualification. `fastRandomDisplayTicks` now runs in
production for the first time, so its whole-client performance in production
has not been measured.

## Part 2: loopback teleport diagnosis

Fresh copy of the vanilla 1.8.9 server per client (SHA-256 `c18e4245…a8312`,
`127.0.0.1:25567`, offline, spectator, seed 1789). Auto-connect, then 20 s,
`tp 512 80 512` (server: "Cannot place block outside of the world"), 40 s,
`tp -190 80 280` ("Block placed"), 55 s, then F3 + F2 sent to the focused
window.

| Attempt | Mods | Screenshot at -189.5 80 280.5 |
|---|---|---|
| `rc2-mp-control` | OSL modules only, no Alacer | `MultiplayerChunkCache: 0, 0`, `C: 0/10000`, no terrain |
| `rc2-mp-alacer` | fixed Alacer + OSL | identical: `0, 0`, `C: 0/10000`, no terrain |

So the earlier zero-chunk frame is vanilla 1.8.9 behaviour after a teleport
into chunks the server had not loaded, not an Alacer defect. Neither client
reacted to `CloseMainWindow` within 60 s; both were killed (exit 1, server
logs "Connection reset"). Since the control behaves the same, that is a harness
limitation, not an Alacer failure. A normal multiplayer disconnect and client
exit was shown in the earlier `rc-ui-loopback` run on c79ff99, not with 6f60f08.
Both servers stopped through the console with exit 0.

## Part 3: singleplayer UI lifecycle (`rc2-ui-lifecycle`)

Normal client, shipped defaults, driven through the real game window with
Win32 clicks/keys (`ui.ps1`). Desktop computer-use could not attach because
the game runs as a bare `java.exe`. Each step was checked with an in-game F2
screenshot (`screenshots/rc2-ui-lifecycle/`) and the client log.

| Step | Observation | Result |
|---|---|---|
| Title screen | Renders (17.10.24) | Pass |
| World A | Loaded, 561 chunks, terrain (17.11.18); Save and Quit, A `level.dat` updated 17:11:47 | Pass |
| World B | Second integrated server session, terrain (17.12.22); Save and Quit, B `level.dat` updated 17:12:44 | Pass |
| Video Settings Cancel | Lighting style Vanilla → Soft ("1 unsaved change"), Cancel → "Discard unsaved changes?" → Discard; reopened shows Vanilla; config unchanged | Pass |
| Video Settings Apply | Soft + Apply: config changed only `quality.lightingStyle` 0→1; Vanilla + Apply: "Settings applied and saved", parsed key/value set identical to the launch config (136 keys) | Pass |
| Resource pack | Enable: log `Reloading ResourceManager: Default, release-language-check.zip`, title shows "Release Check Worlds / Release Check Servers" (17.15.33); disable: third reload, "Singleplayer / Multiplayer" restored (17.16.15) | Pass |
| Quit Game | Exit 0, `Stopping!`, 444 s total | Pass |

No GL/mixin/native errors, no compatibility fallback, only Realms and the
paulscode banner in WARN/ERROR. Options are byte-identical before and after.
Observation: Apply rewrites `config/alacer.properties` as a plain properties
dump (timestamp header, no per-option comments; 409 → 138 lines). Values are
preserved, but the documentation in the first-launch file is lost. That's a
polish item, not a functional failure.

Not covered: the physical `F3+T` chord (still unverified), resource packs
with textures/models, a world load after a pack change, the options menu
inside a world, and broad gameplay.

## Limitations

Offline profile-driven launches only; no official launcher/Prism frontend or
Microsoft authentication. One machine/GPU/driver. No third-party mod
interaction (no compatible Ornithe Gen 2 mods were installed). No performance
claim follows from any client here.
