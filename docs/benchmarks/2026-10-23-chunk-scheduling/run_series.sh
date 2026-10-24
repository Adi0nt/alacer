#!/bin/bash
# The series as run on 2026-10-23 (serial launches, labels cs-<scene>-<n><arm>, order ABBABAAB per scene).
# Requires JAVA_HOME (Temurin 25), xvfb-run and the base world in run/saves/alacer-benchmark.
set -u
cd "$(dirname "$0")/../../.."
mkdir -p build/cs-logs
ORDER=ABBABAAB
for scene in edit flight; do
  if [ $scene = edit ]; then sp=-Palacer.benchmark.chunkEdit=true; else sp=-Palacer.benchmark.chunkFlight=near; fi
  for i in 0 1 2 3 4 5 6 7; do
    arm=${ORDER:$i:1}; lab=cs-$scene-$((i + 1))${arm,,}
    if [ $arm = A ]; then floor=false; else floor=true; fi
    LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" bash ./gradlew runClient --offline -Pwindow=854x480 -Pxmx=2G \
      -Palacer.benchmark=true $sp -Palacer.benchmark.label=$lab -Palacer.benchmark.timeout=800 -Palacer.benchmark.vbo=false \
      -Palacer.benchmark.viewDistance=8 -Palacer.profiler.chunkLatency=true -Palacer.render.chunkTimeFloor=$floor > build/cs-logs/$lab.log 2>&1
    echo "$lab exit $?"
  done
done
