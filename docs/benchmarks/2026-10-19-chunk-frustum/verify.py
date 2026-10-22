"""Archive and check live frustum diagnostics separately from performance selection."""
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

DEST = Path(__file__).resolve().parent
ROOT = DEST.parents[2]
result = {}
for label in ("fr-verify-boxes", "fr-verify-traversal"):
    folders = list((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*"))
    if len(folders) != 1:
        raise ValueError(f"{label}: expected one complete diagnostic")
    summary = json.loads((folders[0] / "summary.json").read_text())
    log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
    if "BUILD SUCCESSFUL" not in log or set(summary["phases"]) != {"static", "pan", "fly"}:
        raise ValueError(label + ": incomplete diagnostic")
    for failure in ("Chunk frustum test mismatch", "Chunk frustum replay:",
                    "Chunk visibility mismatch with unchanged chunk data", "GL ERROR", "OpenGLException",
                    "could not list the mixins", "is changed by mixin"):
        if failure in log:
            raise ValueError(label + ": " + failure)
    data = {"source": str(folders[0].relative_to(ROOT)), "frames": {}, "log": f"build/{label}.log"}
    for name, phase in summary["phases"].items():
        if phase["dropped_frames"] or phase["discarded_setup_frames"] != 1:
            raise ValueError(label + ": capture failure")
        config = phase["config"]
        boxes = label == "fr-verify-boxes"
        if config["render.fastChunkFrustum.verify"] != str(boxes).lower():
            raise ValueError(label + ": box verification flag")
        if config["render.fastChunkVisibility.verify"] != str(not boxes).lower():
            raise ValueError(label + ": traversal verification flag")
        data["frames"][name] = phase["frames"]
    if label == "fr-verify-boxes":
        matches = re.findall(r"Chunk frustum verification final: (\d+) boxes compared, (\d+) mismatched, (\d+) answered", log)
        if len(matches) != 1:
            raise ValueError(label + ": missing final verification")
        count, mismatches, fallback = map(int, matches[0])
        if count < 1_000_000 or mismatches or fallback:
            raise ValueError(label + ": box verification failed")
        data.update(boxes_compared=count, mismatches=mismatches, fallback=fallback)
    else:
        matches = re.findall(r"Chunk visibility verification: (\d+) searches compared; (\d+) mismatched with stable input; (\d+) had compiled chunk data change mid-comparison \((\d+) of those differed\)", log)
        count, mismatches, changing, different = map(int, matches[-1])
        if count < 1000 or mismatches:
            raise ValueError(label + ": traversal verification failed")
        data.update(searches_at_last_periodic_log=count, stable_mismatches=mismatches,
                    changed_inputs_at_last_log=changing, changed_input_differences_at_last_log=different)
        data["replay"] = {}
        for name, phase in summary["phases"].items():
            vanilla = phase["frustum_replay_vanilla_mean_ms"]
            planes = phase["frustum_replay_planes_mean_ms"]
            data["replay"][name] = {"vanilla_ms_per_frame": vanilla, "planes_ms_per_frame": planes,
                                     "tests_per_frame": phase["frustum_tests_mean"]}
            if name in ("pan", "fly") and not (vanilla > 0 and planes > 0):
                raise ValueError(label + ": no replay measurements")
    (DEST / "runs").mkdir(exist_ok=True)
    (DEST / "runs" / f"{label}.summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    result[label] = data
label = "fr-final-defaults-smoke"
folders = list((ROOT / "run/alacer/benchmarks").glob(f"*-{label}-vis_*-ab"))
if len(folders) != 1:
    raise ValueError(label + ": expected one complete smoke")
summary = json.loads((folders[0] / "summary.json").read_text())
log = (ROOT / "build" / f"{label}.log").read_text(encoding="utf-8")
if "BUILD SUCCESSFUL" not in log or len(summary["phases"]) != 12:
    raise ValueError(label + ": incomplete smoke")
defaults = ("fastChunkVisibility", "fastEntityChunkLookup", "throttleGlErrorChecks",
            "avoidMatrixReads", "cacheClouds", "fastEntityShadows")
for name, phase in summary["phases"].items():
    expected = str(name.rsplit("-", 1)[1].startswith("b")).lower()
    for key in defaults:
        if phase["config"]["render." + key] != expected:
            raise ValueError(f"{label}/{name}: preset {key}")
    for key in ("fastChunkFrustum", "batchModelLists", "skipZeroTranslates"):
        if phase["config"]["render." + key] != "false":
            raise ValueError(f"{label}/{name}: experiment {key} enabled")
    if phase["dropped_frames"] or phase["discarded_setup_frames"] != 1:
        raise ValueError(label + ": capture failure")
result[label] = {"phases": len(summary["phases"]), "preset_verified": True,
                 "performance_evidence": False, "time_scale": 0.1}
(DEST / "runs" / f"{label}.summary.json").write_text(json.dumps(summary, indent=2) + "\n")
tests = [ET.parse(p).getroot() for p in (ROOT / "build/test-results/test").glob("TEST-*.xml")]
counts = {key: sum(int(test.get(key)) for test in tests) for key in ("tests", "failures", "errors", "skipped")}
if counts["tests"] < 39 or any(counts[k] for k in ("failures", "errors", "skipped")):
    raise ValueError("Final tests failed: " + str(counts))
if "BUILD SUCCESSFUL" not in (ROOT / "build/fr-final-build.log").read_text(encoding="utf-8"):
    raise ValueError("Final build did not pass")
result["final_build"] = counts
(DEST / "verification.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
