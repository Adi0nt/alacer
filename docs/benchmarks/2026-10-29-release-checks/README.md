# Installer profile, eight-minute soak and loopback lifecycle (c79ff99)

Source c79ff99 on `codex/v1-release-checks`, JAR SHA-256
`214c7be1fd6317638d85ae1f31c11a267d105ee46c3262dbd91b7f9d8dba5da2`, Java
25.0.4.1+1-LTS (Temurin), RTX 3060 Ti / NVIDIA 616.92, Windows 11, master
volume 0 in every instance. Protocols: `PROTOCOL.md`, `SOAK-HEAP.md`,
`LIFECYCLE-PROTOCOL.md`; tool/protocol hashes in `freeze.json`. This report was
written afterwards by a continuing session from the retained files; the clients
were run by the previous session. Follow-up campaign:
`../2026-10-29-release-checks-2/README.md`.

## Installation evidence

- Official Ornithe installer 0.16.0 (stable in `installer-meta.json`), SHA-256
  `ca35f123…7ae8a` verified against Maven (`installer-download.json`), run as
  `install client 1.8.9 Official Fabric 0.20.0 --intermediary-generation=2`
  into an isolated directory (`logs/install.log`).
- Generated profiles kept byte-for-byte in `profiles/` (hashes in
  `prepared-profile.json`). They inherit `1.8.9-gen2` and themselves supply
  `-Dfabric.fixPackageAccess=true` and `-Dfabric.gameVersion=1.8.9`, so the
  package-access flag the earlier manual installation needed comes from the
  installer and was not added by the harness.
- `profile.py` resolved 46 libraries (43 on the classpath after OS rules),
  natives and 734 asset objects from the profile chain. The first `prepare`
  failed on a locked asset file (`logs/prepare.log`); the second completed.
- The generated `1.8.9-gen2` profile has no `com.mojang:realms` library, which
  explains the `Realms module missing` error in every client (also in the
  no-Alacer control of the follow-up campaign).
- The launch is profile-driven and offline (`ProfileTest`, legacy token). It
  validates the generated metadata, not the official launcher/Prism frontends
  or Microsoft account authentication.

## Results

| Attempt | Result |
|---|---|
| `rc-installer-smoke` (2 GiB, 200 mobs, timeScale 0.35) | Pass. Exit 0, 52 s, static/pan/fly 2,301/2,969/9,801 frames, 0 dropped, Alacer loaded from the instance `mods/` JAR, terrain/entities visible in all three screenshots. |
| `rc-default-soak` (4 GiB per `SOAK-HEAP.md`, 60/120/300 s) | Pass. Exit 0, 512 s, 61,159/96,293/190,873 frames, 0 dropped, max frame 26.9 ms in fly, normal integrated server save/stop. |
| `rc-ui-loopback` + dedicated vanilla server | Multiplayer pass, lifecycle incomplete; details below. |

The soak FPS figures are a functional record, not a performance result: no
control, single run, benchmark-retention heap amendment.

### Defect found in all three clients

Each log contains `Modern rendering fallback: could not inspect mixin
compatibility` with `ClassNotFoundException: net.minecraft.block.Block`, and
the benchmark clients report `Ambient tick verification final: 0 ticks`.
`RenderCompatibility` named vanilla classes with named-mapping strings that
Loom does not remap, so on the intermediary production runtime the shipped
`render.fastRandomDisplayTicks=true` default never ran, and the opt-in chunk
neighbour and modern GL paths were always forced to fallback. The same warning
appears in the earlier production-JAR archives (`2026-10-29-remaining-features`
`q3r3-production-jar`, `2026-10-29-v1-hardening` `v1h-production-fallback`),
undiagnosed. Fixed in 6f60f08 and natively retested in the follow-up campaign.
Smoke/soak stability results above remain valid for c79ff99 but describe a
build where that optimization was inactive.

### Loopback multiplayer (`rc-ui-loopback`, `server.json`, `logs/server.log`)

Vanilla 1.8.9 dedicated server, JAR SHA-256 `c18e4245…a8312` (checked against
the profile's server download SHA-1), `127.0.0.1:25566`, offline, spectator,
seed 1789, no mods. Observed: join at 15:22:46 with terrain (screenshot
15.22.57); console `gamemode`, `tp`, `time`, `setblock` applied; disconnect at
15:26:29, rejoin 15:26:51 with 277 cached chunks and the placed glowstone
visible (15.27.34); second disconnect; `save-all` and `stop` with exit 0. The
first launch argument produced `Connecting to 127.0.0.1;25566` (a harness
string error) before the UI connection succeeded.

Screenshot 15.24.33 showed `MultiplayerChunkCache: 0, 0` and no terrain 55 s
after `tp ProfileTest -190 80 280`, which followed a teleport into unloaded
server chunks ("Cannot place block outside of the world"). The follow-up
campaign reproduced the identical frame with a client that has no Alacer,
so this is vanilla behaviour, not an Alacer defect.

Not exercised by this client: world A → world B switch (one world session),
Video Settings Cancel/Apply, resource-pack enable/disable (only the startup
reload is logged). These were run in the follow-up campaign with the fixed JAR.
The final options differ from the template only by `difficulty` and
`lastServer`; master volume stayed 0.

## Limitations

Known optional startup errors persist: Realms (missing from the generated
profile), Patchy `InitialContextFactoryBuilder already set`, Log4j/paulscode
banner. Twitch is not present on this profile. No online authentication,
remote latency, server scale, mod compatibility or performance claim follows.
