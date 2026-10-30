# Follow-on local multiplayer and UI lifecycle checks

Start only after the installer smoke and eight-minute soak finish successfully.
At most one additional client and one disposable dedicated server in a separate
campaign, at most fifteen minutes from client start. Java 25 explicit, master
volume zero, normal shipped Alacer defaults. No public server, real credentials,
production world, third-party message or release publication.

Use the unchanged official-installer profile and exact same Alacer JAR/OSL
modules. The client is a normal non-benchmark launch in its own game directory.
It has copies of the benchmark world labeled by distinct folder names, plus one
small synthetic resource pack that changes menu language strings only.

Vanilla Minecraft 1.8.9 dedicated server: official server JAR hash checked against
installer metadata, loopback 127.0.0.1:25566 only, offline mode for the artificial
ProfileTest identity, max two players, spectator mode, disposable world and log.
The server has no Alacer or other mods. This does not test online authentication,
remote latency, server scale or adversarial networking.

In the single normal client, using actual game UI:
1. Connect to the dedicated local server; verify player join and world rendering.
   Apply server-console teleport/time/block updates and observe the client view.
2. Disconnect and reconnect; confirm the same artificial player joins and the
   world continues to render. Disconnect normally.
3. Load copied singleplayer world A, return to title, then load world B. Inspect
   terrain/entities and normal save/unload logs. Preserve original base world.
4. Exercise Video Settings staged edit/Cancel and edit/Apply, and inspect the
   corresponding instance config if used. Do not enable rejected experiments.
5. Enable the language-only test pack through the resource-pack UI, observe its
   menu label, disable it and observe normal text restored. Retain reload logs.
6. Close the client normally, stop the local server through its console, and
   check client/server logs for fatal/mixin/GL/network/lifecycle failures.

Stop the affected campaign on a functional failure; preserve the attempt and
diagnose it before proposing further clients. Missing or automation-limited
steps stay explicitly unverified. Record actual UI observations/screenshots and
console commands separately from automated smoke/soak metrics. These checks are
not full gameplay, broad resource-pack/mod compatibility or performance proof.
