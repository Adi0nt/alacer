"""Validate and archive the final in-game model-list checks; run from the repository root."""
import json
import pathlib
import re

DEST = pathlib.Path(__file__).resolve().parent
ROOT = DEST.parents[2]
LABELS = ("ml-verify1000", "ml-verify200-off", "ml-fault200", "ml-default-smoke")
PATTERN = re.compile(
    r"Model list batch final: (\d+) lists compiled, (\d+) face draws -> (\d+) batched draws, "
    r"(\d+) unsupported compilations; (\d+) native feedback comparisons, (\d+) floats compared, "
    r"(\d+) mismatches, (\d+) empty comparisons")
FIELDS = ("compiled", "face_draws", "batched_draws", "unsupported", "comparisons", "floats", "mismatches", "empty")
results = {}
for label in LABELS:
    folder = sorted((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*"))[-1]
    summary_text = (folder / "summary.json").read_text(encoding="utf-8")
    summary = json.loads(summary_text)
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
    assert "BUILD SUCCESSFUL" in log
    assert not re.search(r"GL ERROR|OpenGLException|Rendering entity in world", log)
    assert summary["environment"]["benchmark.generationRun"] == "false"
    assert summary["config"]["profiler.entityDetail"] == "false"
    assert summary["config"]["render.cacheClouds"] == "true"
    assert all(p["dropped_frames"] == 0 and p["frames"] > 0 for p in summary["phases"].values())
    assert all(p["entity_draw_mean_ms"] == 0 and p["entity_model_draw_mean_ms"] == 0
               for p in summary["phases"].values())
    match = PATTERN.search(log)
    assert match, label
    row = dict(zip(FIELDS, map(int, match.groups())))
    row["batch_enabled"] = summary["config"]["render.batchModelLists"]
    row["verify_enabled"] = summary["config"]["render.batchModelLists.verify"]
    row["frames"] = sum(p["frames"] for p in summary["phases"].values())
    assert row["empty"] == 0
    if label == "ml-fault200":
        assert row["mismatches"] == 3 and row["comparisons"] > 3
        assert "-Dalacer.benchmark.modelListFault=true" in summary["environment"]["jvm.args"]
    elif label == "ml-default-smoke":
        assert row["compiled"] == row["comparisons"] == row["mismatches"] == 0
        assert row["batch_enabled"] == row["verify_enabled"] == "false"
    else:
        assert row["compiled"] > 0 and row["comparisons"] == 3 * row["compiled"]
        assert row["floats"] > 0 and row["mismatches"] == 0
        assert row["verify_enabled"] == "true"
        assert row["batch_enabled"] == ("true" if label == "ml-verify1000" else "false")
    results[label] = row
    (DEST / "runs" / f"{label}.summary.json").write_text(summary_text, encoding="utf-8", newline="\n")

(DEST / "verification.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
out = ["# Final native verification", "",
       "All checks ran the real development client on Temurin 8, 1920x1080, distance 12,",
       "VBO off, cloud caching on and entity detail off. No base-world generation or dropped",
       "frames. All static/pan/fly phases completed. These runs are excluded from performance selection.", "",
       "| Launch | Batch | Verify | Lists | Original face draws | Feedback checks | Floats compared | Differences |",
       "|---|---|---|---:|---:|---:|---:|---:|"]
for label, row in results.items():
    out.append(f"| {label} | {row['batch_enabled']} | {row['verify_enabled']} | {row['compiled']}"
               f" | {row['face_draws']} | {row['comparisons']} | {row['floats']} | {row['mismatches']} |")
out += ["", "The two full verification runs compare transformed native list output under three views,",
        "including nonuniform and negative scales. Final current normal/texture coordinates are",
        "also compared. Zero empty comparisons: the native output was actually exercised.", "",
        "The negative control adds 0.125 to one uploaded position in the first alternate list.",
        "All three views detect it. The failed alternate is deleted and the part uses its retained",
        "vanilla list. Later lists pass; the benchmark completes without a GL error or rendering exception.", "",
        "The disabled-startup smoke check reports zero alternate compilations and comparisons,",
        "confirming the startup gate in the actual client. Unit tests also cover startup flag",
        "selection, system-property/config parsing, defaults and runtime toggle acceptance.", "",
        "Static and fly screenshots from `ml-verify1000` were visually inspected: models, fur,",
        "textures, terrain and cached clouds were present without an obvious geometry defect.",
        "Screenshots remain at `run/screenshots/alacer-ml-verify1000-vis_on-ecl_on-ent1000-{static,pan,fly}.png`.",
        "This is sampled visual inspection plus native geometry/attribute parity; framebuffer identity,",
        "all lighting states, every model species and arbitrary mod injection compatibility were not established.",
        "The 512-list cap and GC reclamation were reviewed in source but were not stress-tested here.", "",
        "```bash",
        "./gradlew runClient -Palacer.benchmark=true -Palacer.benchmark.label=ml-verify1000 -Palacer.benchmark.entities=1000 -Palacer.render.batchModelLists=true -Palacer.render.batchModelLists.verify=true -Palacer.benchmark.screenshots=true -Palacer.profiler.entityDetail=false -Pwindow=1920x1080 -Pxmx=2G",
        "# ml-verify200-off: entities=200, batchModelLists=false, verify=true, screenshots=false.",
        "# ml-fault200: entities=200, batchModelLists=true, verify=true, timeScale=0.15, benchmark.modelListFault=true.",
        "# ml-default-smoke: entities=200, batchModelLists=false, verify=false, timeScale=0.15.",
        "python docs/benchmarks/2026-10-19-model-lists/verify.py",
        "```", ""]
(DEST / "verification.md").write_text("\n".join(out), encoding="utf-8", newline="\n")
print(json.dumps(results, indent=2))
