"""Archive cc-r* summaries and regenerate tables from the local, ignored per-frame CSVs.

Run from the repository root: python docs/benchmarks/2026-10-19-cloud-cache/analyze.py
Optional arguments select launch labels, e.g. cc-r1 cc-r2 cc-r3.
"""
import json
import pathlib
import sys

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
    for label, folder in runs:
        summary = json.loads((folder / "summary.json").read_text(encoding="utf-8"))
        assert summary["environment"]["benchmark.generationRun"] == "false"
        assert summary["environment"]["benchmark.toggle"].startswith("render.cacheClouds ")
        assert all(p["dropped_frames"] == 0 for p in summary["phases"].values())
        (DEST / "runs" / f"{label}.summary.json").write_text(
            json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n")
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
                no_tick = sorted(a for a, t in zip(columns["alloc_bytes"], columns["tick_ns"]) if t == 0)
                stats["non-tick allocation median bytes"] = compare.pct(no_tick, 50)
                variants[variant] = stats
            results[label][phase] = variants
    (DEST / f"{prefix}metrics.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
    out = ["# Fancy cloud display list: in-process A/B, 200 mobs", "",
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
            "| Run | Phase | Clouds | Render | Swap | Client CPU | Process cores | Allocation KB/frame | B rebuilds |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a2"], v["b"]
            vals = [f"{a[k]:.3f} -> {b[k]:.3f}" for k in (
                "clouds mean ms", "render mean ms", "swap mean ms", "client thread CPU ms/frame", "process CPU cores")]
            out.append(f"| {label} | {phase} | " + " | ".join(vals)
                       + f" | {a['alloc KB/frame']:.1f} -> {b['alloc KB/frame']:.1f} | {b['cloud rebuilds total']} |")
    out += ["", "## Tails (A2 -> B, percentiles calculated from pooled raw frames)", "",
            "| Run | Phase | p99 ms | 1% low FPS |", "|---|---|---:|---:|"]
    for label, phases in results.items():
        for phase, v in phases.items():
            a, b = v["a2"], v["b"]
            out.append(f"| {label} | {phase} | {a['frame p99 ms']:.3f} -> {b['frame p99 ms']:.3f}"
                       + f" | {a['1% low fps']:.1f} -> {b['1% low fps']:.1f} |")
    (DEST / f"{prefix}comparison.md").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1:] or ["cc-r1", "cc-r2", "cc-r3"])
