"""Rebuild session 13 evidence from ignored raw CSVs. Run from repository root."""
import csv
import hashlib
import json
import math
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = Path("run/alacer/benchmarks")
BASELINE = ["cp-baseline-r1", "cp-baseline-r2", "cp-baseline-r3"]
DETAIL = ["cp-detail-r1", "cp-detail-jfr-r2", "cp-detail-age-r3", "cp-detail-age-r4", "cp-detail-jfr-r5"]
TOP_LEVEL = ["terrain_setup", "chunk_upload", "entities", "camera_setup", "frustum", "frustum_culler", "lightmap", "pick", "sky", "clouds", "terrain_solid", "terrain_cutout_mipped", "terrain_cutout", "terrain_translucent", "particles", "weather", "hand", "gui"]
EXPERIMENTS = ["fastChunkFrustum", "skipZeroTranslates", "batchModelLists"]
DEFAULTS = ["fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks", "avoidMatrixReads", "cacheClouds", "fastEntityShadows"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def quantile(values, q):
    values = sorted(values)
    return values[min(len(values)-1, math.ceil(q*len(values))-1)]


def collect(label):
    dirs = list(RAW.glob("*-"+label+"-*"))
    assert len(dirs) == 1, (label, dirs)
    path = dirs[0]
    summary = json.loads((path/"summary.json").read_text())
    env = summary["environment"]
    assert env["benchmark.generationRun"] == "false"
    assert env["benchmark.anchor"] == "-200.5 65.0 267.5"
    assert env["benchmark.mobs"] == "0" and env["benchmark.chests"] == "0"
    expected = {"java.version":"1.8.0_492", "window":"1920x1080", "options.renderDistance":"12", "options.fancyGraphics":"true", "options.clouds":"2", "options.vsync":"false", "options.fpsLimit":"260", "options.useVbo":"false", "vboActive":"false"}
    for k, v in expected.items():
        assert env[k] == v, (label, k, env[k])
    log = Path("build", label+".log").read_text(errors="replace")
    assert "BUILD SUCCESSFUL" in log and "Benchmark: results written" in log
    for bad in ["Benchmark aborted", "Mixin apply failed", "InjectionError", "OpenGL Error", "Chunk render task was"]:
        assert bad not in log, (label, bad)
    result = {"raw_directory":str(path), "environment":env, "log_sha256":sha(Path("build", label+".log")), "phases":{}}
    assert set(summary["phases"]) == {"static", "pan", "fly"}
    for phase, seconds in [("static",10), ("pan",15), ("fly",20)]:
        stats = summary["phases"][phase]
        assert stats["dropped_frames"] == 0 and stats["discarded_setup_frames"] == 1
        assert stats["target_seconds"] == seconds and seconds-.05 <= stats["recorded_wall_seconds"] < seconds+1
        config = stats["config"]
        for key in DEFAULTS:
            assert config["render."+key] == "true"
        for key in EXPERIMENTS:
            assert config["render."+key] == "false"
        assert all(v=="false" for k,v in config.items() if k.endswith(".verify"))
        assert config["profiler.entityDetail"] == config["profiler.frustumTiming"] == config["profiler.shadowTiming"] == "false"
        detail = label in DETAIL
        # First diagnostic pilot predates describe()'s new field; JVM args plus nonzero timers establish its mode.
        assert config.get("profiler.chunkDetail", str(detail).lower() if label=="cp-detail-r1" else "false") == str(detail).lower()
        if label == "cp-detail-r1":
            assert "-Dalacer.profiler.chunkDetail=true" in env["jvm.args"]
        rows = [{k:int(v) for k,v in r.items()} for r in csv.DictReader((path/(phase+".csv")).open())]
        assert len(rows) == stats["frames"] and rows
        total_ns = sum(r["frame_ns"] for r in rows)
        assert abs(total_ns/1e9-stats["recorded_wall_seconds"]) < .03
        assert abs(1e9*len(rows)/total_ns-stats["avg_fps"]) < .02
        for a,b in zip(rows, rows[1:]):
            assert a["start_ns"]+a["frame_ns"] == b["start_ns"]
        if not detail:
            assert all(not v for r in rows for k,v in r.items() if k.startswith("chunk_") and k not in ("chunk_upload_ns", "chunk_matrix_ns", "chunk_matrix_reads"))
        m = dict(stats)
        m["csv_sha256"] = sha(path/(phase+".csv"))
        m["pending_max"] = max(r["pending_chunks"] for r in rows)
        m["pending_p99"] = quantile([r["pending_chunks"] for r in rows], .99)
        m["pending_time_weighted"] = sum(r["pending_chunks"]*r["frame_ns"] for r in rows)/total_ns
        m["frames_over_8ms"] = sum(r["frame_ns"]>8e6 for r in rows)
        m["frames_over_16_67ms"] = sum(r["frame_ns"]>1e9/60 for r in rows)
        m["terrain_layers_mean_ms"] = sum(statistics.mean(r[k+"_ns"] for r in rows)/1e6 for k in ["terrain_solid", "terrain_cutout_mipped", "terrain_cutout", "terrain_translucent"])
        m["render_residual_mean_ms"] = statistics.mean(r["render_ns"]-sum(r[k+"_ns"] for k in TOP_LEVEL) for r in rows)/1e6
        m["worst_frames"] = []
        for r in sorted(rows,key=lambda r:r["frame_ns"],reverse=True)[:20]:
            item = {k:v for k,v in r.items() if k in ["start_ns", "frame_ns", "tick_ns", "render_ns", "swap_ns", "alloc_bytes", "pending_chunks"] or (k.endswith("_ns") and (k[:-3] in TOP_LEVEL or k.startswith("chunk_")))}
            item["render_residual_ns"] = r["render_ns"]-sum(r[k+"_ns"] for k in TOP_LEVEL)
            m["worst_frames"].append(item)
        if detail:
            pairs=[("chunk_build_worker_ns","chunk_build_worker_calls"), ("chunk_resort_worker_ns","chunk_resort_worker_calls"), ("chunk_build_client_ns","chunk_build_client_calls"), ("chunk_queue_wait_ns","chunk_task_starts"), ("chunk_upload_latency_ns","chunk_task_completions"), ("chunk_buffer_wait_ns","chunk_buffer_waits"), ("chunk_gl_list_ns","chunk_gl_list_calls"), ("chunk_schedule_wait_ns","chunk_observed_schedules")]
            m["completed_intervals"] = {}
            for ns, count in pairs:
                if ns not in rows[0]: continue
                calls = sum(r[count] for r in rows)
                m["completed_intervals"][ns] = {"calls":calls,"total_ms":sum(r[ns] for r in rows)/1e6,"mean_per_call_ms":sum(r[ns] for r in rows)/max(1,calls)/1e6}
            m["time_weighted_gauges"] = {k:sum(r[k]*r["frame_ns"] for r in rows)/total_ns for k in ["chunk_dirty","chunk_queued","chunk_ready_uploads","chunk_available_buffers"]}
            m["queue_full_frame_fraction"] = statistics.mean(r["chunk_queued"]==100 for r in rows)
            m["buffers_empty_frame_fraction"] = statistics.mean(r["chunk_available_buffers"]==0 for r in rows)
            if "chunk_dirty_age_ns" in rows[0]:
                m["observed_dirty_age_mean_ms"] = sum(r["chunk_dirty_age_ns"] for r in rows)/max(1,sum(r["chunk_dirty"] for r in rows))/1e6
                m["observed_dirty_age_max_ms"] = max(r["chunk_dirty_age_max_ns"] for r in rows)/1e6
        result["phases"][phase] = m
    (HERE/"runs").mkdir(exist_ok=True)
    (HERE/"runs"/(label+".summary.json")).write_bytes((path/"summary.json").read_bytes())
    return result


def main():
    # The base is copied for every launch and must remain byte-identical throughout the series.
    base = Path("run/saves/alacer-benchmark")
    world = json.loads((HERE/"world.json").read_text())
    actual={str(p.relative_to(base)).replace(chr(92),"/"):sha(p) for p in sorted(base.rglob("*")) if p.is_file()}
    assert actual == world, "Base world changed"
    implementation = json.loads((HERE/"implementation.json").read_text())
    for name, expected in implementation["sha256"].items():
        assert sha(Path(name)) == expected, ("Frozen implementation changed", name)
    runs = {label:collect(label) for label in BASELINE+DETAIL}
    (HERE/"metrics.json").write_text(json.dumps({"baseline":BASELINE,"diagnostics":DETAIL,"runs":runs},indent=2)+"\n")
    lines=["# Session 13 measurements", "", "Fresh copied world per launch; diagnostics are attribution only. No candidate A/B or FPS gain is claimed.", "", "| Run | Fly mean ms | p99 ms | Max ms | CPU ms/frame | Allocation B/frame | Pending mean/max | Terrain ms | Upload ms |", "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label,run in runs.items():
        p=run["phases"]["fly"]
        lines.append(f"| {label} | {1000/p['avg_fps']:.3f} | {p['frame_p99_ms']:.3f} | {p['frame_max_ms']:.3f} | {p['client_thread_cpu_ms_per_frame']:.3f} | {p['alloc_mean_bytes']:.0f} | {p['pending_chunks_mean']:.1f}/{p['pending_max']} | {p['terrain_layers_mean_ms']:.3f} | {p['chunk_upload_mean_ms']:.3f} |")
    (HERE/"measurements.md").write_text("\n".join(lines)+"\n")
    print("Validated", len(runs), "full-duration launches; rebuilt metrics.json and measurements.md")

if __name__ == "__main__": main()
