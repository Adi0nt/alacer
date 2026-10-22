"""Rebuild the entity shadow session's tables from the ignored game CSVs. Run from the repository root.

Series (launch labels):
  attribution   sh-attr-{200,1000}-r{1,2}: A vanilla shadow queries, B the game's Entity Shadows option off
  optimization  sh-{200,1000}-r{1,2,3}:    A vanilla shadow queries, B render.fastEntityShadows
  timing        sh-time-{200,1000}-r1:     the optimization toggle with the coarse shadow timer on
Only complete, full-duration launches qualify; smoke and verification runs are separate.

`--pilot` instead rebuilds pilot.md from the superseded shd-* launches, whose shadow hooks used
@WrapOperation (see implementation.json). They are kept as evidence and never used for selection.
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
DEFAULTS = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks", "avoidMatrixReads", "cacheClouds")
OFF = ("skipZeroTranslates", "batchModelLists")
SECTIONS = ("render", "world", "entities", "entity_loop", "shadows", "clouds", "gl_error_check", "camera_setup", "swap", "tick")
DETAIL = ("entity_chunk_lookup", "entity_visibility_check", "entity_draw", "block_entity_draw",
          "entity_living_setup", "entity_model_prepare", "entity_model_animation", "entity_render_model",
          "entity_model_draw", "entity_layers", "entity_shadow", "entity_shadow_block", "entity_shadow_query",
          "entity_name_tag", "entity_overlay")
SERIES = {
    "attribution": {"labels": [f"sh-attr-{n}-r{r}" for r in (1, 2) for n in (200, 1000)],
                    "toggle": "benchmark.disableEntityShadows", "timing": True},
    "optimization": {"labels": [f"sh-{n}-r{r}" for r in (1, 2, 3) for n in (200, 1000)],
                     "toggle": "render.fastEntityShadows", "timing": False},
    "timing": {"labels": [f"sh-time-{n}-r1" for n in (200, 1000)],
               "toggle": "render.fastEntityShadows", "timing": True},
    "pilot-attribution": {"labels": [f"shd-attr-{n}-r{r}" for r in (1, 2) for n in (200, 1000)],
                          "toggle": "benchmark.disableEntityShadows", "timing": True, "pilot": True},
    "pilot-optimization": {"labels": [f"shd-{n}-r{r}" for r in (1, 2) for n in (200, 1000)],
                           "toggle": "render.fastEntityShadows", "timing": False, "pilot": True},
}
SHADOWS_OFF = "benchmark.disableEntityShadows"


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
        "shadows drawn mean": statistics.mean(columns["shadows_drawn"]),
        "client CPU ms/frame": sum(p["client_thread_cpu_ms_per_frame"] * p["frames"] for p in phases) / n,
        "process CPU cores": sum(p["process_cpu_cores"] * p["frames"] for p in phases) / n,
    }
    for section in SECTIONS:
        result[section + " mean ms"] = statistics.mean(columns[section + "_ns"]) / 1e6
    return result


def validate_config(series, name, config):
    b = name.rsplit("-", 1)[1].startswith("b")
    for key in DEFAULTS:
        assert config["render." + key] == "true", (name, key)
    for key in OFF:
        assert config["render." + key] == "false", (name, key)
    assert all(v == "false" for k, v in config.items() if k.endswith(".verify")), name
    assert config["profiler.entityDetail"] == config["profiler.hud"] == "false"
    assert config["profiler.enabled"] == "true" and config["profiler.logInterval"] == "0"
    assert config["profiler.shadowTiming"] == str(SERIES[series]["timing"]).lower(), name
    if SERIES[series]["toggle"] == SHADOWS_OFF:
        assert config["render.fastEntityShadows"] == "false", name
        assert config["options.entityShadows"] == str(not b).lower(), name
    else:
        assert config["render.fastEntityShadows"] == str(b).lower(), name
        assert config["options.entityShadows"] == "true", name


def load_launch(series, label):
    folder = sorted((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))[-1]
    source = (folder / "summary.json").read_text(encoding="utf-8")
    summary = json.loads(source)
    env = summary["environment"]
    for key, expected in {"window": "1920x1080", "options.renderDistance": "12", "options.useVbo": "false",
                          "options.vsync": "false", "options.fancyGraphics": "true", "options.clouds": "2",
                          "options.entityShadows": "true", "benchmark.generationRun": "false",
                          "benchmark.logGlErrors": "true"}.items():
        assert env[key] == expected, (label, key, env[key])
    assert "StartFlightRecording" not in env["jvm.args"]
    assert env["benchmark.toggle"] == SERIES[series]["toggle"] + " (a=false, b=true)", label
    mobs = int(label.split("-")[-2])
    assert int(env["benchmark.mobs"]) == mobs, label
    expected_names = {f"{phase}-{segment}" for phase in ("static", "pan") for segment in ("a1", "b1", "b2", "a2")}
    expected_names |= {f"warmup-{phase}-{variant}" for phase in ("static", "pan") for variant in ("a", "b")}
    assert set(summary["phases"]) == expected_names, label
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
    assert "BUILD SUCCESSFUL" in log and "Benchmark aborted" not in log, label
    for failure in ("GL ERROR", "OpenGLException", "Rendering entity in world", "Matrix read mismatch",
                    "reading matrices from OpenGL every frame again", "checking for GL errors every frame again",
                    "Entity shadow block state mismatch", "Entity shadow raw brightness mismatch",
                    "Entity shadow brightness mismatch"):
        assert failure not in log, (label, failure)
    assert "Model list batch final: 0 lists compiled" in log, label
    assert "Entity shadow queries final: 0 queries compared, 0 mismatched" in log, label
    data = {}
    for name, phase in summary["phases"].items():
        validate_config(series, name, phase["config"])
        assert phase["dropped_frames"] == 0 and phase["discarded_setup_frames"] == 1, (label, name)
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
        # The coarse timer runs only when requested, and B of the attribution draws no shadows.
        shadowless = SERIES[series]["toggle"] == SHADOWS_OFF and name.rsplit("-", 1)[1].startswith("b")
        if not SERIES[series]["timing"] or shadowless:
            assert not any(columns["shadows_ns"]) and not any(columns["shadows_drawn"]), (label, name)
        else:
            assert statistics.mean(columns["shadows_drawn"]) > 10, (label, name)
        assert min(columns["alloc_bytes"]) >= 0
        assert abs(len(columns["frame_ns"]) * 1e9 / sum(columns["frame_ns"]) - phase["avg_fps"]) < 0.00006
        # Capture and CPU clocks begin at the same completed-frame boundary.
        elapsed = sum(columns["frame_ns"]) / 1e9
        assert abs(elapsed - phase["recorded_wall_seconds"]) < 0.02
        data[name] = columns
    runs = DEST / "runs" / ("pilot" if SERIES[series].get("pilot") else "")
    runs.mkdir(parents=True, exist_ok=True)
    (runs / f"{label}.summary.json").write_text(source, encoding="utf-8", newline="\n")
    variants = {}
    for phase in ("static", "pan"):
        variants[phase] = {}
        for variant, names in (("a1", ["a1"]), ("a2", ["a2"]), ("b1", ["b1"]), ("b2", ["b2"]),
                               ("a", ["a1", "a2"]), ("b", ["b1", "b2"])):
            keys = [phase + "-" + name for name in names]
            variants[phase][variant] = stats(merge([data[k] for k in keys]), [summary["phases"][k] for k in keys])
    return variants, env


def change(b, a, key):
    return (b[key] / a[key] - 1) * 100


def paired(results, population, phase):
    rows = [v[phase] for label, v in results.items() if int(label.split("-")[-2]) == population]
    fps = [change(v["b"], v["a"], "fps") for v in rows]
    fps2 = [change(v["b"], v["a2"], "fps") for v in rows]
    frame = [-change(v["b"], v["a"], "frame mean ms") for v in rows]
    frame2 = [-change(v["b"], v["a2"], "frame mean ms") for v in rows]
    saved = [v["a"]["frame mean ms"] - v["b"]["frame mean ms"] for v in rows]
    return {"launches": len(rows), "fps_gain_percent_median": statistics.median(fps),
            "fps_gain_percent_min": min(fps), "fps_gain_percent_max": max(fps),
            "fps_gain_vs_A2_percent_median": statistics.median(fps2),
            "frame_reduction_percent_median": statistics.median(frame),
            "frame_reduction_percent_min": min(frame), "frame_reduction_percent_max": max(frame),
            "frame_reduction_vs_A2_percent_median": statistics.median(frame2),
            "frame_ms_saved_median": statistics.median(saved),
            "wins_vs_A": sum(x > 0 for x in fps), "wins_vs_A2": sum(x > 0 for x in fps2)}


def launch_tables(out, results, sections):
    out += ["| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |",
            "|---|---|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a1, a2, a, b = (v[k] for k in ("a1", "a2", "a", "b"))
            out.append(f"| {label} | {phase} | {a1['frame mean ms']:.3f} | {a2['frame mean ms']:.3f}"
                       f" | {b['frame mean ms']:.3f} | {change(b, a, 'frame mean ms'):+.1f}%"
                       f" | {change(b, a2, 'frame mean ms'):+.1f}% | {a['fps']:.1f} -> {b['fps']:.1f} |")
    out += ["", "CPU, allocation and tails (pooled A -> B):", "",
            "| Launch | Phase | Client CPU ms/frame | Allocation KB/frame | Non-tick median bytes | p99 ms | 1% low FPS |",
            "|---|---|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:{f}} -> {b[k]:{f}}" for k, f in
                      (("client CPU ms/frame", ".3f"), ("alloc KB/frame", ".1f"), ("non-tick allocation median bytes", ".0f"),
                       ("p99 ms", ".3f"), ("1% low fps", ".1f"))]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")
    out += ["", "Coarse sections (pooled A -> B, ms/frame). Nested sections overlap; do not add them.",
            "Process CPU cores do not identify driver threading behavior.", "",
            "| Launch | Phase | " + " | ".join(name for name, _ in sections) + " |",
            "|---|---|" + "---:|" * len(sections)]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            out.append(f"| {label} | {phase} | " + " | ".join(f"{a[k]:.3f} -> {b[k]:.3f}" for _, k in sections) + " |")


def paired_table(out, results, populations):
    out += ["| Mobs | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | B faster than A / A2 |",
            "|---|---|---:|---:|---:|---:|---:|"]
    selection = {}
    for population in populations:
        selection[str(population)] = {}
        for phase in ("static", "pan"):
            r = paired(results, population, phase)
            selection[str(population)][phase] = r
            out.append(f"| {population} | {phase} | {r['frame_reduction_percent_median']:.1f}%"
                       f" ({r['frame_reduction_percent_min']:.1f} to {r['frame_reduction_percent_max']:.1f}%)"
                       f" | {r['frame_reduction_vs_A2_percent_median']:.1f}%"
                       f" | {r['fps_gain_percent_median']:+.1f}% ({r['fps_gain_percent_min']:+.1f} to {r['fps_gain_percent_max']:+.1f}%)"
                       f" | {r['frame_ms_saved_median']:.3f} | {r['wins_vs_A']}/{r['launches']} / {r['wins_vs_A2']}/{r['launches']} |")
    return selection


def main(selected, pilot):
    if not pilot:
        manifest = json.loads((DEST / "implementation.json").read_text(encoding="utf-8"))
        for name, digest in manifest["source_sha256"].items():
            assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
    all_results, environments, selection = {}, [], {}
    title = "Superseded pilot (@WrapOperation hooks)" if pilot else "Entity shadow session"
    out = [f"# {title}: raw-frame-derived tables", "",
           "A pools a1/a2, B pools b1/b2; warmups are excluded. Every number derives from raw frames.",
           "Entity detail and verification are off; the five shipped defaults are on in A and B.", ""]
    for series in selected:
        labels = [label for label in SERIES[series]["labels"]
                  if sorted((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))]
        if not labels:
            continue
        results = {}
        for label in labels:
            results[label], env = load_launch(series, label)
            environments.append(env)
        all_results[series] = results
        populations = sorted({int(label.split("-")[-2]) for label in labels})
        if SERIES[series]["toggle"] == SHADOWS_OFF:
            out += [f"## {series.capitalize()}: vanilla shadows (A) versus the Entity Shadows option off (B)", "",
                    "A uses vanilla's shadow queries (`render.fastEntityShadows=false`) with the coarse shadow",
                    "timer on (two `nanoTime` calls per shadow). B changes the drawn image; it is not an option.", ""]
            launch_tables(out, results, [("Shadows (A)", "shadows mean ms"), ("Shadows drawn", "shadows drawn mean"),
                                         ("Entity loop", "entity_loop mean ms"), ("Render", "render mean ms"),
                                         ("Swap", "swap mean ms"), ("Process cores", "process CPU cores")])
        else:
            out += [f"## {series.capitalize()}: vanilla shadow queries (A) versus `render.fastEntityShadows` (B)", ""]
            sections = [("Entity loop", "entity_loop mean ms"), ("Render", "render mean ms"),
                        ("Swap", "swap mean ms"), ("Process cores", "process CPU cores")]
            if SERIES[series]["timing"]:
                sections = [("Shadows", "shadows mean ms"), ("Shadows drawn", "shadows drawn mean")] + sections
            launch_tables(out, results, sections)
        out += ["", f"Paired summary ({series}): median of the per-launch changes; the range shows every repeat.", ""]
        selection[series] = paired_table(out, results, populations)
        out.append("")
    for key in ("java.version", "java.vendor", "cpu", "gl.renderer", "gl.version", "jvm.maxHeapMB"):
        assert len({env[key] for env in environments}) == 1, key
    # Fabric Loader lists mods in hash order, which varies per launch; ids and versions must still match.
    assert len({frozenset(env["mods"].split()) for env in environments}) == 1, "mods"
    prefix = "pilot-" if pilot else ""
    (DEST / f"{prefix}metrics.json").write_text(json.dumps(all_results, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / f"{prefix}selection.json").write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / ("pilot.md" if pilot else "comparison.md")).write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(json.dumps(selection, indent=2))


if __name__ == "__main__":
    pilot = "--pilot" in sys.argv[1:]
    names = [a for a in sys.argv[1:] if a != "--pilot"]
    main(names or [k for k, v in SERIES.items() if bool(v.get("pilot")) == pilot], pilot)
