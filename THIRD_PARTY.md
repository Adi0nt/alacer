# Third-party components

Alacer's own source code is original work, licensed under the GNU Lesser General Public License,
version 3 only (`LGPL-3.0-only`, see `COPYING.LESSER` and `COPYING`). The mod jar includes both
license texts.

This file records everything Alacer builds against, ships with, or borrows build scaffolding
from, so the licensing picture stays clear. Licenses were checked on
2026-10-19 against each project's repository.

## Runtime dependencies (required by players, not bundled in the Alacer jar)

| Component | Version | Used for | License | Notes |
|---|---|---|---|---|
| Fabric Loader | 0.20.0 | Mod loading, Mixin | Apache-2.0 | Installed by the player via the Ornithe installer. |
| OSL `core` | 0.11.0 | Required by the OSL modules below | Apache-2.0 | Part of Ornithe Standard Libraries 0.22.2. |
| OSL `entrypoints` | 0.6.1 | `client-init` entrypoint | Apache-2.0 | |
| OSL `lifecycle-events` | 0.6.1 | Client tick start/end, ready events | Apache-2.0 | |
| OSL `keybinds` | 0.3.0 | Registering the overlay/export keys | Apache-2.0 | |
| Quilt Parsers (JSON) | 0.2.1 | Transitive dependency of OSL | Apache-2.0 | Not used by Alacer directly. |
| SpongePowered Mixin (Fabric fork) | 0.17.5+mixin.0.8.7 | Bytecode injection | MIT | Provided by Fabric Loader. |
| MixinExtras | 0.5.5 | Provided by Fabric Loader | MIT | Not used by Alacer directly. |
| Minecraft 1.8.9 | — | The game | Proprietary (Mojang) | Never redistributed. |

Apache-2.0 obligations apply if any of these are ever bundled (jar-in-jar): ship their license
and NOTICE files. Alacer currently bundles none of them.

## Build-time only (not distributed)

| Component | Version | License | Notes |
|---|---|---|---|
| Gradle (wrapper) | 9.7.1 | Apache-2.0 | `gradlew`, `gradle/wrapper/*` come from the Ornithe template. |
| Fabric Loom | 1.18.x | MIT | |
| Ploceus | 1.18.x | Apache-2.0 | Ornithe's Loom extension. |
| Feather mappings (gen 2) | 1.8.9 build 2 | CC0-1.0 | Deobfuscation names. |
| Calamus intermediary (gen 2) | 1.8.9 | CC0-1.0 | Runtime namespace. |
| JUnit Jupiter | 5.13.4 | EPL-2.0 | Tests only. |

## Scaffolding

The Gradle wrapper, `.editorconfig`, `.gitattributes`, `.gitignore`, `settings.gradle` and the
shape of `build.gradle` were taken from the
[Ornithe mod template](https://github.com/OrnitheMC/ornithe-mod-template) (`gen2/fabric`
branch), which is CC0-1.0 and requires no attribution. No code was taken from any other mod.
