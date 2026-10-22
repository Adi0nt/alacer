"""Align final JFR GC pauses with frame CSVs using the exported wall-clock anchor.

First export selected events (from repository root):
jfr print --json --events jdk.ExecutionSample,jdk.NativeMethodSample,jdk.GCPhasePause,jdk.JavaMonitorEnter,jdk.SafepointBegin,jdk.ExecuteVMOperation run/cp-detail-jfr-r5.jfr > build/cp-jfr-final-events.json
Then: python docs/benchmarks/2026-10-19-chunk-pipeline/analyze_jfr.py
"""
import collections
import csv
import datetime
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def millis(iso):
    return datetime.datetime.fromisoformat(iso).timestamp() * 1000


def overlap(a, b, c, d):
    return max(0, min(b, d)-max(a, c))


def main():
    path, = Path("run/alacer/benchmarks").glob("*-cp-detail-jfr-r5-*")
    summary = json.loads((path/"summary.json").read_text())
    env = summary["environment"]
    epoch = int(env["profiler.epochWallMillis"])
    uncertainty = int(env["profiler.epochWallUncertaintyNanos"])/1e6
    events = json.loads(Path("build/cp-jfr-final-events.json").read_text())["recording"]["events"]
    pauses = []
    samples = []
    monitors = []
    vm_intervals = []
    for event in events:
        v = event["values"]
        start = millis(v["startTime"])
        if event["type"] == "jdk.GCPhasePause":
            # JFR emits ISO second durations for these sub-minute events.
            duration = float(v["duration"][2:-1])*1000
            pauses.append({"start_ms":start, "end_ms":start+duration, "duration_ms":duration, "gc_id":v["gcId"]})
        elif event["type"] in ("jdk.ExecutionSample", "jdk.NativeMethodSample"):
            thread = v.get("sampledThread") or {}
            # Java 8 records the renamed client under its original 'main' JFR thread identity.
            if thread.get("javaThreadId") == 1:
                frames = (v.get("stackTrace") or {}).get("frames", [])
                stack = [f["method"]["type"]["name"]+"."+f["method"]["name"] for f in frames]
                samples.append((start, tuple(stack[:5])))
        elif event["type"] == "jdk.JavaMonitorEnter":
            thread = v.get("eventThread") or {}
            monitors.append({"start_ms":start, "thread":thread.get("javaName"), "duration":v["duration"]})
        elif event["type"] in ("jdk.SafepointBegin", "jdk.ExecuteVMOperation"):
            duration = float(v["duration"][2:-1])*1000
            vm_intervals.append({"type":event["type"], "start_ms":start, "end_ms":start+duration,
                                 "duration_ms":duration, "operation":v.get("operation"),
                                 "safepoint_id":v["safepointId"]})

    result = {"description":"Intrusive JFR attribution, not performance selection. Frame alignment uses a wall-clock anchor; alignment assumes no wall-clock jump during the run. Native and execution stack samples are not a CPU-time decomposition.", "clock_uncertainty_ms":uncertainty,
              "jfr_sha256":hashlib.sha256(Path("run/cp-detail-jfr-r5.jfr").read_bytes()).hexdigest(), "phases":{}}
    for phase in ("static", "pan", "fly"):
        rows = [{k:int(v) for k,v in r.items()} for r in csv.DictReader((path/(phase+".csv")).open())]
        begin = epoch+rows[0]["start_ns"]/1e6
        end = epoch+(rows[-1]["start_ns"]+rows[-1]["frame_ns"])/1e6
        gc = [p for p in pauses if overlap(begin, end, p["start_ms"], p["end_ms"])>0]
        stacks = collections.Counter(stack for time,stack in samples if begin<=time<end)
        worst = []
        for row in sorted(rows,key=lambda r:r["frame_ns"],reverse=True)[:20]:
            a = epoch+row["start_ns"]/1e6
            b = a+row["frame_ns"]/1e6
            central = sum(overlap(a,b,p["start_ms"],p["end_ms"]) for p in gc)
            definite = sum(overlap(a+uncertainty,b-uncertainty,p["start_ms"],p["end_ms"]) for p in gc)
            vm = [dict(v, overlap_ms=overlap(a,b,v["start_ms"],v["end_ms"])) for v in vm_intervals
                  if overlap(a,b,v["start_ms"],v["end_ms"])>0]
            worst.append({"start_ns":row["start_ns"],"frame_ms":row["frame_ns"]/1e6,
                          "estimated_gc_overlap_ms":central,"conservative_gc_overlap_ms":definite,
                          "vm_intervals":vm,
                          "chunk_upload_ms":row["chunk_upload_ns"]/1e6,"chunk_gl_list_ms":row["chunk_gl_list_ns"]/1e6})
        result["phases"][phase] = {"start_wall_ms_estimate":begin,"end_wall_ms_estimate":end,
            "gc_pauses":gc,"gc_pause_total_ms":sum(p["duration_ms"] for p in gc),
            "worst_frames":worst,"client_stack_samples":[{"samples":count,"stack":list(stack)} for stack,count in stacks.most_common(20)],
            "monitor_events":[v for v in monitors if begin<=v["start_ms"]<end]}
    (HERE/"jfr.json").write_text(json.dumps(result,indent=2)+"\n")
    (HERE/"jfr-vm.json").write_text(json.dumps(result["phases"]["fly"]["worst_frames"][:3],indent=2)+"\n")
    fly = result["phases"]["fly"]
    print("Fly GC pauses:",len(fly["gc_pauses"]),"total ms:",round(fly["gc_pause_total_ms"],3),"clock uncertainty ms:",round(uncertainty,3))
    print("Worst frames (ms, conservative GC overlap):",[(round(f["frame_ms"],2),round(f["conservative_gc_overlap_ms"],2)) for f in fly["worst_frames"][:10]])


if __name__ == "__main__": main()
