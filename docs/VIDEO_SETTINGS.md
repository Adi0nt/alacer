# Video settings

Open **Options -> Video Settings**. The menu uses Minecraft 1.8.9's own button
textures, slider handles, bordered text field, tooltip renderer and background.
The centered, two-column layout follows the game's existing options screens.
Resource packs continue to supply the standard widget textures.

Graphics, Details, Display and Performance organize 28 controls. Search matches
names and descriptions across every category. Hover a setting for its full name,
description, default and application requirements. Long button names shorten to
fit; their values remain visible.

Gold identifies the selected category and help headings. Descriptions use gray;
pending edits have a yellow `*` and a yellow count. A saved confirmation is green,
a restart notice is gold, and a save failure is red. Text and markers accompany
these colors, so color alone never carries the setting state.

## Editing and saving

- Left-click a choice to advance it; right-click to go back. Drag a slider to
  adjust it. The mouse wheel, scrollbar and Page Up/Down navigate longer results.
- Edits are staged. **Apply** saves them; **Done** saves and returns to Options.
- **Cancel** or Escape asks before discarding pending edits. Already applied
  values remain saved. Escape closes a confirmation before leaving the menu.
- **Reset page** asks for confirmation and stages that category's defaults.
  Apply is still required. Reset is disabled during a global search.
- Tab/Shift+Tab move button focus; arrows adjust a focused setting; Enter/Space
  activate it. Ctrl+F focuses search. These keyboard paths are implemented; the
  full keyboard-only interaction matrix remains a live validation gap.

Vanilla options keep Minecraft's existing application/reload methods and save to
`options.txt`. Explicit Alacer edits merge into `config/alacer.properties` without
saving unrelated live overrides. The Alacer file is replaced atomically where
the filesystem supports it. Vanilla's own save method still owns its file; this
is not a transaction spanning both files. Unknown Alacer property values survive
a menu save, but original comments and formatting are regenerated.

Benchmark launches are read-only. JVM-controlled Alacer settings are locked with
an explanation. Unsupported VBO controls are also locked. Experimental render
paths, intrusive profilers and verification switches are deliberately absent.
The nine shipped optimization defaults and the user's saved vanilla backend are
unchanged. Particle vertex writing is selected at startup: its saved preference
appears immediately in the menu, while the active hooks change after restart.
Reverting that preference to the currently active value clears the restart notice.

## Independent quality controls

| Setting | Choices | Application |
|---|---|---|
| Leaves | Follow Graphics, Fast, Fancy | Rebuilds terrain using Minecraft's existing leaf modes. |
| Weather | Follow Graphics, Fast, Fancy, Off | Changes rain/snow geometry and drawing distance. |

Both default to **Follow Graphics**, preserving the base Graphics choice. Weather
Off hides precipitation geometry; server weather, sounds and splash particles
remain. These controls expose existing rendering modes independently. They do
not introduce a new enhanced Fancy renderer or establish a performance gain.

## Validation, 2026-10-21

`JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot` was explicit
for builds, tests and the game. `test build --offline --console=plain` passed:
98 tests, no failures/errors/skips; 165 main classes at classfile version 69.
The actual game executable and command line were checked: the specified JDK's
`bin/java.exe`, Java 25.0.4.1, `--enable-native-access=ALL-UNNAMED`,
`-XX:StackShadowPages=32`, and `-Xmx2G`, with Mixin diagnostics for these checks.

Six serial UI attempts were retained with unique logs at
`build/settings-ui-live-r1.log` through `r6.log`. Attempts 1 and 2 failed because
the leaf reload selector did not resolve; the final `reload()V` descriptor fixed
the injection. Attempts 3 and 4 exercised the initial menu and in-world apply;
5 checked the vanilla-widget redesign; 6 checked the formatting-code theme.
Builds happened before launches, and sources were not edited while clients ran.

Live checks used `build/settings-ui-client`, with a dedicated copy of the
benchmark base world for the in-world smoke test. These were interactive checks,
not measured flights. The normal user's paused client was saved and closed with
permission before serial testing. Its settings/world were not used for UI edits.

Observed live: opening from title and in-world Options; global search; staged
values staying off disk until Apply; Keep Editing retaining pending changes;
scoped reset staging defaults; persistence across relaunch; GUI scale application;
leaf/weather application with an active world and terrain reload; yellow pending,
gold restart and green saved messages; restart preference reversion clearing the
notice. No selected application, injection or GL error was reported in successful
attempts. The legacy Realms missing-class startup warning remains separate.

The final color check saved particle vertex writing Off and then restored On,
and restored both quality overrides to Follow Graphics in the isolated config.
Unit tests separately cover retained live startup state, next-load preference,
save failures, malformed files, JVM override precedence, benchmark read-only
behavior, reset scoping, global search and independent quality selection.

Remaining checks include rainy/snowy-biome visual comparisons, leaf appearance
across resource packs/backends, fullscreen and monitor changes, every GUI scale,
complete keyboard-only operation, live read-only/save-failure UI, other mods and
non-English localization. There is no new frame-time or pixel-equivalence claim.
Frozen benchmark archives, prior runners and Java 8 evidence remain unchanged.

![Native Minecraft menu with gold help and yellow pending edits](validation/2026-10-21-video-settings/native-theme.png)

Hashes, attempt outcomes and preservation checks are in
[validation.json](validation/2026-10-21-video-settings/validation.json).
