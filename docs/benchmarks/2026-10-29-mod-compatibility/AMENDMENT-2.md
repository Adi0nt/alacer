# Amendment 2: Skin Layers with a Kotlin runtime (written after rc3b, before rc3c)

With the user's approval, Fabric Language Kotlin 1.14.2+kotlin.2.4.21
(FabricMC, Apache-2.0; Modrinth version SKW1BTL3,
`fabric-language-kotlin-1.14.2+kotlin.2.4.21.jar`, 8,142,878 bytes,
SHA-256 `fbdbf5286ab9d65fedb49e6e01c15e338ae303eb7b9d8c7423d0a9bcfe526ef9`,
Modrinth SHA-512/SHA-1 verified) supplies the Kotlin standard library that
Legacy Skin Layers 1.0.1 references but does not declare. Static inspection:
`fabric.mod.json` depends only on `fabricloader >=0.19.5`; no mixins, no
Minecraft references, Java 8 bytecode; a Kotlin language adapter plus 13
nested libraries (kotlin-stdlib/reflect 2.4.21, kotlinx coroutines,
serialization, datetime, io, atomicfu). Modrinth tags it Fabric 1.14+, and
Skin Layers does not name it: this pairing is ours, not the author's
documented setup.

Two clients, same arguments, gates and verifier set as rc3b, with only
Legacy Skin Layers, `osl-config` 0.6.2 and Fabric Language Kotlin added to
Alacer and the four OSL modules:

1. `rc3c-skin-default`: Alacer shipped defaults.
2. `rc3c-skin-verify`: the eight verifiers, including the opt-in ModelPart
   paths `skipZeroTranslates.verify` and `batchModelLists.verify`, so that
   Alacer's ModelPart injections and Skin Layers' cancellable
   `ModelPart.render` HEAD injection apply to the same method.

Extra gate: Fabric lists alacer, legacy-skin-layers, osl-config and
fabric-language-kotlin, and no `client-init` failure occurs. If a client fails
in a way that could come from the mods alone, one no-Alacer control
`rc3c-skin-control` runs. No retry.

Limitation stated in advance: the benchmark player is a first-person
spectator, so PlayerEntityRenderer and the player's skin meshes are not drawn.
Skin Layers' ModelPart hook runs on the 200 mobs' model parts, where it finds
no mesh and falls through. This tests mixin co-application, initialization and
Alacer correctness beside it, not the visual 3D skin feature.
