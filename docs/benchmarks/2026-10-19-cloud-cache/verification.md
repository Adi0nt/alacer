# Cloud cache verification

Run from the repository root (Gradle on JDK 25, game on Temurin 8):

```bash
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=cc-verify -Palacer.benchmark.entities=200 -Palacer.render.cacheClouds.verify=true -Pwindow=1920x1080 -Pxmx=2G
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=cc-sweep-verify12 -Palacer.benchmark.cloudSweep=true -Palacer.benchmark.timeScale=0.5 -Palacer.render.cacheClouds=false -Palacer.render.cacheClouds.verify=true -Palacer.benchmark.screenshots=true -Pwindow=1920x1080 -Pxmx=2G
./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=cc-sweep-cached -Palacer.benchmark.cloudSweep=true -Palacer.benchmark.timeScale=0.5 -Palacer.benchmark.screenshots=true -Pwindow=1920x1080 -Pxmx=2G
```

- `cc-verify`: ordinary 200-mob static/pan/fly. Last periodic report: 172,021,248 vertices
  compared, zero vertex or tile-size mismatches; maximum position error 0.0000038146973.
- `cc-sweep-verify12`: **25,648 fancy-cloud invocations, 275,956,224 vertices compared, zero
  vertex or tile-size mismatches**. Maximum position error 0.0000038146973 cloud units;
  3,129 rebuilds (including stereo eyes and changing rain colors). Height-band mask 7 and
  anaglyph-pass mask 7 confirm all three bands and passes 0/1/2 were checked.
- The sweep records below/through/above-cloud views, negative and positive texture-wrap
  boundaries, rain at time 13000, anaglyph, flat clouds, disabled clouds and a return to fancy.
  Verification is deliberately enabled while `render.cacheClouds=false`, proving the debug
  option works independently of the optimization toggle. Both modes share cache construction.
- `cc-sweep-cached` uses the actual list drawing with verification off and saves the same
  ten scene screenshots under `run/screenshots/`. This is a visual smoke check, not a pixel
  equality comparison: the launches do not have synchronized cloud drift, weather or entities.
  Screenshots below/above the layer and in stereo/rain/returned-fancy views were inspected:
  cloud faces, shading and transparency render; stereo colors and weather remain visible.

`./gradlew build` passed after implementation, including unit tests and access-widener
validation. Changed source, documentation and archived JSON files use LF line endings.

Extracted verifier output is in [verification.log.md](verification.log.md). Diagnostic summaries
are archived in `runs/`; screenshots and full launch logs remain in ignored `run/screenshots/`
and `build/cc-*.log`. None of these diagnostic runs contributes to the A/B performance tables.

A first sweep at view distance 4 (`cc-sweep-verify`) could not resolve the initial benchmark
anchor outside the loaded spawn area. It was stopped, then rerun at the established distance
12. Its partial counts are excluded. No additional driver mode was forced during this session.
