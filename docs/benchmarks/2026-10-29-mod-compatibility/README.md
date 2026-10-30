# Small Ornithe Gen 2 mod compatibility check

Alacer JAR SHA-256 `588631a2…e261` (source 6f60f08; harness run at docs commit
0b7cd1b, which leaves Alacer code unchanged), the untouched Ornithe 0.16.0
generated profile, the four OSL Gen 2 modules, Java 25.0.4.1, master volume 0,
isolated instances. `PROTOCOL.md` was frozen before the first client;
`AMENDMENT-1.md` was written after the failed attempt and its control, and
`AMENDMENT-2.md` before the Kotlin-runtime clients. Hashes
are in `freeze.json`, mod provenance in `mods-meta.json` (Modrinth version IDs,
URLs, SHA-512/SHA-1 verified) and static inspection in `mods-inspect.json`
(`inspect_mods.py`). Downloads were approved by the user.

## Selection and static checks

Four dependency-free Ornithe 1.8.9 builds from the Modrinth catalogue: Lomka
0.7.0, Legacy Skin Layers 1.0.1, Ksyxis 1.4.6, Async Logger 2.2.3. Every
intermediary class referenced by Lomka (19) and Skin Layers (29) exists in the
Gen 2 game JAR the profile loads. Async Logger has no Minecraft references.
Ksyxis is a multi-version JAR whose plugin picks mixins at runtime. Skin Layers
needed `osl-config` 0.6.2 (Gen 2, local Gradle cache). OneConfig-based mods
(Smooth Skies, Better Screens) and AxolotlClient were skipped because they need
more dependencies.

## Results

| Attempt | Mods | Result |
|---|---|---|
| `rc3-mods-default` | Alacer + all four + osl-config | Fail before world load: Skin Layers `client-init` → `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics` |
| `rc3-mods-control` | the same, no Alacer | Same crash: the mod itself fails |
| `rc3b-mods-default` | Alacer defaults + Lomka, Ksyxis, Async Logger | Pass |
| `rc3b-mods-verify` | same + 8 Alacer verifiers | Pass |
| `rc3c-skin-default` | Alacer defaults + Skin Layers, osl-config, Fabric Language Kotlin | Pass |
| `rc3c-skin-verify` | same + 8 Alacer verifiers | Pass |

Legacy Skin Layers 1.0.1 is Kotlin code. It needs a Kotlin runtime that its
metadata and Modrinth page do not declare, so without one it cannot start on
this profile, with or without Alacer. Fabric's crash window appeared on screen
for both attempts. With a Kotlin runtime it starts; see Amendment 2 below.

`rc3b` clients: 13 loaded mods including alacer, lomka, ksyxis and asynclogger.
Async Logger configured its async context, Lomka initialized with its default
mixin config, Ksyxis reported ready. Exit 0, zero dropped frames in all phases,
no GL/native/mixin errors, no Alacer compatibility fallback, Alacer loaded
from the instance JAR, ambient fast path active (760 and 730 ticks). Verifier
totals, all with 0 mismatches:

| Verifier | Compared |
|---|---|
| Entity shadow queries | 22,441,441 |
| Entity renderer cache | 4,070,494 (4,069,279 hits) |
| Entity lighting queries | 2,154,782 |
| Entity culling | 1,666,528 |
| Ambient ticks | 730 ticks, 251,101 blocks |
| Ray traces | 17,929 |
| Model zero translations (opt-in path) | 432 |
| Model list batching (opt-in path) | 32 lists, 49,140 floats via native feedback |

Only known warnings appeared (Realms missing from the generated profile, the
paulscode banner), plus a JDK `sun.misc.Unsafe` deprecation notice from Async
Logger's bundled LMAX disruptor.

## Render-hook overlap with a Kotlin runtime (Amendment 2)

With the user's approval, Fabric Language Kotlin 1.14.2+kotlin.2.4.21
(FabricMC, Apache-2.0, 8,142,878 bytes, SHA-256 `fbdbf528…6ef9`, Modrinth
hashes verified, `flk-meta.json`) supplied the Kotlin stdlib. Static check:
only depends on `fabricloader >=0.19.5`, no mixins or Minecraft references,
Java 8 bytecode, 13 nested Kotlin/kotlinx libraries. Modrinth tags it Fabric
1.14+, and Skin Layers does not name it, so this pairing is ours.

Both `rc3c` clients loaded 26 mod IDs, including the nested libraries. Skin
Layers' `client-init` ran and wrote `config/skinlayers.json`. Its mixin config is
`required` with `defaultRequire: 1`, so every injection on loaded classes
applied, or the client would have crashed. Exit 0, zero dropped frames, no
mixin warnings, no Alacer compatibility fallback, ambient fast path active
(728/727 ticks). With the verifiers, Alacer's opt-in ModelPart injections ran
beside Skin Layers' cancellable `ModelPart.render` HEAD injection: model
zero-translations 432 compared / 0 changed, model list batching 49,140 floats /
0 mismatches. Other totals: 0 mismatches over 26,679,008 shadow, 4,776,213
renderer-cache, 2,534,337 lighting and 1,954,665 culling queries, 21,109 ray
traces and 251,232 ambient blocks.

The player is a first-person spectator, so the 3D skin meshes themselves
(PlayerEntityRenderer, skulls) were not drawn. Skin Layers' ModelPart hook
ran on mob model parts, where it has no mesh and falls through. This shows
mixin co-application, initialization and unchanged Alacer correctness, not
the visual skin-layer feature.

## Limits

Four working mods (Skin Layers with a Kotlin runtime) on one machine and map, short benchmark smokes. Not
covered: the visual player skin-layer rendering, Ksyxis's actual spawn-chunk effect, mod
config screens, multiplayer, other mods/versions, and performance.
