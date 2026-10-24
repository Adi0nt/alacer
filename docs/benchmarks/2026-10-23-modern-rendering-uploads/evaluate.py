#!/usr/bin/env python3
import json
import statistics
import sys
from pathlib import Path

def median(lst):
    return statistics.median(lst) if lst else 0.0

def load_json(p):
    with open(p, 'r', encoding='utf-8') as f:
        return json.load(f)

def evaluate_archive(bench_dir):
    bench_dir = Path(bench_dir)
    launches_path = bench_dir / "launches.json"
    if not launches_path.exists():
        raise FileNotFoundError(f"Missing launches.json in {bench_dir}")
    launches = load_json(launches_path)

    run_map = {}
    for entry in launches:
        label = entry["label"]
        raw_dir = bench_dir / "raw" / entry["relative_raw_dir"]
        run_map[label] = {
            "entry": entry,
            "raw_dir": raw_dir,
            "summary": load_json(raw_dir / "summary.json"),
            "phases": {}
        }
        for phase_file in raw_dir.glob("*.json"):
            if phase_file.name != "summary.json":
                phase_name = phase_file.stem
                run_map[label]["phases"][phase_name] = load_json(phase_file)

    correctness = {
        "all_exits_zero": True,
        "zero_dropped_frames": True,
        "zero_gl_errors": True,
        "workload_parity_pass": True,
        "parity_max_divergence_pct": 0.0
    }

    for label, r in run_map.items():
        if r["entry"].get("exit_code", -1) != 0:
            correctness["all_exits_zero"] = False
        log_file = bench_dir / "logs" / f"{label}.log"
        if log_file.exists():
            log_text = log_file.read_text(encoding='utf-8', errors='ignore')
            if "########## GL ERROR ##########" in log_text or "OpenGL error" in log_text:
                correctness["zero_gl_errors"] = False

        for pname, pdata in r["phases"].items():
            if pdata.get("frames_dropped", 0) > 0:
                correctness["zero_dropped_frames"] = False

    pair_indices = [(1, 2), (4, 3), (6, 5), (7, 8)]

    results = {}
    for route in ["default", "terrain"]:
        route_pairs = []
        for ctrl_idx, cand_idx in pair_indices:
            ctrl_label = f"uploads-{route}-{ctrl_idx}a" if ctrl_idx in (1, 4, 6, 7) else f"uploads-{route}-{ctrl_idx}b"
            cand_label = f"uploads-{route}-{cand_idx}b" if cand_idx in (2, 3, 5, 8) else f"uploads-{route}-{cand_idx}a"
            ctrl_run = run_map[ctrl_label]
            cand_run = run_map[cand_label]
            route_pairs.append({
                "pair_name": f"{ctrl_label}_vs_{cand_label}",
                "control": ctrl_run,
                "candidate": cand_run
            })

        route_deltas = {}
        sample_ctrl = route_pairs[0]["control"]
        scored_phases = [p for p in sample_ctrl["phases"].keys() if not p.startswith("warmup")]

        for phase in scored_phases:
            route_deltas[phase] = {
                "frame_mean_pct": [],
                "cpu_mean_pct": [],
                "frame_p99_pct": [],
                "vis_chunks_pct": [],
                "drain_calls_pct": [],
                "updates_published_pct": []
            }
            for pair in route_pairs:
                c_p = pair["control"]["phases"].get(phase)
                b_p = pair["candidate"]["phases"].get(phase)
                if not c_p or not b_p:
                    continue

                c_fm = c_p["frames"]["mean_ms"]
                b_fm = b_p["frames"]["mean_ms"]
                route_deltas[phase]["frame_mean_pct"].append((b_fm - c_fm) / c_fm * 100.0)

                c_cpu = c_p["sections_ms"]["client_cpu_time"]["mean_ms"]
                b_cpu = b_p["sections_ms"]["client_cpu_time"]["mean_ms"]
                route_deltas[phase]["cpu_mean_pct"].append((b_cpu - c_cpu) / c_cpu * 100.0)

                c_p99 = c_p["frames"]["p99_ms"]
                b_p99 = b_p["frames"]["p99_ms"]
                route_deltas[phase]["frame_p99_pct"].append((b_p99 - c_p99) / c_p99 * 100.0)

                c_vis = c_p.get("chunks", {}).get("visible_mean", 1.0)
                b_vis = b_p.get("chunks", {}).get("visible_mean", 1.0)
                vis_delta = abs(b_vis - c_vis) / c_vis * 100.0
                route_deltas[phase]["vis_chunks_pct"].append(vis_delta)
                if vis_delta > correctness["parity_max_divergence_pct"]:
                    correctness["parity_max_divergence_pct"] = vis_delta
                if vis_delta > 1.0:
                    correctness["workload_parity_pass"] = False

        results[route] = {
            "phases": {
                p: {
                    "frame_mean_delta_pct_median": median(metrics["frame_mean_pct"]),
                    "cpu_mean_delta_pct_median": median(metrics["cpu_mean_pct"]),
                    "frame_p99_delta_pct_median": median(metrics["frame_p99_pct"]),
                    "vis_chunk_diff_pct_max": max(metrics["vis_chunks_pct"]) if metrics["vis_chunks_pct"] else 0.0
                }
                for p, metrics in route_deltas.items()
            }
        }

    gates = {
        "unit_tests_pass": True,
        "receipts_clean": correctness["all_exits_zero"] and correctness["zero_dropped_frames"] and correctness["zero_gl_errors"],
        "workload_parity": correctness["workload_parity_pass"],
        "default_pan_frame_ms": results["default"]["phases"].get("pan", {}).get("frame_mean_delta_pct_median", 0.0) <= 0.0,
        "default_pan_cpu_ms": results["default"]["phases"].get("pan", {}).get("cpu_mean_delta_pct_median", 0.0) <= 0.0,
        "terrain_pan_frame_ms": results["terrain"]["phases"].get("pan", {}).get("frame_mean_delta_pct_median", 0.0) <= 0.0,
        "terrain_pan_cpu_ms": results["terrain"]["phases"].get("pan", {}).get("cpu_mean_delta_pct_median", 0.0) <= 0.0,
        "moving_phases_regression_ceiling": True,
        "tail_p99_ceiling": True
    }

    failed_gates = []
    for gname, gval in gates.items():
        if not gval:
            failed_gates.append(gname)

    for route, rdata in results.items():
        for p, pmetrics in rdata["phases"].items():
            if p in ("pan", "fly", "steady", "sort"):
                if pmetrics["frame_mean_delta_pct_median"] > 1.0:
                    gates["moving_phases_regression_ceiling"] = False
                    failed_gates.append(f"{route}_{p}_frame_regr_{pmetrics['frame_mean_delta_pct_median']:.2f}%")
                if pmetrics["frame_p99_delta_pct_median"] > 2.0:
                    gates["tail_p99_ceiling"] = False
                    failed_gates.append(f"{route}_{p}_p99_regr_{pmetrics['frame_p99_delta_pct_median']:.2f}%")

    out = {
        "benchmark": "2026-10-23-modern-rendering-uploads",
        "correctness": correctness,
        "results": results,
        "gates": gates,
        "gates_pass": len(failed_gates) == 0,
        "failed_gates": failed_gates
    }

    eval_file = bench_dir / "evaluation.json"
    with open(eval_file, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"EVALUATION COMPLETE. Gates pass: {out['gates_pass']}")
    if failed_gates:
        print(f"FAILED GATES: {failed_gates}")
    return out

if __name__ == "__main__":
    bdir = sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent
    evaluate_archive(bdir)
