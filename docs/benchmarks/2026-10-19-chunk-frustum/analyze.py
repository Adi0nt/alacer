"""Validate the frustum runs and rebuild comparison.md/metrics.json from local raw CSVs.

Run from the repository root after the six full-duration fr-{0,200}-r{1,2,3} launches.
Verification and replay timings are separate from selection.
"""
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics

DEST = Path(__file__).resolve().parent
ROOT = DEST.parents[2]
LABELS = [f"fr-{n}-r{r}" for r in (1, 2, 3) for n in (0, 200)]
SECTIONS = ("visibility", "terrain_setup", "render", "entities", "swap", "tick")
DEFAULTS = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks",
            "avoidMatrixReads", "cacheClouds", "fastEntityShadows")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def percentile(values, p):
    return sorted(values)[max(0, math.ceil(len(values) * p) - 1)]


def folder_for(label):
    folders = list((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))
    require(len(folders) == 1, f"{label}: expected exactly one completed launch, got {folders}")
    return folders[0]


def stats(columns, phases):
    n = len(columns["frame_ns"])
    frame = columns["frame_ns"]
    non_tick = [a for a, t in zip(columns["alloc_bytes"], columns["tick_ns"]) if t == 0]
    result = {
        "frames": n, "frame_ms": statistics.mean(frame) / 1e6,
        "fps": n * 1e9 / sum(frame), "p99_ms": percentile(frame, .99) / 1e6,
        "one_percent_low_fps": 1e9 / statistics.mean(sorted(frame)[-max(1, math.ceil(n * .01)):]),
        "allocation_bytes": statistics.mean(columns["alloc_bytes"]),
        "non_tick_median_bytes": statistics.median(non_tick),
        "client_cpu_ms": sum(p["client_thread_cpu_ms_per_frame"] * p["frames"] for p in phases) / n,
        "process_cpu_cores": sum(p["process_cpu_cores"] * p["frames"] for p in phases) / n,
        "frustum_tests": statistics.mean(columns["frustum_tests"]),
        "searches": statistics.mean(columns["visibility_searches"]),
    }
    for section in SECTIONS:
        result[section + "_ms"] = statistics.mean(columns[section + "_ns"]) / 1e6
    return result


