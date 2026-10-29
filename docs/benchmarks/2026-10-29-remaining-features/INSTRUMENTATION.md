# Follow-up instrumentation

After the first entity launch, a separately compiled, non-shipped Java agent
observes existing sound, entity-pipeline and MDI counters every five seconds and
at shutdown. It does not transform classes or replace any implementation. These
counter snapshots are functional evidence, not timing measurements; periodic
observation has overhead and most counters are not synchronized snapshots.

For four resource-only launches, `alacer.followup.resourceReload=true` also enables
the archived ResourceReloadDriver. At 35 seconds after JVM start it schedules
three real `Minecraft.reloadResources()` calls, five seconds apart, through the
game's own `executeTask(Runnable)`. Each runs on the client thread with a loaded
world, logs begin/completion and reports cumulative mipmap verification counts.
This tests the same reload entrypoint invoked by F3+T. It does not establish that
the keyboard shortcut itself was delivered successfully. A separate UI attempt
is recorded in the final report. The resource launches use timeScale=1 so the
client remains alive through all reloads. No performance acceptance is assigned.

Build observer before clients (Temurin Java 25 explicit):

```
javac -d build/remaining-feature-agent-classes <archive>/CounterAgent.java <archive>/ResourceReloadDriver.java
jar cfm build/remaining-feature-observer.jar build/remaining-feature-agent.mf -C build/remaining-feature-agent-classes .
```

The manifest contains `Premain-Class: CounterAgent`. observer.init.gradle adds
only this agent to runClient and provides a dependency-export task for the
isolated production installation. Production uses no observer or reload driver.
