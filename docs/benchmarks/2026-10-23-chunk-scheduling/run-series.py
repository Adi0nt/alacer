"""Run the unchanged protocol serially inside the prepared Linux container.

From the repository root: JAVA_HOME=/opt/alacer-jdk python3 -I <this file>.
Generate the base world and compile/configure the client BEFORE invoking this file.
No build or analysis runs concurrently with a client. Every attempt has its own log.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path.cwd()
ARCHIVE = ROOT / "docs/benchmarks/2026-10-23-chunk-scheduling"
OUT = ROOT / "build/chunk-scheduling-series"
OUT.mkdir(parents=True, exist_ok=True)


def hashes():
    paths = sorted(Path("src").rglob("*")) + [Path("build.gradle"), Path("gradle.properties"),
        ARCHIVE.relative_to(ROOT) / "protocol.md", ARCHIVE.relative_to(ROOT) / "evaluate.py"]
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}


frozen = hashes()
base = Path("run/saves/alacer-benchmark")
assert base.is_dir(), "Generate the base world first"
(ARCHIVE / "base-world-hashes.json").write_text(json.dumps({str(p.relative_to(base)):
    hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(base.rglob("*")) if p.is_file()}, indent=2) + "\n")
freeze = ARCHIVE / "rerun-freeze.json"
if freeze.exists():
    raise SystemExit("A rerun freeze already exists; preserve this attempt and review before restarting")
freeze.write_text(json.dumps({"source_commit": "5030c5f53bae8bf903f842705465dcae61efe1bb",
    "hashes": frozen, "java_home": os.environ["JAVA_HOME"], "order": "ABBABAAB",
    "note": "New complete local Docker/llvmpipe series; prior cloud attempt remains aborted. See rerun-environment.txt."}, indent=2) + "\n")

attempts = []
for scene in ("edit", "flight"):
    queue = [(i + 1, arm, 1) for i, arm in enumerate("ABBABAAB")]
    for number, arm, attempt in queue:
        assert hashes() == frozen, "Sources or protocol changed during series"
        label = f"cs-{scene}-{number}{arm.lower()}"
        log = OUT / f"{label}-attempt{attempt}.log"
        if log.exists():
            raise SystemExit(f"Refusing to overwrite {log}")
        before = set(Path("run/alacer/benchmarks").glob(f"*-{label}-vis_*/summary.json"))
        command = ["xvfb-run", "-a", "-s", "-screen 0 1280x720x24", "bash", "./gradlew",
            "runClient", "--offline", "--console=plain", "-Pwindow=854x480", "-Pxmx=2G",
            "-Palacer.benchmark=true", f"-Palacer.benchmark.label={label}",
            "-Palacer.benchmark.viewDistance=8", "-Palacer.benchmark.vbo=false",
            "-Palacer.benchmark.timeout=800", "-Palacer.profiler.chunkLatency=true",
            "-Palacer.profiler.chunkDetail=false", f"-Palacer.render.chunkTimeFloor={str(arm == 'B').lower()}",
            "-Palacer.benchmark.chunkEdit=true" if scene == "edit" else "-Palacer.benchmark.chunkFlight=near"]
        print(f"START {label} attempt {attempt}", flush=True)
        started = time.time()
        with log.open("w") as stream:
            result = subprocess.run(command, env={**os.environ, "LIBGL_ALWAYS_SOFTWARE": "1"},
                stdout=stream, stderr=subprocess.STDOUT)
        after = set(Path("run/alacer/benchmarks").glob(f"*-{label}-vis_*/summary.json"))
        completed = result.returncode == 0 and len(after - before) == 1
        attempts.append({"label": label, "attempt": attempt, "exit_code": result.returncode,
            "elapsed_seconds": time.time() - started, "completed": completed,
            "summaries": [str(p) for p in sorted(after - before)], "command": command})
        (OUT / "attempts.json").write_text(json.dumps(attempts, indent=2) + "\n")
        print(f"END {label}: completed={completed}, elapsed={time.time() - started:.1f}s", flush=True)
        if not completed:
            for summary in after - before:
                target = OUT / "aborted" / summary.parent.name
                target.parent.mkdir(parents=True, exist_ok=True)
                summary.parent.rename(target)
            if attempt == 1:
                queue.append((number, arm, 2))
            else:
                raise SystemExit(f"Second failure for {label}; cannot complete the frozen series")
assert hashes() == frozen
print("SERIES COMPLETE: 16 launches; evaluate only after this runner exits", flush=True)
