"""Rebuild Phase 10 tables from the ignored game CSVs. Run from the repository root.

Default: p10-{0,200,1000}-r{1,2,3}. Explicit labels may be passed for an interim report.
Only complete, full-duration launches qualify; the screenshot smoke run is separate.
"""
import csv
import hashlib
import json
import math
import pathlib
import statistics
import sys

DEST = pathlib.Path(__file__).resolve().parent
ROOT = DEST.parents[2]
ON = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks", "avoidMatrixReads", "cacheClouds")
OFF = ("skipZeroTranslates", "batchModelLists")
SECTIONS = ("render", "world", "terrain_setup", "visibility", "chunk_upload", "entities", "entity_loop",
            "camera_setup", "frustum", "frustum_culler", "gl_error_check", "clouds", "swap", "tick")
DETAIL = ("entity_chunk_lookup", "entity_visibility_check", "entity_draw", "block_entity_draw",
          "entity_living_setup", "entity_model_prepare", "entity_model_animation", "entity_render_model",
          "entity_model_draw", "entity_layers", "entity_shadow", "entity_shadow_block", "entity_shadow_query",
          "entity_name_tag", "entity_overlay")


def percentile(values, percent):
    return values[max(0, min(len(values) - 1, math.ceil(percent / 100 * len(values)) - 1))]


def merge(columns):
    return {key: [v for c in columns for v in c[key]] for key in columns[0]}


