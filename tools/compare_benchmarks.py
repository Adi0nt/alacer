#!/usr/bin/env python3
"""Compare Alacer benchmark runs.

Usage:
    python tools/compare_benchmarks.py [--phases static,pan] A_DIR [A_DIR ...] -- B_DIR [B_DIR ...]
    python tools/compare_benchmarks.py --toggle [--phases static,pan] RUN_DIR [RUN_DIR ...]

Each directory is one benchmark output (run/alacer/benchmarks/<timestamp>-<label>/).

The first form compares separate launches: frames from all runs of a variant are pooled per
phase. The --toggle form reads runs made with -Dalacer.benchmark.toggle=<option>, where each
launch measured both variants: segments "<phase>-a*" (option off) are pooled as A and
"<phase>-b*" (option on) as B.

Everything is summarized from the raw per-frame CSVs, so the comparison does not depend on how
summary.json was computed. Phase-level CPU time comes from summary.json (it is sampled at phase
boundaries, not per frame). Prints a Markdown table per phase. Standard library only.
"""

import csv
import json
import math
import os
import sys

DEFAULT_PHASES = ("static", "pan", "fly")
# Sections shown, in order. Missing columns (older runs) are skipped.
SECTIONS = ("render", "terrain_setup", "visibility", "chunk_upload", "entities", "entity_loop",
            "entity_chunk_lookup", "entity_visibility_check", "entity_draw", "block_entity_loop",
            "block_entity_draw", "entity_living_setup", "entity_model_prepare", "entity_model_animation",
            "entity_render_model", "entity_model_draw", "entity_layers", "entity_shadow", "entity_shadow_block",
            "entity_shadow_query", "entity_name_tag", "entity_overlay", "world", "sky", "clouds", "terrain_solid", "terrain_cutout_mipped",
            "terrain_cutout", "terrain_translucent", "particles", "gui", "swap", "gl_error_check")
COUNTERS = ("visible_chunks", "pending_chunks", "visibility_searches", "entity_chunk_lookups",
            "entity_chunk_cache_hits", "entities_checked", "entities_drawn", "block_entities_drawn",
            "cloud_rebuilds")


def separate(dirs, phase):
    return [(d, phase) for d in dirs]


def toggled(dirs, phase, variant):
    prefix = f"{phase}-{variant}"
    return [(d, f[:-4]) for d in dirs for f in sorted(os.listdir(d))
            if f.startswith(prefix) and f.endswith(".csv")]


def load(sources):
    cols = {}
    for d, name in sources:
        with open(os.path.join(d, name + ".csv"), newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                for k, v in row.items():
                    cols.setdefault(k, []).append(int(v))
    return cols


def phase_cpu(sources):
    thread = process = frames = 0.0
    for d, name in sources:
        with open(os.path.join(d, "summary.json"), encoding="utf-8") as f:
            p = json.load(f)["phases"][name]
        if "client_thread_cpu_ms_per_frame" not in p:
            return None
        n = p["frames"]
        frames += n
        thread += p["client_thread_cpu_ms_per_frame"] * n
        process += p["process_cpu_cores"] * n
    return {"client thread CPU ms/frame": thread / frames, "process CPU cores": process / frames}


def pct(sorted_values, p):
    if not sorted_values:
        return 0
    rank = math.ceil(p / 100.0 * len(sorted_values))
    return sorted_values[max(0, min(len(sorted_values) - 1, rank - 1))]


def summarize(sources):
    cols = load(sources)
    frames = sorted(cols["frame_ns"])
    n = len(frames)
    total = sum(frames)
    worst = frames[n - max(1, n // 100):]
    out = {
        "frames": n,
        "avg fps": n * 1e9 / total,
        "1% low fps": 1e9 / (sum(worst) / len(worst)),
        "frame p50 ms": pct(frames, 50) / 1e6,
        "frame p95 ms": pct(frames, 95) / 1e6,
        "frame p99 ms": pct(frames, 99) / 1e6,
        "frame p99.9 ms": pct(frames, 99.9) / 1e6,
    }
    cpu = phase_cpu(sources)
    if cpu:
        out.update(cpu)
    for s in SECTIONS:
        key = s + "_ns"
        if key in cols and any(cols[key]):
            out[s + " mean ms"] = sum(cols[key]) / n / 1e6
    alloc = cols["alloc_bytes"]
    if min(alloc) >= 0:
        out["alloc KB/frame"] = sum(alloc) / n / 1024
        out["alloc MB/s"] = sum(alloc) / (total / 1e9) / 2**20
    for c in COUNTERS:
        if c in cols and any(cols[c]):
            out[c] = sum(cols[c]) / n
    return out


def fmt(v):
    if isinstance(v, int):
        return str(v)
    return f"{v:.3f}" if abs(v) < 10 else f"{v:.1f}"


def table(phase, a, b):
    print(f"### {phase}\n")
    print("| metric | A | B | change |")
    print("|---|---:|---:|---:|")
    for key in a:
        if key not in b:
            continue
        va, vb = a[key], b[key]
        change = "" if not va else f"{(vb - va) / va * 100:+.1f}%"
        print(f"| {key} | {fmt(va)} | {fmt(vb)} | {change} |")
    print()


def main(argv):
    toggle = False
    phases = None
    while argv and argv[0].startswith("--") and argv[0] != "--":
        if argv[0] == "--toggle":
            toggle = True
            argv = argv[1:]
        elif argv[0] == "--phases":
            phases = tuple(argv[1].split(","))
            argv = argv[2:]
        else:
            print(__doc__)
            return 2
    if toggle:
        phases = phases or ("static", "pan")
        print(f"A (option off) vs B (option on) within {len(argv)} run(s)\n")
        for phase in phases:
            table(phase, summarize(toggled(argv, phase, "a")), summarize(toggled(argv, phase, "b")))
        return 0
    if "--" not in argv:
        print(__doc__)
        return 2
    phases = phases or DEFAULT_PHASES
    split = argv.index("--")
    a_dirs, b_dirs = argv[:split], argv[split + 1:]
    print(f"A: {len(a_dirs)} run(s), B: {len(b_dirs)} run(s)\n")
    for phase in phases:
        table(phase, summarize(separate(a_dirs, phase)), summarize(separate(b_dirs, phase)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
