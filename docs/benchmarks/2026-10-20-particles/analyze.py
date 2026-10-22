"""Rebuild the particle session's tables from the ignored game CSVs. Run from the repository root.

Series (launch labels, all with the dedicated particle scenario, Java 25, entity/particle detail off
unless stated):
  attribution   pt-attr-r{1,2} (scale 1), pt-attr2-r1 (scale 2):
                A emits the particle schedule, B stops it and clears the particles (benchmark.disableParticles)
  optimization  pt-opt-{1,2}-r{1,2,3} (particle scale 1 and 2):
                A vanilla particle light queries, B render.fastParticleLight
  detail        pt-detail-r1: static/pan with profiler.particleDetail=true (intrusive attribution only)
  jfr           pt-jfr-r1: static/pan with Java Flight Recorder (attribution only)
Only complete, full-duration launches qualify; smoke and verification runs are separate (verification.md).

The attribution, detail and JFR launches ran the earlier attribution build (implementation.json,
attribution_source_sha256); the optimization launches ran the frozen source_sha256 build.
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
BENCH = ROOT / "run/alacer/benchmarks"
DEFAULTS = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks", "avoidMatrixReads", "cacheClouds",
            "fastEntityShadows")
OFF = ("skipZeroTranslates", "batchModelLists", "fastChunkFrustum")
SECTIONS = ("render", "world", "particles", "particle_draw", "tick", "particle_tick", "particle_spawn", "entities", "swap")
DETAIL = ("particle_update", "particle_move", "particle_remove", "particle_light")
ENTITY_DETAIL = ("entity_chunk_lookup", "entity_visibility_check", "entity_draw", "block_entity_draw", "entity_shadow")
DISABLE = "benchmark.disableParticles"
OPTION = "render.fastParticleLight"
SERIES = {
    "attribution": {"labels": ["pt-attr-r1", "pt-attr-r2", "pt-attr2-r1"], "toggle": DISABLE},
    "optimization": {"labels": [f"pt-opt-{scale}-r{r}" for r in (1, 2, 3) for scale in (1, 2)], "toggle": OPTION},
}
SINGLE = {"detail": ["pt-detail-r1"], "jfr": ["pt-jfr-r1"]}
FAILURES = ("GL ERROR", "OpenGLException", "Rendering Particle", "Ticking Particle", "Particle light mismatch",
            "Matrix read mismatch", "reading matrices from OpenGL every frame again",
            "checking for GL errors every frame again", "Benchmark aborted", "Benchmark timed out")


def percentile(values, percent):
    return values[max(0, min(len(values) - 1, math.ceil(percent / 100 * len(values)) - 1))]


def merge(columns):
    return {key: [v for c in columns for v in c[key]] for key in columns[0]}


def scale_of(label):
    return 2 if label.startswith(("pt-attr2-", "pt-opt-2-")) else 1


def stats(columns, phases):
    frame = sorted(columns["frame_ns"])
    n = len(frame)
    assert n > 0
    total = sum(frame)
    slowest = frame[n - max(1, n // 100):]
    ticked = [f for f, t in zip(columns["frame_ns"], columns["tick_ns"]) if t > 0]
    quiet = [f for f, t in zip(columns["frame_ns"], columns["tick_ns"]) if t == 0]
    non_tick = sorted(a for a, t in zip(columns["alloc_bytes"], columns["tick_ns"]) if t == 0)
    seconds = total / 1e9
    result = {
        "frames": n, "frame mean ms": total / n / 1e6, "fps": n * 1e9 / total,
        "p50 ms": percentile(frame, 50) / 1e6, "p99 ms": percentile(frame, 99) / 1e6,
        "max ms": frame[-1] / 1e6, "1% low fps": 1e9 / statistics.mean(slowest),
        "tick-frame mean ms": statistics.mean(ticked) / 1e6 if ticked else 0.0,
        "non-tick-frame mean ms": statistics.mean(quiet) / 1e6 if quiet else 0.0,
        "alloc KB/frame": statistics.mean(columns["alloc_bytes"]) / 1024,
        "alloc MB/s": sum(columns["alloc_bytes"]) / seconds / 2**20,
        "non-tick allocation median bytes": percentile(non_tick, 50) if non_tick else 0,
        "particles rendered mean": statistics.mean(columns["particles_rendered"]),
        "particles ticked per second": sum(columns["particles_ticked"]) / seconds,
        "particles spawned per second": sum(columns["particles_spawned"]) / seconds,
        "client CPU ms/frame": sum(p["client_thread_cpu_ms_per_frame"] * p["frames"] for p in phases) / n,
        "process CPU cores": sum(p["process_cpu_cores"] * p["frames"] for p in phases) / n,
    }
    for section in SECTIONS + DETAIL:
        result[section + " mean ms"] = statistics.mean(columns[section + "_ns"]) / 1e6
    result["particles_removed per second"] = sum(columns["particles_removed"]) / seconds
    return result


def check_environment(label, env, toggle):
    for key, expected in {"window": "1920x1080", "options.renderDistance": "12", "options.useVbo": "false",
                          "options.vsync": "false", "options.fancyGraphics": "true", "options.clouds": "2",
                          "options.particles": "0", "options.entityShadows": "true",
                          "benchmark.generationRun": "false", "benchmark.logGlErrors": "true",
                          "benchmark.particles": "true", "benchmark.mobs": "0", "benchmark.chests": "0"}.items():
        assert env[key] == expected, (label, key, env[key])
    # The attribution build predates the verification-only diagnostic flag.
    assert env.get("benchmark.particleDiagnostic", "false") == "false", label
    assert env["benchmark.particleScale"] == str(scale_of(label)), label
    assert env["java.version"].startswith("25"), label
    assert ("StartFlightRecording" in env["jvm.args"]) == (label == "pt-jfr-r1"), label
    assert env["benchmark.toggle"] == ("" if toggle is None else toggle + " (a=false, b=true)"), label
    assert int(env["benchmark.particlesSpawned"]) > 0 and int(env["benchmark.particleTicks"]) > 0, label


def validate_config(label, name, config, toggle):
    b = name.rsplit("-", 1)[1].startswith("b") if toggle else False
    for key in DEFAULTS:
        assert config["render." + key] == "true", (label, name, key)
    for key in OFF:
        assert config["render." + key] == "false", (label, name, key)
    assert all(v == "false" for k, v in config.items() if k.endswith(".verify")), (label, name)
    assert config["profiler.entityDetail"] == config["profiler.hud"] == config["profiler.shadowTiming"] == "false"
    assert config["profiler.chunkDetail"] == config["profiler.frustumTiming"] == "false"
    assert config["profiler.particleDetail"] == str(label == "pt-detail-r1").lower(), (label, name)
    assert config["profiler.enabled"] == "true" and config["profiler.logInterval"] == "0"
    assert config["options.particles"] == "0" and config["benchmark.particleScale"] == str(scale_of(label))
    if toggle == OPTION:
        assert config[OPTION] == str(b).lower() and config["benchmark.particleEmission"] == "true", (label, name)
    else:
        assert config.get(OPTION, "false") == "false", (label, name)
        assert config["benchmark.particleEmission"] == str(not b).lower(), (label, name)


def load_launch(label, toggle):
    suffix = "-ab" if toggle else ""
    folders = sorted(BENCH.glob(f"*-{label}-vis_on-ecl_on-ent0{suffix}"))
    assert len(folders) == 1, (label, folders)
    folder = folders[0]
    source = (folder / "summary.json").read_text(encoding="utf-8")
    summary = json.loads(source)
    env = summary["environment"]
    check_environment(label, env, toggle)
    if toggle:
        expected_names = {f"{phase}-{segment}" for phase in ("static", "pan") for segment in ("a1", "b1", "b2", "a2")}
        expected_names |= {f"warmup-{phase}-{variant}" for phase in ("static", "pan") for variant in ("a", "b")}
    else:
        expected_names = {"static", "pan"}
    assert set(summary["phases"]) == expected_names, label
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8", errors="replace")
    assert "BUILD SUCCESSFUL" in log, label
    for failure in FAILURES:
        assert failure not in log, (label, failure)
    assert "Model list batch final: 0 lists compiled" in log, label
    assert "Entity shadow queries final: 0 queries compared, 0 mismatched" in log, label
    if label.startswith("pt-opt-"):
        assert "Particle light queries final: 0 queries compared, 0 mismatched" in log, label
    data = {}
    for name, phase in summary["phases"].items():
        validate_config(label, name, phase["config"], toggle)
        assert phase["dropped_frames"] == 0 and phase["discarded_setup_frames"] == 1, (label, name)
        if toggle:
            target = 7.5 if "pan" in name else 5.0
        else:
            target = 15.0 if name == "pan" else 10.0
        assert phase["target_seconds"] == target, (label, name)
        assert phase["recorded_wall_seconds"] >= target - 0.001
        # The player and the eight animals saved in the base world; the scenario adds none.
        # pt-opt-1-r1 saw one more client entity in every phase (constant within the launch).
        assert phase["entities"] == (10 if label == "pt-opt-1-r1" else 9), (label, name, phase["entities"])
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
        for section in ENTITY_DETAIL:
            assert not any(columns[section + "_ns"]), (label, name, section)
        assert not any(columns["gl_calls"])
        if label != "pt-detail-r1":
            for section in DETAIL:
                assert not any(columns[section + "_ns"]), (label, name, section)
        emitting = phase["config"]["benchmark.particleEmission"] == "true"
        rendered = statistics.mean(columns["particles_rendered"])
        if emitting:
            assert rendered > (2500 if scale_of(label) == 1 else 4500), (label, name, rendered)
            assert sum(columns["particles_spawned"]) > 0
        else:
            assert rendered < 100 and not any(columns["particles_spawned"]), (label, name, rendered)
        assert min(columns["alloc_bytes"]) >= 0
        assert abs(len(columns["frame_ns"]) * 1e9 / sum(columns["frame_ns"]) - phase["avg_fps"]) < 0.00006
        # Capture and CPU clocks begin at the same completed-frame boundary.
        elapsed = sum(columns["frame_ns"]) / 1e9
        assert abs(elapsed - phase["recorded_wall_seconds"]) < 0.02, (label, name)
        data[name] = columns
    runs = DEST / "runs"
    runs.mkdir(parents=True, exist_ok=True)
    (runs / f"{label}.summary.json").write_text(source, encoding="utf-8", newline="\n")
    variants = {}
    for phase in ("static", "pan"):
        variants[phase] = {}
        if not toggle:
            variants[phase]["run"] = stats(data[phase], [summary["phases"][phase]])
            continue
        for variant, names in (("a1", ["a1"]), ("a2", ["a2"]), ("b1", ["b1"]), ("b2", ["b2"]),
                               ("a", ["a1", "a2"]), ("b", ["b1", "b2"])):
            keys = [phase + "-" + name for name in names]
            variants[phase][variant] = stats(merge([data[k] for k in keys]), [summary["phases"][k] for k in keys])
    return variants, env


def change(b, a, key):
    return (b[key] / a[key] - 1) * 100


def paired(rows):
    fps = [change(v["b"], v["a"], "fps") for v in rows]
    fps2 = [change(v["b"], v["a2"], "fps") for v in rows]
    frame = [-change(v["b"], v["a"], "frame mean ms") for v in rows]
    frame2 = [-change(v["b"], v["a2"], "frame mean ms") for v in rows]
    saved = [v["a"]["frame mean ms"] - v["b"]["frame mean ms"] for v in rows]
    particles = [v["a"]["particles mean ms"] - v["b"]["particles mean ms"] for v in rows]
    cpu = [v["a"]["client CPU ms/frame"] - v["b"]["client CPU ms/frame"] for v in rows]
    alloc = [v["a"]["alloc KB/frame"] - v["b"]["alloc KB/frame"] for v in rows]
    p99 = [v["a"]["p99 ms"] - v["b"]["p99 ms"] for v in rows]
    low = [change(v["b"], v["a"], "1% low fps") for v in rows]
    return {"launches": len(rows), "fps_gain_percent_median": statistics.median(fps),
            "fps_gain_percent_min": min(fps), "fps_gain_percent_max": max(fps),
            "fps_gain_vs_A2_percent_median": statistics.median(fps2),
            "frame_reduction_percent_median": statistics.median(frame),
            "frame_reduction_percent_min": min(frame), "frame_reduction_percent_max": max(frame),
            "frame_reduction_vs_A2_percent_median": statistics.median(frame2),
            "frame_ms_saved_median": statistics.median(saved),
            "particles_ms_saved_median": statistics.median(particles),
            "particles_ms_saved_min": min(particles), "particles_ms_saved_max": max(particles),
            "client_cpu_ms_saved_median": statistics.median(cpu),
            "alloc_KB_saved_median": statistics.median(alloc),
            "p99_ms_saved_median": statistics.median(p99), "p99_lower": sum(x > 0 for x in p99),
            "one_percent_low_gain_percent_median": statistics.median(low), "one_percent_low_higher": sum(x > 0 for x in low),
            "particles_lower": sum(x > 0 for x in particles), "cpu_lower": sum(x > 0 for x in cpu),
            "alloc_lower": sum(x > 0 for x in alloc),
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
    out += ["", "CPU, allocation and tails (pooled A -> B). Tick frames are frames that ran a client tick.", "",
            "| Launch | Phase | Client CPU ms/frame | Alloc KB/frame | Alloc MB/s | p99 ms | 1% low FPS | Max ms | Tick-frame mean ms |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:{f}} -> {b[k]:{f}}" for k, f in
                      (("client CPU ms/frame", ".3f"), ("alloc KB/frame", ".1f"), ("alloc MB/s", ".0f"),
                       ("p99 ms", ".3f"), ("1% low fps", ".1f"), ("max ms", ".2f"), ("tick-frame mean ms", ".3f"))]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")
    out += ["", "Coarse sections and particle counts (pooled A -> B, ms/frame). Nested sections overlap; do not add them.",
            "Process CPU cores do not identify driver threading behavior.", "",
            "| Launch | Phase | " + " | ".join(name for name, _ in sections) + " |",
            "|---|---|" + "---:|" * len(sections)]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            out.append(f"| {label} | {phase} | " + " | ".join(
                f"{a[k]:.{0 if 'mean ms' not in k and 'cores' not in k else 3}f} -> {b[k]:.{0 if 'mean ms' not in k and 'cores' not in k else 3}f}"
                for _, k in sections) + " |")


def paired_table(out, results):
    out += ["| Scale | Phase | Frame reduction vs pooled A, median (range) | vs A2, median | FPS gain, median (range) | ms saved, median | Particle pass ms saved, median (range) | B faster than A / A2 |",
            "|---|---|---:|---:|---:|---:|---:|---:|"]
    selection = {}
    for scale in sorted({scale_of(label) for label in results}):
        selection[str(scale)] = {}
        for phase in ("static", "pan"):
            rows = [v[phase] for label, v in results.items() if scale_of(label) == scale]
            r = paired(rows)
            selection[str(scale)][phase] = r
            out.append(f"| {scale} | {phase} | {r['frame_reduction_percent_median']:.1f}%"
                       f" ({r['frame_reduction_percent_min']:.1f} to {r['frame_reduction_percent_max']:.1f}%)"
                       f" | {r['frame_reduction_vs_A2_percent_median']:.1f}%"
                       f" | {r['fps_gain_percent_median']:+.1f}% ({r['fps_gain_percent_min']:+.1f} to {r['fps_gain_percent_max']:+.1f}%)"
                       f" | {r['frame_ms_saved_median']:.3f}"
                       f" | {r['particles_ms_saved_median']:.3f} ({r['particles_ms_saved_min']:.3f} to {r['particles_ms_saved_max']:.3f})"
                       f" | {r['wins_vs_A']}/{r['launches']} / {r['wins_vs_A2']}/{r['launches']} |")
    out += ["", "| Scale | Phase | Particle pass lower | Client CPU lower (median ms) | Allocation lower (median KB/frame) | p99 lower (median ms) | 1% low higher (median) |",
            "|---|---|---:|---:|---:|---:|---:|"]
    for scale, phases in selection.items():
        for phase, r in phases.items():
            n = r["launches"]
            out.append(f"| {scale} | {phase} | {r['particles_lower']}/{n} | {r['cpu_lower']}/{n} ({r['client_cpu_ms_saved_median']:.3f})"
                       f" | {r['alloc_lower']}/{n} ({r['alloc_KB_saved_median']:.1f}) | {r['p99_lower']}/{n} ({r['p99_ms_saved_median']:.3f})"
                       f" | {r['one_percent_low_higher']}/{n} ({r['one_percent_low_gain_percent_median']:+.1f}%) |")
    return selection


def single_tables(out, results):
    keys = [("Frame ms", "frame mean ms"), ("p99 ms", "p99 ms"), ("Client CPU ms/frame", "client CPU ms/frame"),
            ("Alloc KB/frame", "alloc KB/frame"), ("Render", "render mean ms"), ("Particles (pass)", "particles mean ms"),
            ("Draw (Tesselator.end)", "particle_draw mean ms"), ("Light (detail)", "particle_light mean ms"),
            ("Tick", "tick mean ms"), ("Particle tick", "particle_tick mean ms"), ("Update (detail)", "particle_update mean ms"),
            ("Move (detail)", "particle_move mean ms"), ("Remove (detail)", "particle_remove mean ms"),
            ("Spawn", "particle_spawn mean ms"), ("Swap", "swap mean ms"), ("Particles rendered", "particles rendered mean"),
            ("Ticked/s", "particles ticked per second"), ("Removed/s (detail)", "particles_removed per second")]
    out += ["| Launch | Phase | " + " | ".join(name for name, _ in keys) + " |", "|---|---|" + "---:|" * len(keys)]
    for label, phases in results.items():
        for phase, v in phases.items():
            r = v["run"]
            out.append(f"| {label} | {phase} | " + " | ".join(
                f"{r[k]:.0f}" if k.startswith("particles") and "mean ms" not in k or "per second" in k else f"{r[k]:.3f}"
                for _, k in keys) + " |")


def main():
    manifest = json.loads((DEST / "implementation.json").read_text(encoding="utf-8"))
    final = manifest.get("final_source_sha256", manifest["source_sha256"])
    for name, digest in final.items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
    environments, metrics, selection = [], {}, {}
    out = ["# Particle session: raw-frame-derived tables", "",
           "Dedicated particle scenario (`benchmark.particles`), Java 25, 1920x1080, render distance 12, no mobs.",
           "A pools a1/a2, B pools b1/b2; warmups are excluded. Every number derives from raw frames.",
           "Entity detail and verification are off; the six shipped defaults are on in A and B.", ""]
    for series, spec in SERIES.items():
        results = {}
        for label in spec["labels"]:
            results[label], env = load_launch(label, spec["toggle"])
            environments.append(env)
        metrics[series] = results
        if spec["toggle"] == DISABLE:
            out += ["## Attribution: particles emitting (A) versus none (B)", "",
                    "B stops the schedule and clears every particle; it changes the image and is not an option.",
                    "These launches ran the attribution build, without the light redirect.", ""]
        else:
            out += ["## Optimization: vanilla particle light (A) versus `render.fastParticleLight` (B)", ""]
        launch_tables(out, results, [("Particles", "particles mean ms"), ("Draw", "particle_draw mean ms"),
                                     ("Particle tick", "particle_tick mean ms"), ("Render", "render mean ms"),
                                     ("Swap", "swap mean ms"), ("Rendered", "particles rendered mean"),
                                     ("Process cores", "process CPU cores")])
        out += ["", f"Paired summary ({series}): median of the per-launch changes; the range shows every repeat.", ""]
        selection[series] = paired_table(out, results)
        out.append("")
    singles = {}
    for series, labels in SINGLE.items():
        for label in labels:
            singles[label], env = load_launch(label, None)
            environments.append(env)
    metrics["single"] = singles
    out += ["## Detail and JFR launches (static 10 s, pan 15 s; no toggle)", "",
            "`pt-detail-r1` adds per-particle timers (two `nanoTime` calls per particle and operation); they",
            "inflate what they time and are for attribution only. Detail columns are zero in `pt-jfr-r1`.",
            "Section values are means over all frames; the particle tick runs only in frames with a client tick.", ""]
    single_tables(out, singles)
    out.append("")
    for key in ("java.version", "java.vendor", "cpu", "gl.renderer", "gl.version", "jvm.maxHeapMB"):
        assert len({env[key] for env in environments}) == 1, key
    for key in ("benchmark.anchor", "benchmark.particleEmitters"):
        assert len({env[key] for env in environments}) == 1, key
    # Fabric Loader lists mods in hash order, which varies per launch; ids and versions must still match.
    assert len({frozenset(env["mods"].split()) for env in environments}) == 1, "mods"
    (DEST / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "selection.json").write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "comparison.md").write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(json.dumps(selection, indent=2))


if __name__ == "__main__":
    main()
