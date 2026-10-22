"""Rebuild the particle-removal session's tables from the ignored game CSVs. Run from the repository root.

Series (launch labels, all with the dedicated particle scenario, Java 25, entity/particle detail and
verification off unless stated):
  defaults      pr-dflt-{1,2}-r{1,2,3,4} (particle scale 1 and 2, scale order alternating by round), with
                the seven shipped defaults on (render.fastParticleLight passed explicitly):
                A vanilla ArrayList.removeAll of dead particles, B render.fastParticleRemoval
  light-off     pr-opt-{1,2}-r{1,2,3,4}: the same protocol, run first, but render.fastParticleLight was
                off in A and B (run/config/alacer.properties still held session 15's measured-build value
                false, and file values override defaults). A valid A/B on a heavier frame; secondary.
  detail        pr-dflt-detail-{off,on}-{1,2} (light on) and pr-detail-{off,on}-{1,2} (light off): static/pan
                with profiler.particleDetail=true and the option off/on (intrusive attribution only)
  verification  pr-verify-1 (scale 1), pr-verify-2 (scale 2 with benchmark.particleRemovalDiagnostic):
                render.fastParticleRemoval.verify=true (light off); only their log lines are used
Only complete, full-duration launches qualify; smoke runs (pr-smoke-*, pr-verify-r1) are not used.

Every launch here ran the frozen build in implementation.json (source_sha256).
"""
import csv
import hashlib
import json
import math
import pathlib
import re
import statistics

DEST = pathlib.Path(__file__).resolve().parent
ROOT = DEST.parents[2]
BENCH = ROOT / "run/alacer/benchmarks"
DEFAULTS = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks", "avoidMatrixReads", "cacheClouds",
            "fastEntityShadows")
LIGHT = "render.fastParticleLight"
OFF = ("skipZeroTranslates", "batchModelLists", "fastChunkFrustum")
SECTIONS = ("render", "world", "particles", "particle_draw", "tick", "particle_tick", "particle_spawn", "entities", "swap")
DETAIL = ("particle_update", "particle_move", "particle_remove", "particle_light")
ENTITY_DETAIL = ("entity_chunk_lookup", "entity_visibility_check", "entity_draw", "block_entity_draw", "entity_shadow")
OPTION = "render.fastParticleRemoval"
ROUNDS = [(r, scale) for r in (1, 2, 3, 4) for scale in ((1, 2) if r % 2 else (2, 1))]
SERIES = {"defaults": [f"pr-dflt-{scale}-r{r}" for r, scale in ROUNDS],
          "light-off": [f"pr-opt-{scale}-r{r}" for r, scale in ROUNDS]}
DETAIL_LAUNCHES = ["pr-dflt-detail-off-1", "pr-dflt-detail-on-1", "pr-dflt-detail-on-2", "pr-dflt-detail-off-2",
                   "pr-detail-off-1", "pr-detail-on-1", "pr-detail-on-2", "pr-detail-off-2"]
VERIFY = ["pr-verify-1", "pr-verify-2"]
FAILURES = ("GL ERROR", "OpenGLException", "Rendering Particle", "Ticking Particle", "Particle light mismatch",
            "Particle removal mismatch", "Matrix read mismatch", "reading matrices from OpenGL every frame again",
            "checking for GL errors every frame again", "Benchmark aborted", "Benchmark timed out")
UNUSED_REMOVAL = "Particle removal final: 0 removals compared (0 particles removed, 0 vanilla fallbacks), 0 mismatched"


def percentile(values, percent):
    return values[max(0, min(len(values) - 1, math.ceil(percent / 100 * len(values)) - 1))]


def merge(columns):
    return {key: [v for c in columns for v in c[key]] for key in columns[0]}


def scale_of(label):
    """pr-opt-2-r1 / pr-dflt-2-r1 -> 2; detail and verification labels end with the scale."""
    if label.rsplit("-", 1)[1].startswith("r"):
        return int(label.split("-")[2])
    return int(label.rsplit("-", 1)[1])


