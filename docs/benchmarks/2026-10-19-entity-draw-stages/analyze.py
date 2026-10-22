"""Regenerate entity attribution and instrumentation controls from ignored local CSVs/logs.

Run from the repository root. Optional launch labels allow partial progress reports.
"""
import json
import pathlib
import re
import sys

DEST = pathlib.Path(__file__).resolve().parent
REPO = DEST.parents[2]
sys.path.insert(0, str(REPO / "tools"))
import compare_benchmarks as compare

STAGES = ("living_setup", "model_prepare", "model_animation", "render_model", "model_draw",
          "layers", "shadow", "shadow_block", "shadow_query", "name_tag", "overlay")


def main(labels):
    data = {}
    logs = []
    classes = {}
    for label in labels:
        folder = sorted((REPO / "run/alacer/benchmarks").glob(f"*-{label}-vis_*"))[-1]
        summary = json.loads((folder / "summary.json").read_text(encoding="utf-8"))
        assert summary["environment"]["benchmark.generationRun"] == "false"
        assert summary["config"]["render.cacheClouds"] == "true"
        assert all(p["dropped_frames"] == 0 for p in summary["phases"].values())
        stages = summary["config"]["profiler.entityStages"] == "true"
        (DEST / "runs" / f"{label}.summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n")
        data[label] = {"stages": stages, "phases": {}}
        for phase in ("static", "pan", "fly"):
            sources = [(str(folder), phase)]
            columns = compare.load(sources)
            drawn = sum(columns["entities_drawn"])
            # Actual per-frame timing invariants, not sums of overlapping sections.
            for parent, child in (("entity_render_model_ns", "entity_model_draw_ns"),
                                  ("entity_shadow_ns", "entity_shadow_block_ns"),
                                  ("entity_shadow_ns", "entity_shadow_query_ns")):
                assert all(a >= b for a, b in zip(columns[parent], columns[child])), (label, phase, child)
            top = ["entity_living_setup_ns", "entity_model_prepare_ns", "entity_render_model_ns",
                   "entity_layers_ns", "entity_shadow_ns", "entity_name_tag_ns"]
            assert all(sum(v) <= total for v, total in
                       zip(zip(*(columns[k] for k in top)), columns["entity_draw_ns"])), (label, phase, "top-level sum")
            stats = compare.summarize(sources)
            stats["frame mean ms"] = 1000 / stats["avg fps"]
            stats["draw us per entity"] = sum(columns["entity_draw_ns"]) / drawn / 1000
            stats["stages us per entity"] = {
                stage: sum(columns[f"entity_{stage}_ns"]) / drawn / 1000 for stage in STAGES}
            if not stages:
                assert sum(stats["stages us per entity"].values()) == 0
            data[label]["phases"][phase] = stats
        lines = (REPO / "build" / f"{label}.log").read_text(encoding="utf-8-sig").splitlines()
        classes[label] = {}
        for i, line in enumerate(lines):
            match = re.search(r"Entity draw detail in '([^']+)'", line)
            if match:
                phase = match[1]
                rows = {}
                logs += [f"## {label} / {phase}", "```text", line]
                for following in lines[i + 1:]:
                    if not following.startswith("  "):
                        break
                    logs.append(following)
                    if "incomplete spans=" in following:
                        assert following.strip() == "incomplete spans=0"
                    name = re.match(r"  (\w+) n=(\d+)", following)
                    if name:
                        rows[name[1]] = {"draws": int(name[2]), "stages": {
                            k: {"us": float(v), "calls": float(n)} for k, v, n in
                            re.findall(r"(\w+)=([\d.]+)us\(([\d.]+) calls\)", following)}}
                logs += ["```", ""]
                classes[label][phase] = rows
        assert len(classes[label]) == (3 if stages else 0)
    (DEST / "metrics.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "classes.json").write_text(json.dumps(classes, indent=2) + "\n", encoding="utf-8", newline="\n")
    (DEST / "classes.md").write_text("\n".join(logs), encoding="utf-8", newline="\n")
    out = ["# Entity drawing: stage attribution and instrumentation controls", "",
           "All runs use cloud caching; extra stage hooks are absent in controls. Times below are",
           "inclusive microseconds per drawn entity. MODEL_DRAW is inside MODEL; SHADOW_BLOCK",
           "inside SHADOW; ANIMATION includes root and layer models; QUERY overlaps SHADOW_BLOCK.", "",
           "| Run | Phase | Draw | Setup | Prepare | Animation | Model | Model draw | Layers | Shadow | Shadow block | Shadow query | Name tag | Overlay |",
           "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label, run in data.items():
        if run["stages"]:
            for phase, p in run["phases"].items():
                values = [p["draw us per entity"]] + [p["stages us per entity"][s] for s in STAGES]
                out.append(f"| {label} | {phase} | " + " | ".join(f"{v:.3f}" for v in values) + " |")
    out += ["", "## Instrumentation comparison (separate JVMs, includes drift)", "",
            "| Run | Phase | Frame ms | Client CPU ms/frame | Entity draw us/entity | Allocation KB/frame | Process cores |",
            "|---|---|---:|---:|---:|---:|---:|"]
    for label, run in data.items():
        for phase, p in run["phases"].items():
            out.append(f"| {label} | {phase} | {p['frame mean ms']:.3f} | {p['client thread CPU ms/frame']:.3f}"
                       + f" | {p['draw us per entity']:.3f} | {p['alloc KB/frame']:.1f} | {p['process CPU cores']:.3f} |")
    (DEST / "comparison.md").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1:] or [f"eds-{mobs}-{segment}" for mobs in (200, 1000) for segment in ("r1", "control", "r2")])
