# Installer profile and longer functional release checks

Source c79ff99 plus documentation/tooling only, on codex/v1-release-checks.
Keep Minecraft master volume zero, Java 25 explicit, shipped optimization
defaults unchanged, and original worlds/config/options untouched.

Use official Ornithe installer 0.16.0 (stable metadata, Maven SHA-256 verified)
to generate an Official/Fabric 0.20.0/Minecraft 1.8.9/Calamus Gen 2 installation
under build/v1-release-checks/installer-instance. Preserve generated JSON bytes.
Resolve its actual inherited libraries/rules/JVM arguments; do not substitute
the prior manually composed classpath or add namespace/package-access fixes
that the generated profile does not supply. A profile-driven offline launch is
not a test of the official launcher frontend or account authentication.

First campaign: at most two clients and 25 minutes from the first client start,
serial, no retries. Stop on native/mixin/GL errors, missing summaries or functional
failure; retain every attempt. Prepare all downloads/tooling before the clients.

1. Installer-generated profile smoke: actual remapped Alacer JAR and documented
   four OSL modules, no development classes or observer, normal defaults, 200 mobs,
   short static/pan/fly phases and native screenshots. Require source/JAR class
   origins, Java 25, normal exit and visible terrain/entities.
2. Longer default stability: same generated profile/artifact/dependencies,
   static/pan/fly phases of 60/120/300 seconds, watchdog 660 seconds, normal
   shutdown, complete phase records, no fatal/GL/mixin errors and zero dropped
   profiler frames. This is an eight-minute functional soak, not Extended
   performance qualification or full gameplay/lifecycle coverage.

Archive installer command/version/hash, original generated profiles, resolved
dependency hashes/URLs, actual Java argument lists, logs/summaries/screenshots,
raw-file hashes and options/config integrity. No source edit or build beside
these clients. Preserve existing optional startup errors as limitations. No
performance score or default promotion follows from this campaign.

Afterward, update the Claude handoff with actual completed work and remaining
world-switch/reload/resource-pack, loopback multiplayer, compatible mod-matrix,
external-launcher frontend/authentication and Extended performance gates. Further
clients need their own declared scenario/protocol, not silent extra attempts.
