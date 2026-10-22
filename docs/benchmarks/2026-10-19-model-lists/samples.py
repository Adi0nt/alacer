"""Summarize the detail-off JFR cross-check, without treating samples as exact timings.

First run jfr print --json --stack-depth 128 --events jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ActiveSetting
run/ml-sample1000.jfr > build/ml-samples.json
"""
import collections
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[3]
DEST = pathlib.Path(__file__).resolve().parent
events = json.loads((ROOT / "build/ml-samples.json").read_text(encoding="utf-8-sig"))["recording"]["events"]
counts = collections.defaultdict(collections.Counter)
leaves = collections.Counter()
model_leaves = collections.defaultdict(collections.Counter)
settings = []
for event in events:
    values = event["values"]
    trace = values.get("stackTrace")
    if event["type"] == "jdk.ActiveSetting" and values.get("name") == "period":
        if values.get("value") == "1 ms":
            settings.append({k: values[k] for k in ("id", "name", "value")})
    # JFR retains this thread's startup name (main), before Minecraft renames it Client thread.
    if values.get("sampledThread", {}).get("javaName") not in ("main", "Client thread") or not trace:
        continue
    frames = [f["method"]["type"]["name"].replace("/", ".") + "." + f["method"]["name"] for f in trace["frames"]]
    if not any("EntityRenderDispatcher.render" in f for f in frames):
        continue
    category = "other"
    for needle, name in (("EntityRenderer.renderShadow", "shadow"), ("LivingEntityRenderer.renderLayers", "layers"),
                         ("LivingEntityRenderer.renderModel", "model"), ("LivingEntityRenderer.render", "setup/cleanup")):
        if any(needle in f for f in frames):
            category = name
            break
    counts[event["type"]][category] += 1
    leaves[frames[0]] += 1
    if any("ModelPart.render" in f for f in frames):
        model_leaves[event["type"]][frames[0]] += 1
result = {"counts": dict(counts), "leaves": dict(leaves), "all_events": len(events),
          "scope": "whole launch including warmup; attribution only, not performance selection",
          "model_part_leaves": dict(model_leaves),
          "period_settings": settings,
          "periods": {"jdk.ExecutionSample": "1ms", "jdk.NativeMethodSample": "1ms"}}
(DEST / "samples.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
print(json.dumps(result, indent=2))
