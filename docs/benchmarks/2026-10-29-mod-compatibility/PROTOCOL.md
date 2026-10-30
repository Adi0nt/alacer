# Small Ornithe Gen 2 mod compatibility check (6f60f08)

Alacer source 6f60f08, JAR SHA-256 `588631a2…e261`, the same untouched
Ornithe 0.16.0 generated profile/libraries/natives/assets and four OSL Gen 2
modules as `../2026-10-29-release-checks-2/`. Java 25.0.4.1 explicit, master
volume zero, isolated instances and benchmark-world copies, serial, one attempt
per label, no retries; stop on a functional failure.

## Mods (downloaded with the user's approval, Modrinth SHA-512/SHA-1 verified)

Picked from the Modrinth Ornithe 1.8.9 catalogue among builds with no extra
required Modrinth dependencies, covering different hook areas.

| Mod | File | SHA-256 | Why |
|---|---|---|---|
| Lomka 0.7.0 | `lomka-0.7.0-ornithe+1.8.9.jar` | `aa610f5e…41c7` | Small optimizations: Minecraft, RenderTarget, FallbackResourceManager, Box/Direction/MathHelper/Vec3i, NBT, network compression mixins (overlap/interaction case) |
| Legacy Skin Layers 1.0.1 | `LegacySkinLayers-1.0.1.jar` | `ca2b68e5…e3f6` | Render hooks: ModelPart (cancellable HEAD of render), PlayerEntityRenderer, skull renderers |
| Ksyxis 1.4.6 | `Ksyxis-1.4.6.jar` | `e044b1b2…f111` | Multi-version world-loading mixins selected by a plugin |
| Async Logger 2.2.3 | `asynclogger-2.2.3+1.8.9-ornithe.jar` | `227847e9…46b6` | Logging/preLaunch, `client.main.Main` mixin |

Plus `osl-config` 0.6.2+mc14w27a-mc15w39c (Gen 2) from the local Gradle cache
(Skin Layers requires `osl-config >=0.6.0`; its own requirements core,
entrypoints and lifecycle-events are already present).

Static inspection (data only): every `net/minecraft/unmapped/C_*` reference of
Lomka (19) and Skin Layers (29) exists in the Gen 2 intermediary game JAR the
profile loads; Async Logger has none. Ksyxis has 3 references absent from it,
plus modern `class_*` names: it is a multi-version JAR whose plugin chooses mixins,
so its 1.8.9 path is decided only at runtime. Skin Layers is Java 25 bytecode;
the others are Java 8 (two Async Logger classes Java 17).

## Clients (budget 2 clients + at most 1 conditional control, 15 minutes)

Benchmark smoke arguments as `rc2-guard-default` (2 GiB, 200 mobs, timeScale
0.35, screenshots, 180 s watchdog), all five extra JARs in `mods/`.

1. `rc3-mods-default`: shipped Alacer defaults.
2. `rc3-mods-verify`: adds verifiers for features near these mods' hooks:
   `fastRandomDisplayTicks`, `fastEntityCulling`, `fastEntityRendererLookup`,
   `fastEntityLighting`, `fastEntityShadows`, `fastRayTrace`, and the opt-in
   ModelPart paths `skipZeroTranslates.verify` and `batchModelLists.verify`
   (these enable Alacer's ModelPart hooks alongside Skin Layers' ModelPart hook).

Gates for both: Fabric loads alacer, lomka, legacy-skin-layers, ksyxis,
asynclogger and osl-config; normal exit; summary present; zero dropped frames;
no GL/native/mixin apply or transformer errors, no `could not inspect mixin
compatibility`; Alacer class origin is the fixed JAR; ambient ticks > 0 (if
Async Logger prevents the line from reaching the captured log, record
"not observed" and fail the gate). Client 2 also needs every reported verifier
total > 0 where Alacer prints one, and 0 mismatches. Alacer info lines
`Modern rendering fallback: X is modified by Y` are recorded, not failures:
they show the conservative fallback working.

Conditional control `rc3-mods-control` (the same five mods, no Alacer, normal
launch to the title screen, F2 screenshot, then close): run only if a client
fails in a way that could come from the mods alone.

## Not covered

Player-model skin layers (the benchmark player is a first-person spectator,
so PlayerEntityRenderer is not drawn), mod configuration screens, multiplayer,
any other mods or versions, the OneConfig/AxolotlClient families (more
required dependencies), and any performance claim.
