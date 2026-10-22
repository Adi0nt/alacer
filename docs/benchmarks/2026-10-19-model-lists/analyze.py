"""Archive ml-* summaries and regenerate tables from the local, ignored per-frame CSVs.

Run from the repository root: python docs/benchmarks/2026-10-19-cloud-cache/analyze.py
Optional arguments select launch labels, e.g. ml-200-r2 ml-1000-r2.
"""
import json
import pathlib
import sys
import statistics
import re

REPO = pathlib.Path(__file__).resolve().parents[3]
DEST = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(REPO / "tools"))
import compare_benchmarks as compare


def main(labels, prefix=""):
    runs = []
    for label in labels:
        matches = sorted((REPO / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))
        if not matches:
            raise SystemExit(f"No completed launch for {label}")
        runs.append((label, matches[-1]))
    (DEST / "runs").mkdir(exist_ok=True)
    results = {}
    compilations = {}
    for label, folder in runs:
        summary = json.loads((folder / "summary.json").read_text(encoding="utf-8"))
        assert summary["environment"]["benchmark.generationRun"] == "false"
        assert summary["environment"]["benchmark.toggle"].startswith("render.batchModelLists ")
        assert all(p["dropped_frames"] == 0 for p in summary["phases"].values())
        assert summary["config"]["profiler.entityDetail"] == "false"
        assert summary["config"]["render.batchModelLists.verify"] == "false"
        assert summary["config"]["render.cacheClouds"] == "true"
        assert summary["environment"]["options.useVbo"] == "false"
        for key, value in {"window": "1920x1080", "options.renderDistance": "12",
                           "options.fancyGraphics": "true", "options.vsync": "false",
                           "options.clouds": "2"}.items():
            assert summary["environment"][key] == value
        assert all(v == "false" for k, v in summary["config"].items() if k.endswith(".verify"))
        assert int(summary["environment"]["benchmark.mobs"]) in (200, 1000)
        (DEST / "runs" / f"{label}.summary.json").write_text(
            json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n")
        log = (REPO / "build" / f"{label}.log").read_text(encoding="utf-8")
        match = re.search(r"Model list batch final: (\d+) lists compiled, (\d+) face draws -> (\d+) batched draws", log)
        assert match, label
        compilations[label] = dict(zip(("lists", "face_draws", "batched_draws"), map(int, match.groups())))
        assert compilations[label]["lists"] > 0
        assert compilations[label]["face_draws"] > compilations[label]["batched_draws"]
        results[label] = {}
        for phase in ("static", "pan"):
            variants = {}
            for variant, names in (("a1", [phase + "-a1"]), ("a2", [phase + "-a2"]),
                                   ("a", [phase + "-a1", phase + "-a2"]),
                                   ("b", [phase + "-b1", phase + "-b2"])):
                sources = [(str(folder), name) for name in names]
                stats = compare.summarize(sources)
                stats["frame mean ms"] = 1000 / stats["avg fps"]
                columns = compare.load(sources)
                stats["cloud rebuilds total"] = int(sum(columns["cloud_rebuilds"]))
                assert not any(columns["entity_draw_ns"])
                assert not any(columns["entity_model_draw_ns"])
                no_tick = sorted(a for a, t in zip(columns["alloc_bytes"], columns["tick_ns"]) if t == 0)
                stats["non-tick allocation median bytes"] = compare.pct(no_tick, 50)
                variants[variant] = stats
            results[label][phase] = variants
    (DEST / "compilations.json").write_text(json.dumps(compilations, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / f"{prefix}metrics.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
    out = ["# Model-part display lists: in-process A/B, 200 and 1,000 mobs", "",
           "Generated from the local per-frame CSVs by `analyze.py`; summaries are in `runs/`.",
           "A = off, B = on. B pools b1/b2. A1/A2 are shown separately to expose drift;",
           "the last column compares B with both A segments pooled. Times are ms/frame.", "",
           "| Run | Phase | A1 frame | A2 frame | B frame | B vs A2 | B vs pooled A |",
           "|---|---|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a1, a2, a, b = (v[k]["frame mean ms"] for k in ("a1", "a2", "a", "b"))
            out.append(f"| {label} | {phase} | {a1:.3f} | {a2:.3f} | {b:.3f} | {(b/a2-1)*100:+.1f}% | {(b/a-1)*100:+.1f}% |")
    out += ["", "## Attribution (A2 -> B)", "",
            "| Run | Phase | Entity loop | Render | Swap | Client CPU | Process cores | Allocation KB/frame | Non-tick allocation bytes |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a2"], v["b"]
            vals = [f"{a[k]:.3f} -> {b[k]:.3f}" for k in (
                "entity_loop mean ms", "render mean ms", "swap mean ms", "client thread CPU ms/frame", "process CPU cores")]
            out.append(f"| {label} | {phase} | " + " | ".join(vals)
                       + f" | {a['alloc KB/frame']:.1f} -> {b['alloc KB/frame']:.1f} | {a['non-tick allocation median bytes']} -> {b['non-tick allocation median bytes']} |")
    out += ["", "## Tails (A2 -> B, percentiles calculated from pooled raw frames)", "",
            "| Run | Phase | p99 ms | 1% low FPS |", "|---|---|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a2"], v["b"]
            out.append(f"| {label} | {phase} | {a['frame p99 ms']:.3f} -> {b['frame p99 ms']:.3f}"
                       + f" | {a['1% low fps']:.1f} -> {b['1% low fps']:.1f} |")
    out += ["", "## Selection summary (median of per-launch changes)", "",
            "| Mobs | Phase | B vs pooled A | B vs A2 | Wins vs A / A2 |",
            "|---|---|---:|---:|---:|"]
    selection = {}
    for population in (200, 1000):
        selection[str(population)] = {}
        for phase in ("static", "pan"):
            rows = [v[phase] for label, v in results.items() if f"ml-{population}-" in label]
            if not rows:
                continue
            da = [(v["b"]["frame mean ms"] / v["a"]["frame mean ms"] - 1) * 100 for v in rows]
            da2 = [(v["b"]["frame mean ms"] / v["a2"]["frame mean ms"] - 1) * 100 for v in rows]
            record = {"median_B_vs_A_percent": statistics.median(da),
                      "median_B_vs_A2_percent": statistics.median(da2),
                      "wins_vs_A": sum(x < 0 for x in da), "wins_vs_A2": sum(x < 0 for x in da2),
                      "launches": len(rows)}
            selection[str(population)][phase] = record
            out.append(f"| {population} | {phase} | {statistics.median(da):+.1f}% | {statistics.median(da2):+.1f}%"
                       f" | {record['wins_vs_A']}/{len(rows)} / {record['wins_vs_A2']}/{len(rows)} |")
    (DEST / "selection.json").write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / f"{prefix}comparison.md").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1:] or [f"ml-{pop}-r{r}" for r in (2, 3, 4) for pop in (200, 1000)])
