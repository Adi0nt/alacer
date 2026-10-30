# Amendment 1: Legacy Skin Layers cannot start (written after rc3-mods-control)

`rc3-mods-default` (Alacer + all extra mods) exited 1 after 37 s, before
world load: `Could not execute entrypoint stage 'client-init' ... provided by
'legacy-skin-layers'`, caused by `NoClassDefFoundError:
kotlin/jvm/internal/Intrinsics` in `LegacySerializers.<clinit>`. All six mod
IDs had loaded and Lomka had initialized. The declared conditional control
`rc3-mods-control` (same extra mods, no Alacer) failed identically. Fabric's
crash window kept that process open until the harness killed it after 90 s;
the user saw both crash windows. Legacy Skin Layers 1.0.1 is Kotlin code that needs a
Kotlin runtime which neither its `fabric.mod.json` nor its Modrinth page
declares. That is a packaging gap in that mod, not an Alacer incompatibility, and
its render hooks were never exercised.

Change: drop Legacy Skin Layers and `osl-config` (only it needed that) and run
the remaining three mods under new labels, with the same clients, arguments,
verifier set and gates: `rc3b-mods-default`, `rc3b-mods-verify`. No additional
download. Budget: 2 clients, no further control unless one fails in a new way.
The render-hook overlap with Alacer's ModelPart paths is therefore not tested;
Lomka's RenderTarget/Minecraft hooks remain the only render-side interaction.