def stats(columns, phases):
    frame = sorted(columns["frame_ns"])
    n = len(frame)
    assert n > 0
    total = sum(frame)
    slowest = frame[n - max(1, n // 100):]
    non_tick = sorted(a for a, t in zip(columns["alloc_bytes"], columns["tick_ns"]) if t == 0)
    result = {
        "frames": n, "frame mean ms": total / n / 1e6, "fps": n * 1e9 / total,
        "p50 ms": percentile(frame, 50) / 1e6, "p99 ms": percentile(frame, 99) / 1e6,
        "1% low fps": 1e9 / statistics.mean(slowest),
        "alloc KB/frame": statistics.mean(columns["alloc_bytes"]) / 1024,
        "non-tick allocation median bytes": percentile(non_tick, 50),
        "visible chunks mean": statistics.mean(columns["visible_chunks"]),
        "pending chunks mean": statistics.mean(columns["pending_chunks"]),
        "client CPU ms/frame": sum(p["client_thread_cpu_ms_per_frame"] * p["frames"] for p in phases) / n,
        "process CPU cores": sum(p["process_cpu_cores"] * p["frames"] for p in phases) / n,
    }
    for section in SECTIONS:
        result[section + " mean ms"] = statistics.mean(columns[section + "_ns"]) / 1e6
    return result


def validate_config(name, config):
    enabled = name.rsplit("-", 1)[1].startswith("b")
    for key in ON:
        assert config["render." + key] == str(enabled).lower(), (name, key)
    for key in OFF:
        assert config["render." + key] == "false", (name, key)
    assert all(v == "false" for k, v in config.items() if k.endswith(".verify"))
    assert config["profiler.entityDetail"] == config["profiler.hud"] == "false"
    assert config["profiler.enabled"] == "true" and config["profiler.logInterval"] == "0"
    assert {k for k in config if k.startswith("render.") and not k.endswith(".verify")} == {"render." + k for k in ON + OFF}


def load_launch(label):
    folder = sorted((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))[-1]
    source = (folder / "summary.json").read_text(encoding="utf-8")
    summary = json.loads(source)
    env = summary["environment"]
    for key, expected in {"window": "1920x1080", "options.renderDistance": "12", "options.useVbo": "false",
                          "options.vsync": "false", "options.fancyGraphics": "true", "options.clouds": "2",
                          "benchmark.generationRun": "false", "benchmark.logGlErrors": "true"}.items():
        assert env[key] == expected, (label, key, env[key])
    assert "StartFlightRecording" not in env["jvm.args"]
    assert env["benchmark.toggle"] == "render.defaults (a=false, b=true)"
    assert int(env["benchmark.mobs"]) in (0, 200, 1000)
    expected_names = {f"{phase}-{segment}" for phase in ("static", "pan") for segment in ("a1", "b1", "b2", "a2")}
    expected_names |= {f"warmup-{phase}-{variant}" for phase in ("static", "pan") for variant in ("a", "b")}
    assert set(summary["phases"]) == expected_names
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
    assert "BUILD SUCCESSFUL" in log and "Benchmark aborted" not in log
    for failure in ("GL ERROR", "OpenGLException", "Rendering entity in world", "Matrix read mismatch",
                    "reading matrices from OpenGL every frame again", "checking for GL errors every frame again"):
        assert failure not in log, (label, failure)
    assert "Model list batch final: 0 lists compiled" in log
    data = {}
    for name, phase in summary["phases"].items():
        validate_config(name, phase["config"])
        assert phase["dropped_frames"] == 0 and phase["discarded_setup_frames"] == 1
        target = 7.5 if "pan" in name else 5.0
        assert phase["target_seconds"] == target
        assert phase["recorded_wall_seconds"] >= target - 0.001
        report = json.loads((folder / (name + ".json")).read_text(encoding="utf-8"))
        assert report["config"] == phase["config"]
        columns = {}
        with (folder / (name + ".csv")).open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                for key, value in row.items():
                    columns.setdefault(key, []).append(int(value))
        assert len(columns["frame_ns"]) == phase["frames"] > 0
        assert all(n > 0 for n in columns["frame_ns"])
        assert all(b > a for a, b in zip(columns["start_ns"], columns["start_ns"][1:]))
        for section in DETAIL:
            assert not any(columns[section + "_ns"]), (label, name, section)
        assert not any(columns["gl_calls"])
        assert min(columns["alloc_bytes"]) >= 0
        assert abs(len(columns["frame_ns"]) * 1e9 / sum(columns["frame_ns"]) - phase["avg_fps"]) < 0.00006
        # Capture and CPU clocks begin at the same completed-frame boundary.
        elapsed = sum(columns["frame_ns"]) / 1e9
        assert abs(elapsed - phase["recorded_wall_seconds"]) < 0.02
        data[name] = columns
    (DEST / "runs").mkdir(exist_ok=True)
    (DEST / "runs" / f"{label}.summary.json").write_text(source, encoding="utf-8", newline="\n")
    variants = {}
    for phase in ("static", "pan"):
        variants[phase] = {}
        for variant, names in (("a1", ["a1"]), ("a2", ["a2"]), ("b1", ["b1"]), ("b2", ["b2"]),
                               ("a", ["a1", "a2"]), ("b", ["b1", "b2"])):
            keys = [phase + "-" + name for name in names]
            variants[phase][variant] = stats(merge([data[k] for k in keys]), [summary["phases"][k] for k in keys])
    return variants


def main(labels):
    manifest = json.loads((DEST / "implementation.json").read_text(encoding="utf-8"))
    for name, digest in manifest["source_sha256"].items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
    results = {label: load_launch(label) for label in labels}
    environments = [json.loads((DEST / "runs" / f"{label}.summary.json").read_text(encoding="utf-8"))["environment"] for label in labels]
    for key in ("java.version", "java.vendor", "cpu", "gl.renderer", "gl.version", "jvm.maxHeapMB"):
        assert len({env[key] for env in environments}) == 1, key
    # Fabric Loader lists mods in hash order, which varies per launch; ids and versions must still match.
    assert len({frozenset(env["mods"].split()) for env in environments}) == 1, "mods"
    (DEST / "metrics.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
    out = ["# Phase 10: current Alacer defaults versus vanilla render paths", "",
           "A = five default optimizations off; B = current defaults. Both experiments remain off.",
           "The same instrumented client measures both; this is not a separate unmodified-client test.",
           "B pools b1/b2, A pools a1/a2; warmups are excluded. All numbers derive from raw frames.", "",
           "| Launch | Phase | A1 ms | A2 ms | B ms | B vs A ms | B vs A2 ms | FPS A -> B | FPS gain |",
           "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a1, a2, a, b = (v[k] for k in ("a1", "a2", "a", "b"))
            out.append(f"| {label} | {phase} | {a1['frame mean ms']:.3f} | {a2['frame mean ms']:.3f}"
                       f" | {b['frame mean ms']:.3f} | {(b['frame mean ms']/a['frame mean ms']-1)*100:+.1f}%"
                       f" | {(b['frame mean ms']/a2['frame mean ms']-1)*100:+.1f}%"
                       f" | {a['fps']:.1f} -> {b['fps']:.1f} | {(b['fps']/a['fps']-1)*100:+.1f}% |")
    selection = {}
    out += ["", "## Paired summary", "", "Median of the per-launch changes; the range shows every repeat.", "",
            "| Added mobs | Phase | FPS gain median (range) | Frame reduction median | FPS gain vs A2 median | Wins vs A / A2 |",
            "|---|---|---:|---:|---:|---:|"]
    for population in (0, 200, 1000):
        selection[str(population)] = {}
        for phase in ("static", "pan"):
            rows = [v[phase] for label, v in results.items() if label.startswith(f"p10-{population}-")]
            if not rows:
                continue
            fps = [(v["b"]["fps"] / v["a"]["fps"] - 1) * 100 for v in rows]
            fps2 = [(v["b"]["fps"] / v["a2"]["fps"] - 1) * 100 for v in rows]
            frame = [(1 - v["b"]["frame mean ms"] / v["a"]["frame mean ms"]) * 100 for v in rows]
            record = {"launches": len(rows), "fps_gain_percent_median": statistics.median(fps),
                      "fps_gain_percent_min": min(fps), "fps_gain_percent_max": max(fps),
                      "fps_gain_vs_A2_percent_median": statistics.median(fps2),
                      "frame_reduction_percent_median": statistics.median(frame),
                      "wins_vs_A": sum(x > 0 for x in fps), "wins_vs_A2": sum(x > 0 for x in fps2)}
            selection[str(population)][phase] = record
            out.append(f"| {population} | {phase} | {statistics.median(fps):+.1f}% ({min(fps):+.1f} to {max(fps):+.1f}%)"
                       f" | {statistics.median(frame):.1f}% | {statistics.median(fps2):+.1f}%"
                       f" | {record['wins_vs_A']}/{len(rows)} / {record['wins_vs_A2']}/{len(rows)} |")
    (DEST / "selection.json").write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    out += ["", "## CPU, allocation and tails (pooled A -> B)", "",
            "| Launch | Phase | Client CPU ms/frame | Allocation KB/frame | Non-tick median bytes | p99 ms | 1% low FPS |",
            "|---|---|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:.3f} -> {b[k]:.3f}" for k in
                      ("client CPU ms/frame", "alloc KB/frame", "non-tick allocation median bytes", "p99 ms", "1% low fps")]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")
    out += ["", "## Coarse sections (pooled A -> B, ms/frame)", "",
            "Nested sections overlap; do not add them. Process CPU cores do not identify driver threading behavior.", "",
            "| Launch | Phase | Visibility | Entity loop | Clouds | GL checks | Camera | Render | Swap | Process cores |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:.3f} -> {b[k]:.3f}" for k in
                      ("visibility mean ms", "entity_loop mean ms", "clouds mean ms", "gl_error_check mean ms",
                       "camera_setup mean ms", "render mean ms", "swap mean ms", "process CPU cores")]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")
    (DEST / "comparison.md").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(selection, indent=2))


if __name__ == "__main__":
    main(sys.argv[1:] or [f"p10-{n}-r{r}" for r in (1, 2, 3) for n in (0, 200, 1000)])
