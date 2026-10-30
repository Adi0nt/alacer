# Part 3: singleplayer UI lifecycle (declared after Part 2, before launch)

Part 2 result: both `rc2-mp-control` (no Alacer) and `rc2-mp-alacer` showed
`MultiplayerChunkCache: 0, 0` and no terrain 55 s after the teleport sequence,
so the earlier zero-chunk observation is vanilla 1.8.9 behaviour. Both clients
ignored the harness's window close request for 60 s and were killed: a harness
limitation shared by the control, not an Alacer failure.

The earlier `rc-ui-loopback` client left these LIFECYCLE-PROTOCOL steps
unexercised: world A then world B, Video Settings Cancel/Apply, resource-pack
enable/disable. One normal client `rc2-ui-lifecycle`, fixed JAR 588631a2…e261,
shipped defaults, same generated profile/libraries/OSL modules, master volume
zero, its own game directory with copies `release-world-A` and
`release-world-B` of the benchmark world and the same language-only test pack
(menu.singleplayer/menu.multiplayer strings). No server, no network. Driven
through the actual game window by desktop computer-use clicks/keys. Budget:
one launch, 15 minutes watchdog, no retry.

Steps and observations (screenshots via computer-use plus the client log):
1. Title screen renders. Open Singleplayer, load world A, observe terrain,
   Esc → Save and Quit to Title.
2. Load world B, observe terrain, Save and Quit to Title.
3. Options → Video Settings: change one Alacer-native option, press Cancel (or
   Esc where Cancel is absent), reopen and observe the original value. Change
   it again and press Done/Apply, observe it persisted, then restore the
   original value and apply. Instance config is compared to the launch copy.
4. Options → Resource Packs: enable the test pack, Done, observe the
   "Release Check Worlds" title label and a resource reload in the log;
   disable it, Done, observe "Singleplayer" restored and a second reload.
5. Quit Game from the title screen; require exit code 0 and "Stopping!".

Gates: normal exit, no GL/mixin/native/inspection errors, terrain visible in
both worlds, both worlds save/unload in the log, two pack reloads, labels
change and restore, Cancel does not persist, Apply persists and is restored.
Steps that the automation cannot perform are recorded as unverified. This is
functional UI evidence only, not broad resource-pack or gameplay coverage.