def load(label):
    folder = folder_for(label)
    summary = json.loads((folder / "summary.json").read_text())
    env = summary["environment"]
    expected_env = {"window": "1920x1080", "options.renderDistance": "12", "options.useVbo": "false",
                    "options.vsync": "false", "options.fancyGraphics": "true", "options.clouds": "2",
                    "benchmark.generationRun": "false", "benchmark.logGlErrors": "true",
                    "benchmark.toggle": "render.fastChunkFrustum (a=false, b=true)",
                    "benchmark.mobs": label.split("-")[1]}
    for key, value in expected_env.items():
        require(env[key] == value, f"{label}: {key}={env[key]} expected {value}")
    require("StartFlightRecording" not in env["jvm.args"], label + ": JFR enabled")
    expected_phases = {f"{p}-{s}" for p in ("static", "pan") for s in ("a1", "b1", "b2", "a2")}
    expected_phases |= {f"warmup-{p}-{s}" for p in ("static", "pan") for s in ("a", "b")}
    require(set(summary["phases"]) == expected_phases, label + ": phase set")
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
    require("BUILD SUCCESSFUL" in log and "Benchmark aborted" not in log, label + ": completion")
    for text in ("GL ERROR", "OpenGLException", "Rendering entity in world", "Matrix read mismatch",
                 "reading matrices from OpenGL every frame again", "Chunk frustum test mismatch",
                 "could not list the mixins", "is changed by mixin",
                 "checking for GL errors every frame again", "Chunk frustum replay:"):
        require(text not in log, f"{label}: {text}")
    data = {}
    for name, phase in summary["phases"].items():
        config = phase["config"]
        for key in DEFAULTS:
            require(config["render." + key] == "true", f"{label}/{name}: {key}")
        require(config["render.fastChunkFrustum"] == str(name.rsplit("-", 1)[1].startswith("b")).lower(),
                f"{label}/{name}: wrong toggle")
        for key in ("render.skipZeroTranslates", "render.batchModelLists", "profiler.entityDetail",
                    "profiler.frustumTiming", "profiler.shadowTiming", "profiler.hud"):
            require(config[key] == "false", f"{label}/{name}: {key}")
        require(all(v == "false" for k, v in config.items() if k.endswith(".verify")), f"{label}/{name}: verify")
        require(config["profiler.enabled"] == "true", f"{label}/{name}: profiler disabled")
        require(phase["dropped_frames"] == 0 and phase["discarded_setup_frames"] == 1, f"{label}/{name}: capture")
        target = 7.5 if "pan" in name else 5.0
        require(phase["target_seconds"] == target and phase["recorded_wall_seconds"] >= target - .001,
                f"{label}/{name}: duration")
        report = json.loads((folder / f"{name}.json").read_text())
        require(report["config"] == config, f"{label}/{name}: report config")
        with (folder / f"{name}.csv").open(newline="") as handle:
            rows = list(csv.DictReader(handle))
        columns = {k: [int(row[k]) for row in rows] for k in rows[0]}
        require(len(rows) == phase["frames"] and min(columns["frame_ns"]) > 0, f"{label}/{name}: rows")
        require(all(b > a for a, b in zip(columns["start_ns"], columns["start_ns"][1:])), f"{label}/{name}: order")
        require(abs(sum(columns["frame_ns"]) / 1e9 - phase["recorded_wall_seconds"]) < .02, f"{label}/{name}: clock")
        require(abs(len(rows) * 1e9 / sum(columns["frame_ns"]) - phase["avg_fps"]) < .00006,
                f"{label}/{name}: FPS summary differs from raw frames")
        require(min(columns["alloc_bytes"]) >= 0, f"{label}/{name}: no allocation data")
        for key in ("frustum_replay_vanilla_ns", "frustum_replay_planes_ns", "entity_draw_ns", "gl_calls"):
            require(not any(columns[key]), f"{label}/{name}: intrusive counters")
        if "pan" in name:
            require(statistics.mean(columns["frustum_tests"]) > 100, f"{label}/{name}: no frustum tests")
        data[name] = columns
    (DEST / "runs").mkdir(exist_ok=True)
    (DEST / "runs" / f"{label}.summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    result = {}
    for p in ("static", "pan"):
        result[p] = {}
        for variant, suffixes in (("a", ("a1", "a2")), ("b", ("b1", "b2")),
                                  ("a1", ("a1",)), ("a2", ("a2",))):
            names = [f"{p}-{s}" for s in suffixes]
            columns = {k: [v for name in names for v in data[name][k]] for k in data[names[0]]}
            result[p][variant] = stats(columns, [summary["phases"][name] for name in names])
    return result, env


def main():
    manifest = json.loads((DEST / "implementation.json").read_text())
    for path, expected in manifest["sha256"].items():
        require(hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == expected, "Source changed: " + path)
    results = {}
    environment = None
    for label in LABELS:
        results[label], env = load(label)
        identity = {k: env[k] for k in ("java.version", "java.vm", "cpu", "gl.renderer", "gl.version", "mods",
                                       "os", "jvm.maxHeapMB", "jvm.gc", "options.fpsLimit",
                                       "options.smoothLighting", "options.mipmapLevels", "options.particles",
                                       "benchmark.seed", "benchmark.anchor")}
        identity["mods"] = sorted(identity["mods"].split())
        if environment is None:
            environment = identity
        require(environment == identity, label + ": environment changed")
    lines = ["# Chunk frustum: in-process A/B/B/A", "", "A: vanilla culler; B: one corner per plane. Other defaults on. Timers and verification off.",
             "", "| Launch | Phase | Frame A → B ms | Visibility A → B ms | Client CPU A → B ms | FPS change | vs A2 | p99 A → B ms | Non-tick bytes A → B |",
             "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for name, v in phases.items():
            a, b, a2 = v["a"], v["b"], v["a2"]
            pairs = [f"{a[k]:.3f} → {b[k]:.3f}" for k in ("frame_ms", "visibility_ms", "client_cpu_ms")]
            lines.append(f"| {label} | {name} | " + " | ".join(pairs)
                         + f" | {(b['fps']/a['fps']-1)*100:+.2f}% | {(b['fps']/a2['fps']-1)*100:+.2f}%"
                         + f" | {a['p99_ms']:.3f} → {b['p99_ms']:.3f} | {a['non_tick_median_bytes']:.0f} → {b['non_tick_median_bytes']:.0f} |")
    selection = {}
    for n in (0, 200):
        selection[str(n)] = {}
        for phase in ("static", "pan"):
            rows = [v[phase] for label, v in results.items() if label.split("-")[1] == str(n)]
            gains = [(v["b"]["fps"] / v["a"]["fps"] - 1) * 100 for v in rows]
            selection[str(n)][phase] = {"fps_gain_median_percent": statistics.median(gains),
                "fps_gain_range_percent": [min(gains), max(gains)],
                "wins_vs_a": sum(v["b"]["fps"] > v["a"]["fps"] for v in rows),
                "wins_vs_a2": sum(v["b"]["fps"] > v["a2"]["fps"] for v in rows),
                "visibility_ms_saved_median": statistics.median(v["a"]["visibility_ms"] - v["b"]["visibility_ms"] for v in rows),
                "visibility_wins_vs_a": sum(v["b"]["visibility_ms"] < v["a"]["visibility_ms"] for v in rows),
                "visibility_wins_vs_a2": sum(v["b"]["visibility_ms"] < v["a2"]["visibility_ms"] for v in rows)}
    lines += ["", "A1/A2 drift and full section means (ms/frame). Sections overlap; do not add them.", "",
              "| Launch | Phase | Frame A1 | Frame A2 | Visibility A1 | Visibility A2 | Visibility B | Swap A → B | Render A → B |",
              "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for name, v in phases.items():
            a, b, a1, a2 = v["a"], v["b"], v["a1"], v["a2"]
            lines.append(f"| {label} | {name} | {a1['frame_ms']:.3f} | {a2['frame_ms']:.3f}"
                         + f" | {a1['visibility_ms']:.3f} | {a2['visibility_ms']:.3f} | {b['visibility_ms']:.3f}"
                         + f" | {a['swap_ms']:.3f} → {b['swap_ms']:.3f} | {a['render_ms']:.3f} → {b['render_ms']:.3f} |")
    (DEST / "comparison.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (DEST / "metrics.json").write_text(json.dumps({"runs": results, "selection": selection}, indent=2) + "\n")
    print(json.dumps(selection, indent=2))


if __name__ == "__main__":
    main()
