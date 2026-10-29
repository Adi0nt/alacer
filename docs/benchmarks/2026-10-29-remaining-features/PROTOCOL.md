# Muted native feature follow-up

Source starts at `0277cbd` on `codex/remaining-feature-testing`. This is a new
functional campaign, separate from the completed defaults qualification. No
optimization defaults will be changed. Java 25 is explicit for Gradle and the
client. Persisted Alacer configuration is not used by benchmark launches.

Before launching, back up options/config and set Minecraft master volume to 0.
Keep master volume muted afterwards as requested. Do not mute other applications.
Use fresh benchmark world copies. Retain every attempt, command, exit code, log,
summary and screenshot; never overwrite an existing label or silently retry.

Functional diagnostics (not timed A/B evidence):

1. Entity pipeline, lighting, culling, renderer lookup with 200 mobs and 50 chests.
2. Ray tracing, ambient display ticks, sound-thread waits and chunk adjacency.
3. Modern GL terrain shader and upload readback verification.
4. Multi-draw indirect command/origin validation with its required terrain path.
5. Particle light/vertices/removal under existing adversarial particle probes.
6. Mipmap off/on/on/off resource reloads, with resource timing and verification.
   Attempt the actual F3+T shortcut. If unsupported, report that distinctly and
   exercise reload through the existing Video Settings resource-change path.
7. The actual remapped JAR under a production-namespace Fabric Loader launch,
   using locally installed dependencies, without development Alacer classes.

Inspect current code to distinguish reachable paths from implemented but unwired
GPU compute/sort helpers. A zero mismatch count only passes a verifier with
positive comparison activity. MDI validates structs/origins, not framebuffer
identity. Native screenshots need visual review. Stop the affected feature after
a crash or correctness failure; independent diagnostics can continue. Known
Log4j/Twitch/Realms startup errors are retained and explicitly reported.

No performance promotion, listening validation, arbitrary mod compatibility,
multiplayer/network throughput, other hardware/OS, or release publication is
implied by these functional checks. Extended stability remains a separate gate.