def light_on(label):
    return label.startswith("pr-dflt-")


def stats(columns, phases):
    frame = sorted(columns["frame_ns"])
    n = len(frame)
    assert n > 0
    total = sum(frame)
    slowest = frame[n - max(1, n // 100):]
    tick_rows = [i for i, t in enumerate(columns["tick_ns"]) if t > 0]
    ticked = sorted(columns["frame_ns"][i] for i in tick_rows)
    quiet = [f for f, t in zip(columns["frame_ns"], columns["tick_ns"]) if t == 0]
    seconds = total / 1e9
    result = {
        "frames": n, "frame mean ms": total / n / 1e6, "fps": n * 1e9 / total,
        "p50 ms": percentile(frame, 50) / 1e6, "p99 ms": percentile(frame, 99) / 1e6,
        "max ms": frame[-1] / 1e6, "1% low fps": 1e9 / statistics.mean(slowest),
        "tick frames": len(ticked),
        "tick-frame mean ms": statistics.mean(ticked) / 1e6,
        "tick-frame p50 ms": percentile(ticked, 50) / 1e6,
        "tick-frame p95 ms": percentile(ticked, 95) / 1e6,
        "tick-frame p99 ms": percentile(ticked, 99) / 1e6,
        "tick-frame max ms": ticked[-1] / 1e6,
        "non-tick-frame mean ms": statistics.mean(quiet) / 1e6 if quiet else 0.0,
        # Per frame that ticked (almost always one client tick per such frame at these frame rates).
        "tick per tick frame ms": statistics.mean(columns["tick_ns"][i] for i in tick_rows) / 1e6,
        "particle_tick per tick frame ms": statistics.mean(columns["particle_tick_ns"][i] for i in tick_rows) / 1e6,
        "particle_remove per tick frame ms": statistics.mean(columns["particle_remove_ns"][i] for i in tick_rows) / 1e6,
        "particle_update per tick frame ms": statistics.mean(columns["particle_update_ns"][i] for i in tick_rows) / 1e6,
        "particle_move per tick frame ms": statistics.mean(columns["particle_move_ns"][i] for i in tick_rows) / 1e6,
        "particles ticked per tick frame": statistics.mean(columns["particles_ticked"][i] for i in tick_rows),
        "particles removed per tick frame": statistics.mean(columns["particles_removed"][i] for i in tick_rows),
        "alloc KB/frame": statistics.mean(columns["alloc_bytes"]) / 1024,
        "alloc MB/s": sum(columns["alloc_bytes"]) / seconds / 2**20,
        "tick-frame alloc KB": statistics.mean(columns["alloc_bytes"][i] for i in tick_rows) / 1024,
        "particles rendered mean": statistics.mean(columns["particles_rendered"]),
        "particles ticked per second": sum(columns["particles_ticked"]) / seconds,
        "particles spawned per second": sum(columns["particles_spawned"]) / seconds,
        "client CPU ms/frame": sum(p["client_thread_cpu_ms_per_frame"] * p["frames"] for p in phases) / n,
        "process CPU cores": sum(p["process_cpu_cores"] * p["frames"] for p in phases) / n,
    }
    for section in SECTIONS + DETAIL:
        result[section + " mean ms"] = statistics.mean(columns[section + "_ns"]) / 1e6
    return result


def check_environment(label, env, toggle):
    for key, expected in {"window": "1920x1080", "options.renderDistance": "12", "options.useVbo": "false",
                          "options.vsync": "false", "options.fancyGraphics": "true", "options.clouds": "2",
                          "options.particles": "0", "options.entityShadows": "true",
                          "benchmark.generationRun": "false", "benchmark.logGlErrors": "true",
                          "benchmark.particles": "true", "benchmark.mobs": "0", "benchmark.chests": "0",
                          "benchmark.particleDiagnostic": "false", "benchmark.particleRemovalDiagnostic": "false"}.items():
        assert env[key] == expected, (label, key, env[key])
    assert env["benchmark.particleScale"] == str(scale_of(label)), label
    assert env["java.version"].startswith("25"), label
    assert "StartFlightRecording" not in env["jvm.args"], label
    assert env["benchmark.toggle"] == ("" if toggle is None else toggle + " (a=false, b=true)"), label
    assert int(env["benchmark.particlesSpawned"]) > 0 and int(env["benchmark.particleTicks"]) > 0, label


def validate_config(label, name, config, toggle):
    for key in DEFAULTS:
        assert config["render." + key] == "true", (label, name, key)
    for key in OFF:
        assert config["render." + key] == "false", (label, name, key)
    assert config[LIGHT] == str(light_on(label)).lower(), (label, name)
    assert all(v == "false" for k, v in config.items() if k.endswith(".verify")), (label, name)
    assert config["profiler.entityDetail"] == config["profiler.hud"] == config["profiler.shadowTiming"] == "false"
    assert config["profiler.chunkDetail"] == config["profiler.frustumTiming"] == "false"
    assert config["profiler.particleDetail"] == str("detail" in label).lower(), (label, name)
    assert config["profiler.enabled"] == "true" and config["profiler.logInterval"] == "0"
    assert config["options.particles"] == "0" and config["benchmark.particleScale"] == str(scale_of(label))
    assert config["benchmark.particleEmission"] == "true", (label, name)
    if toggle:
        expected = name.rsplit("-", 1)[1].startswith("b")
    else:
        expected = "-on-" in label
    assert config[OPTION] == str(expected).lower(), (label, name)


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
    assert "Particle light queries final: 0 queries compared, 0 mismatched" in log, label
    # No verification and no vanilla fallback (every particle class here inherits Entity.equals/hashCode).
    assert UNUSED_REMOVAL in log, label
    data = {}
    entities = set()
    for name, phase in summary["phases"].items():
        validate_config(label, name, phase["config"], toggle)
        assert phase["dropped_frames"] == 0 and phase["discarded_setup_frames"] == 1, (label, name)
        if toggle:
            target = 7.5 if "pan" in name else 5.0
        else:
            target = 15.0 if name == "pan" else 10.0
        assert phase["target_seconds"] == target, (label, name)
        assert phase["recorded_wall_seconds"] >= target - 0.001
        # The player and the eight animals saved in the base world; some launches see one more or
        # one fewer client entity (as in session 15). The scenario adds none. Constant within a launch.
        entities.add(phase["entities"])
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
        if "detail" not in label:
            for section in DETAIL:
                assert not any(columns[section + "_ns"]), (label, name, section)
            assert not any(columns["particles_removed"])
        else:
            assert any(columns["particle_remove_ns"]), (label, name)
        rendered = statistics.mean(columns["particles_rendered"])
        assert rendered > (2500 if scale_of(label) == 1 else 4500), (label, name, rendered)
        assert sum(columns["particles_spawned"]) > 0
        assert min(columns["alloc_bytes"]) >= 0
        assert abs(len(columns["frame_ns"]) * 1e9 / sum(columns["frame_ns"]) - phase["avg_fps"]) < 0.00006
        # Capture and CPU clocks begin at the same completed-frame boundary.
        elapsed = sum(columns["frame_ns"]) / 1e9
        assert abs(elapsed - phase["recorded_wall_seconds"]) < 0.02, (label, name)
        data[name] = columns
    assert len(entities) == 1 and entities <= {8, 9, 10}, (label, entities)
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
    return variants, env, entities.pop()


def change(b, a, key):
    return (b[key] / a[key] - 1) * 100


def paired(rows):
    def saved(key):
        return [v["a"][key] - v["b"][key] for v in rows]

    frame = [-change(v["b"], v["a"], "frame mean ms") for v in rows]
    frame2 = [-change(v["b"], v["a2"], "frame mean ms") for v in rows]
    tick_frame = [-change(v["b"], v["a"], "tick-frame mean ms") for v in rows]
    result = {"launches": len(rows),
              "frame_reduction_percent_median": statistics.median(frame),
              "frame_reduction_percent_min": min(frame), "frame_reduction_percent_max": max(frame),
              "frame_reduction_vs_A2_percent_median": statistics.median(frame2),
              "tick_frame_reduction_percent_median": statistics.median(tick_frame),
              "tick_frame_reduction_percent_min": min(tick_frame), "tick_frame_reduction_percent_max": max(tick_frame),
              "wins_vs_A": sum(x > 0 for x in frame), "wins_vs_A2": sum(x > 0 for x in frame2)}
    for name, key in (("frame_ms", "frame mean ms"), ("tick_frame_ms", "tick-frame mean ms"),
                      ("non_tick_frame_ms", "non-tick-frame mean ms"), ("tick_frame_p95_ms", "tick-frame p95 ms"),
                      ("tick_frame_p99_ms", "tick-frame p99 ms"), ("tick_frame_max_ms", "tick-frame max ms"),
                      ("p99_ms", "p99 ms"), ("max_ms", "max ms"),
                      ("particle_tick_per_tick_frame_ms", "particle_tick per tick frame ms"),
                      ("client_cpu_ms", "client CPU ms/frame"), ("alloc_KB", "alloc KB/frame"),
                      ("particles_pass_ms", "particles mean ms")):
        values = saved(key)
        result[name + "_saved_median"] = statistics.median(values)
        result[name + "_saved_min"] = min(values)
        result[name + "_saved_max"] = max(values)
        result[name + "_lower"] = sum(x > 0 for x in values)
    low = [change(v["b"], v["a"], "1% low fps") for v in rows]
    result["one_percent_low_gain_percent_median"] = statistics.median(low)
    result["one_percent_low_higher"] = sum(x > 0 for x in low)
    return result


def launch_tables(out, results):
    out += ["| Launch | Phase | A1 ms | A2 ms | B ms | B vs pooled A | B vs A2 | FPS A -> B |",
            "|---|---|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a1, a2, a, b = (v[k] for k in ("a1", "a2", "a", "b"))
            out.append(f"| {label} | {phase} | {a1['frame mean ms']:.3f} | {a2['frame mean ms']:.3f}"
                       f" | {b['frame mean ms']:.3f} | {change(b, a, 'frame mean ms'):+.1f}%"
                       f" | {change(b, a2, 'frame mean ms'):+.1f}% | {a['fps']:.1f} -> {b['fps']:.1f} |")
    out += ["", "Frames that ran a client tick (pooled A -> B). Static pools about 200 tick frames per variant, pan about 300,",
            "so tick-frame p99 is roughly the second or third slowest and the maximum is a single frame.", "",
            "| Launch | Phase | Tick frames | Mean ms | p50 ms | p95 ms | p99 ms | Max ms | Particle tick ms per tick frame | Non-tick-frame mean ms |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:{f}} -> {b[k]:{f}}" for k, f in
                      (("tick frames", ".0f"), ("tick-frame mean ms", ".3f"), ("tick-frame p50 ms", ".3f"),
                       ("tick-frame p95 ms", ".3f"), ("tick-frame p99 ms", ".3f"), ("tick-frame max ms", ".2f"),
                       ("particle_tick per tick frame ms", ".3f"), ("non-tick-frame mean ms", ".3f"))]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")
    out += ["", "All frames: CPU, allocation and tails (pooled A -> B).", "",
            "| Launch | Phase | Client CPU ms/frame | Alloc KB/frame | Tick-frame alloc KB | p99 ms | 1% low FPS | Max ms | Particle pass ms | Particles rendered | Ticked per tick frame |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a"], v["b"]
            fields = [f"{a[k]:{f}} -> {b[k]:{f}}" for k, f in
                      (("client CPU ms/frame", ".3f"), ("alloc KB/frame", ".1f"), ("tick-frame alloc KB", ".1f"),
                       ("p99 ms", ".3f"), ("1% low fps", ".1f"), ("max ms", ".2f"), ("particles mean ms", ".3f"),
                       ("particles rendered mean", ".0f"), ("particles ticked per tick frame", ".0f"))]
            out.append(f"| {label} | {phase} | " + " | ".join(fields) + " |")


def paired_table(out, results):
    out += ["| Scale | Phase | Frame reduction vs pooled A, median (range) | vs A2 | Tick-frame mean reduction, median (range) | Tick-frame ms saved, median (range) | Particle tick ms saved per tick frame, median (range) | B faster than A / A2 |",
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
                       f" | {r['tick_frame_reduction_percent_median']:.1f}% ({r['tick_frame_reduction_percent_min']:.1f} to {r['tick_frame_reduction_percent_max']:.1f}%)"
                       f" | {r['tick_frame_ms_saved_median']:.3f} ({r['tick_frame_ms_saved_min']:.3f} to {r['tick_frame_ms_saved_max']:.3f})"
                       f" | {r['particle_tick_per_tick_frame_ms_saved_median']:.3f} ({r['particle_tick_per_tick_frame_ms_saved_min']:.3f} to {r['particle_tick_per_tick_frame_ms_saved_max']:.3f})"
                       f" | {r['wins_vs_A']}/{r['launches']} / {r['wins_vs_A2']}/{r['launches']} |")
    out += ["", "Counts of launches where B is lower (median saving A - B in parentheses; positive is better).", "",
            "| Scale | Phase | Tick-frame mean | Tick-frame p95 | Tick-frame p99 | Tick-frame max | Non-tick-frame mean | All-frame p99 | Client CPU | Alloc KB/frame | 1% low higher |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for scale, phases in selection.items():
        for phase, r in phases.items():
            n = r["launches"]

            def cell(name, digits=3):
                return f"{r[name + '_lower']}/{n} ({r[name + '_saved_median']:.{digits}f})"
            out.append(f"| {scale} | {phase} | {cell('tick_frame_ms')} | {cell('tick_frame_p95_ms')} | {cell('tick_frame_p99_ms')}"
                       f" | {cell('tick_frame_max_ms', 2)} | {cell('non_tick_frame_ms')} | {cell('p99_ms')} | {cell('client_cpu_ms')}"
                       f" | {cell('alloc_KB', 1)} | {r['one_percent_low_higher']}/{n} ({r['one_percent_low_gain_percent_median']:+.1f}%) |")
    return selection


def detail_table(out, results):
    keys = [("Frame ms", "frame mean ms"), ("Tick-frame mean ms", "tick-frame mean ms"),
            ("Tick-frame p99 ms", "tick-frame p99 ms"), ("Particle tick / tick frame", "particle_tick per tick frame ms"),
            ("Remove / tick frame", "particle_remove per tick frame ms"), ("Update / tick frame", "particle_update per tick frame ms"),
            ("Move / tick frame", "particle_move per tick frame ms"), ("Ticked / tick frame", "particles ticked per tick frame"),
            ("Removed / tick frame", "particles removed per tick frame"), ("Particles rendered", "particles rendered mean")]
    out += ["| Launch | Light opt. | Removal opt. | Phase | " + " | ".join(name for name, _ in keys) + " |",
            "|---|---|---|---|" + "---:|" * len(keys)]
    for label, phases in results.items():
        for phase, v in phases.items():
            r = v["run"]
            out.append(f"| {label} | {'on' if light_on(label) else 'off'} | {'on' if '-on-' in label else 'off'} | {phase} | " + " | ".join(
                f"{r[k]:.0f}" if k.startswith("particles") else f"{r[k]:.3f}" for _, k in keys) + " |")


def verification(out):
    pattern = re.compile(r"Particle removal final: (\d+) removals compared \((\d+) particles removed, (\d+) vanilla fallbacks\), (\d+) mismatched")
    probes = re.compile(r"particle removal diagnostic added (\d+) probe particles; (.*)$", re.M)
    rows = {}
    out += ["| Launch | Scale | Probes | Removals compared | Particles removed | Vanilla fallbacks | Mismatches | Probe cases |",
            "|---|---:|---|---:|---:|---:|---:|---|"]
    for label in VERIFY:
        log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8", errors="replace")
        assert "BUILD SUCCESSFUL" in log, label
        for failure in FAILURES:
            assert failure not in log, (label, failure)
        compared, removed, fallbacks, mismatched = map(int, pattern.search(log).groups())
        assert mismatched == 0 and compared > 0 and removed > 0, label
        found = probes.search(log)
        diagnostic = label == "pr-verify-2"
        assert bool(found) == diagnostic, label
        assert diagnostic == (fallbacks > 0), (label, fallbacks)
        rows[label] = {"removals_compared": compared, "particles_removed": removed, "vanilla_fallbacks": fallbacks,
                       "mismatches": mismatched, "probes": found.group(2).strip() if found else ""}
        out.append(f"| {label} | {scale_of(label)} | {'yes, ' + found.group(1) if found else 'no'} | {compared} | {removed}"
                   f" | {fallbacks} | {mismatched} | {rows[label]['probes']} |")
    return rows


def main():
    manifest = json.loads((DEST / "implementation.json").read_text(encoding="utf-8"))
    final = manifest.get("final_source_sha256", manifest["source_sha256"])
    for name, digest in final.items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
    environments, metrics, entities = [], {}, {}
    out = ["# Particle removal session: raw-frame-derived tables", "",
           "Dedicated particle scenario (`benchmark.particles`), Java 25, 1920x1080, render distance 12, no mobs.",
           "A pools a1/a2, B pools b1/b2; warmups are excluded. Every number derives from raw frames.",
           "Entity/particle detail and verification are off. A: vanilla `removeAll`; B: `render.fastParticleRemoval`.", ""]
    selection = {}
    for series, labels in SERIES.items():
        results = {}
        for label in labels:
            results[label], env, entities[label] = load_launch(label, OPTION)
            environments.append(env)
        metrics[series] = results
        if series == "defaults":
            out += ["## Primary series: shipped defaults (`render.fastParticleLight` on) in A and B", ""]
        else:
            out += ["## Secondary series: `render.fastParticleLight` off in A and B (persisted config, run first)", ""]
        launch_tables(out, results)
        out += ["", f"Paired summary ({series}): median of the per-launch changes; the range shows every repeat.", ""]
        selection[series] = paired_table(out, results)
        out.append("")
    details = {}
    for label in DETAIL_LAUNCHES:
        details[label], env, entities[label] = load_launch(label, None)
        environments.append(env)
    metrics["detail"] = details
    out += ["## Detail launches (static 10 s, pan 15 s; no toggle; `profiler.particleDetail=true`)", "",
            "Two `nanoTime` calls per particle update, move and light query, and around each removal; they inflate",
            "what they time and are for attribution only. Values per frame that ran a client tick.", ""]
    detail_table(out, details)
    out += ["", "## Verification (`render.fastParticleRemoval.verify=true`)", "",
            "The fast path runs on a copy of each list, vanilla's `removeAll` on the real list, and the survivors are",
            "compared element by element (identity, order, count and the return value). These ran with",
            "`render.fastParticleLight` off (the persisted config), which does not affect removal.", ""]
    metrics["verification"] = verification(out)
    metrics["client_entities"] = entities
    out.append("")
    for key in ("java.version", "java.vendor", "cpu", "gl.renderer", "gl.version", "jvm.maxHeapMB"):
        assert len({env[key] for env in environments}) == 1, key
    for key in ("benchmark.anchor", "benchmark.particleEmitters"):
        assert len({env[key] for env in environments}) == 1, key
    # Fabric Loader lists mods in hash order, which varies per launch; ids and versions must still match.
    assert len({frozenset(env["mods"].split()) for env in environments}) == 1, "mods"
    (DEST / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "selection.json").write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "comparison.md").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(selection, indent=2))


if __name__ == "__main__":
    main()
