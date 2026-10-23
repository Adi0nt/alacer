"""Scores the frozen chunk scheduling series (protocol.md). Usage: python3 -I evaluate.py <benchmarks dir>"""
import glob, json, statistics, sys

root = sys.argv[1]
def load(label):
    found = sorted(glob.glob(f"{root}/*-{label}-vis_*/summary.json"))
    if len(found) != 1:
        raise SystemExit(f"{label}: expected one result, found {len(found)}")
    return json.load(open(found[0]))

ORDER = "ABBABAAB"
def pairs(scene):
    runs = [(arm, load(f"cs-{scene}-{i + 1}{arm.lower()}")) for i, arm in enumerate(ORDER)]
    out = []
    for i in range(0, 8, 2):
        a = next(r for arm, r in runs[i:i + 2] if arm == "A")
        b = next(r for arm, r in runs[i:i + 2] if arm == "B")
        out.append((a, b))
    return out

def change(a, b): return b / a - 1 if a else float("inf")
def metric(run, phase, key):
    p = run["phases"][phase]
    return 1000.0 / p["avg_fps"] if key == "frame_mean_ms" else p[key]

results, valid = {}, True
for scene, phase in (("edit", "edit"), ("flight", "fresh-flight")):
    ps = pairs(scene)
    for a, b in ps:
        for run in (a, b):
            for p in run["phases"].values():
                valid &= p["dropped_frames"] == 0 and p.get("update_latency_overflow", 0) == 0
    for key in ("update_latency_p50_ms", "update_latency_p90_ms", "update_latency_p99_ms", "update_latency_count",
                "chunk_visible_uncompiled_mean", "frame_mean_ms", "frame_p99_ms", "frame_max_ms"):
        cs = [change(metric(a, phase, key), metric(b, phase, key)) for a, b in ps]
        results[f"{scene}.{key}"] = {"A": [metric(a, phase, key) for a, _ in ps], "B": [metric(b, phase, key) for _, b in ps],
                                     "changes": cs, "median": statistics.median(cs)}
    cs = [change(metric(a, "still", "frame_mean_ms"), metric(b, "still", "frame_mean_ms")) for a, b in ps]
    results[f"{scene}.still.frame_mean_ms"] = {"changes": cs, "median": statistics.median(cs)}

r = results
gates = {
    "G1": all(c < 0 for c in r["edit.update_latency_p90_ms"]["changes"]) and r["edit.update_latency_p90_ms"]["median"] <= -0.25,
    "G2": sum(c < 0 for c in r["flight.update_latency_p90_ms"]["changes"]) >= 3 and r["flight.update_latency_p90_ms"]["median"] <= -0.10,
    "G3": r["flight.chunk_visible_uncompiled_mean"]["median"] <= 0.05,
    "G4": r["edit.frame_mean_ms"]["median"] <= 0.05 and r["edit.frame_p99_ms"]["median"] <= 0.10,
    "G5": r["flight.frame_mean_ms"]["median"] <= 0.05 and r["flight.frame_p99_ms"]["median"] <= 0.10,
    "G6": all(abs(r[f"{s}.still.frame_mean_ms"]["median"]) <= 0.05 for s in ("edit", "flight")),
    "G7": valid,
}
json.dump({"metrics": results, "gates": gates, "pass": all(gates.values())}, sys.stdout, indent=1)
print()
