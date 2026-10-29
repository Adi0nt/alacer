"""Gate table for the 1.0.0 frame pacing check (see PROTOCOL.md).

Usage: python analyze.py RUN_DIR [RUN_DIR ...] > results.json

Reads each in-process toggle run's per-segment CSVs (A = option off, B = option on) and
summary.json, and prints per launch and phase: median, p99 and standard deviation of frame
time, mean frame rate, and client-thread CPU per frame. Standard library only.
"""
import csv
import glob
import json
import os
import statistics
import sys


def frames(run, phase, variant):
    values = []
    for path in sorted(glob.glob(os.path.join(run, f"{phase}-{variant}[0-9]*.csv"))):
        with open(path, newline="") as f:
            values += [int(row["frame_ns"]) / 1e6 for row in csv.DictReader(f)]
    return values


def cpu(run, phase, variant):
    summary = json.load(open(os.path.join(run, "summary.json")))
    phases = summary["phases"]
    total = n = 0.0
    for name, p in (phases.items() if isinstance(phases, dict) else ((p.get("name"), p) for p in phases)):
        if name and name.startswith(f"{phase}-{variant}") and "client_thread_cpu_ms_per_frame" in p:
            total += p["client_thread_cpu_ms_per_frame"] * p["frames"]
            n += p["frames"]
    return total / n if n else None


def describe(values):
    ordered = sorted(values)
    return {
        "frames": len(values),
        "median_ms": statistics.median(values),
        "p99_ms": ordered[min(len(ordered) - 1, int(0.99 * len(ordered)))],
        "stdev_ms": statistics.pstdev(values),
        "mean_fps": 1000.0 / statistics.fmean(values),
    }


def main(runs):
    out = {}
    for run in runs:
        summary = json.load(open(os.path.join(run, "summary.json")))
        env = summary["environment"]
        launch = {"toggle": env.get("benchmark.toggle"), "reverse": env.get("benchmark.toggleReverse"),
                  "fpsLimit": env.get("benchmark.fpsLimit"), "alacer": env.get("alacer.version"),
                  "phases": {}}
        for phase in ("static", "pan"):
            row = {}
            for variant in ("a", "b"):
                row[variant] = describe(frames(run, phase, variant))
                row[variant]["client_cpu_ms_per_frame"] = cpu(run, phase, variant)
            row["change_pct"] = {
                k: 100.0 * (row["b"][k] / row["a"][k] - 1.0)
                for k in row["a"] if k != "frames" and row["a"][k] and row["b"][k] is not None
            }
            launch["phases"][phase] = row
        out[os.path.basename(run.rstrip("/\\"))] = launch
    json.dump(out, sys.stdout, indent=2)
    print()


if __name__ == "__main__":
    main(sys.argv[1:])
