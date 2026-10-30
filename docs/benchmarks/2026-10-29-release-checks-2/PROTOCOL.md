# Production guard retest and loopback teleport diagnosis

Follow-on to `../2026-10-29-release-checks/`, which used source c79ff99 and JAR
`214c7be1…5da2`. Its installer smoke, eight-minute soak and UI/loopback client
logged `Modern rendering fallback: could not inspect mixin compatibility` with
`ClassNotFoundException: net.minecraft.block.Block`, and `Ambient tick
verification final: 0 ticks`: RenderCompatibility used named-mapping string
literals that do not exist on the intermediary production runtime. Commit
6f60f08 replaces them with remapped class literals (361 Java 25 tests passed;
`javap` of the remapped JAR shows `net/minecraft/unmapped/C_*` class constants).

Fixed artifact: `build/libs/alacer-1.0.0+mc1.8.9.jar`, SHA-256
`588631a2fe3fe61edf9ee551ee42ce4f0affcc5c0895405fc5d9031c9271e261`, built
from a working tree byte-identical to 6f60f08. Same untouched Ornithe 0.16.0
generated profile, resolved libraries, natives, assets and four OSL modules as
the earlier campaign; installer metadata hashes are rechecked before each
client. Java 25.0.4.1 explicit, master volume zero, isolated game directories,
copied worlds, no public server or credentials. Serial, one attempt per label,
no retries; stop the campaign on a functional failure and retain the attempt.

## Part 1: production guard (benchmark smoke, at most 3 clients, 12 minutes)

Same arguments as `rc-installer-smoke` (2 GiB, 200 mobs, timeScale 0.35,
screenshots, 180 s watchdog) except label and listed overrides.

1. `rc2-guard-default`: shipped defaults. Gates: normal exit, summary present,
   zero dropped frames, no GL/mixin/native errors, no `could not inspect mixin
   compatibility`, Alacer class origin is the fixed JAR, and `Ambient tick
   verification final` reports more than zero ticks (fast path active).
2. `rc2-guard-verify`: adds `-Dalacer.render.fastRandomDisplayTicks.verify=true`
   and `-Dalacer.render.fastChunkNeighbours.verify=true`. Extra gates: ambient
   ticks > 0 with 0 mismatches; chunk neighbour verification compared > 0 with
   0 mismatches. Verifier mode is not a performance configuration.
3. `rc2-guard-modern-verify`: adds `-Dalacer.render.modernGl.verify=true`
   (intrusive VAO validation and byte readback of every named VBO upload; opt-in
   experiments, defaults unchanged). Extra gates: no forced fallback, named
   uploads and VAO checks > 0, zero upload/VAO failures. If the environment
   reports modern GL inactive, record it as not exercised rather than passed.

## Part 2: loopback teleport diagnosis (2 clients, 2 disposable servers)

The earlier UI client showed `MultiplayerChunkCache: 0, 0` and no terrain 55 s
after `/tp ProfileTest -190 80 280`, which followed `/tp ProfileTest 512 80 512`
into chunks the server had not loaded ("Cannot place block outside of the
world"). Rejoining restored 277 chunks. Determine whether Alacer causes it.

For each client a fresh copy of the same vanilla 1.8.9 server (same JAR hash,
seed 1789, loopback 127.0.0.1:25567, offline, spectator, view distance 8):
`rc2-mp-control` (generated profile, OSL modules only, no Alacer JAR) first,
then `rc2-mp-alacer` (fixed JAR, defaults). Normal non-benchmark clients use
the generated profile with `--server 127.0.0.1 --port 25567` auto-connect.
After join plus 20 s, the server console runs the earlier sequence:
`gamemode 3 ProfileTest`, `tp ProfileTest 512 80 512`, `setblock 512 80 518
minecraft:glowstone`, `time set 18000`; 40 s later `tp ProfileTest -190 80 280`,
`setblock -190 80 286 minecraft:glowstone`, `time set 6000`; 55 s later the
harness presses F3 then F2 in the focused game window (a native screenshot),
then closes the client window normally and stops the server through its console.
Budget: 6 minutes per client, server watchdog 8 minutes.

Interpretation: if both clients show zero cached chunks, the behaviour is
vanilla and not an Alacer release gate. If only Alacer shows it, it is a
release-blocking defect to diagnose. If neither shows it, the original event
is not reproduced and remains an open observation. Key delivery to LWJGL is
best effort: a missing screenshot is "not observed", never a pass.

These checks are functional evidence only. They are not Extended performance
qualification, online authentication, remote latency, mod compatibility or
broad gameplay coverage.
